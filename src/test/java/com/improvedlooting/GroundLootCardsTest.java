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
    @Test public void highlightedNamesAndRailsStayYellowWithSolidAndAnimatedTiers() throws Exception
    {
        LootRuntimeTest.Fixture f=new LootRuntimeTest.Fixture("rule(true){}");GroundLootCards cards=cards(f,1);
        f.loot.preferences=LootDisplayOptionsTest.preferences(new java.util.HashMap<>());
        f.loot.preferences.set(1,LootPreferences.Action.HIGHLIGHT,"");f.loot.refreshOverrides();
        when(f.loot.config.priceTiersEnabled()).thenReturn(true);
        cards.tiers=new PriceTiers();cards.tiers.configs=mock(net.runelite.client.config.ConfigManager.class);cards.tiers.gson=new com.google.gson.Gson();
        for(ImprovedLootingConfig.CardTheme theme:ImprovedLootingConfig.CardTheme.values()) {
            when(f.loot.config.cardTheme()).thenReturn(theme);
            for(PriceTiers.Effect effect:PriceTiers.Effect.values()) {
                PriceTiers.Setup setup=PriceTiers.defaults();for(PriceTiers.Tier tier:setup.tiers){tier.effect=effect;tier.color=0xff00ff;}
                cards.tiers.save(cards.tiers.prepare(setup));BufferedImage image=render(cards);
                // Fixed-size production card: rail at x=270, name begins at x=311.
                assertTrue(hasColor(image.getSubimage(270,242,6,26),Color.YELLOW.getRGB()));
                assertTrue(hasColor(image.getSubimage(311,242,70,26),Color.YELLOW.getRGB()));
                if(effect==PriceTiers.Effect.RGB)javax.imageio.ImageIO.write(image,"png",new java.io.File("build/highlight-"+theme.name().toLowerCase()+"-preview.png"));
            }
        }
    }
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
    @Test public void untradeablesRenderInSelectedColorAndOwnershipHidesBothThemes() throws Exception
    {
        LootRuntimeTest.Fixture f=new LootRuntimeTest.Fixture("rule(true){}");GroundLootCards cards=cards(f,1);
        GroundLoot.Entry entry=f.loot.entries().iterator().next();
        entry.facts=com.improvedlooting.rs2f.model.ItemFacts.builder().id(1).name("Untradeable drop").quantity(1).gePrice(0).tradeable(false).build();
        when(f.loot.config.minimumLabelValue()).thenReturn(600);
        when(f.loot.config.priceTiersEnabled()).thenReturn(true);
        PriceTiers.Setup setup=PriceTiers.defaults();setup.tiers[0].effect=PriceTiers.Effect.RGB;
        cards.tiers=new PriceTiers();cards.tiers.configs=mock(net.runelite.client.config.ConfigManager.class);cards.tiers.gson=new com.google.gson.Gson();cards.tiers.save(cards.tiers.prepare(setup));
        for(ImprovedLootingConfig.CardTheme theme:ImprovedLootingConfig.CardTheme.values())
        {
            when(f.loot.config.cardTheme()).thenReturn(theme);
            when(f.loot.config.onlyShowOwnedDrops()).thenReturn(false);
            when(f.loot.config.untradeableColor()).thenReturn(new Color(0xff5555));
            BufferedImage red=render(cards);assertTrue(top(red)>0);assertTrue(hasColor(red,0xffff5555));
            javax.imageio.ImageIO.write(red,"png",new java.io.File("build/untradeable-"+theme.name().toLowerCase()+"-preview.png"));
            when(f.loot.config.untradeableColor()).thenReturn(new Color(0x12ab34));assertTrue(hasColor(render(cards),0xff12ab34));
            when(f.loot.config.onlyShowOwnedDrops()).thenReturn(true);when(entry.item.getOwnership()).thenReturn(net.runelite.api.TileItem.OWNERSHIP_OTHER);
            assertEquals(-1,top(render(cards)));
            when(entry.item.getOwnership()).thenReturn(net.runelite.api.TileItem.OWNERSHIP_SELF);assertTrue(top(render(cards))>0);
        }
    }
    private static boolean hasColor(BufferedImage image,int color)
    {for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if(image.getRGB(x,y)==color)return true;return false;}
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
    @Test public void individualWidthsAffectActualRowsAndManualWidthStaysFixed() throws Exception
    {
        LootRuntimeTest.Fixture f=new LootRuntimeTest.Fixture("rule(true){}");GroundLootCards cards=cards(f,2);
        int n=0;for(GroundLoot.Entry e:f.loot.entries())e.facts=com.improvedlooting.rs2f.model.ItemFacts.builder().id(e.item.getId()).name(n++==0?"Coins":"Super extended antifire mix(2)").quantity(2).gePrice(10000).tradeable(true).build();
        when(f.loot.config.cardAutoWidth()).thenReturn(true);BufferedImage shared=render(cards);int y=top(shared)+13;
        int first=span(shared,y),second=span(shared,y+28);assertEquals(first,second);
        when(f.loot.config.cardIndividualWidth()).thenReturn(true);BufferedImage individual=render(cards);
        assertTrue(span(individual,y)<span(individual,y+28));
        javax.imageio.ImageIO.write(individual,"png",new java.io.File("build/individual-card-widths-preview.png"));
        when(f.loot.config.cardAutoWidth()).thenReturn(false);BufferedImage fixed=render(cards);assertEquals(span(fixed,y),span(fixed,y+28));
    }
    private static int span(BufferedImage image,int y) {
        int min=800,max=-1;for(int x=0;x<800;x++)if((image.getRGB(x,y)>>>24)>50){min=Math.min(min,x);max=x;}return max-min+1;
    }
    private static java.awt.Rectangle bounds(BufferedImage image)
    {
        int x1=image.getWidth(),y1=image.getHeight(),x2=-1,y2=-1;
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if((image.getRGB(x,y)>>>24)>0){x1=Math.min(x1,x);x2=Math.max(x2,x);y1=Math.min(y1,y);y2=Math.max(y2,y);}
        return new java.awt.Rectangle(x1,y1,x2-x1+1,y2-y1+1);
    }
}
