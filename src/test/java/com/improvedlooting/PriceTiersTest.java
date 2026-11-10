package com.improvedlooting;

import java.awt.*;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import javax.swing.*;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.Notifier;
import net.runelite.client.audio.AudioPlayer;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.PluginManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PriceTiersTest
{
    private PriceTiers store(){PriceTiers s=new PriceTiers();s.configs=mock(ConfigManager.class);s.gson=new com.google.gson.Gson();s.executor=mock(ScheduledExecutorService.class);s.sounds=TierSoundTestFiles.temporary();return s;}
    @Test public void boundariesHaveExactlyOneTierAndStackValuesDoNotOverflow() {
        PriceTiers s=store();PriceTiers.Setup data=PriceTiers.defaults();s.prepare(data);
        for(int i=0;i<7;i++){assertEquals(i,PriceTiers.index(data,data.tiers[i].minimum));if(i>0)assertEquals(i-1,PriceTiers.index(data,data.tiers[i].minimum-1));}
        assertEquals(6,PriceTiers.index(data,Long.MAX_VALUE));assertEquals(2000000,PriceTiers.value(1000000,2));assertEquals(Long.MAX_VALUE,PriceTiers.value(Long.MAX_VALUE,2));assertEquals(0,PriceTiers.value(-1,4));
        data.tiers[2].minimum=data.tiers[1].minimum;try{s.prepare(data);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void soundFilesAreRequiredOnlyForCustomAndValidatedBeforeSave() {
        PriceTiers s=store();PriceTiers.Setup data=PriceTiers.defaults();data.tiers[6].sound=PriceTiers.Sound.CUSTOM;
        try{s.prepare(data);fail();}catch(IllegalArgumentException expected){}
        data.tiers[6].audio="garbage";try{s.prepare(data);fail();}catch(IllegalArgumentException expected){}
        verify(s.configs,never()).setConfiguration(anyString(),anyString(),any());
    }
    @Test public void oldProfileLoadCannotReplaceNewProfileAndDraftIsIndependent() {
        PriceTiers s=store();List<Runnable> jobs=new ArrayList<>();doAnswer(i->{jobs.add(i.getArgument(0));return null;}).when(s.executor).execute(any(Runnable.class));
        PriceTiers.Setup next=PriceTiers.defaults();next.tiers[1].minimum=2000;
        when(s.configs.getConfiguration(ImprovedLootingConfig.GROUP,PriceTiers.KEY)).thenReturn(null,s.gson.toJson(next));s.reload();s.reload();jobs.get(1).run();jobs.get(0).run();assertEquals(2000,s.snapshot().setup.tiers[1].minimum);
        PriceTiers.Setup copy=PriceTiers.copy(next);copy.tiers[1].minimum=3000;assertEquals(2000,next.tiers[1].minimum);
    }
    @Test public void effectsMoveInNamedDirectionAndStaticRainbowDoesNotMove() {
        assertTrue(LootTextPaint.start(PriceTiers.Effect.WAVE_LEFT,100,200,.25)<100);
        assertTrue(LootTextPaint.start(PriceTiers.Effect.WAVE_RIGHT,100,200,.25)>100);
        assertEquals(100,LootTextPaint.start(PriceTiers.Effect.RAINBOW,100,200,.25),0);
        assertEquals(LootTextPaint.phase(0,8),LootTextPaint.phase(8000000000L,8),0);
        PriceTiers.Tier tier=new PriceTiers.Tier();assertNull(LootTextPaint.paint(tier,8,0,0,100));tier.effect=PriceTiers.Effect.RGB;assertNotEquals(LootTextPaint.paint(tier,8,0,0,100),LootTextPaint.paint(tier,8,2000000000L,0,100));
    }
    @Test public void perTierBoldPersistsAndOldTierSettingsDefaultToRegular()
    {
        PriceTiers s=store();PriceTiers.Setup data=PriceTiers.defaults();String old=s.gson.toJson(data).replace(",\"bold\":false","");
        for(PriceTiers.Tier tier:s.parse(old).setup.tiers)assertFalse(tier.bold);
        data.tiers[3].bold=true;PriceTiers.Setup copy=PriceTiers.copy(data);assertTrue(copy.tiers[3].bold);copy.tiers[3].bold=false;assertTrue(data.tiers[3].bold);
        PriceTiers.Setup restored=s.parse(s.gson.toJson(data)).setup;for(int i=0;i<7;i++)assertEquals(i==3,restored.tiers[i].bold);
    }
    @Test public void beamCheckboxesPersistIndependentlyAndOlderSettingsKeepThemOff()
    {
        PriceTiers s=store();PriceTiers.Setup data=PriceTiers.defaults();String old=s.gson.toJson(data).replace(",\"lootBeam\":false","");
        for(PriceTiers.Tier tier:s.parse(old).setup.tiers)assertFalse(tier.lootBeam);
        data.tiers[4].lootBeam=true;data.tiers[6].lootBeam=true;PriceTiers.Setup copy=PriceTiers.copy(data);copy.tiers[4].lootBeam=false;assertTrue(data.tiers[4].lootBeam);
        PriceTiers.Setup restored=s.parse(s.gson.toJson(data)).setup;for(int i=0;i<7;i++)assertEquals(i==4||i==6,restored.tiers[i].lootBeam);
    }
    @Test public void textRendererKeepsAccentAndRestoresGraphicsPaint() {
        BufferedImage image=new BufferedImage(220,60,2);Graphics2D g=image.createGraphics();Paint previous=new GradientPaint(0,0,Color.BLACK,20,20,Color.WHITE);g.setPaint(previous);
        com.improvedlooting.rs2f.util.TextComponent text=new com.improvedlooting.rs2f.util.TextComponent();text.setText("Expensive drop");text.setPosition(new Point(4,30));text.setForegroundPaint(new GradientPaint(0,0,Color.RED,100,0,Color.BLUE));text.render(g);assertSame(previous,g.getPaint());g.dispose();
    }
    @Test public void tierSoundOverridesRuleSoundAndSilentPreventsPlayback() throws Exception {
        for(PriceTiers.Sound sound:PriceTiers.Sound.values())for(boolean enabled:new boolean[]{false,true}) {
            LootAlerts alerts=new LootAlerts();alerts.client=mock(Client.class);alerts.config=mock(ImprovedLootingConfig.class);alerts.notifier=mock(Notifier.class);alerts.audio=mock(AudioPlayer.class);alerts.executor=mock(ScheduledExecutorService.class);
            PriceTiers store=store();PriceTiers.Setup setup=PriceTiers.defaults();setup.tiers[0].sound=sound;
            if(sound==PriceTiers.Sound.CUSTOM){ByteArrayOutputStream out=new ByteArrayOutputStream();AudioSystem.write(new AudioInputStream(new ByteArrayInputStream(new byte[800]),new AudioFormat(8000,8,1,true,false),800),AudioFileFormat.Type.WAVE,out);setup.tiers[0].audio=Base64.getEncoder().encodeToString(out.toByteArray());}
            store.save(store.prepareForSave(setup));alerts.tiers=store;
            when(alerts.config.priceTiersEnabled()).thenReturn(enabled);when(alerts.config.lootSounds()).thenReturn(true);when(alerts.config.alertVolume()).thenReturn(80);when(alerts.client.getTickCount()).thenReturn(100);
            FilterIcons.Prepared filter=new FilterIcons.Prepared();filter.program=com.improvedlooting.rs2f.LootFilter.compile("rule(true){sound=123;}");
            Tile tile=mock(Tile.class);TileItem item=mock(TileItem.class);GroundLoot.Entry entry=new GroundLoot.Entry(tile,item);entry.facts=com.improvedlooting.rs2f.model.ItemFacts.builder().id(385).name("Shark").quantity(1).gePrice(100).build();entry.display=filter.program.evaluate(entry.facts);
            doAnswer(i->{((Runnable)i.getArgument(0)).run();return null;}).when(alerts.executor).execute(any(Runnable.class));
            alerts.drop(entry,filter);
            when(alerts.client.getTickCount()).thenReturn(101);alerts.flush();
            verify(alerts.client,times(!enabled || sound==PriceTiers.Sound.RULE?1:0)).playSoundEffect(123,101);
            verify(alerts.audio,times(enabled && sound==PriceTiers.Sound.CUSTOM?1:0)).play(any(InputStream.class),anyFloat());
        }
    }
    @Test public void tierDialogContentFitsAtDefaultSize() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            try{UIManager.setLookAndFeel(new net.runelite.client.ui.laf.RuneLiteLAF());}catch(Exception ex){throw new AssertionError(ex);}
            PriceTiers s=store();PriceTiers.Setup setup=PriceTiers.defaults();setup.tiers[4].lootBeam=true;setup.tiers[4].bold=true;s.save(s.prepare(setup));
            PriceTierEditor editor=new PriceTierEditor(s,mock(AudioPlayer.class));JPanel panel=editor.build(new JPanel());panel.setSize(1180,540);layout(panel);
            List<JCheckBox> checks=new ArrayList<>();checkboxes(panel,checks);assertEquals(14,checks.size());
            for(int i=1;i<=7;i++){String name="Beam tier "+i;JCheckBox checkbox=checks.stream().filter(b->name.equals(b.getAccessibleContext().getAccessibleName())).findFirst().get();assertEquals(i==5,checkbox.isSelected());}
            BufferedImage image=new BufferedImage(1180,540,2);Graphics2D g=image.createGraphics();panel.printAll(g);g.dispose();
            try{ImageIO.write(image,"png",new File("build/price-tiers-preview.png"));}catch(IOException e){throw new AssertionError(e);}editor.close();
        });
    }
    private static void checkboxes(Container c,List<JCheckBox> result){for(Component child:c.getComponents()){if(child instanceof JCheckBox)result.add((JCheckBox)child);if(child instanceof Container)checkboxes((Container)child,result);}}
    private static void layout(Container c){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container)layout((Container)child);}
}
