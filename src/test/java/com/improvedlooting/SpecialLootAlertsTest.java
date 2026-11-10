package com.improvedlooting;

import com.improvedlooting.rs2f.DisplayConfig;
import com.improvedlooting.rs2f.model.ItemFacts;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.gameval.VarClientID;
import net.runelite.client.audio.AudioPlayer;
import net.runelite.client.eventbus.EventBus;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SpecialLootAlertsTest
{
    static final class Fixture
    {
        final LootAlerts alerts = new LootAlerts();
        final SpecialLootEvents events = new SpecialLootEvents();
        final SpecialDropSounds.Setup setup = SpecialDropSounds.defaults();
        final List<Runnable> jobs = new ArrayList<>();
        final byte[][] files = new byte[][]{{1},{2},{3},{4},{5},{6},{7}};
        Fixture()
        {
            alerts.client = mock(Client.class); alerts.config = mock(ImprovedLootingConfig.class, CALLS_REAL_METHODS);
            alerts.audio = mock(AudioPlayer.class); alerts.executor = mock(ScheduledExecutorService.class);
            when(alerts.client.getGameState()).thenReturn(GameState.LOGGED_IN); when(alerts.config.lootSounds()).thenReturn(true);
            when(alerts.config.priceTiersEnabled()).thenReturn(true); when(alerts.client.getTickCount()).thenReturn(100);
            alerts.specials = SpecialDropSoundsTest.store(); alerts.tiers = new PriceTiers(); alerts.tiers.configs = alerts.specials.configs; alerts.tiers.gson = alerts.specials.gson;
            PriceTiers.Setup price = PriceTiers.defaults(); for (PriceTiers.Tier t : price.tiers) t.sound = PriceTiers.Sound.CUSTOM;
            alerts.tiers.save(new PriceTiers.Ready(price, files));
            doAnswer(i -> { jobs.add(i.getArgument(0)); return null; }).when(alerts.executor).execute(any(Runnable.class));
            events.client = alerts.client; events.alerts = alerts; save();
        }
        void set(SpecialDropSounds.Kind kind, int tier)
        { SpecialDropSounds.Choice choice = setup.categories.get(kind); choice.mode = SpecialDropSounds.Mode.TIER; choice.tier = tier; }
        void save() { alerts.specials.save(alerts.specials.prepareForSave(setup)); }
        void tick(int tick) { when(alerts.client.getTickCount()).thenReturn(tick); events.onGameTick(new GameTick()); }
        void drop(String name, boolean tradeable)
        {
            TileItem item = mock(TileItem.class); when(item.getId()).thenReturn(name.hashCode()); when(item.getOwnership()).thenReturn(TileItem.OWNERSHIP_SELF);
            GroundLoot.Entry e = new GroundLoot.Entry(mock(Tile.class), item);
            e.facts = ItemFacts.builder().id(name.hashCode()).name(name).quantity(1).gePrice(0).tradeable(tradeable).build();
            e.display = DisplayConfig.builder().build(); alerts.drop(e, new FilterIcons.Prepared());
        }
        void message(ChatMessageType type, String text)
        { events.onChatMessage(new ChatMessage(null, type, "", text, "", 0)); }
        void petDefinition(String name)
        {
            EnumComposition pets = mock(EnumComposition.class); when(pets.size()).thenReturn(1); when(pets.getIntValue(0)).thenReturn(123);
            when(alerts.client.getEnum(EnumID.PETS)).thenReturn(pets); ItemComposition item = mock(ItemComposition.class);
            when(item.getName()).thenReturn(name); when(alerts.client.getItemDefinition(123)).thenReturn(item);
        }
    }
    @Test public void logOverridesMatchingGroundDropAndChatPlusPopupIsOnlyOneSound() throws Exception
    {
        Fixture f = new Fixture(); f.set(SpecialDropSounds.Kind.COLLECTION_LOG, 6); f.save(); f.drop("Dragon warhammer", true);
        f.message(ChatMessageType.GAMEMESSAGE, "New item added to your collection log: <col=ff0000>Dragon warhammer</col>");
        when(f.alerts.client.getVarcStrValue(VarClientID.NOTIFICATION_TITLE)).thenReturn("Collection log");
        when(f.alerts.client.getVarcStrValue(VarClientID.NOTIFICATION_MAIN)).thenReturn("New item: <col=ff0000>Dragon warhammer</col>");
        f.events.onScriptPreFired(new ScriptPreFired(ScriptID.NOTIFICATION_START));
        f.events.onScriptPreFired(new ScriptPreFired(ScriptID.NOTIFICATION_DELAY));
        assertEquals(0, f.jobs.size()); f.tick(101); assertEquals(1, f.jobs.size()); f.jobs.get(0).run();
        org.mockito.ArgumentCaptor<java.io.InputStream> in = org.mockito.ArgumentCaptor.forClass(java.io.InputStream.class);
        verify(f.alerts.audio).play(in.capture(), anyFloat()); assertEquals(6, in.getValue().read()); f.tick(102); assertEquals(1, f.jobs.size());
    }
    @Test public void petRollAndMatchingLogUsePetSoundInEitherOrder() throws Exception
    {
        for (boolean logFirst : new boolean[]{true, false})
        {
            Fixture f = new Fixture(); f.set(SpecialDropSounds.Kind.PET, 7); f.set(SpecialDropSounds.Kind.COLLECTION_LOG, 6); f.save(); f.petDefinition("Baby mole");
            if (logFirst) f.events.collection("Baby mole");
            f.message(ChatMessageType.GAMEMESSAGE, "You have a funny feeling like you're being followed.");
            if (!logFirst) f.events.collection("Baby mole");
            f.tick(101); assertEquals(1, f.jobs.size()); f.jobs.get(0).run();
            org.mockito.ArgumentCaptor<java.io.InputStream> in = org.mockito.ArgumentCaptor.forClass(java.io.InputStream.class);
            verify(f.alerts.audio).play(in.capture(), anyFloat()); assertEquals(7, in.getValue().read());
        }
    }
    @Test public void publicChatAndBankingPetsDoNotTriggerButDuplicatePetRollDoes()
    {
        Fixture f = new Fixture(); f.set(SpecialDropSounds.Kind.PET, 7); f.save();
        f.message(ChatMessageType.PUBLICCHAT, "You have a funny feeling like you're being followed.");
        f.tick(101); assertTrue(f.jobs.isEmpty());
        f.message(ChatMessageType.GAMEMESSAGE, "You have a funny feeling like you would have been followed.");
        f.tick(102); assertEquals(1, f.jobs.size());
    }
    @Test public void urgentAlertsWaitForPlaybackAndBypassOrdinaryCooldown()
    {
        Fixture f = new Fixture(); f.set(SpecialDropSounds.Kind.PET, 7); f.save(); when(f.alerts.config.soundCooldown()).thenReturn(60);
        f.drop("Coins", true); f.tick(101); assertEquals(1, f.jobs.size());
        f.alerts.special("", true, false); f.tick(102); assertEquals(1, f.jobs.size());
        f.jobs.get(0).run(); f.tick(103); assertEquals(2, f.jobs.size());
    }
    @Test public void silentItemOverrideWinsOverClueAndUntradeable()
    {
        Fixture f = new Fixture(); f.set(SpecialDropSounds.Kind.HARD, 5); f.set(SpecialDropSounds.Kind.UNTRADEABLE, 4);
        SpecialDropSounds.Choice choice = new SpecialDropSounds.Choice(); choice.mode = SpecialDropSounds.Mode.SILENT;
        f.setup.items.put("Clue scroll (hard)", choice); f.save(); f.drop("Clue scroll (hard)", false); f.tick(101); assertTrue(f.jobs.isEmpty());
    }
    @Test public void referencedTierUsesLatestSoundAndCategoryVolume() throws Exception
    {
        Fixture f = new Fixture(); f.set(SpecialDropSounds.Kind.UNTRADEABLE, 4); f.setup.categories.get(SpecialDropSounds.Kind.UNTRADEABLE).volume = 50; f.save();
        f.drop("Dark totem", false); f.files[3] = new byte[]{42}; f.tick(101); f.jobs.get(0).run();
        org.mockito.ArgumentCaptor<java.io.InputStream> in = org.mockito.ArgumentCaptor.forClass(java.io.InputStream.class);
        verify(f.alerts.audio).play(in.capture(), eq(LootAlerts.gain(40))); assertEquals(42, in.getValue().read());
    }
    @Test public void shutdownCancelsQueuedPlaybackAndPendingEvents()
    {
        Fixture f = new Fixture(); f.set(SpecialDropSounds.Kind.PET, 7); f.save(); f.alerts.special("", true, false); f.tick(101);
        f.events.clear(); f.jobs.get(0).run(); verifyNoInteractions(f.alerts.audio); f.tick(102); assertEquals(1, f.jobs.size());
    }
    @Test public void distinctCollectionItemsAreQueuedRatherThanLostToCooldown()
    {
        Fixture f = new Fixture(); f.set(SpecialDropSounds.Kind.COLLECTION_LOG, 6); f.save();
        f.events.collection("First item"); f.events.collection("Second item"); f.tick(101); assertEquals(1, f.jobs.size());
        f.jobs.get(0).run(); f.tick(102); assertEquals(2, f.jobs.size());
    }
    @Test public void realEventBusRegistersAndDispatchesHandlers()
    {
        Fixture f = new Fixture(); f.set(SpecialDropSounds.Kind.PET, 7); f.save(); EventBus bus = new EventBus(); bus.register(f.events);
        bus.post(new ChatMessage(null, ChatMessageType.GAMEMESSAGE, "", "You feel something weird sneaking into your backpack.", "", 0));
        when(f.alerts.client.getTickCount()).thenReturn(101); bus.post(new GameTick()); assertEquals(1, f.jobs.size());
        bus.unregister(f.events); f.events.clear(); bus.post(new ChatMessage(null, ChatMessageType.GAMEMESSAGE, "", "You have a funny feeling like you're being followed.", "", 0));
        f.tick(102); assertEquals(1, f.jobs.size());
    }
}
