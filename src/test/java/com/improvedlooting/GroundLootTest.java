package com.improvedlooting;

import com.improvedlooting.rs2f.*;
import com.improvedlooting.rs2f.model.*;
import java.awt.Color;
import java.util.*;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.Notifier;
import net.runelite.client.plugins.PluginManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class GroundLootTest
{
    @Test public void fullDisplayMergesFalseOverridesWithoutChangingMenuIconSemantics()
    {
        LootFilter filter=LootFilter.compile("apply(true) {hidden=true; icon=Item(995); showLootbeam=true; textColor=\"#00ff00\"; notify=true;} rule(true) {hidden=false; showLootbeam=false; hideOverlay=true;}");
        ItemFacts facts=ItemFacts.builder().id(385).name("Shark").quantity(1).build();
        DisplayConfig display=filter.evaluate(facts);
        assertFalse(display.isHidden()); assertTrue(display.isHideOverlay()); assertFalse(display.isShowLootbeam()); assertTrue(display.isNotify());
        assertEquals(Color.GREEN, display.getTextColor()); assertNull(filter.find(facts)); assertEquals(2,display.getEvalTrace().size());
    }
    @Test public void existingPileIsSilentFactsAreCachedAndLogoutClears()
    {
        GroundLoot loot=new GroundLoot(); loot.client=mock(Client.class); loot.config=mock(ImprovedLootingConfig.class); loot.filters=mock(FilterIcons.class);
        loot.plugins=mock(PluginManager.class); loot.notifier=mock(Notifier.class); loot.icons=mock(ItemIcons.class);
        when(loot.plugins.getPlugins()).thenReturn(Collections.emptyList()); when(loot.config.lootOverlay()).thenReturn(true); when(loot.config.lootNotifications()).thenReturn(true);
        when(loot.client.getGameState()).thenReturn(GameState.LOGGED_IN); when(loot.client.getTickCount()).thenReturn(100);
        WorldView view=mock(WorldView.class); Scene scene=mock(Scene.class); Tile tile=mock(Tile.class); TileItem item=mock(TileItem.class);
        when(loot.client.getTopLevelWorldView()).thenReturn(view); when(view.getScene()).thenReturn(scene);
        when(scene.getTiles()).thenReturn(new Tile[][][]{{{tile}}}); when(tile.getGroundItems()).thenReturn(Arrays.asList(item));
        ItemFacts facts=ItemFacts.builder().id(385).name("Shark").quantity(1).build(); when(loot.filters.facts(tile,item)).thenReturn(facts);
        FilterIcons.Prepared prepared=new FilterIcons.Prepared(); prepared.program=LootFilter.compile("rule(true) {notify=true;}"); when(loot.filters.snapshot()).thenReturn(prepared);
        Player player=mock(Player.class);when(loot.client.getLocalPlayer()).thenReturn(player);when(player.getWorldView()).thenReturn(view);net.runelite.api.coords.LocalPoint point=new net.runelite.api.coords.LocalPoint(128,128,view);when(player.getLocalLocation()).thenReturn(point);when(tile.getLocalLocation()).thenReturn(point);
        loot.onGameTick(new GameTick()); assertEquals(1,loot.entries().size()); verifyNoInteractions(loot.notifier);
        DisplayConfig first=loot.entries().iterator().next().display;
        loot.onGameTick(new GameTick()); assertSame(first,loot.entries().iterator().next().display);
        when(loot.filters.facts(tile,item)).thenReturn(ItemFacts.builder().id(385).name("Shark").quantity(2).build());
        when(item.getQuantity()).thenReturn(2);loot.onGameTick(new GameTick()); assertNotSame(first,loot.entries().iterator().next().display); verifyNoInteractions(loot.notifier);
        when(loot.client.getGameState()).thenReturn(GameState.LOGIN_SCREEN); loot.onGameTick(new GameTick()); assertTrue(loot.entries().isEmpty()); assertFalse(loot.isRunning());
    }
    @Test public void invalidCustomAudioDoesNotApply()
    {
        try { FilterSounds.decode(Base64.getEncoder().encodeToString(new byte[]{1,2,3})); fail(); } catch(IllegalArgumentException expected) { }
        SoundProvider sound=LootFilter.compile("rule(true) {sound=123;}").rules.get(0).getDisplay().getSound();
        assertEquals(Integer.valueOf(123),sound.getEffect()); assertNull(sound.getFile());
    }
}
