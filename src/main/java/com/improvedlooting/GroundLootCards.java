package com.improvedlooting;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Tile;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.AsyncBufferedImage;

/** Draw-only cards. Mouse position can expand a pile, but no input is intercepted. */
final class GroundLootCards
{
    @Inject Client client;
    @Inject ClientThread thread;
    @Inject GroundLoot loot;
    @Inject ImprovedLootingConfig config;
    @Inject PriceTiers tiers;
    @Inject LootLabelInput input;
    private final Map<List<Object>,BufferedImage> images=new LinkedHashMap<List<Object>,BufferedImage>(128,.75f,true){protected boolean removeEldestEntry(Map.Entry<List<Object>,BufferedImage> e){return size()>512;}};
    private final Map<BufferedImage,Boolean> ready=new LinkedHashMap<>();
    private final PriceTiers.Setup defaultTiers=PriceTiers.defaults();
    private Tile expanded;
    private Rectangle expandedBounds;
    private long generation;
    void clear(){generation++;images.clear();ready.clear();expanded=null;expandedBounds=null;}
    BufferedImage image(GroundLoot.Entry entry,int size,double fraction)
    {
        BufferedImage source=entry.image!=null?entry.image:loot.icons.items.getImage(entry.item.getId());if(source==null)return null;
        if(source instanceof AsyncBufferedImage && !ready.containsKey(source)){
            if(ready.size()>=GroundLoot.MAX_ITEMS)ready.remove(ready.keySet().iterator().next());ready.put(source,false);long token=generation;
            ((AsyncBufferedImage)source).onLoaded(()->thread.invokeLater(()->{if(token==generation&&ready.containsKey(source))ready.put(source,true);}));
        }
        int step=(int)Math.round(Math.max(0,Math.min(1,fraction))*80);List<Object> key=Arrays.asList(source,size,step,config.originalImages());
        BufferedImage cached=images.get(key);if(cached!=null)return cached;
        BufferedImage copy=LootCardPainter.icon(source,size,step/80.0,config.originalImages());
        if(!(source instanceof AsyncBufferedImage)||Boolean.TRUE.equals(ready.get(source)))images.put(key,copy);
        return copy;
    }
    void render(Graphics2D original)
    {
        Graphics2D g=(Graphics2D)original.create();
        try{
            Rectangle viewport=new Rectangle(client.getViewportXOffset()+3,client.getViewportYOffset()+3,client.getViewportWidth()-6,client.getViewportHeight()-6);if(viewport.width<120||viewport.height<20)return;g.clip(viewport);
            Font plain=new Font(Font.SANS_SERIF,Font.PLAIN,config.labelFontSize()==0?12:LootAlerts.clamp(config.labelFontSize(),8,32)),bold=plain.deriveFont(Font.BOLD);g.setFont(plain);
            int height=Math.max(LootCardPainter.height(g.getFontMetrics(plain),config.cardHeight()),LootCardPainter.height(g.getFontMetrics(bold),config.cardHeight()));
            int size=LootCardPainter.iconSize(height),pitch=height+LootAlerts.clamp(config.labelSpacing(),0,16);
            int limit=LootAlerts.clamp(config.cardRows(),0,20),maximum=LootAlerts.clamp(config.maxLabels(),10,1000),drawn=0,overflow=0;
            Map<Tile,List<GroundLoot.Entry>> piles=new LinkedHashMap<>();boolean reveal=loot.reveal();
            for(GroundLoot.Entry e:loot.entries())if(!e.grouped&&e.facts!=null&&e.display!=null&&(!loot.labelHidden(e)||reveal))piles.computeIfAbsent(e.tile,k->new ArrayList<>()).add(e);
            net.runelite.api.Point mouse=client.getMouseCanvasPosition();Point pointer=mouse==null?new Point(-1,-1):new Point(mouse.getX(),mouse.getY());
            Tile previous=expanded;Rectangle previousBounds=expandedBounds;expanded=null;expandedBounds=null;
            List<Rectangle> occupied=new ArrayList<>();PriceTiers.Ready setup=tiers==null?null:tiers.snapshot();PriceTiers.Setup selected=setup==null?defaultTiers:setup.setup;double now=loot.renderTick();
            for(List<GroundLoot.Entry> entries:piles.values())for(GroundLoot.Entry e:entries)if(loot.selected(e)||e.display.isHighlightTile()){
                Polygon poly=Perspective.getCanvasTilePoly(client,e.tile.getLocalLocation());if(poly!=null){if(e.display.getTileFillColor()!=null){g.setColor(e.display.getTileFillColor());g.fill(poly);}g.setColor(loot.selected(e)?Color.CYAN:e.display.getTileStrokeColor());g.draw(poly);}break;
            }
            for(Map.Entry<Tile,List<GroundLoot.Entry>> pile:piles.entrySet()){
                Tile tile=pile.getKey();List<GroundLoot.Entry> entries=pile.getValue();
                int width=LootAlerts.clamp(config.cardWidth(),120,2048);
                if(config.cardAutoWidth() || config.lootCompact()){
                    width=config.lootCompact()?48:120;
                    for(GroundLoot.Entry e:entries){long value=PriceTiers.value(e.facts.getGePrice(),e.renderQuantity);PriceTiers.Tier tier=selected.tiers[PriceTiers.index(selected,value)];
                        width=Math.max(width,LootCardPainter.width(g.getFontMetrics(tier.bold?bold:plain),size,config.lootCompact()?"":e.facts.getName(),e.renderQuantity,LootDisplayOptions.price(config,e),config.cardShortPrices(),seconds(e)));}
                }
                width=Math.min(viewport.width,width);
                net.runelite.api.Point anchor=Perspective.getCanvasTextLocation(client,g,tile.getLocalLocation(),"",(tile.getItemLayer()==null?0:tile.getItemLayer().getHeight())+16);if(anchor==null)continue;
                int layoutX=anchor.getX()+(config.labelAlignment()==ImprovedLootingConfig.LabelAlignment.LEFT?width/2:config.labelAlignment()==ImprovedLootingConfig.LabelAlignment.RIGHT?-width/2:0);
                int baseCount=limit==0?entries.size():Math.min(limit,entries.size());int fit=Math.max(1,viewport.height/pitch-1);baseCount=Math.min(baseCount,fit);
                int baseHeight=baseCount*pitch-(pitch-height)+(entries.size()>baseCount?pitch:0);
                Rectangle collapsed=LootCardPainter.place(layoutX,anchor.getY(),width,baseHeight,viewport,occupied);
                if(collapsed==null){overflow+=entries.size();continue;}
                boolean hover=collapsed.contains(pointer)||(tile==previous&&previousBounds!=null&&previousBounds.contains(pointer));
                int count=Math.min(Math.min(hover?entries.size():baseCount,fit),Math.max(0,maximum-drawn));if(count==0){overflow+=entries.size();continue;}
                Rectangle box=LootCardPainter.place(layoutX,anchor.getY(),width,count*pitch-(pitch-height)+(entries.size()>count?pitch:0),viewport,occupied);
                if(box==null){count=Math.min(baseCount,maximum-drawn);box=collapsed;}occupied.add(box);
                if(hover){expanded=tile;expandedBounds=box.union(collapsed);}
                if(Math.abs(box.x+width/2-anchor.getX())>10 || Math.abs(box.y+box.height-anchor.getY())>12){g.setColor(new Color(180,190,200,90));g.drawLine(box.x+width/2,box.y+box.height,anchor.getX(),anchor.getY());}
                for(int i=0;i<count;i++){
                    GroundLoot.Entry e=entries.get(i);Rectangle row=new Rectangle(box.x,box.y+i*pitch,width,height);long value=PriceTiers.value(e.facts.getGePrice(),e.renderQuantity);
                    Color filter=e.display.getTextColor();PriceTiers.Tier tier=selected.tiers[PriceTiers.index(selected,value)];g.setFont(tier.bold?bold:plain);
                    Paint rail=filter.equals(Color.WHITE)?new Color(tier.color):filter;
                    if(config.priceTiersEnabled()&&setup!=null){Paint effect=LootTextPaint.paint(tier,selected.seconds,System.nanoTime(),row.x,Math.max(80,width));if(effect!=null)rail=effect;}
                    double fraction=config.iconDespawnFade()?LootCardPainter.remaining(e.renderSpawnTick,e.renderDespawn,now):1;
                    LootCardPainter.row(g,row,rail,image(e,size,fraction),config.lootCompact()?"":e.facts.getName(),e.renderQuantity,LootDisplayOptions.price(config,e),loot.labelHidden(e),config.cardBackground(),config.cardBorder(),config.cardOpacity(),config.cardShortPrices(),seconds(e));
                    if(input!=null)input.add(row,e,loot.labelHidden(e));
                }
                g.setFont(plain);
                drawn+=count;
                if(entries.size()>count)footer(g,new Rectangle(box.x,box.y+count*pitch,width,height),"+ "+(entries.size()-count)+" more"+(hover?" · Nearby loot":" · hover to expand"));
            }
            if(overflow>0){String text=overflow+" more items · Nearby loot";int summaryWidth=Math.min(viewport.width-8,g.getFontMetrics().stringWidth(text)+16);Rectangle summary=LootCardPainter.place(viewport.x+summaryWidth/2+4,viewport.y+height+4,summaryWidth,height,viewport,occupied);if(summary!=null)footer(g,summary,text);}
        }finally{g.dispose();}
    }
    private int seconds(GroundLoot.Entry entry)
    {
        return LootDisplayOptions.seconds(config,entry,client.getTickCount());
    }
    private void footer(Graphics2D g,Rectangle box,String text)
    {
        LootCardPainter.surface(g,box,config.cardBackground(),config.cardBorder(),config.cardOpacity());g.setColor(LootCardPainter.VALUE);
        text=LootCardPainter.fit(text,g.getFontMetrics(),box.width-16);
        g.drawString(text,box.x+(box.width-g.getFontMetrics().stringWidth(text))/2,box.y+(box.height-g.getFontMetrics().getHeight())/2+g.getFontMetrics().getAscent());
    }
}
