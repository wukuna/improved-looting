package com.improvedlooting;

import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javax.swing.*;
import net.runelite.client.util.Filepath;

/** A modeless settings window. Large backups never become Swing text documents. */
final class LootProfilesEditor
{
    private final LootProfiles profiles;
    private final Object profile;
    private final JComboBox<String> saved=new JComboBox<>();
    private final JTextField name=new JTextField(22);
    private final JComboBox<String> starter=new JComboBox<>(new String[]{"Show all loot", "Valuable loot", "Ground Items hide / highlight lists"});
    private final JTextArea summary=help("Choose a file, saved setup or starter to review. Nothing changes until you apply it.");
    private final JTextArea status=help(" ");
    private final java.util.List<JComponent> controls=new ArrayList<>();
    private JButton apply,delete;
    private JDialog dialog;
    private boolean closed,busy,confirmDelete;
    private int revision;
    private Review pending;

    private static final class Review
    {
        final LootProfiles.Setup setup,before;
        final String beforeJson,label;
        Review(LootProfiles.Setup setup,LootProfiles.Setup before,String beforeJson,String label){this.setup=setup;this.before=before;this.beforeJson=beforeJson;this.label=label;}
    }

    LootProfilesEditor(LootProfiles profiles){this.profiles=profiles;profile=profiles.icons.configs.getProfile();}
    void open(Component owner)
    {
        if(dialog!=null&&dialog.isDisplayable()){dialog.toFront();return;}
        dialog=new JDialog(SwingUtilities.getWindowAncestor(owner),"Settings — Improved Looting",Dialog.ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);dialog.setContentPane(build());
        dialog.setMinimumSize(new Dimension(600,520));dialog.setSize(680,570);dialog.setLocationRelativeTo(owner);
        dialog.addWindowListener(new java.awt.event.WindowAdapter(){public void windowClosed(java.awt.event.WindowEvent e){close();}});
        dialog.setVisible(true);
    }
    boolean isOpen(){return !closed&&dialog!=null&&dialog.isDisplayable();}

    JPanel build()
    {
        JPanel root=new JPanel(new BorderLayout(8,10));root.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        JPanel sections=new JPanel();sections.setLayout(new BoxLayout(sections,BoxLayout.Y_AXIS));
        JPanel transfer=section("Transfer settings");
        JTextArea instructions=help("Export a file, switch to the destination account / RuneLite profile, then import it here. Includes rules, tiers, sounds, images and item choices.");instructions.setRows(3);transfer.add(instructions);
        JPanel files=row();button(files,"Export settings…",this::chooseExport);button(files,"Import settings…",this::chooseImport);transfer.add(files);sections.add(transfer);

        JPanel setups=section("Saved setups");
        JPanel current=row();current.add(new JLabel("Name"));name.getAccessibleContext().setAccessibleName("Setup name");current.add(name);controls.add(name);
        button(current,"Save current",()->changeSaved(false));setups.add(current);
        JPanel stored=row();saved.setPreferredSize(new Dimension(260,28));saved.getAccessibleContext().setAccessibleName("Saved setups");stored.add(saved);controls.add(saved);
        button(stored,"Review",()->{String selected=(String)saved.getSelectedItem();if(selected==null)throw new IllegalArgumentException("Save a setup first.");String raw=libraryRaw();review(selected,()->profiles.encode(profiles.library(raw).presets.get(selected)));});
        delete=button(stored,"Delete",()->{if(!confirmDelete){confirmDelete=true;delete.setText("Confirm delete");status.setText("Click Confirm delete to remove this saved setup. Current settings are retained.");}else changeSaved(true);});
        saved.addActionListener(e->{confirmDelete=false;if(delete!=null)delete.setText("Delete");});setups.add(stored);sections.add(setups);

        JPanel starters=section("Start from");JPanel source=row();starter.setPreferredSize(new Dimension(330,28));source.add(starter);controls.add(starter);
        button(source,"Review starter",this::reviewStarter);starters.add(source);sections.add(starters);
        root.add(sections,BorderLayout.NORTH);
        JPanel review=section("Review");summary.setRows(4);summary.getAccessibleContext().setAccessibleName("Setup review");review.add(summary);
        JPanel actions=row();apply=button(actions,"Apply reviewed setup",this::applyReviewed);apply.setEnabled(false);
        button(actions,"Undo last change",()->{String raw=profiles.undo();if(raw==null)throw new IllegalArgumentException("No setup change to undo.");review("Undo last change",()->raw);});review.add(actions);root.add(review,BorderLayout.CENTER);
        status.setRows(2);status.getAccessibleContext().setAccessibleName("Settings status");root.add(status,BorderLayout.SOUTH);
        String raw=libraryRaw();background("Loading saved setups…",()->profiles.library(raw),library->{refresh(library);status.setText("Ready. Imported and saved setups are reviewed before applying.");});
        return root;
    }

