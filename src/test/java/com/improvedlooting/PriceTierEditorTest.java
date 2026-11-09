package com.improvedlooting;

import java.awt.Component;
import java.awt.Container;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.sound.sampled.*;
import javax.swing.*;
import net.runelite.client.audio.AudioPlayer;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigProfile;
import net.runelite.client.util.Filepath;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PriceTierEditorTest
{
    private final PriceTiers store=new PriceTiers();
    private final AtomicReference<ConfigProfile> profile=new AtomicReference<>();
    private final AtomicReference<String> raw=new AtomicReference<>();
    private final AtomicReference<Filepath> selected=new AtomicReference<>();
    private PriceTierEditor editor;
    private JPanel panel;
    private JButton save;
    private List<JButton> choose;

    @Before public void setup() throws Exception
    {
        store.configs=mock(ConfigManager.class);store.gson=new com.google.gson.Gson();store.sounds=TierSoundTestFiles.temporary();
        profile.set(profile(42));
        when(store.configs.getProfile()).thenAnswer(i->profile.get());
        when(store.configs.getConfiguration(ImprovedLootingConfig.GROUP,PriceTiers.KEY)).thenAnswer(i->raw.get());
        doAnswer(i->{raw.set(i.getArgument(2));return null;}).when(store.configs)
            .setConfiguration(eq(ImprovedLootingConfig.GROUP),eq(PriceTiers.KEY),any(String.class));
        store.save(store.prepare(PriceTiers.defaults()));
        clearInvocations(store.configs);
        editor=new PriceTierEditor(store,mock(AudioPlayer.class),owner->selected.get());
        SwingUtilities.invokeAndWait(()->{
            panel=editor.build(new JPanel());
            List<JButton> buttons=components(panel,JButton.class);
            choose=new ArrayList<>();
            for(JButton b:buttons)if("Choose file".equals(b.getText()))choose.add(b);
            save=buttons.stream().filter(b->"Save tiers".equals(b.getText())).findFirst().get();
        });
        assertEquals(7,choose.size());
    }

    @After public void close() throws Exception { SwingUtilities.invokeAndWait(()->editor.close()); }

    @Test public void multipleCustomSoundsSurviveBackgroundRefreshOfSameProfile() throws Exception
    {
        Filepath first=sound("tier-one.wav",8000),fourth=sound("tier-four.wav",16000);
        importSound(0,first);
        ConfigProfile before=profile.get();profile.set(profile(42));assertNotSame(before,profile.get());
        importSound(3,fourth);
        SwingUtilities.invokeAndWait(()->save.doClick());
        await(()->save.isEnabled() || !raw.get().equals(store.gson.toJson(PriceTiers.defaults())));
        PriceTiers.Setup saved=store.parse(raw.get()).setup;
        assertEquals(PriceTiers.Sound.CUSTOM,saved.tiers[0].sound);
        assertEquals(PriceTiers.Sound.CUSTOM,saved.tiers[3].sound);
        assertEquals("tier-one.wav",saved.tiers[0].name);
        assertEquals("tier-four.wav",saved.tiers[3].name);
        assertNotEquals(saved.tiers[0].audio,saved.tiers[3].audio);
        for(int i:new int[]{1,2,4,5,6})assertEquals(PriceTiers.Sound.RULE,saved.tiers[i].sound);
        verify(store.configs,times(1)).setConfiguration(eq(ImprovedLootingConfig.GROUP),eq(PriceTiers.KEY),any(String.class));
    }

    @Test public void actualProfileSwitchStillRejectsSave() throws Exception
    {
        importSound(0,sound("first.wav",8000));
        profile.set(profile(99));
        assertRejectedSave();
    }

    @Test public void tiersChangedElsewhereStillRejectSave() throws Exception
    {
        importSound(3,sound("fourth.wav",8000));
        PriceTiers.Setup other=PriceTiers.defaults();other.tiers[2].minimum=15000;
        String changed=store.gson.toJson(other);raw.set(changed);
        assertRejectedSave();
        assertEquals(changed,raw.get());
    }

    private void assertRejectedSave() throws Exception
    {
        SwingUtilities.invokeAndWait(()->save.doClick());await(()->save.isEnabled());
        SwingUtilities.invokeAndWait(()->assertTrue(components(panel,JTextArea.class).get(0).getText().contains("changed elsewhere")));
        verify(store.configs,never()).setConfiguration(anyString(),anyString(),any(String.class));
    }
    private void importSound(int tier,Filepath file) throws Exception
    {
        selected.set(file);SwingUtilities.invokeAndWait(()->choose.get(tier).doClick());
        await(()->"Replace file".equals(choose.get(tier).getText()) && save.isEnabled());
        verify(store.configs,never()).setConfiguration(anyString(),anyString(),any(String.class));
    }
    private static ConfigProfile profile(long id)
    { ConfigProfile p=mock(ConfigProfile.class);when(p.getId()).thenReturn(id);return p; }
    private static Filepath sound(String name,int rate) throws Exception
    {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(AudioInputStream in=new AudioInputStream(new ByteArrayInputStream(new byte[rate*2]),new AudioFormat(rate,16,1,true,false),rate))
        { AudioSystem.write(in,AudioFileFormat.Type.WAVE,out); }
        Filepath file=mock(Filepath.class);when(file.getFileName()).thenReturn(name);
        when(file.openInputStream()).thenAnswer(i->new ByteArrayInputStream(out.toByteArray()));return file;
    }
    private static void await(BooleanSupplier condition) throws Exception
    {
        CountDownLatch done=new CountDownLatch(1);AtomicReference<Timer> timer=new AtomicReference<>();
        SwingUtilities.invokeAndWait(()->{timer.set(new Timer(10,e->{if(condition.getAsBoolean())done.countDown();}));timer.get().start();});
        boolean finished=done.await(3,TimeUnit.SECONDS);
        SwingUtilities.invokeAndWait(()->timer.get().stop());assertTrue("Editor operation did not finish",finished);
    }
    private static <T> List<T> components(Container root,Class<T> type)
    {
        List<T> result=new ArrayList<>();
        for(Component child:root.getComponents()){if(type.isInstance(child))result.add(type.cast(child));if(child instanceof Container)result.addAll(components((Container)child,type));}
        return result;
    }
}
