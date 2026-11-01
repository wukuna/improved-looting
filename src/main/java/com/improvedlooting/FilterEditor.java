package com.improvedlooting;

import java.awt.*;
import java.awt.event.*;
import java.util.Objects;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.undo.UndoManager;

/** Draft-based editor: compiling and closing never replace the active filter. */
final class FilterEditor
{
    private final ItemIcons icons;
    private final JTextArea source = new JTextArea();
    private final JTextArea status = new JTextArea(3, 50);
    private final JButton save = new JButton("Save filter");
    private final JButton validate = new JButton("Check draft");
    private JDialog dialog;
    private Object profile;
    private String originalBackup, savedSource;
    private FilterIcons.Bundle original=new FilterIcons.Bundle();
    private FilterIcons.Prepared checked;
    private int revision;
    private boolean closed;
    private String seedName;
    private boolean menuOnly;
    void seedName(String name) { seedName=name; }
    void menuOnly(){menuOnly=true;}
    private ItemPicker itemPicker;
    private IconPalette iconPalette;
    private RuleOptions options;

    FilterEditor(ItemIcons icons) { this.icons = icons; }

    void open(Component owner)
    {
        profile = icons.configs.getProfile();
        originalBackup = icons.filters.export();
        original = originalBackup.isBlank() ? new FilterIcons.Bundle() : icons.gson.fromJson(originalBackup, FilterIcons.Bundle.class);
        savedSource = originalBackup.isBlank() ? "" : FilterIcons.source(original);
        source.setText(savedSource);
        source.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        source.setTabSize(4);
        UndoManager undo = new UndoManager();
        source.getDocument().addUndoableEditListener(undo);
        source.getInputMap().put(KeyStroke.getKeyStroke("control Z"), "undoDraft");
        source.getActionMap().put("undoDraft", new AbstractAction() { public void actionPerformed(ActionEvent e) { if (undo.canUndo()) undo.undo(); } });
        source.getInputMap().put(KeyStroke.getKeyStroke("control Y"), "redoDraft");
        source.getActionMap().put("redoDraft", new AbstractAction() { public void actionPerformed(ActionEvent e) { if (undo.canRedo()) undo.redo(); } });
        source.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { changed(); }
            public void removeUpdate(DocumentEvent e) { changed(); }
            public void changedUpdate(DocumentEvent e) { changed(); }
        });
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        root.add(new JLabel("<html><b>Filter editor</b><br>Changes stay in this draft until you check and save. Existing image files are retained.</html>"), BorderLayout.NORTH);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Build a rule", builder(tabs));
        JPanel advanced = new JPanel(new BorderLayout(8, 8));
        advanced.add(new JLabel("Full Filterscape script • Ctrl+Z undo • Ctrl+Y redo • copy all to export"), BorderLayout.NORTH);
        advanced.add(new JScrollPane(source), BorderLayout.CENTER);
        tabs.addTab("Advanced script", advanced);
        root.add(tabs, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout(8, 8));
        status.setEditable(false); status.setLineWrap(true); status.setWrapStyleWord(true); status.setOpaque(false);
        status.setText("Rules control ground labels and icons. Appearance and Alerts are in the Improved Looting side panel.");
        footer.add(new JScrollPane(status), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("Close"); cancel.addActionListener(e -> requestClose());
        validate.addActionListener(e -> check()); save.setEnabled(false); save.addActionListener(e -> save());
        actions.add(cancel); actions.add(validate); actions.add(save); footer.add(actions, BorderLayout.SOUTH);
        root.add(footer, BorderLayout.SOUTH);
        dialog = new JDialog(SwingUtilities.getWindowAncestor(owner), "Filter editor — Improved Looting", Dialog.ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dialog.addWindowListener(new WindowAdapter() { public void windowClosing(WindowEvent e) { requestClose(); } });
        dialog.setContentPane(root); dialog.setMinimumSize(new Dimension(660, 580)); dialog.setSize(900, 760);
        dialog.setLocationRelativeTo(owner); dialog.setVisible(true);
    }

    JPanel builder(JTabbedPane tabs)
    {
        JPanel body=new JPanel(new BorderLayout(10,10));
        body.setBorder(BorderFactory.createEmptyBorder(8,4,8,4));
        JTextArea explanation=new JTextArea("Find an item, choose an icon, then add the rule to your draft. You can still type a wildcard pattern such as *potion*. Earlier matching rules take priority.");
        explanation.setPreferredSize(new Dimension(400,30));
        explanation.setRows(2); explanation.setLineWrap(true); explanation.setWrapStyleWord(true); explanation.setEditable(false); explanation.setOpaque(false);
        body.add(explanation,BorderLayout.NORTH);
        itemPicker=new ItemPicker(icons.search);
        iconPalette=new IconPalette(icons,original.images);
        itemPicker.onSelect(iconPalette::currentItem);
        if(seedName!=null)itemPicker.value(seedName);
        JPanel match=new JPanel(new BorderLayout()); match.setBorder(BorderFactory.createTitledBorder("1. Find the item")); match.add(itemPicker,BorderLayout.NORTH);
        JPanel visual=new JPanel(new BorderLayout()); visual.setBorder(BorderFactory.createTitledBorder("2. Choose its icon")); visual.add(iconPalette,BorderLayout.CENTER);
        JPanel columns=new JPanel(new GridLayout(1,2,10,0)); columns.add(match); columns.add(visual);
        body.add(columns,BorderLayout.CENTER);
        JPanel actions=new JPanel(new BorderLayout(8,8));
        JComboBox<String> position=new JComboBox<>(new String[]{"Before existing rules (higher priority)","After existing rules (fallback)"});
        if(original.sounds==null)original.sounds=new java.util.LinkedHashMap<>();
        options=new RuleOptions(icons,original.sounds);
        options.onMenuModeChanged(()->{enableControls(visual,!options.isMenuOnly());position.setEnabled(!options.isMenuOnly());if(options.isMenuOnly())position.setSelectedIndex(0);
            explanation.setText(options.isMenuOnly()?"Choose an item name or wildcard, then set conditions and its hidden-tag menu choice. Existing appearance rules stay in effect.":"Find an item, choose an icon, then add the rule to your draft. Use Conditions and menu for value, ownership or region limits.");});
        if(menuOnly)options.menuOnly();
        JPanel choices=new JPanel(new BorderLayout(5,5));choices.add(options,BorderLayout.CENTER);choices.add(position,BorderLayout.SOUTH);
        actions.add(choices,BorderLayout.NORTH);
        JButton add=new JButton("Add to draft and review");
        add.addActionListener(e -> {
            try {
                IconPalette.Selection choice=options.isMenuOnly()?null:iconPalette.selection();
                String rule=options.apply(choice==null?rule(itemPicker.value(),0,0):rule(itemPicker.value(),choice));
                if(choice!=null && choice.image!=null) {
                    String existing=original.images.get(choice.file);
                    if(existing!=null && !existing.equals(choice.image)) throw new IllegalArgumentException("An existing image uses that filename. Choose a different icon.");
                    original.images.put(choice.file,choice.image);
                }
                String text=source.getText();
                source.setText(position.getSelectedIndex()==0?rule+"\n"+text:text+"\n"+rule);
                tabs.setSelectedIndex(1); source.setCaretPosition(position.getSelectedIndex()==0?0:source.getDocument().getLength());
            } catch(IllegalArgumentException ex) { status.setText(ex.getMessage()); }
        });
        actions.add(add,BorderLayout.SOUTH); body.add(actions,BorderLayout.SOUTH);
        return body;
    }

    private static void enableControls(Container parent,boolean enabled){for(Component child:parent.getComponents()){child.setEnabled(enabled);if(child instanceof Container)enableControls((Container)child,enabled);}}

    static String rule(String name,IconPalette.Selection selection)
    {
        if(selection.kind==null) return rule(name,2,0);
        return rule(name,0,0).replace("icon=CurrentItem();","icon="+selection.expression()+";");
    }

    static String rule(String name, int effect, int item)
    {
        name = name.trim();
        if (name.isEmpty() || name.length() > 200 || name.indexOf('"') >= 0 || name.indexOf('\\') >= 0 || name.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Enter an item name without quotes, backslashes or line breaks (up to 200 characters).");
        if (effect < 0 || effect > 2 || item < 0 || item > 100000) throw new IllegalArgumentException("Invalid icon choice.");
        String display = effect == 2 ? "hideOverlay=true;" : "hidden=false; hideOverlay=false; icon=" + (effect == 0 ? "CurrentItem()" : "Item(" + item + ")") + ";";
        return "rule(name:\"" + name + "\") { " + display + " }\n";
    }

    private void changed() { revision++; checked = null; save.setEnabled(false); status.setText("Draft changed. Check it before saving; your active filter is unchanged."); }
    private boolean contextValid()
    {
        if (closed) return false;
        if (!Objects.equals(profile, icons.configs.getProfile()) || !Objects.equals(originalBackup, icons.filters.export())) {
            checked = null; save.setEnabled(false); status.setText("The profile or active filter changed elsewhere. Copy your draft, then reopen the editor to avoid overwriting those changes."); return false;
        }
        return true;
    }
    private void check()
    {
        if (!contextValid()) return;
        final int version = revision;
        FilterIcons.Bundle draft = new FilterIcons.Bundle(); draft.compressedSource = FilterIcons.compress(source.getText()); draft.images.putAll(original.images);
        if (original.sounds != null) draft.sounds.putAll(original.sounds);
        validate.setEnabled(false); save.setEnabled(false); status.setText("Checking script and image references…");
        new SwingWorker<FilterIcons.Prepared, Void>() {
            protected FilterIcons.Prepared doInBackground() { return icons.filters.prepare(draft); }
            protected void done() {
                validate.setEnabled(true);
                if (!contextValid() || revision != version) return;
                try { checked = get(); save.setEnabled(true); status.setText("Ready to save: " + checked.program.rules.size() + " rules, " + checked.images.size() + " referenced images. Your previous filter will be available through Undo filter import."); }
                catch (Exception e) { checked = null; status.setText("Not saved. " + (e.getCause() == null ? e.getMessage() : e.getCause().getMessage())); }
            }
        }.execute();
    }
    private void save()
    {
        if (!contextValid() || checked == null) return;
        icons.filters.save(checked); originalBackup = icons.filters.export(); original = checked.bundle; savedSource = source.getText();
        save.setEnabled(false); status.setText("Filter saved. Ground display follows your Appearance settings. Connected menu icons use Improved Right-Clicks.");
    }
    private void requestClose()
    {
        if (!source.getText().equals(savedSource) && JOptionPane.showConfirmDialog(dialog, "Discard unsaved changes?", "Close filter editor", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        close();
    }
    void close() { closed = true; if(options!=null)options.close(); if(itemPicker!=null) itemPicker.close(); if(iconPalette!=null) iconPalette.close(); if (dialog != null) dialog.dispose(); }
    boolean isOpen() { return dialog != null && dialog.isDisplayable(); }
    void focus() { dialog.toFront(); }
}