    private void chooseExport()
    {
        java.util.List<Filepath> files=new Filepath.Chooser().setIsSave().setAcceptsFiles().setDialogTitle("Export Looting settings")
            .setFileName("improved-looting-settings.json").setDefaultExtension("json").addExtensionFilter("Looting settings", "json").showDialog(dialog);
        if(files!=null&&!files.isEmpty()&&valid())exportTo(files.get(0));
    }
    void exportTo(Filepath file)
    {
        if(!valid()||busy)return;LootProfiles.Setup snapshot=profiles.capture();
        background("Exporting settings… You can keep playing.",()->{
            String raw=profiles.encode(snapshot);
            // Never silently overwrite an existing backup selected in the chooser.
            try(Writer writer=file.openBufferedWriter(java.nio.file.StandardOpenOption.CREATE_NEW,java.nio.file.StandardOpenOption.WRITE)){writer.write(raw);}
            return raw.length();
        },size->status.setText("Settings exported ("+size+" characters). Import this file in the destination profile."));
    }
    private void chooseImport()
    {
        java.util.List<Filepath> files=new Filepath.Chooser().setIsOpen().setAcceptsFiles().setDialogTitle("Import Looting settings")
            .addExtensionFilter("Looting settings", "json", "txt").showDialog(dialog);
        if(files!=null&&!files.isEmpty()&&valid())importFrom(files.get(0));
    }
    void importFrom(Filepath file){review("Imported settings",()->{try(Reader reader=file.openBufferedReader()){return read(reader);}});}
    static String read(Reader reader)throws IOException
    {
        StringBuilder raw=new StringBuilder();char[] buffer=new char[8192];int count;
        while((count=reader.read(buffer))!=-1){if(raw.length()+count>24000000)throw new IllegalArgumentException("Setup exceeds 24 MB");raw.append(buffer,0,count);}
        return raw.toString();
    }
    private void reviewStarter()
    {
        int selected=starter.getSelectedIndex();LootProfiles.Setup snapshot=profiles.capture();
        String highlights=profiles.icons.configs.getConfiguration("grounditems","highlightedItems"),hidden=profiles.icons.configs.getConfiguration("grounditems","hiddenItems");
        review((String)starter.getSelectedItem(),()->selected==2?profiles.groundItemsPreview(snapshot,highlights,hidden):profiles.starter(selected==1,snapshot));
    }
    void review(String label,Callable<String> source)
    {
        if(!valid()||busy)return;LootProfiles.Setup before=profiles.capture();pending=null;
        background("Checking settings, rules, images and sounds…",()->new Review(profiles.parse(source.call()),before,profiles.encode(before),label),ready->{
            pending=ready;apply.setEnabled(true);
            summary.setText(ready.label+"\nReady to replace this profile's Looting settings, rules, tiers, sounds, images and item choices. Missing options return to their defaults.\nUndo keeps the complete previous setup.");
            status.setText("Ready to apply. Current settings have not changed.");
        });
    }
    private void applyReviewed()
    {
        if(pending==null)return;
        profiles.applyValidated(pending.setup,profile,pending.before,pending.beforeJson);pending=null;apply.setEnabled(false);
        summary.setText("Setup applied. Use Undo last change to review and restore the previous setup.");status.setText("Settings applied to this profile.");
    }
    private String libraryRaw(){return profiles.icons.configs.getConfiguration(ImprovedLootingConfig.GROUP,LootProfiles.SAVED);}
    private void changeSaved(boolean remove)
    {
        String selected=remove?(String)saved.getSelectedItem():name.getText().trim();
        if(selected==null||selected.isBlank()||!LootPreferences.validLabel(selected,60))throw new IllegalArgumentException("Enter a setup name of 1–60 characters.");
        String previous=libraryRaw();LootProfiles.Setup snapshot=profiles.capture();
        background(remove?"Removing saved setup…":"Saving current setup…",()->{
            LootProfiles.Library library=profiles.library(previous);if(remove)library.presets.remove(selected);else library.presets.put(selected,snapshot);
            return profiles.encodeLibrary(library);
        },raw->{
            if(!Objects.equals(previous,libraryRaw()))throw new IllegalArgumentException("Saved setups changed. Reopen Settings and try again.");
            profiles.icons.configs.setConfiguration(ImprovedLootingConfig.GROUP,LootProfiles.SAVED,raw);
            // Update the small name list without parsing the asset-filled library on EDT.
            if(remove)saved.removeItem(selected);else{boolean exists=false;for(int i=0;i<saved.getItemCount();i++)exists|=selected.equals(saved.getItemAt(i));if(!exists)saved.addItem(selected);saved.setSelectedItem(selected);}
            confirmDelete=false;delete.setText("Delete");status.setText(remove?"Saved setup removed. Current settings are retained.":"Current setup saved. Use Export settings to move it to another profile.");
        });
    }
    private void refresh(LootProfiles.Library library){saved.removeAllItems();for(String key:library.presets.keySet())saved.addItem(key);}
    private boolean valid()
    {
        if(closed)return false;
        if(!Objects.equals(profile,profiles.icons.configs.getProfile())){pending=null;apply.setEnabled(false);status.setText("Profile changed. Close and reopen Settings in the intended profile.");return false;}
        return true;
    }
    private <T> void background(String message,Callable<T> work,Consumer<T> done)
    {
        if(!valid()||busy)return;busy=true;int token=++revision;controls.forEach(c->c.setEnabled(false));status.setText(message);
        new SwingWorker<T,Void>(){
            protected T doInBackground()throws Exception{return work.call();}
            protected void done(){
                if(closed||token!=revision)return;busy=false;controls.forEach(c->c.setEnabled(true));apply.setEnabled(pending!=null);
                if(!valid())return;try{done.accept(get());}catch(Exception e){Throwable cause=e.getCause()==null?e:e.getCause();if(cause instanceof java.nio.file.FileAlreadyExistsException)status.setText("That file already exists. Export again with a new name to keep both backups.");else error(cause);}
            }
        }.execute();
    }
    private JButton button(JPanel parent,String label,Runnable action)
    {
        JButton button=new JButton(label);controls.add(button);button.addActionListener(e->{if(!valid()||busy)return;try{action.run();}catch(Exception ex){error(ex);}});parent.add(button);return button;
    }
    private void error(Throwable error){status.setText("Not changed: "+error.getMessage());}
    private static JPanel row(){return new JPanel(new FlowLayout(FlowLayout.LEFT,6,4));}
    private static JPanel section(String label){JPanel panel=new JPanel();panel.setLayout(new BoxLayout(panel,BoxLayout.Y_AXIS));panel.setBorder(BorderFactory.createTitledBorder(label));return panel;}
    private static JTextArea help(String text){JTextArea area=new JTextArea(text);area.setEditable(false);area.setOpaque(false);area.setLineWrap(true);area.setWrapStyleWord(true);area.setColumns(45);return area;}
    void close(){closed=true;revision++;pending=null;if(dialog!=null&&dialog.isDisplayable())dialog.dispose();}
}
