package com.improvedlooting;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TierReloadAlertsTest
{
    static final class Loading
    {
        final SpecialLootAlertsTest.Fixture f = new SpecialLootAlertsTest.Fixture();
        final List<Runnable> loads = new ArrayList<>();
        Loading(PriceTiers.Sound mode) throws Exception
        {
            PriceTiers tiers = f.alerts.tiers;
            tiers.sounds = TierSoundTestFiles.temporary();
            tiers.executor = mock(ScheduledExecutorService.class);
            doAnswer(i -> { loads.add(i.getArgument(0)); return null; }).when(tiers.executor).execute(any(Runnable.class));
            PriceTiers.Setup setup = PriceTiers.defaults();
            for (PriceTiers.Tier tier : setup.tiers)
            {
                tier.sound = mode;
                if (mode == PriceTiers.Sound.CUSTOM) tier.audio = Base64.getEncoder().encodeToString(TierSoundTestFiles.wave(800, 4));
            }
            PriceTiers.Ready ready = tiers.prepareForSave(setup);
            when(tiers.configs.getConfiguration(ImprovedLootingConfig.GROUP, PriceTiers.KEY)).thenReturn(tiers.gson.toJson(ready.setup));
            tiers.reload(); assertNull(tiers.snapshot());
        }
        void loaded() { loads.remove(0).run(); }
    }
    @Test public void ordinarySoundSurvivesReloadBeforeAndAfterFirstFlush() throws Exception
    {
        for (boolean flushFirst : new boolean[]{false, true})
        {
            Loading l = new Loading(PriceTiers.Sound.CUSTOM); l.f.drop("Coins", true);
            if (flushFirst) { l.f.tick(101); assertTrue(l.f.jobs.isEmpty()); }
            l.loaded(); l.f.tick(102); assertEquals(1, l.f.jobs.size());
        }
    }
    @Test public void silentTierNeverFallsBackToRuleWhileLoading() throws Exception
    {
        Loading l = new Loading(PriceTiers.Sound.SILENT);
        LootAlerts.Pending p = new LootAlerts.Pending("Coins", 100);
        p.pricePending = true; p.rule = new LootAlerts.Sound(null, 123, 100); p.price = p.rule; p.dropSettled = true;
        java.lang.reflect.Method add = LootAlerts.class.getDeclaredMethod("add", LootAlerts.Pending.class); add.setAccessible(true); add.invoke(l.f.alerts, p);
        l.f.tick(101); verify(l.f.alerts.client, never()).playSoundEffect(anyInt(), anyInt());
        l.loaded(); l.f.tick(102); verify(l.f.alerts.client, never()).playSoundEffect(anyInt(), anyInt());
    }
    @Test public void failedLoadExpiresAndProfileClearDiscardsPendingSounds() throws Exception
    {
        Loading expired = new Loading(PriceTiers.Sound.CUSTOM); expired.f.drop("Coins", true);
        expired.f.tick(106); expired.loaded(); expired.f.tick(107); assertTrue(expired.f.jobs.isEmpty());
        Loading profile = new Loading(PriceTiers.Sound.CUSTOM); profile.f.drop("Coins", true);
        profile.f.events.clear(); profile.loaded(); profile.f.tick(101); assertTrue(profile.f.jobs.isEmpty());
    }
    @Test public void waitingPetAndCollectionSoundsKeepPriorityOverOrdinaryDrop() throws Exception
    {
        for (SpecialDropSounds.Kind kind : new SpecialDropSounds.Kind[]{SpecialDropSounds.Kind.PET, SpecialDropSounds.Kind.COLLECTION_LOG})
        {
            Loading l = new Loading(PriceTiers.Sound.CUSTOM); l.f.set(kind, 7); l.f.save();
            l.f.drop("Coins", true); l.f.alerts.special("Reward", kind == SpecialDropSounds.Kind.PET, kind == SpecialDropSounds.Kind.COLLECTION_LOG);
            l.f.tick(101); assertTrue(l.f.jobs.isEmpty());
            l.loaded(); l.f.tick(102); assertEquals(1, l.f.jobs.size());
        }
    }
    @Test public void profileChangeRejectsOldTierSnapshotAndQueuedPlayback() throws Exception
    {
        SpecialLootAlertsTest.Fixture f = new SpecialLootAlertsTest.Fixture();
        f.drop("Coins", true); f.tick(101); assertEquals(1, f.jobs.size());
        net.runelite.client.config.ConfigProfile profile = mock(net.runelite.client.config.ConfigProfile.class);
        when(profile.getId()).thenReturn(99L); when(f.alerts.tiers.configs.getProfile()).thenReturn(profile);
        assertNull(f.alerts.tiers.snapshot()); f.jobs.get(0).run(); verifyNoInteractions(f.alerts.audio);
    }
}
