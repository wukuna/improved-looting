package com.improvedlooting;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.AsyncBufferedImage;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class GroundLootCardsTest
{
    private static GroundLootCards cards(LootRuntimeTest.Fixture f,int count)
    {
        when(f.view.getId()).thenReturn(WorldView.TOPLEVEL);
        LocalPoint point=new LocalPoint(128,128,f.view);when(f.tile.getLocalLocation()).thenReturn(point);when(f.player.getLocalLocation()).thenReturn(point);
        when(f.loot.client.getWorldView(WorldView.TOPLEVEL)).thenReturn(f.view);
        when(f.loot.client.getCameraX()).thenReturn(128);when(f.loot.client.getScale()).thenReturn(256);
        when(f.loot.client.getViewportWidth()).thenReturn(800);when(f.loot.client.getViewportHeight()).thenReturn(600);
        when(f.loot.client.getMouseCanvasPosition()).thenReturn(new net.runelite.api.Point(-1,-1));
        when(f.loot.config.lootOverlay()).thenReturn(true);when(f.loot.config.iconDespawnFade()).thenReturn(false);
        when(f.loot.config.cardAutoWidth()).thenReturn(false);when(f.loot.config.cardHeight()).thenReturn(26);
        for(int i=0;i<count;i++)f.items.add(f.item(i+1,2,200));f.tick(100);
        for(GroundLoot.Entry e:f.loot.entries())e.image=sprite();
        GroundLootCards cards=new GroundLootCards();cards.client=f.loot.client;cards.loot=f.loot;cards.config=f.loot.config;cards.thread=mock(ClientThread.class);return cards;
    }
    private static BufferedImage sprite(){BufferedImage image=new BufferedImage(20,20,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();g.setColor(Color.CYAN);g.fillRect(0,0,20,20);g.dispose();return image;}
    private static BufferedImage render(GroundLootCards cards){BufferedImage image=new BufferedImage(800,600,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();cards.render(g);g.dispose();return image;}
    private static int top(BufferedImage image){for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if((image.getRGB(x,y)>>>24)>0)return y;return -1;}
    @Test public void actualProjectionDrawsCardsWithNoItemLayerAndHonoursPictureOnlySwitch()
    {
        LootRuntimeTest.Fixture f=new LootRuntimeTest.Fixture("rule(true){}");GroundLootCards cards=cards(f,1);
        assertNull(f.tile.getItemLayer());BufferedImage normal=render(cards);assertTrue(top(normal)>0);
        when(f.loot.config.lootCompact()).thenReturn(true);BufferedImage compactSaved=render(cards);
        assertTrue(bounds(compactSaved).width<bounds(normal).width);
        assertEquals(bounds(normal).height,bounds(compactSaved).height);
    }
    @Test public void hoverExpandsPileThenCollapsesWithoutCapturingInput() throws Exception
    {
        LootRuntimeTest.Fixture f=new LootRuntimeTest.Fixture("rule(true){}");GroundLootCards cards=cards(f,8);
        BufferedImage collapsed=render(cards);int first=top(collapsed);assertTrue(first>0);
        when(f.loot.client.getMouseCanvasPosition()).thenReturn(new net.runelite.api.Point(400,first+15));BufferedImage expanded=render(cards);assertTrue(top(expanded)<first);
        when(f.loot.client.getMouseCanvasPosition()).thenReturn(new net.runelite.api.Point(-1,-1));assertEquals(first,top(render(cards)));
        javax.imageio.ImageIO.write(collapsed,"png",new java.io.File("build/ground-stack-collapsed-preview.png"));
        javax.imageio.ImageIO.write(expanded,"png",new java.io.File("build/ground-stack-expanded-preview.png"));
    }
    @Test public void textTimerAndIconFadeAreIndependent()
    {
        LootRuntimeTest.Fixture f=new LootRuntimeTest.Fixture("rule(true){}");GroundLootCards cards=cards(f,1);f.tick(150);
        GroundLoot.Entry entry=f.loot.entries().iterator().next();entry.image=sprite();
        BufferedImage neither=render(cards);when(f.loot.config.iconDespawnFade()).thenReturn(true);BufferedImage fade=render(cards);
        when(f.loot.config.alwaysShowTimer()).thenReturn(true);BufferedImage both=render(cards);when(f.loot.config.iconDespawnFade()).thenReturn(false);BufferedImage timer=render(cards);
        // Manual 260 x 26 card at x=270, y=242; picture scales to 24 px.
        assertNotEquals(neither.getRGB(290,247),fade.getRGB(290,247));assertEquals(timer.getRGB(290,247),neither.getRGB(290,247));
        assertArrayEquals(fade.getRGB(310,242,210,26,null,0,210),neither.getRGB(310,242,210,26,null,0,210));
        assertArrayEquals(timer.getRGB(310,242,210,26,null,0,210),both.getRGB(310,242,210,26,null,0,210));
        assertFalse(java.util.Arrays.equals(neither.getRGB(310,242,210,26,null,0,210),timer.getRGB(310,242,210,26,null,0,210)));
    }
    @Test public void lateSpritesAreNotCachedBlankAndOldCallbacksCannotSurviveClear()
    {
        LootRuntimeTest.Fixture f=new LootRuntimeTest.Fixture("rule(true){}");GroundLootCards cards=cards(f,1);List<Runnable> callbacks=new ArrayList<>();
        doAnswer(call->{callbacks.add(call.getArgument(0));return null;}).when(cards.thread).invokeLater(any(Runnable.class));
        AsyncBufferedImage sprite=new AsyncBufferedImage(cards.thread,20,20,BufferedImage.TYPE_INT_ARGB);GroundLoot.Entry entry=f.loot.entries().iterator().next();entry.image=sprite;
        assertEquals(0,cards.image(entry,20,1).getRGB(10,10)>>>24);
        Graphics2D g=sprite.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,20,20);g.dispose();sprite.loaded();
        assertEquals(255,cards.image(entry,20,1).getRGB(10,10)>>>24);cards.clear();callbacks.forEach(Runnable::run);
        assertEquals(0,cards.image(entry,20,0).getRGB(10,10)>>>24);assertEquals(255,sprite.getRGB(10,10)>>>24);
    }
    @Test public void automaticWidthFitsLongBoldNamesAndManualDimensionsScalePictures()
    {
        LootRuntimeTest.Fixture f=new LootRuntimeTest.Fixture("rule(true){}");GroundLootCards cards=cards(f,1);GroundLoot.Entry entry=f.loot.entries().iterator().next();
        entry.facts=com.improvedlooting.rs2f.model.ItemFacts.builder().id(1).name("Super extended antifire mix(2)").quantity(2).gePrice(10000).build();
        PriceTiers.Setup setup=PriceTiers.defaults();setup.tiers[2].bold=true;cards.tiers=new PriceTiers();cards.tiers.configs=mock(net.runelite.client.config.ConfigManager.class);cards.tiers.gson=new com.google.gson.Gson();cards.tiers.save(cards.tiers.prepare(setup));
        when(f.loot.config.cardAutoWidth()).thenReturn(true);when(f.loot.config.cardHeight()).thenReturn(0);when(f.loot.config.alwaysShowTimer()).thenReturn(true);
        when(f.loot.config.priceDisplay()).thenReturn(ImprovedLootingConfig.DetailMode.ALWAYS);
        BufferedImage automatic=render(cards);java.awt.Rectangle bounds=bounds(automatic);
        Graphics2D g=automatic.createGraphics();g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF,java.awt.Font.BOLD,12));
        int height=LootCardPainter.height(ImprovedLootingConfig.CardTheme.NATIVE,g.getFontMetrics(),0),size=LootCardPainter.iconSize(height);
        int required=LootCardPainter.width(g.getFontMetrics(),size,entry.facts.getName(),2,20000,false,60);g.dispose();
        assertEquals(required,bounds.width);assertTrue(bounds.width>260);assertEquals(height,bounds.height);assertTrue(height<26);
        setup.tiers[2].bold=false;assertTrue(bounds(render(cards)).width<bounds.width);setup.tiers[2].bold=true;
        when(f.loot.config.cardAutoWidth()).thenReturn(false);when(f.loot.config.cardWidth()).thenReturn(500);when(f.loot.config.cardHeight()).thenReturn(44);
        BufferedImage manual=render(cards);java.awt.Rectangle larger=bounds(manual);assertEquals(500,larger.width);assertEquals(44,larger.height);
        assertEquals(Color.CYAN.getRGB(),manual.getRGB(larger.x+10+40,larger.y+40));
        when(f.loot.config.cardWidth()).thenReturn(2048);assertEquals(794,bounds(render(cards)).width);
    }
    private static java.awt.Rectangle bounds(BufferedImage image)
    {
        int x1=image.getWidth(),y1=image.getHeight(),x2=-1,y2=-1;
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if((image.getRGB(x,y)>>>24)>0){x1=Math.min(x1,x);x2=Math.max(x2,x);y1=Math.min(y1,y);y2=Math.max(y2,y);}
        return new java.awt.Rectangle(x1,y1,x2-x1+1,y2-y1+1);
    }
}
