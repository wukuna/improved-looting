package com.improvedlooting;

import java.awt.*;
import java.awt.event.*;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.event.*;

/** Search suggestions stay inside the editor; no floating menus or focus stealing. */
final class ItemPicker extends JPanel
{
    private final JTextField query=new JTextField();
    private final DefaultListModel<ItemSearch.Choice> model=new DefaultListModel<>();
    private final JList<ItemSearch.Choice> results=new JList<>(model);
    private final JLabel hint=new JLabel("Type at least 2 letters to search items");
    private final javax.swing.Timer debounce;
    private volatile int revision;
    private volatile boolean closed;
    private boolean choosing;
    private ItemSearch.Choice selected;
    private Consumer<ItemSearch.Choice> onSelect=c -> {};
    ItemPicker(ItemSearch search) { this(search,4); }
    ItemPicker(ItemSearch search,int rows)
    {
        super(new BorderLayout(4,4)); add(query,BorderLayout.NORTH);
        results.setVisibleRowCount(rows); results.setFixedCellHeight(34); results.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        results.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus) {
                JLabel label=(JLabel)super.getListCellRendererComponent(list,value,index,selected,focus);
                ItemSearch.Choice choice=(ItemSearch.Choice)value; label.setText(choice.name); label.setIcon(choice.image==null?null:new ImageIcon(choice.image));
                label.setBorder(BorderFactory.createEmptyBorder(2,6,2,6)); return label;
            }
        });
        JScrollPane scroll=new JScrollPane(results); scroll.setPreferredSize(new Dimension(240,rows*34+4)); add(scroll,BorderLayout.CENTER); add(hint,BorderLayout.SOUTH);
        debounce=new javax.swing.Timer(180,e -> {
            int current=revision; String term=query.getText();
            if(term.trim().length()<2) { hint.setText("Type at least 2 letters to search items"); return; }
            hint.setText("Searching item catalogue…");
            search.search(term,() -> !closed && revision==current,choices -> {
                if(closed || revision!=current) return;
                model.clear(); for(ItemSearch.Choice choice:choices) {
                    model.addElement(choice);
                    if(choice.image instanceof net.runelite.client.util.AsyncBufferedImage)
                        ((net.runelite.client.util.AsyncBufferedImage)choice.image).onLoaded(() -> SwingUtilities.invokeLater(() -> { if(!closed) results.repaint(); }));
                }
                hint.setText(choices.isEmpty()?"No matches. Exact names / * still work.":"Click a match, or use ↓ and Enter.");
                results.repaint();
            });
        }); debounce.setRepeats(false);
        query.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { changed(); }
            public void removeUpdate(DocumentEvent e) { changed(); }
            public void changedUpdate(DocumentEvent e) { changed(); }
            private void changed() { if(choosing) return; selected=null; revision++; model.clear(); debounce.restart(); onSelect.accept(null); }
        });
        query.addKeyListener(new KeyAdapter() { @Override public void keyPressed(KeyEvent e) { if(e.getKeyCode()==KeyEvent.VK_DOWN && !model.isEmpty()) { results.setSelectedIndex(0); results.requestFocusInWindow(); e.consume(); } } });
        results.addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) { int i=results.locationToIndex(e.getPoint()); if(i>=0 && results.getCellBounds(i,i).contains(e.getPoint())) choose(); } });
        results.getInputMap().put(KeyStroke.getKeyStroke("ENTER"),"choose");
        results.getActionMap().put("choose",new AbstractAction() { public void actionPerformed(ActionEvent e) { choose(); } });
    }
    void onSelect(Consumer<ItemSearch.Choice> action) { onSelect=action; }
    private void choose()
    {
        ItemSearch.Choice choice=results.getSelectedValue(); if(choice==null) return;
        choosing=true; query.setText(choice.name); choosing=false; selected=choice; revision++; debounce.stop();
        hint.setText("Selected: "+choice.name); onSelect.accept(choice); query.requestFocusInWindow();
    }
    void value(String name) { query.setText(name); }
    String value() { return query.getText(); }
    ItemSearch.Choice selected() { return selected; }
    void close() { closed=true; revision++; debounce.stop(); }
}
