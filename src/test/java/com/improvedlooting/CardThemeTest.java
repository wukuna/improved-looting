package com.improvedlooting;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.Test;
import static com.improvedlooting.ImprovedLootingConfig.CardTheme.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CardThemeTest
{
    @Test public void themeAndCustomColorSurviveTheRealConfigProxyAndBackups() throws Exception
    {
        int rgb=new Color(77,29,91).getRGB();
        ImprovedLootingConfig old=ImprovedLootingConfigProxyTest.proxy(Map.of());
        assertEquals(NATIVE,old.cardTheme());assertNull(old.cardBackgroundColor());
        ImprovedLootingConfig restored=ImprovedLootingConfigProxyTest.proxy(Map.of("cardTheme","OSRS","cardBackgroundColor",Integer.toString(rgb)));
        assertEquals(OSRS,restored.cardTheme());assertEquals(rgb,restored.cardBackgroundColor().getRGB());
        assertEquals(new Color(55,44,28),LootCardPainter.background(OSRS,null));
        assertEquals(restored.cardBackgroundColor(),LootCardPainter.background(NATIVE,restored.cardBackgroundColor()));
        LootProfiles profiles=LootProfilesTest.profiles();
        when(profiles.icons.configs.getConfiguration(ImprovedLootingConfig.GROUP,"cardTheme")).thenReturn("OSRS");
        when(profiles.icons.configs.getConfiguration(ImprovedLootingConfig.GROUP,"cardBackgroundColor")).thenReturn(Integer.toString(rgb));
        String backup=profiles.export();LootProfiles.Setup parsed=profiles.parse(backup);
        profiles.applyValidated(parsed,null,backup);
        verify(profiles.icons.configs).setConfiguration(ImprovedLootingConfig.GROUP,"cardTheme","OSRS");
        verify(profiles.icons.configs).setConfiguration(ImprovedLootingConfig.GROUP,"cardBackgroundColor",Integer.toString(rgb));
        parsed.settings.put("cardBackgroundColor","not a color");
        try{profiles.validate(parsed);fail("Malformed colors must reject the backup");}catch(IllegalArgumentException expected){}
        parsed.settings.put("cardBackgroundColor",null);parsed.settings.put("cardTheme","UNKNOWN");
        try{profiles.validate(parsed);fail("Unknown themes must reject the backup");}catch(IllegalArgumentException expected){}
        assertNull(profiles.parse("{\"version\":1,\"settings\":{}}").settings.get("cardBackgroundColor"));
    }

    @Test public void bothThemesRespectSelectedColorOpacityAndIndependentBorderSwitches()
    {
        Color selected=new Color(40,100,200,5);Rectangle box=new Rectangle(2,2,100,30);
        for(ImprovedLootingConfig.CardTheme theme:ImprovedLootingConfig.CardTheme.values()){
            BufferedImage image=new BufferedImage(110,40,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();
            LootCardPainter.surface(g,box,true,false,50,theme,selected);
            Color center=new Color(image.getRGB(50,15),true);
            assertEquals(128,center.getAlpha());assertTrue(Math.abs(40-center.getRed())<=1);assertTrue(Math.abs(100-center.getGreen())<=1);assertTrue(Math.abs(200-center.getBlue())<=1);
            g.setComposite(AlphaComposite.Clear);g.fillRect(0,0,110,40);g.setComposite(AlphaComposite.SrcOver);
            LootCardPainter.surface(g,box,false,true,100,theme,selected);
            assertEquals(0,image.getRGB(50,15)>>>24);assertTrue((image.getRGB(50,2)>>>24)>0);
            g.setComposite(AlphaComposite.Clear);g.fillRect(0,0,110,40);g.setComposite(AlphaComposite.SrcOver);
            LootCardPainter.surface(g,box,true,false,0,theme,selected);assertEquals(0,image.getRGB(50,15));g.dispose();
        }
    }

    @Test public void osrsSquareCornersAndFontKeepNativeAppearanceAndSizingIndependent()
    {
        BufferedImage nativeImage=new BufferedImage(110,40,BufferedImage.TYPE_INT_ARGB),osrsImage=new BufferedImage(110,40,BufferedImage.TYPE_INT_ARGB);
        Graphics2D nativeGraphics=nativeImage.createGraphics(),osrsGraphics=osrsImage.createGraphics();Rectangle box=new Rectangle(2,2,100,30);
        LootCardPainter.surface(nativeGraphics,box,true,false,100,NATIVE,null);LootCardPainter.surface(osrsGraphics,box,true,false,100,OSRS,null);
        assertEquals(0,nativeImage.getRGB(2,2)>>>24);assertEquals(255,osrsImage.getRGB(2,2)>>>24);
        assertEquals(new Color(23,27,32).getRGB(),nativeImage.getRGB(50,15));assertEquals(new Color(55,44,28).getRGB(),osrsImage.getRGB(50,15));
        nativeGraphics.setFont(LootCardPainter.font(NATIVE,0,false));osrsGraphics.setFont(LootCardPainter.font(OSRS,0,false));
        assertEquals(Font.SANS_SERIF,nativeGraphics.getFont().getFamily());assertEquals(12,nativeGraphics.getFont().getSize());
        assertNotEquals(nativeGraphics.getFont().getFamily(),osrsGraphics.getFont().getFamily());
        assertTrue(LootCardPainter.height(OSRS,osrsGraphics.getFontMetrics(),0)>=osrsGraphics.getFontMetrics().getHeight()+6);
        assertEquals(60,LootCardPainter.height(OSRS,osrsGraphics.getFontMetrics(),60));assertEquals(24,LootCardPainter.font(OSRS,24,true).getSize());
        nativeGraphics.dispose();osrsGraphics.dispose();
    }

    @Test public void renderActualThemesAndCustomBackgrounds() throws Exception
    {
        BufferedImage image=new BufferedImage(900,330,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();g.setColor(new Color(38,40,34));g.fillRect(0,0,900,330);
        BufferedImage rune=ImageIO.read(net.runelite.client.RuneLite.class.getResourceAsStream("/net/runelite/client/plugins/runepouch/law_rune.png"));
        Color[] colors={new Color(192,128,255),new Color(255,220,64),new Color(128,255,128)};
        String[] names={"Abyssal whip","Rune platebody","Nature rune"};long[] quantities={1,1,24},prices={1200000,38500,2400};
        for(int column=0;column<2;column++){
            ImprovedLootingConfig.CardTheme theme=column==0?NATIVE:OSRS;int x=20+column*440;
            g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,16));g.setColor(Color.WHITE);g.drawString(theme.toString(),x,30);
            for(int i=0;i<3;i++){
                g.setFont(LootCardPainter.font(theme,0,i==0));int height=LootCardPainter.height(theme,g.getFontMetrics(),0);
                LootCardPainter.row(g,new Rectangle(x,50+i*35,415,height),colors[i],LootCardPainter.icon(rune,LootCardPainter.iconSize(height),1),names[i],quantities[i],prices[i],false,true,true,90,true,-1,theme,null);
            }
            g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,12));g.setColor(Color.WHITE);g.drawString("Custom plum background, 75% opacity",x,182);
            g.setFont(LootCardPainter.font(theme,0,false));int height=LootCardPainter.height(theme,g.getFontMetrics(),0);
            LootCardPainter.row(g,new Rectangle(x,195,415,height),Color.CYAN,LootCardPainter.icon(rune,LootCardPainter.iconSize(height),.5),"Law rune",2,8300,false,true,true,75,false,42,theme,new Color(70,28,72));
        }
        g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,12));g.setColor(Color.LIGHT_GRAY);g.drawString("Actual renderer - sample data and a reused rune icon. Tier text, quantity, price and fade controls are retained.",20,298);g.dispose();
        ImageIO.write(image,"png",new java.io.File("build/card-themes-preview.png"));
    }
}
