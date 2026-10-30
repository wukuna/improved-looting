package com.improvedlooting;

import com.improvedlooting.rs2f.LootFilter;
import com.improvedlooting.rs2f.model.ItemFacts;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.*;
import net.runelite.client.Notifier;
import net.runelite.client.audio.AudioPlayer;
import net.runelite.client.plugins.PluginManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class LootRuntimeTest
{
    static class Fixture
    {
        final GroundLoot loot=new GroundLoot();final WorldView view=mock(WorldView.class);final Player player=mock(Player.class);final Tile tile=mock(Tile.class);final Scene scene=mock(Scene.class);final List<TileItem> items=new ArrayList<>();
        Fixture(String script)
        {
            loot.client=mock(Client.class);loot.config=mock(ImprovedLootingConfig.class,CALLS_REAL_METHODS);loot.filters=mock(FilterIcons.class);loot.plugins=mock(PluginManager.class);loot.icons=mock(ItemIcons.class);
            when(loot.plugins.getPlugins()).thenReturn(Collections.emptyList());when(loot.client.getGameState()).thenReturn(GameState.LOGGED_IN);when(loot.client.getTickCount()).thenReturn(100);when(loot.client.getLocalPlayer()).thenReturn(player);when(loot.client.getTopLevelWorldView()).thenReturn(view);when(player.getWorldView()).thenReturn(view);when(view.getScene()).thenReturn(scene);when(scene.getTiles()).thenReturn(new Tile[][][]{{{tile}}});when(tile.getGroundItems()).thenReturn(items);
            LocalPoint point=new LocalPoint(128,128,view);when(tile.getLocalLocation()).thenReturn(point);when(player.getLocalLocation()).thenReturn(point);
            FilterIcons.Prepared filter=new FilterIcons.Prepared();filter.program=LootFilter.compile(script);when(loot.filters.snapshot()).thenReturn(filter);
            loot.alerts=new LootAlerts();loot.alerts.client=loot.client;loot.alerts.config=loot.config;loot.alerts.notifier=mock(Notifier.class);loot.alerts.audio=mock(AudioPlayer.class);loot.alerts.executor=mock(ScheduledExecutorService.class);
        }
        TileItem item(int id,int quantity,int expiry)
        {
            TileItem item=mock(TileItem.class);when(item.getId()).thenReturn(id);when(item.getQuantity()).thenReturn(quantity);when(item.getDespawnTime()).thenReturn(expiry);
            when(loot.filters.facts(tile,item)).thenReturn(ItemFacts.builder().id(id).name("Item "+id).quantity(quantity).gePrice(10000).build());return item;
        }
        void tick(int tick){when(loot.client.getTickCount()).thenReturn(tick);loot.onGameTick(new GameTick());}
    }
    @Test public void existingPilesStaySilentNewDropsWaitOneTickAndDuplicateEventsDoNotAlert()
    {
        Fixture f=new Fixture("rule(true){notify=true;}");when(f.loot.config.lootNotifications()).thenReturn(true);when(f.loot.config.notificationCards()).thenReturn(true);
        f.items.add(f.item(1,1,300));f.tick(100);verifyNoInteractions(f.loot.alerts.notifier);
        TileItem drop=f.item(2,1,300);f.loot.onItemSpawned(new ItemSpawned(f.tile,drop));f.loot.onItemSpawned(new ItemSpawned(f.tile,drop));verifyNoInteractions(f.loot.alerts.notifier);
        f.tick(101);verify(f.loot.alerts.notifier,times(1)).notify(anyString());assertEquals(1,f.loot.alerts.cards().size());f.tick(102);verifyNoMoreInteractions(f.loot.alerts.notifier);
        f.loot.clear();assertTrue(f.loot.rows().isEmpty());assertTrue(f.loot.alerts.cards().isEmpty());
    }
    @Test public void hiddenDropsNeverAlertAndSeededNearExpiryDoesNotWarn()
    {
        Fixture f=new Fixture("rule(true){hidden=true;notify=true;}");when(f.loot.config.lootNotifications()).thenReturn(true);when(f.loot.config.despawnWarnings()).thenReturn(true);
        f.items.add(f.item(1,1,120));f.tick(100);TileItem drop=f.item(2,1,150);f.loot.onItemSpawned(new ItemSpawned(f.tile,drop));f.tick(101);f.tick(130);verifyNoInteractions(f.loot.alerts.notifier);
        assertTrue(f.loot.rows().get(0).hidden);
    }
    @Test public void despawnWarningOccursOnceAtThresholdAndUnknownTimesStayUnknown()
    {
        Fixture f=new Fixture("rule(true){showDespawn=true;}");when(f.loot.config.despawnWarnings()).thenReturn(true);f.items.add(f.item(1,1,150));f.tick(100);f.tick(126);f.tick(127);verify(f.loot.alerts.notifier,times(1)).notify(contains("despawns"));
        assertEquals(-1,LootResolution.secondsRemaining(0,100));assertEquals(1,LootResolution.secondsRemaining(101,100));assertEquals(0,LootResolution.secondsRemaining(99,100));
    }
    @Test public void groupingPreservesVisibilityDifferencesAndUsesEarliestExpiry()
    {
        Fixture f=new Fixture("rule(quantity:>2){hidden=true;}");when(f.loot.config.groupStacks()).thenReturn(true);f.items.add(f.item(1,1,200));f.items.add(f.item(1,2,150));f.items.add(f.item(1,3,170));f.tick(100);
        assertEquals(3,f.loot.rows().size());assertEquals(2,f.loot.entries().stream().filter(e->!e.grouped).count());GroundLoot.Entry visible=f.loot.entries().stream().filter(e->!e.grouped&&!e.display.isHidden()).findFirst().get();assertEquals(3,visible.renderQuantity);assertEquals(150,visible.renderDespawn);
    }
    @Test public void browserWorksWithLabelsOffAndWorldViewMustMatch()
    {
        Fixture f=new Fixture("rule(true){}");f.items.add(f.item(1,1,0));f.tick(100);assertEquals(1,f.loot.rows().size());assertFalse(f.loot.groundAllowed());GroundLoot.Entry e=f.loot.entries().iterator().next();assertTrue(f.loot.inRange(e,24));when(f.view.getId()).thenReturn(22);assertFalse(f.loot.inRange(e,24));
    }
    @Test public void soundCooldownIsSharedAcrossDifferentPiles()
    {
        Fixture f=new Fixture("rule(true){sound=123;}");when(f.loot.config.lootSounds()).thenReturn(true);f.tick(100);f.loot.onItemSpawned(new ItemSpawned(f.tile,f.item(1,1,200)));f.loot.onItemSpawned(new ItemSpawned(f.tile,f.item(2,1,200)));f.tick(101);verify(f.loot.client,times(1)).playSoundEffect(123,101);
    }
    @Test public void densePilesStayBoundedAndProduceBrowserSnapshot()
    {
        Fixture f=new Fixture("rule(true){}");for(int i=0;i<GroundLoot.MAX_ITEMS+5;i++)f.items.add(f.item(i,1,200));long start=System.nanoTime();f.tick(100);assertTrue(f.loot.entries().size()<=256);System.out.println("Dense 4,101-pile index / first bounded batch (mock API): "+(System.nanoTime()-start)/1000000+" ms");for(int tick=101;tick<126;tick++)f.tick(tick);assertEquals(GroundLoot.MAX_ITEMS,f.loot.entries().size());assertEquals(512,f.loot.rows().size());
        start=System.nanoTime();f.tick(126);System.out.println("Dense steady tick (mock API): "+(System.nanoTime()-start)/1000000+" ms");f.loot.clear();assertTrue(f.loot.entries().isEmpty());
    }
    @Test public void currentApiHasNoPileLocationAndInspectorDoesNotGuessSource()
    {
        Fixture f=new Fixture("rule(true){}");f.items.add(f.item(1,1,200));f.tick(100);
        assertNull(new net.runelite.client.game.ItemStack(1,1,f.tile.getLocalLocation()).getLocation());
        assertTrue(f.loot.entries().stream().allMatch(e->e.source==null));
        assertTrue(f.loot.explain(f.loot.rows().get(0).key).contains("Source: Unavailable"));
    }
    @Test public void activeOtherDisplayWarnsWithoutSilentlyBlockingOurCards()
    {
        Fixture f=new Fixture("rule(true){}");net.runelite.client.plugins.Plugin other=mock(net.runelite.client.plugins.Plugin.class);
        when(other.getName()).thenReturn("Loot Filters TEST");when(f.loot.plugins.getPlugins()).thenReturn(Collections.singleton(other));
        when(f.loot.plugins.isPluginEnabled(other)).thenReturn(true);when(f.loot.config.lootOverlay()).thenReturn(true);
        f.items.add(f.item(1,1,200));f.tick(100);assertTrue(f.loot.groundAllowed());assertFalse(f.loot.status().contains("duplicate"));
        when(f.loot.plugins.isPluginActive(other)).thenReturn(true);f.tick(101);assertTrue(f.loot.groundAllowed());assertTrue(f.loot.status().contains("Loot Filters TEST"));assertEquals(1,f.loot.entries().size());
    }
    @Test public void changingAppearanceAndLoadingFiltersDoNotRestartKnownLifetime()
    {
        Fixture f=new Fixture("rule(true){notify=true;}");when(f.loot.config.lootNotifications()).thenReturn(true);
        f.items.add(f.item(1,1,200));f.tick(100);GroundLoot.Entry entry=f.loot.entries().iterator().next();
        f.tick(150);f.loot.refreshSettings();f.tick(151);assertSame(entry,f.loot.entries().iterator().next());assertEquals(100,entry.spawnTick);
        FilterIcons.Prepared prepared=f.loot.filters.snapshot();when(f.loot.filters.snapshot()).thenReturn(null);f.tick(152);assertTrue(f.loot.entries().isEmpty());
        when(f.loot.filters.snapshot()).thenReturn(prepared);f.tick(153);assertSame(entry,f.loot.entries().iterator().next());assertEquals(100,entry.renderSpawnTick);verifyNoInteractions(f.loot.alerts.notifier);
    }
    @Test public void cardStacksCombineAndSortByTotalValueEvenWithLegacyCompactSaved()
    {
        Fixture f=new Fixture("rule(true){}");when(f.loot.config.lootCompact()).thenReturn(true);
        f.items.add(f.item(1,2,200));f.items.add(f.item(2,1,150));f.items.add(f.item(2,2,180));f.tick(100);
        List<GroundLoot.Entry> visible=new ArrayList<>();for(GroundLoot.Entry e:f.loot.entries())if(!e.grouped)visible.add(e);
        assertEquals(2,visible.size());assertEquals(2,visible.get(0).item.getId());assertEquals(3,visible.get(0).renderQuantity);assertEquals(150,visible.get(0).renderDespawn);
    }
}
