package com.improvedlooting;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Map;
import javax.swing.*;

final class IconPalette extends JPanel
{
    static final class Selection
    {
        final ItemIcons.Kind kind;
        final int item;
        final String file, image;
        Selection(ItemIcons.Kind kind,int item,String file,String image) { this.kind=kind; this.item=item; this.file=file; this.image=image; }
        String expression() {
            if(kind==ItemIcons.Kind.CURRENT_ITEM) return "CurrentItem()";
            if(kind==ItemIcons.Kind.ITEM) return "Item("+item+")";
            if(kind==null) return null;
            return "File(\""+file.replace("\\","\\\\").replace("\"","\\\"")+"\")";
        }
    }
    private Selection selected=new Selection(ItemIcons.Kind.CURRENT_ITEM,0,null,null);
    private final JLabel summary=new JLabel("Selected: this item's picture");
    private final ButtonGroup group=new ButtonGroup();
    private final ItemPicker itemPicker;
    private final JToggleButton current;
    private final JToggleButton itemChoice=new JToggleButton("Choose an item picture below");
    private boolean closed;

    IconPalette(ItemIcons icons,Map<String,String> images) { this(icons,images,true); }
    IconPalette(ItemIcons icons,Map<String,String> images,boolean allowHidden)
    {
        super(new BorderLayout(4,4));
        JTabbedPane tabs=new JTabbedPane();
        JPanel common=new JPanel(new GridLayout(0,4,6,6));
        current=tile(common,"This item",null,selected); current.setSelected(true);
        if(allowHidden) tile(common,"Hidden",hiddenImage(),new Selection(null,0,null,null));
        for(ItemIcons.Kind kind:new ItemIcons.Kind[]{ItemIcons.Kind.HEART,ItemIcons.Kind.STAR,ItemIcons.Kind.DIAMOND}) {
            ItemIcons.Assignment assignment=new ItemIcons.Assignment(); assignment.kind=kind;
            BufferedImage image=icons.image(assignment,0);
            String encoded;
            try { encoded=IconImages.encode(image); } catch(IOException ex) { throw new IllegalArgumentException("Could not prepare icon",ex); }
            String name=kind.name().substring(0,1)+kind.name().substring(1).toLowerCase(java.util.Locale.ROOT);
            // Content-derived paths cannot overwrite a user's differently drawn image with the same name.
            String file="improved-icons/"+kind.name().toLowerCase(java.util.Locale.ROOT)+"-"+Integer.toHexString(encoded.hashCode())+".png";
            tile(common,name,image,new Selection(kind,0,file,encoded));
        }
        JPanel commonTop=new JPanel(new BorderLayout()); commonTop.add(common,BorderLayout.NORTH); tabs.addTab("Symbols",commonTop);
        if(!images.isEmpty()) {
            JPanel imported=new JPanel(new GridLayout(0,4,6,6));
            images.forEach((name,encoded) -> tile(imported,name,IconImages.decode(encoded),new Selection(ItemIcons.Kind.IMAGE,0,name,encoded)));
            JScrollPane scroll=new JScrollPane(imported); scroll.setPreferredSize(new Dimension(360,170)); tabs.addTab("Filter images",scroll);
        }
        itemPicker=new ItemPicker(icons.search,3);
        JPanel picture=new JPanel(new BorderLayout(4,4)); picture.add(itemPicker,BorderLayout.CENTER); picture.add(itemChoice,BorderLayout.SOUTH);
        group.add(itemChoice); itemChoice.setEnabled(false);
        itemPicker.onSelect(choice -> {
            itemChoice.setEnabled(choice!=null);
            if(choice!=null) {
                selected=new Selection(ItemIcons.Kind.ITEM,choice.id,null,null); itemChoice.setSelected(true);
                itemChoice.setText(choice.name); itemChoice.setIcon(new Thumbnail(choice.image)); summary.setIcon(null); summary.setText("Selected: "+choice.name);
                if(choice.image instanceof net.runelite.client.util.AsyncBufferedImage)
                    ((net.runelite.client.util.AsyncBufferedImage)choice.image).onLoaded(() -> SwingUtilities.invokeLater(() -> { if(!closed) itemChoice.repaint(); }));
            }
        });
        itemChoice.addActionListener(e -> { ItemSearch.Choice choice=itemPicker.selected(); if(choice!=null) { selected=new Selection(ItemIcons.Kind.ITEM,choice.id,null,null); summary.setText("Selected: "+choice.name); } });
        tabs.addTab("Item pictures",picture);
        add(tabs,BorderLayout.CENTER);
        JPanel footer=new JPanel(new BorderLayout(4,4)); footer.add(summary,BorderLayout.NORTH);
        JButton upload=new JButton("Add image file…"); footer.add(upload,BorderLayout.SOUTH);
        upload.addActionListener(e -> {
            java.util.List<net.runelite.client.util.Filepath> files=new net.runelite.client.util.Filepath.Chooser().setIsOpen().setAcceptsFiles().addExtensionFilter("Images","png","jpg","jpeg","gif").showDialog(this);
            if(files.isEmpty()) return;
            upload.setEnabled(false);
            new SwingWorker<String,Void>() {
                protected String doInBackground() throws Exception { return IconImages.read(files.get(0)); }
                protected void done() {
                    if(closed) return;
                    upload.setEnabled(true);
                    try {
                        String encoded=get();
                        selected=new Selection(ItemIcons.Kind.IMAGE,0,"improved-icons/upload-"+Integer.toHexString(encoded.hashCode())+".png",encoded);
                        group.clearSelection(); summary.setText("Selected: uploaded image"); summary.setIcon(new Thumbnail(IconImages.decode(encoded)));
                    } catch(Exception ex) { summary.setText("Image not loaded: "+(ex.getCause()==null?ex.getMessage():ex.getCause().getMessage())); }
                }
            }.execute();
        });
        add(footer,BorderLayout.SOUTH);
    }
    private JToggleButton tile(JPanel panel,String name,BufferedImage image,Selection selection)
    {
        String label=name.length()>15?name.substring(0,12)+"…":name;
        JToggleButton button=new JToggleButton(label,new Thumbnail(image)); button.setToolTipText(name);
        button.setHorizontalTextPosition(SwingConstants.CENTER); button.setVerticalTextPosition(SwingConstants.BOTTOM);
        button.setPreferredSize(new Dimension(100,64)); button.setMargin(new Insets(4,3,4,3));
        group.add(button); panel.add(button);
        button.addActionListener(e -> { selected=selection; summary.setIcon(null); summary.setText("Selected: "+name); }); return button;
    }
    void currentItem(ItemSearch.Choice choice) {
        current.setIcon(new Thumbnail(choice==null?null:choice.image)); current.setToolTipText(choice==null?"The matching item's own picture":choice.name);
        if(choice!=null && choice.image instanceof net.runelite.client.util.AsyncBufferedImage)
            ((net.runelite.client.util.AsyncBufferedImage)choice.image).onLoaded(() -> SwingUtilities.invokeLater(() -> { if(!closed) current.repaint(); }));
    }
    Selection selection() { return selected; }
    void close() { closed=true; itemPicker.close(); }
    static final class Thumbnail implements Icon
    {
        private final BufferedImage image;
        Thumbnail(BufferedImage image) { this.image=image; }
        public int getIconWidth() { return 32; }
        public int getIconHeight() { return 32; }
        public void paintIcon(Component c,Graphics g,int x,int y) {
            if(image==null) { g.setColor(Color.GRAY); g.drawRect(x+7,y+7,18,18); g.drawString("?",x+12,y+21); return; }
            double scale=Math.min(32d/image.getWidth(),32d/image.getHeight());
            int width=(int)(image.getWidth()*scale),height=(int)(image.getHeight()*scale);
            g.drawImage(image,x+(32-width)/2,y+(32-height)/2,width,height,c);
        }
    }
    private static BufferedImage hiddenImage()
    {
        BufferedImage image=new BufferedImage(32,32,BufferedImage.TYPE_INT_ARGB); Graphics2D g=image.createGraphics();
        try { g.setColor(new Color(220,85,85)); g.setStroke(new BasicStroke(3)); g.drawOval(4,4,23,23); g.drawLine(7,25,25,7); } finally { g.dispose(); } return image;
    }
}
