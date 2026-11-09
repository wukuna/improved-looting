package com.improvedlooting;

import com.google.gson.Gson;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigProfile;
import net.runelite.client.util.Filepath;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TierAudioPersistenceTest
{
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    private final AtomicReference<String> raw=new AtomicReference<>();
    private final AtomicReference<ConfigProfile> profile=new AtomicReference<>(profile(42));
    private final List<Runnable> jobs=new ArrayList<>();

    private PriceTiers store(Filepath root)
    {
        PriceTiers s=new PriceTiers();s.gson=new Gson();s.sounds=new TierSounds(()->root);s.configs=mock(ConfigManager.class);s.executor=mock(ScheduledExecutorService.class);
        when(s.configs.getProfile()).thenAnswer(i->profile.get());
        when(s.configs.getConfiguration(ImprovedLootingConfig.GROUP,PriceTiers.KEY)).thenAnswer(i->raw.get());
        doAnswer(i->{raw.set(i.getArgument(2));return null;}).when(s.configs).setConfiguration(eq(ImprovedLootingConfig.GROUP),eq(PriceTiers.KEY),any(String.class));
        doAnswer(i->{jobs.add(i.getArgument(0));return null;}).when(s.executor).execute(any(Runnable.class));return s;
    }
    private Filepath root(String name) throws Exception { return Filepath.Unchecked.getRooted(folder.newFolder(name).toPath()); }
    private static ConfigProfile profile(long id){ConfigProfile p=mock(ConfigProfile.class);when(p.getId()).thenReturn(id);return p;}
    private static PriceTiers.Setup custom(int count,int frames) throws Exception
    {
        PriceTiers.Setup setup=PriceTiers.defaults();
        for(int i=0;i<count;i++){setup.tiers[i].sound=PriceTiers.Sound.CUSTOM;setup.tiers[i].name="tier-"+i+".wav";setup.tiers[i].audio=Base64.getEncoder().encodeToString(TierSoundTestFiles.wave(frames,i));}
        return setup;
    }
    @Test public void sevenLargeSoundsSurviveFreshStoreAndSettingsRoundTrip() throws Exception
    {
        Filepath root=root("sounds");PriceTiers first=store(root);PriceTiers.Setup original=custom(7,240000);
        first.save(first.prepareForSave(original));String saved=raw.get();assertTrue(saved.length()<4000);
        for(PriceTiers.Tier t:original.tiers)assertFalse(saved.contains(t.audio));
        PriceTiers restarted=store(root);restarted.reload();jobs.remove(0).run();
        for(int i=0;i<7;i++){assertEquals(original.tiers[i].name,restarted.snapshot().setup.tiers[i].name);assertEquals(PriceTiers.Sound.CUSTOM,restarted.snapshot().setup.tiers[i].sound);assertArrayEquals(Base64.getDecoder().decode(original.tiers[i].audio),restarted.snapshot().audio[i]);}
        assertEquals(saved,raw.get());
        try(java.util.stream.Stream<Filepath> files=root.walk()){assertEquals(7,files.filter(Filepath::isFile).count());}
        restarted.prepareForSave(restarted.snapshot().setup);
        try(java.util.stream.Stream<Filepath> files=root.walk()){assertEquals(7,files.filter(Filepath::isFile).count());}
    }
    @Test public void existingEmbeddedSoundsMigrateWithoutChangingAudioOrOtherTierOptions() throws Exception
    {
        PriceTiers s=store(root("legacy"));PriceTiers.Setup original=custom(3,16000);original.tiers[1].bold=true;original.tiers[2].minimum=50000;raw.set(s.gson.toJson(original));
        s.reload();jobs.remove(0).run();SwingUtilities.invokeAndWait(()->{});
        assertTrue(raw.get().length()<4000);assertTrue(s.snapshot().setup.tiers[1].bold);assertEquals(50000,s.snapshot().setup.tiers[2].minimum);
        assertEquals(s.gson.toJson(original),s.portable(raw.get()));
    }
    @Test public void migrationCannotWriteIntoAnotherProfile() throws Exception
    {
        PriceTiers s=store(root("switch"));raw.set(s.gson.toJson(custom(1,8000)));String before=raw.get();s.reload();profile.set(profile(99));jobs.remove(0).run();SwingUtilities.invokeAndWait(()->{});
        assertEquals(before,raw.get());verify(s.configs,never()).setConfiguration(anyString(),anyString(),any(String.class));assertNull(s.snapshot());
    }
    @Test public void missingSoundKeepsTierAndFilenameEditableInsteadOfResetting() throws Exception
    {
        Filepath root=root("missing");PriceTiers s=store(root);s.save(s.prepareForSave(custom(1,8000)));String before=raw.get();
        String reference=s.snapshot().setup.tiers[0].audio;root.joinSegment(reference.substring(5)+".audio").delete();
        s.reload();jobs.remove(0).run();assertNotNull(s.snapshot());assertEquals("tier-0.wav",s.snapshot().setup.tiers[0].name);assertEquals(PriceTiers.Sound.CUSTOM,s.snapshot().setup.tiers[0].sound);assertNull(s.snapshot().audio[0]);assertTrue(s.status().contains("tiers 1"));
        try{s.prepareForSave(s.snapshot().setup);fail();}catch(IllegalArgumentException expected){}assertEquals(before,raw.get());
        s.save(s.prepareForSave(custom(1,8000)));assertNotNull(s.snapshot().audio[0]);assertEquals("",s.status());
    }
    @Test public void failedFileWriteLeavesSavedSettingsUntouched() throws Exception
    {
        PriceTiers s=store(root("failure"));raw.set(s.gson.toJson(PriceTiers.defaults()));String before=raw.get();s.sounds=new TierSounds(()->{throw new java.io.IOException("disk full");});
        try{s.save(s.prepareForSave(custom(1,8000)));fail();}catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("Could not save"));}
        assertEquals(before,raw.get());verify(s.configs,never()).setConfiguration(anyString(),anyString(),any(String.class));
    }
    @Test public void shutdownDiscardsQueuedMigration() throws Exception
    {
        PriceTiers s=store(root("shutdown"));raw.set(s.gson.toJson(custom(1,8000)));String before=raw.get();
        s.reload();s.stop();jobs.remove(0).run();SwingUtilities.invokeAndWait(()->{});
        assertEquals(before,raw.get());assertNull(s.snapshot());verify(s.configs,never()).setConfiguration(anyString(),anyString(),any(String.class));
    }
    @Test public void missingOldAudioDoesNotBlockImportRecoveryOrEraseUndoReferences() throws Exception
    {
        Filepath root=root("recovery");PriceTiers s=store(root);s.save(s.prepareForSave(custom(1,8000)));String before=raw.get();String exported=s.portable(before);
        root.joinSegment(s.snapshot().setup.tiers[0].audio.substring(5)+".audio").delete();
        LootProfiles p=LootProfilesTest.profiles();p.icons.tiers=s;LootProfiles.Setup previous=new LootProfiles.Setup();previous.settings.put(PriceTiers.KEY,before);
        String undo=p.encodeStored(previous);assertTrue(undo.contains("file:"));assertTrue(undo.contains("tier-0.wav"));
        LootProfiles.Setup imported=new LootProfiles.Setup();imported.settings.put(PriceTiers.KEY,exported);
        LootProfiles.Setup ready=p.parse(p.icons.gson.toJson(imported));assertNotNull(s.parse(ready.settings.get(PriceTiers.KEY)).audio[0]);
        assertEquals(before,raw.get());
    }
    @Test public void referencesCannotEscapePluginDirectoryAndCorruptionIsDetected() throws Exception
    {
        Filepath root=root("security");TierSounds sounds=new TierSounds(()->root);byte[] bytes=TierSoundTestFiles.wave(8000,1);String ref=sounds.store(bytes);
        try{sounds.read("file:../../outside");fail();}catch(IllegalArgumentException expected){}
        root.joinSegment(ref.substring(5)+".audio").write(new byte[]{1,2,3});
        try{sounds.read(ref);fail();}catch(IllegalArgumentException expected){}
        assertEquals(ref,sounds.store(bytes));assertArrayEquals(bytes,sounds.read(ref));
    }
    @Test public void portableExportImportsOnAnotherComputerAndPresetsStayCompact() throws Exception
    {
        PriceTiers first=store(root("computer-one"));first.save(first.prepareForSave(custom(2,200000)));
        LootProfiles p=LootProfilesTest.profiles();p.icons.tiers=first;
        LootProfiles.Setup setup=new LootProfiles.Setup();setup.settings.put(PriceTiers.KEY,raw.get());
        String exported=p.encode(setup);assertFalse(exported.contains("file:"));assertTrue(exported.length()>1000000);
        LootProfiles.Library library=new LootProfiles.Library();library.presets.put("Mine",setup);assertTrue(p.encodeLibrary(library).length()<4000);assertTrue(p.encodeStored(setup).length()<4000);
        PriceTiers second=store(root("computer-two"));LootProfiles destination=LootProfilesTest.profiles();destination.icons.tiers=second;
        LootProfiles.Setup imported=destination.parse(exported);String importedTiers=imported.settings.get(PriceTiers.KEY);assertTrue(importedTiers.length()<4000);
        assertEquals(first.portable(raw.get()),second.portable(importedTiers));assertEquals(exported,destination.encode(imported));
    }
}
