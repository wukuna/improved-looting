package com.improvedlooting;

import java.awt.*;
import javax.swing.*;

final class LootProfilesEditor
{
    private final LootProfiles profiles;
    private final JTextArea text=new JTextArea();
    private final JComboBox<String> saved=new JComboBox<>();
    private final JLabel status=new JLabel("Export / paste a complete setup, then validate and apply. Current settings are saved for Undo.");
    private JDialog dialog;
    private volatile boolean closed;
    private final Object profile;
    private int revision;
    LootProfilesEditor(LootProfiles profiles){this.profiles=profiles;profile=profiles.icons.configs.getProfile();}
    void open(Component owner)
    {
        JPanel root=new JPanel(new BorderLayout(8,8));root.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        JPanel actions=new JPanel(new GridLayout(0,3,6,6));actions.add(saved);
        button(actions,"Save current preset",()->{String name=JOptionPane.showInputDialog(dialog,"Preset name");if(name!=null){profiles.save(name);refresh();}});
        button(actions,"Load preset preview",()->{String name=(String)saved.getSelectedItem();if(name!=null)text.setText(profiles.icons.gson.toJson(profiles.library().presets.get(name)));});
        button(actions,"Delete saved preset",()->{String name=(String)saved.getSelectedItem();if(name!=null&&JOptionPane.showConfirmDialog(dialog,"Delete saved preset “"+name+"”? Current settings are retained.","Delete preset",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION){profiles.remove(name);refresh();}});
        button(actions,"Export current setup",()->text.setText(profiles.export()));button(actions,"Apply pasted setup",()->apply(text.getText()));
        button(actions,"Preview visible starter",()->text.setText(profiles.starter(false)));button(actions,"Preview valuable starter",()->text.setText(profiles.starter(true)));
        button(actions,"Undo last setup change",()->{String before=profiles.undo();if(before==null)throw new IllegalArgumentException("No setup change to undo");apply(before);});
        button(actions,"Copy Ground Items lists",()->{text.setText(profiles.groundItemsPreview());status.setText("Preview: hide / highlight names only. Apply replaces our filter; original Ground Items settings are untouched.");});
        root.add(actions,BorderLayout.NORTH);text.setLineWrap(true);text.setWrapStyleWord(true);root.add(new JScrollPane(text),BorderLayout.CENTER);root.add(status,BorderLayout.SOUTH);refresh();
        dialog=new JDialog(SwingUtilities.getWindowAncestor(owner),"Presets and backups — Improved Looting",Dialog.ModalityType.MODELESS);dialog.setContentPane(root);dialog.setSize(850,580);dialog.setLocationRelativeTo(owner);dialog.addWindowListener(new java.awt.event.WindowAdapter(){public void windowClosing(java.awt.event.WindowEvent e){close();}});dialog.setVisible(true);
    }
    private boolean valid(){return !closed&&java.util.Objects.equals(profile,profiles.icons.configs.getProfile());}
    private void apply(String raw)
    {
        if(!valid())return;String before=profiles.export();int token=++revision;status.setText("Checking settings, filter, images and sounds…");
        new SwingWorker<LootProfiles.Setup,Void>(){protected LootProfiles.Setup doInBackground(){return profiles.parse(raw);}protected void done(){if(!valid()||token!=revision)return;try{profiles.applyValidated(get(),profile,before);status.setText("Setup applied. Undo restores the complete previous setup.");}catch(Exception e){error(e);}}}.execute();
    }
    private void refresh(){saved.removeAllItems();for(String name:profiles.library().presets.keySet())saved.addItem(name);}
    private void button(JPanel p,String label,Runnable action){JButton b=new JButton(label);b.addActionListener(e->{if(!valid())return;try{action.run();}catch(Exception ex){error(ex);}});p.add(b);}
    private void error(Exception e){Throwable cause=e.getCause()==null?e:e.getCause();status.setText("Not changed: "+cause.getMessage());}
    void close(){closed=true;revision++;if(dialog!=null)dialog.dispose();}
}
