package com.improvedlooting;

import java.io.InputStream;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemSpawned;
import net.runelite.client.eventbus.EventBus;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class LootSoundTimingTest
{
    @Test public void groundSoundsPlayOnFirstSettledTickWithNativeAndCustomAudio() throws Exception
    {
        for (boolean custom : new boolean[]{false, true})
        {
            LootRuntimeTest.Fixture f = new LootRuntimeTest.Fixture("rule(true){sound=123;}");
            SpecialLootAlertsTest.Fixture sounds = new SpecialLootAlertsTest.Fixture();
            when(f.loot.config.lootSounds()).thenReturn(true);
            when(f.loot.config.priceTiersEnabled()).thenReturn(custom);
            f.loot.alerts.tiers = sounds.alerts.tiers;
            f.loot.alerts.specials = sounds.alerts.specials;
            f.loot.alerts.audio = sounds.alerts.audio;
            f.loot.alerts.executor = sounds.alerts.executor;
            SpecialLootEvents events = new SpecialLootEvents();
            events.client = f.loot.client; events.alerts = f.loot.alerts;
            EventBus bus = new EventBus(); bus.register(f.loot); bus.register(events);
            bus.post(new GameTick());
            bus.post(new ItemSpawned(f.tile, f.item(1, 1, 200)));
            bus.post(new GameTick());
            assertTrue(sounds.jobs.isEmpty());
            verify(f.loot.client, never()).playSoundEffect(anyInt(), anyInt());
            when(f.loot.client.getTickCount()).thenReturn(101); bus.post(new GameTick());
            if (custom) assertSound(sounds, 3);
            else verify(f.loot.client).playSoundEffect(123, 101);
            when(f.loot.client.getTickCount()).thenReturn(102); bus.post(new GameTick());
            if (custom) assertEquals(1, sounds.jobs.size());
            else verify(f.loot.client, times(1)).playSoundEffect(anyInt(), anyInt());
        }
    }

    @Test public void freshDropStillWaitsForItsFirstSettlingTick() throws Exception
    {
        SpecialLootAlertsTest.Fixture f = new SpecialLootAlertsTest.Fixture();
        f.drop("Coins", true); f.tick(100); assertTrue(f.jobs.isEmpty());
        f.tick(101); assertSound(f, 1);
    }

    @Test public void matchingCollectionOverridesSettledDropInEitherOrder() throws Exception
    {
        for (boolean logFirst : new boolean[]{true, false})
        {
            SpecialLootAlertsTest.Fixture f = new SpecialLootAlertsTest.Fixture();
            f.set(SpecialDropSounds.Kind.COLLECTION_LOG, 6); f.save();
            if (logFirst) f.events.collection("Dragon warhammer");
            f.drop("Dragon warhammer", true, 99);
            if (!logFirst) f.events.collection("Dragon warhammer");
            f.tick(100); assertSound(f, 6);
            f.tick(101); assertEquals(1, f.jobs.size());
        }
    }

    @Test public void petWinsWhenUnnamedPetLogAndSettledDropArriveInAnyOrder() throws Exception
    {
        for (String order : new String[]{"pdl", "pld", "dpl", "dlp", "lpd", "ldp"})
        {
            SpecialLootAlertsTest.Fixture f = new SpecialLootAlertsTest.Fixture();
            f.set(SpecialDropSounds.Kind.PET, 7); f.set(SpecialDropSounds.Kind.COLLECTION_LOG, 6);
            f.save(); f.petDefinition("Baby mole");
            for (char event : order.toCharArray())
            {
                if (event == 'p') f.message(ChatMessageType.GAMEMESSAGE, "You have a funny feeling like you're being followed.");
                if (event == 'd') f.drop("Baby mole", false, 99);
                if (event == 'l') f.events.collection("Baby mole");
            }
            f.tick(100); assertSound(f, 7);
            f.tick(101); assertEquals(1, f.jobs.size());
        }
    }

    @Test public void freshSpecialKeepsPriorityOverAnAlreadyWaitingOrdinaryDrop() throws Exception
    {
        for (boolean pet : new boolean[]{true, false})
        {
            SpecialLootAlertsTest.Fixture f = new SpecialLootAlertsTest.Fixture();
            f.set(pet ? SpecialDropSounds.Kind.PET : SpecialDropSounds.Kind.COLLECTION_LOG, pet ? 7 : 6); f.save();
            f.drop("Coins", true);
            when(f.alerts.client.getTickCount()).thenReturn(101);
            f.alerts.special(pet ? "" : "Dragon warhammer", pet, !pet);
            f.tick(101); assertTrue(f.jobs.isEmpty());
            f.tick(102); assertSound(f, pet ? 7 : 6);
        }
    }

    @Test public void freshSpecialKeepsPriorityOverASettledOrdinaryDrop() throws Exception
    {
        for (boolean pet : new boolean[]{true, false})
        {
            SpecialLootAlertsTest.Fixture f = new SpecialLootAlertsTest.Fixture();
            f.set(pet ? SpecialDropSounds.Kind.PET : SpecialDropSounds.Kind.COLLECTION_LOG, pet ? 7 : 6); f.save();
            f.drop("Coins", true, 99);
            f.alerts.special(pet ? "" : "Dragon warhammer", pet, !pet);
            f.tick(100); assertTrue(f.jobs.isEmpty());
            f.tick(101); assertSound(f, pet ? 7 : 6);
        }
    }

    @Test public void silentOrUnconfiguredSpecialDoesNotDelayOrdinaryAudio() throws Exception
    {
        for (SpecialDropSounds.Mode mode : new SpecialDropSounds.Mode[]{SpecialDropSounds.Mode.SILENT, SpecialDropSounds.Mode.INHERIT})
        {
            SpecialLootAlertsTest.Fixture f = new SpecialLootAlertsTest.Fixture();
            f.setup.categories.get(SpecialDropSounds.Kind.COLLECTION_LOG).mode = mode; f.save();
            f.events.collection("Dragon warhammer"); f.drop("Coins", true, 99);
            f.tick(100); assertSound(f, 1);
        }
    }

    @Test public void unresolvedCollectionKeepsItsGroundSoundFallback() throws Exception
    {
        SpecialLootAlertsTest.Fixture f = new SpecialLootAlertsTest.Fixture();
        f.events.collection("Dragon warhammer"); f.tick(100); assertTrue(f.jobs.isEmpty());
        f.drop("Dragon warhammer", true, 99); f.tick(100); assertSound(f, 1);
    }

    @Test public void delayedGroundEvaluationDoesNotExpireTheNewAlert() throws Exception
    {
        SpecialLootAlertsTest.Fixture f = new SpecialLootAlertsTest.Fixture();
        f.drop("Coins", true, 90); f.tick(100); assertSound(f, 1);
    }

    @Test public void logoutCancelsSettledAudioBeforeTheWorkerStarts()
    {
        SpecialLootAlertsTest.Fixture f = new SpecialLootAlertsTest.Fixture();
        f.drop("Coins", true, 99); f.tick(100); assertEquals(1, f.jobs.size());
        GameStateChanged logout = new GameStateChanged(); logout.setGameState(GameState.LOGIN_SCREEN);
        f.events.onGameStateChanged(logout); f.jobs.get(0).run();
        verifyNoInteractions(f.alerts.audio);
        f.tick(101); assertEquals(1, f.jobs.size());
    }

    private static void assertSound(SpecialLootAlertsTest.Fixture f, int expected) throws Exception
    {
        assertEquals(1, f.jobs.size()); f.jobs.get(0).run();
        ArgumentCaptor<InputStream> in = ArgumentCaptor.forClass(InputStream.class);
        verify(f.alerts.audio).play(in.capture(), anyFloat());
        assertEquals(expected, in.getValue().read());
    }
}
