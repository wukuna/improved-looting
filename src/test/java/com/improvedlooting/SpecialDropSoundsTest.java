package com.improvedlooting;

import com.google.gson.Gson;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigProfile;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SpecialDropSoundsTest
{
    static SpecialDropSounds store()
    {
        SpecialDropSounds s = new SpecialDropSounds(); s.gson = new Gson(); s.configs = mock(ConfigManager.class);
        s.sounds = TierSoundTestFiles.temporary(); s.executor = mock(ScheduledExecutorService.class); return s;
    }
    static SpecialDropSounds.Choice custom(int seed) throws Exception
    {
        SpecialDropSounds.Choice c = new SpecialDropSounds.Choice(); c.mode = SpecialDropSounds.Mode.CUSTOM;
        c.audio = Base64.getEncoder().encodeToString(TierSoundTestFiles.wave(800, seed)); c.name = "sound-" + seed + ".wav"; return c;
    }
    @Test public void precedenceIsPetLogItemClueUntradeableThenPrice()
    {
        SpecialDropSounds.Setup s = SpecialDropSounds.defaults();
        SpecialDropSounds.Choice item = new SpecialDropSounds.Choice(); item.mode = SpecialDropSounds.Mode.SILENT; s.items.put("Clue scroll (hard)", item);
        for (SpecialDropSounds.Choice c : s.categories.values()) c.mode = SpecialDropSounds.Mode.TIER;
        assertSame(s.categories.get(SpecialDropSounds.Kind.PET), SpecialDropSounds.match(s, "Clue scroll (hard)", true, true, true));
        assertSame(s.categories.get(SpecialDropSounds.Kind.COLLECTION_LOG), SpecialDropSounds.match(s, "Clue scroll (hard)", false, true, true));
        assertSame(item, SpecialDropSounds.match(s, "CLUE SCROLL (HARD)", false, false, true));
        item.mode = SpecialDropSounds.Mode.INHERIT;
        assertSame(s.categories.get(SpecialDropSounds.Kind.HARD), SpecialDropSounds.match(s, "Clue scroll (hard)", false, false, true));
        s.categories.get(SpecialDropSounds.Kind.HARD).mode = SpecialDropSounds.Mode.INHERIT;
        assertSame(s.categories.get(SpecialDropSounds.Kind.UNTRADEABLE), SpecialDropSounds.match(s, "Clue scroll (hard)", false, false, true));
        assertNull(SpecialDropSounds.match(s, "Coins", false, false, false));
    }
    @Test public void defaultsDoNotChangeAnyExistingSoundSelection()
    {
        SpecialDropSounds.Setup s = SpecialDropSounds.defaults();
        for (boolean pet : new boolean[]{false, true}) for (boolean log : new boolean[]{false, true})
            assertNull(SpecialDropSounds.match(s, "Clue scroll (elite)", pet, log, true));
    }
    @Test public void allSixClueDifficultiesAndContainersAreDistinguished()
    {
        for (SpecialDropSounds.Kind kind : SpecialDropSounds.Kind.values()) if (kind.ordinal() >= 3)
            for (String prefix : new String[]{"Clue scroll", "Clue bottle", "Clue nest", "Clue geode", "Clue scroll box"})
                assertEquals(kind, SpecialDropSounds.clue(prefix + " (" + kind.name().toLowerCase(Locale.ROOT) + ")"));
        assertNull(SpecialDropSounds.clue("Reward casket (hard)"));
        assertNull(SpecialDropSounds.clue("Clue scroll (unknown)"));
        assertNull(SpecialDropSounds.clue("Something clue scroll (hard)"));
    }
    @Test public void categoryAndItemFilesRoundTripWithoutWritingPriceTiers() throws Exception
    {
        SpecialDropSounds s = store(); SpecialDropSounds.Setup setup = SpecialDropSounds.defaults();
        setup.categories.put(SpecialDropSounds.Kind.UNTRADEABLE, custom(1)); setup.items.put("Dark totem", custom(2));
        s.save(s.prepareForSave(setup)); String raw = s.gson.toJson(s.snapshot().setup);
        assertTrue(raw.length() < 5000); assertTrue(raw.contains("file:"));
        verify(s.configs).setConfiguration(eq(ImprovedLootingConfig.GROUP), eq(SpecialDropSounds.KEY), eq(raw));
        verify(s.configs, never()).setConfiguration(eq(ImprovedLootingConfig.GROUP), eq(PriceTiers.KEY), any());
        SpecialDropSounds fresh = store(); fresh.sounds = s.sounds;
        assertEquals(s.portable(raw), fresh.portable(raw));
        SpecialDropSounds elsewhere = store(); String portable = s.portable(raw);
        assertEquals(portable, elsewhere.portable(elsewhere.stored(portable)));
        assertEquals("sound-2.wav", elsewhere.parse(elsewhere.stored(portable)).setup.items.get("Dark totem").name);
    }
    @Test public void reloadKeepsMissingFileChoicesAndFilename() throws Exception
    {
        SpecialDropSounds s = store(); SpecialDropSounds.Setup setup = SpecialDropSounds.defaults(); setup.categories.put(SpecialDropSounds.Kind.PET, custom(1));
        s.save(s.prepareForSave(setup)); String raw = s.gson.toJson(s.snapshot().setup);
        SpecialDropSounds fresh = store(); SpecialDropSounds.Ready missing = fresh.prepare(fresh.gson.fromJson(raw, SpecialDropSounds.Setup.class), true);
        assertTrue(missing.status.contains("Pets")); assertEquals("sound-1.wav", missing.setup.categories.get(SpecialDropSounds.Kind.PET).name);
        assertEquals(SpecialDropSounds.Mode.CUSTOM, missing.setup.categories.get(SpecialDropSounds.Kind.PET).mode);
        assertEquals(raw, fresh.gson.toJson(fresh.prepareForSave(missing.setup).setup));
        try { fresh.portable(raw); fail(); } catch (IllegalArgumentException expected) { }
    }
    @Test public void staleReloadAndShutdownCannotPopulateAnotherProfile()
    {
        for (boolean stop : new boolean[]{false, true})
        {
            SpecialDropSounds s = store(); List<Runnable> jobs = new ArrayList<>();
            doAnswer(i -> { jobs.add(i.getArgument(0)); return null; }).when(s.executor).execute(any(Runnable.class));
            ConfigProfile first = mock(ConfigProfile.class), second = mock(ConfigProfile.class);
            when(first.getId()).thenReturn(1L); when(second.getId()).thenReturn(2L); when(s.configs.getProfile()).thenReturn(first);
            s.reload(); if (stop) s.stop(); else when(s.configs.getProfile()).thenReturn(second);
            jobs.get(0).run(); assertNull(s.snapshot()); verify(s.configs, never()).setConfiguration(anyString(), anyString(), any());
        }
    }
    @Test public void invalidChoicesCannotBeSaved()
    {
        SpecialDropSounds s = store();
        for (int n : new int[]{0, 8})
        {
            SpecialDropSounds.Setup setup = SpecialDropSounds.defaults(); setup.categories.get(SpecialDropSounds.Kind.PET).tier = n;
            try { s.prepareForSave(setup); fail(); } catch (IllegalArgumentException expected) { }
        }
        SpecialDropSounds.Setup setup = SpecialDropSounds.defaults(); setup.items.put("Coins", new SpecialDropSounds.Choice()); setup.items.put("COINS", new SpecialDropSounds.Choice());
        try { s.prepareForSave(setup); fail(); } catch (IllegalArgumentException expected) { }
        verify(s.configs, never()).setConfiguration(anyString(), anyString(), any());
    }
    @Test public void completeProfilesExportBothSoundSystemsWithoutChangingSourceSettings() throws Exception
    {
        LootProfiles p = LootProfilesTest.profiles(); p.icons.tiers.specials = store();
        SpecialDropSounds.Setup special = SpecialDropSounds.defaults(); special.categories.put(SpecialDropSounds.Kind.PET, custom(1));
        String specialRaw = p.icons.gson.toJson(p.icons.tiers.specials.prepareForSave(special).setup);
        PriceTiers.Setup price = PriceTiers.defaults(); price.tiers[3].sound = PriceTiers.Sound.CUSTOM;
        price.tiers[3].audio = custom(2).audio; price.tiers[3].name = "my-updated-tier.wav";
        String priceRaw = p.icons.gson.toJson(p.icons.tiers.prepareForSave(price).setup);
        LootProfiles.Setup setup = new LootProfiles.Setup(); setup.settings.put(PriceTiers.KEY, priceRaw); setup.settings.put(SpecialDropSounds.KEY, specialRaw);
        String exported = p.encode(setup); assertFalse(exported.contains("file:"));
        LootProfiles.Setup imported = p.parse(exported);
        assertEquals(priceRaw, imported.settings.get(PriceTiers.KEY)); assertEquals(specialRaw, imported.settings.get(SpecialDropSounds.KEY));
        assertTrue(p.encodeStored(setup).length() < 6000); verify(p.icons.configs, never()).setConfiguration(anyString(), anyString(), any());
    }
}
