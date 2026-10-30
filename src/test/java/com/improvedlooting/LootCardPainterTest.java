package com.improvedlooting;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.Test;
import static org.junit.Assert.*;
import static com.improvedlooting.ImprovedLootingConfig.CardTheme.*;

public class LootCardPainterTest
{
    @Test public void fadeErasesTopFirstAndNeverModifiesSharedSprite()
    {
        BufferedImage source=new BufferedImage(20,20,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=source.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,20,20);g.dispose();
        BufferedImage full=LootCardPainter.icon(source,20,1),half=LootCardPainter.icon(source,20,.5),gone=LootCardPainter.icon(source,20,0);
        assertEquals(255,full.getRGB(10,2)>>>24);assertEquals(0,half.getRGB(10,2)>>>24);
        assertTrue((half.getRGB(10,10)>>>24)>0);assertEquals(255,half.getRGB(10,18)>>>24);
        for(int y=0;y<20;y++){assertEquals(0,gone.getRGB(10,y)>>>24);assertEquals(255,source.getRGB(10,y)>>>24);}
        assertEquals(1,LootCardPainter.remaining(100,0,150),0);
        assertEquals(1,LootCardPainter.remaining(100,Integer.MAX_VALUE,150),0);
        assertEquals(.5,LootCardPainter.remaining(100,200,150),0);
        assertEquals(.495,LootCardPainter.remaining(100,200,150.5),.0001);
        assertEquals(0,LootCardPainter.remaining(100,200,210),0);
    }
    @Test public void quantitiesPricesAndOptionalTimersAreUnambiguous()
    {
        assertEquals("",LootCardPainter.quantity(1));assertEquals(" (2)",LootCardPainter.quantity(2));assertEquals(" (12,500)",LootCardPainter.quantity(12500));
        assertEquals("(8,300 gp) [42s]",LootCardPainter.details(8300,false,42));
        assertEquals("(8.3k gp)",LootCardPainter.details(8300,true,-1));
        assertEquals("[0s]",LootCardPainter.details(0,false,0));assertEquals("",LootCardPainter.details(0,false,-1));
        assertEquals("999",LootCardPainter.shortNumber(999));assertEquals("1m",LootCardPainter.shortNumber(999999));assertEquals("2.1b",LootCardPainter.shortNumber(Integer.MAX_VALUE));
    }
    @Test public void cardTextAndDetailsUseTierPaintAndHeightFollowsText()
    {
        BufferedImage image=new BufferedImage(500,100,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,12));
        int small=LootCardPainter.height(NATIVE,g.getFontMetrics(),0);assertTrue(small<26);assertEquals(small-2,LootCardPainter.iconSize(small));
        LootCardPainter.row(g,new Rectangle(5,5,480,small),Color.RED,null,"Tier coloured name",2,8300,false,false,false,90,false,42,NATIVE,null);
        boolean name=false,details=false;
        for(int y=6;y<5+small;y++)for(int x=50;x<480;x++){int pixel=image.getRGB(x,y);if((pixel>>>24)>20){assertEquals(0,pixel&0xffff);assertEquals(255,(pixel>>>16)&255);if(x<250)name=true;else details=true;}}
        assertTrue(name);assertTrue(details);g.setFont(g.getFont().deriveFont(24f));assertTrue(LootCardPainter.height(NATIVE,g.getFontMetrics(),0)>small);
        assertEquals(60,LootCardPainter.height(NATIVE,g.getFontMetrics(),60));assertTrue(LootCardPainter.height(NATIVE,g.getFontMetrics(),1)>24);g.dispose();
    }
    @Test public void crowdedPilesStayInsideViewportAndDoNotOverlap()
    {
        Rectangle viewport=new Rectangle(5,7,800,500);List<Rectangle> placed=new ArrayList<>();
        for(int i=0;i<20;i++){
            Rectangle next=LootCardPainter.place(400,450,260,100,viewport,placed);
            if(next==null)continue;
            assertTrue(viewport.contains(next));for(Rectangle previous:placed)assertFalse(previous.intersects(next));placed.add(next);
        }
        assertTrue(placed.size()>1);assertNull(LootCardPainter.place(400,450,900,100,viewport,placed));
    }
    @Test public void backgroundAndThinBorderCanBeDisabledIndependently()
    {
        BufferedImage image=new BufferedImage(280,100,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();g.setFont(new Font(Font.SANS_SERIF,0,12));
        LootCardPainter.row(g,new Rectangle(5,5,260,26),Color.CYAN,null,"",1,0,false,false,false,90,false,-1,NATIVE,null);
        assertEquals(0,image.getRGB(200,15)>>>24);assertEquals(0,image.getRGB(200,5)>>>24);
        LootCardPainter.row(g,new Rectangle(5,35,260,26),Color.CYAN,null,"",1,0,false,true,false,50,false,-1,NATIVE,null);
        assertEquals(128,image.getRGB(200,45)>>>24);
        LootCardPainter.row(g,new Rectangle(5,65,260,26),Color.CYAN,null,"",1,0,false,false,true,50,false,-1,NATIVE,null);g.dispose();
        assertEquals(0,image.getRGB(200,75)>>>24);assertTrue((image.getRGB(200,65)>>>24)>0);
    }
    @Test public void renderActualCardsAndFadePreview() throws Exception
    {
        BufferedImage image=new BufferedImage(680,355,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();
        g.setColor(new Color(45,49,46));g.fillRect(0,0,680,355);g.setFont(new Font(Font.SANS_SERIF,0,12));
        BufferedImage rune=ImageIO.read(net.runelite.client.RuneLite.class.getResourceAsStream("/net/runelite/client/plugins/runepouch/law_rune.png"));
        g.setColor(Color.WHITE);g.drawString("Actual card renderer · sample data",18,26);
        g.setColor(LootCardPainter.VALUE);g.drawString("Separate controls: background / border / opacity / text timer / icon fade",18,48);
        String[] names={"Law rune","Rune platebody","Uncut sapphire","Shark"};long[] quantities={2,1,3,2},prices={8300,38600,1230,1780};
        for(int i=0;i<4;i++)LootCardPainter.row(g,new Rectangle(18,65+i*28,310,26),i<2?new Color(225,183,86):Color.CYAN,LootCardPainter.icon(rune,20,1),names[i],quantities[i],prices[i],false,true,true,90,false,42,NATIVE,null);
        g.setColor(LootCardPainter.VALUE);g.drawString("Picture fade only (text remains unchanged)",352,84);
        for(int i=0;i<5;i++){
            double remaining=1-i*.25;g.setColor(LootCardPainter.VALUE);g.drawString((int)(remaining*100)+"%",352,112+i*28);
            LootCardPainter.row(g,new Rectangle(390,94+i*28,270,26),Color.CYAN,LootCardPainter.icon(rune,20,remaining),"Law rune",2,8300,false,true,true,90,false,-1,NATIVE,null);
        }
        LootCardPainter.row(g,new Rectangle(18,225,310,26),Color.CYAN,LootCardPainter.icon(rune,20,1),"Background off; border on",2,8300,false,false,true,90,true,-1,NATIVE,null);
        LootCardPainter.row(g,new Rectangle(18,263,310,26),Color.CYAN,LootCardPainter.icon(rune,20,1),"Thin border off",2,8300,false,true,false,70,false,-1,NATIVE,null);
        g.setColor(LootCardPainter.VALUE);g.drawString("Sample rune picture reused to demonstrate layout; not a live game capture.",18,330);g.dispose();
        ImageIO.write(image,"png",new java.io.File("build/ground-cards-preview.png"));
    }
    @Test public void renderSlimSizingAndTierTypographyPreview() throws Exception
    {
        BufferedImage image=new BufferedImage(900,400,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();g.setColor(new Color(45,49,46));g.fillRect(0,0,900,400);
        Font base=new Font(Font.SANS_SERIF,Font.PLAIN,12);g.setFont(base);g.setColor(Color.WHITE);g.drawString("Actual renderer · slim rows, full names and tier typography",20,25);
        BufferedImage rune=ImageIO.read(net.runelite.client.RuneLite.class.getResourceAsStream("/net/runelite/client/plugins/runepouch/law_rune.png"));
        int y=52;int[] sizes={12,12,16,24,12},styles={Font.PLAIN,Font.BOLD,Font.BOLD,Font.PLAIN,Font.PLAIN};
        Color[] colors={new Color(128,255,128),new Color(128,192,255),new Color(192,128,255),new Color(255,160,64),new Color(255,96,144)};
        for(int i=0;i<sizes.length;i++){
            g.setFont(base.deriveFont(styles[i],sizes[i]));int height=LootCardPainter.height(NATIVE,g.getFontMetrics(),i==4?42:0),icon=LootCardPainter.iconSize(height);
            String name=i==0?"Law rune":"Super extended antifire mix(2)";int width=LootCardPainter.width(g.getFontMetrics(),icon,name,2,8300,false,42);
            LootCardPainter.row(g,new Rectangle(20,y,width,height),colors[i],LootCardPainter.icon(rune,icon,1),name,2,8300,false,true,true,90,false,42,NATIVE,null);
            g.setFont(base);g.setColor(LootCardPainter.VALUE);g.drawString(sizes[i]+"px text · "+(styles[i]==Font.BOLD?"bold · ":"")+height+"px row",20,y+height+17);y+=height+35;
        }
        g.setColor(LootCardPainter.VALUE);g.drawString("Sample rune picture reused for sizing. Last row uses a custom 42px height.",20,385);g.dispose();
        ImageIO.write(image,"png",new java.io.File("build/ground-card-sizing-preview.png"));
    }
}
