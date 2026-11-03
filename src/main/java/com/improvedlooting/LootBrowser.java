package com.improvedlooting;

import java.awt.*;
import java.awt.event.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.swing.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.LinkBrowser;

final class LootBrowser
{
    private final GroundLoot loot;
    private final ItemIcons icons;
    private final ClientThread clientThread;
    private final LootPreferences preferences;
    private final DefaultListModel<LootRow> model=new DefaultListModel<>();
    private final JList<LootRow> list=new JList<>(model);
    private final JTextField search=new JTextField(),categoryFilter=new JTextField(),category=new JTextField();
    private final JTextArea inspector=new JTextArea();
    private final JCheckBox hidden=new JCheckBox("Include hidden",true),grid=new JCheckBox("Grid");
    private final JComboBox<String> order=new JComboBox<>(new String[]{"Value","Distance","Time remaining","Name"});
    private final JComboBox<LootMenuChoice> menuChoice=new JComboBox<>(LootMenuChoice.values());
    private final JLabel status=new JLabel();
    private final javax.swing.Timer timer;
    private final Object profile;
    private JDialog dialog;
    private FilterEditor editor;
    private volatile boolean closed;
    private boolean refreshing;
    private int selectionRevision;
    LootBrowser(GroundLoot loot,ItemIcons icons,ClientThread thread,LootPreferences preferences)
    { this.loot=loot;this.icons=icons;this.clientThread=thread;this.preferences=preferences;profile=icons.configs.getProfile(); timer=new javax.swing.Timer(600,e->refresh()); }
    JPanel content()
    {
        JPanel root=new JPanel(new BorderLayout(8,8));root.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        JPanel filters=new JPanel(new GridLayout(2,3,8,5));
        search.setToolTipText("Filter nearby item names");categoryFilter.setToolTipText("Filter your categories");
        filters.add(new JLabel("Search items"));filters.add(new JLabel("Category contains"));filters.add(order);
        filters.add(search);filters.add(categoryFilter);
        JPanel mode=new JPanel(new FlowLayout(FlowLayout.LEFT,3,0));mode.add(hidden);mode.add(grid);filters.add(mode);root.add(filters,BorderLayout.NORTH);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);list.setFixedCellHeight(60);
        list.setCellRenderer(new DefaultListCellRenderer(){
            public Component getListCellRendererComponent(JList<?> l,Object value,int index,boolean selected,boolean focus){
                JLabel label=(JLabel)super.getListCellRendererComponent(l,value,index,selected,focus);LootRow r=(LootRow)value;
                label.setText((grid.isSelected()?"<html><body style='width:185px'>":"<html>")+escape(r.name)+" × "+r.quantity+(r.hidden?" · hidden":"")+"<br>"+String.format("%,d",r.value)+" gp · "+r.distance+" tiles · "+(r.seconds<0?"time unknown":r.seconds+"s")+(r.category.isEmpty()?"":" · "+escape(r.category))+"</html>");
                label.setIcon(r.image==null?null:new IconPalette.Thumbnail(r.image));
                label.setBorder(BorderFactory.createEmptyBorder(4,6,4,6));return label;
            }});
        list.addListSelectionListener(e->{if(!e.getValueIsAdjusting()&&!refreshing) inspect();});
        grid.addActionListener(e->{list.setLayoutOrientation(grid.isSelected()?JList.HORIZONTAL_WRAP:JList.VERTICAL);list.setVisibleRowCount(grid.isSelected()?-1:0);list.setFixedCellWidth(grid.isSelected()?220:-1);list.setFixedCellHeight(grid.isSelected()?96:60);});
        inspector.setEditable(false);inspector.setLineWrap(true);inspector.setWrapStyleWord(true);inspector.setText("Select an item to see the rules and final settings. Selection highlights its tile for 10 seconds.");
        JPanel detail=new JPanel(new BorderLayout(6,6));detail.add(new JScrollPane(inspector),BorderLayout.CENTER);
        JPanel actions=new JPanel(new GridLayout(0,2,6,6));
        for(LootPreferences.Action action:LootPreferences.Action.values()) button(actions,action==LootPreferences.Action.FILTER?"Use filter":title(action.name()),()->override(action));
        actions.add(new JLabel("Item category"));actions.add(category);
        button(actions,"Save category",()->override(null));button(actions,"Create filter rule",this::createRule);
        menuChoice.setToolTipText("This exact item: override matching menu rules and the global setting. Visible tags always keep their menu actions.");
        actions.add(new JLabel("Hidden tag in menu"));actions.add(menuChoice);
        button(actions,"Save menu choice",this::saveMenu);button(actions,"Create menu rule",this::createMenuRule);
        button(actions,"Wiki drop table",this::wiki);button(actions,"Source availability",this::source);
        detail.add(actions,BorderLayout.SOUTH);
        JSplitPane split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,new JScrollPane(list),detail);split.setResizeWeight(.55);split.setDividerLocation(470);root.add(split,BorderLayout.CENTER);
        root.add(status,BorderLayout.SOUTH);refresh();return root;
    }
    void open(Component owner){dialog=new JDialog(SwingUtilities.getWindowAncestor(owner),"Nearby loot — Improved Looting",Dialog.ModalityType.MODELESS);dialog.setContentPane(content());dialog.setSize(960,650);dialog.setMinimumSize(new Dimension(750,500));dialog.setLocationRelativeTo(owner);dialog.addWindowListener(new WindowAdapter(){public void windowClosing(WindowEvent e){close();}});dialog.setVisible(true);timer.start();}
    private boolean valid(){return !closed&&Objects.equals(profile,icons.configs.getProfile());}
    private void refresh()
    {
        if(!valid()){close();return;}
        LootRow before=list.getSelectedValue();long key=before==null?-1:before.key;
        java.util.List<LootRow> rows=new ArrayList<>();String query=search.getText().trim().toLowerCase(Locale.ROOT),cat=categoryFilter.getText().trim().toLowerCase(Locale.ROOT);
        for(LootRow row:loot.rows())if((hidden.isSelected()||!row.hidden)&&row.name.toLowerCase(Locale.ROOT).contains(query)&&row.category.toLowerCase(Locale.ROOT).contains(cat))rows.add(row);
        Comparator<LootRow> sort=order.getSelectedIndex()==1?Comparator.comparingInt(r->r.distance):order.getSelectedIndex()==2?Comparator.comparingInt(r->r.seconds<0?Integer.MAX_VALUE:r.seconds):order.getSelectedIndex()==3?Comparator.comparing(r->r.name,String.CASE_INSENSITIVE_ORDER):Comparator.<LootRow>comparingLong(r->r.value).reversed();rows.sort(sort.thenComparingLong(r->r.key));
        refreshing=true;model.clear();for(LootRow row:rows){model.addElement(row);if(row.key==key)list.setSelectedIndex(model.size()-1);}refreshing=false;
        if(before!=null&&list.getSelectedValue()==null){selectionRevision++;inspector.setText("That pile is no longer in this view.");}
        status.setText(loot.status()+" · "+rows.size()+" shown (browser limit 512) · selection never picks up items");
    }
    private void inspect()
    {
        LootRow row=list.getSelectedValue();int revision=++selectionRevision;if(row==null)return;category.setText(row.category);menuChoice.setSelectedItem(LootMenuChoice.of(preferences.hideMenu(row.itemId)));inspector.setText("Reading resolved rules…");
        clientThread.invokeLater(()->{if(!valid())return;loot.select(row.key);String text=loot.explain(row.key);SwingUtilities.invokeLater(()->{if(valid()&&revision==selectionRevision){inspector.setText(text);inspector.setCaretPosition(0);}});});
    }
    private void override(LootPreferences.Action action)
    {
        LootRow row=list.getSelectedValue();if(row==null||!valid())return;
        try{LootPreferences.ItemRule existing=preferences.snapshot().items.get(row.itemId);preferences.set(row.itemId,action!=null?action:existing==null?LootPreferences.Action.FILTER:existing.action,category.getText());
            if(action!=null)preferences.labelHidden(row.itemId,action==LootPreferences.Action.FILTER?null:action==LootPreferences.Action.HIDE);
            inspector.append("\nSaved. The next game tick refreshes this view.");}catch(IllegalArgumentException e){error(e);}
    }
    private void createRule(){LootRow row=list.getSelectedValue();if(row==null||!valid())return;if(editor!=null)editor.close();editor=new FilterEditor(icons);editor.seedName(row.name);editor.open(dialog);}
    private void createMenuRule(){LootRow row=list.getSelectedValue();if(row==null||!valid())return;if(editor!=null && editor.isOpen()){editor.focus();return;}editor=new FilterEditor(icons);editor.seedName(row.name);editor.menuOnly();editor.open(dialog);}
    private void saveMenu()
    {
        LootRow row=list.getSelectedValue();if(row==null||!valid())return;
        try{preferences.hideMenu(row.itemId,((LootMenuChoice)menuChoice.getSelectedItem()).hide);inspect();}
        catch(IllegalArgumentException e){error(e);}
    }
    private void source()
    {
        JOptionPane.showMessageDialog(dialog,"RuneLite 1.13.4 reports monster loot without ground-pile locations.\nThe source of a particular pile is unavailable, so monster-source filters are inactive.\nItem, value and ownership rules remain available; we never infer a source from nearby NPCs.");
    }
    private void wiki()
    {
        LootRow row=list.getSelectedValue();String name=JOptionPane.showInputDialog(dialog,"Monster / activity to look up on the OSRS Wiki. After reviewing Drops, use Create filter rule or the item search in Edit rules.",row!=null&&row.source!=null?row.source:"");
        if(name!=null&&!name.trim().isEmpty())LinkBrowser.browse(wikiUrl(name));
    }
    static String wikiUrl(String name){return "https://oldschool.runescape.wiki/w/"+URLEncoder.encode(name.trim().replace(' ','_'),StandardCharsets.UTF_8).replace("+","%20")+"#Drops";}
    private void error(Exception e){JOptionPane.showMessageDialog(dialog,e.getMessage(),"Not changed",JOptionPane.WARNING_MESSAGE);}
    static String escape(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
    private static String title(String s){return s.charAt(0)+s.substring(1).toLowerCase(Locale.ROOT);}
    private static void button(JPanel p,String label,Runnable action){JButton b=new JButton(label);b.addActionListener(e->action.run());p.add(b);}
    void close(){closed=true;selectionRevision++;timer.stop();if(editor!=null)editor.close();if(dialog!=null)dialog.dispose();}
}
