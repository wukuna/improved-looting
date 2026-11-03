package com.improvedlooting;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import javax.swing.*;
import net.runelite.client.util.Filepath;
import net.runelite.client.config.ConfigProfile;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static java.nio.file.StandardOpenOption.*;

public class LootProfilesEditorTest {
    @Test public void largeExportAndLibraryLeaveUiResponsiveWithoutRenderingBackup() throws Exception {
        LootProfiles profiles=LootProfilesTest.profiles();Map<String,String> values=new HashMap<>();bind(profiles,values);
        values.put(ItemIcons.KEY,"A".repeat(2_000_000));
        LootProfiles.Library library=new LootProfiles.Library();library.presets.put("Existing setup",profiles.capture());
        values.put(LootProfiles.SAVED,profiles.encodeLibrary(library));
        profiles.icons.gson=new com.google.gson.GsonBuilder().registerTypeAdapter(LootProfiles.Library.class,
            (com.google.gson.JsonDeserializer<LootProfiles.Library>)(json,type,context)->{assertFalse("Library parsed off EDT",SwingUtilities.isEventDispatchThread());return new com.google.gson.Gson().fromJson(json,type);}).create();
        Ui ui=new Ui(profiles);ui.ready();
        CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);
        profiles.icons.gson=new com.google.gson.GsonBuilder().registerTypeAdapter(LootProfiles.Setup.class,
            (com.google.gson.JsonSerializer<LootProfiles.Setup>)(setup,type,context)->{assertFalse("Export encoded off EDT",SwingUtilities.isEventDispatchThread());started.countDown();try{assertTrue(release.await(5,TimeUnit.SECONDS));}catch(InterruptedException e){throw new AssertionError(e);}return new com.google.gson.Gson().toJsonTree(setup);}).create();
        Filepath file=mock(Filepath.class);StringWriter result=new StringWriter();
        when(file.openBufferedWriter(CREATE_NEW,WRITE)).thenAnswer(i->{assertFalse(SwingUtilities.isEventDispatchThread());return new BufferedWriter(result);});
        try {
            SwingUtilities.invokeAndWait(()->ui.editor.exportTo(file));assertTrue(started.await(5,TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(()->{
                assertFalse(button(ui.root,"Export settings…").isEnabled());
                ui.editor.exportTo(file);
                assertSmallText(ui.root);
                assertEquals("Existing setup",combo(ui.root,"Saved setups").getSelectedItem());
                render(ui.root,"settings-export-working.png",660,530);
            });
        } finally {release.countDown();}
        ui.ready();SwingUtilities.invokeAndWait(()->render(ui.root,"settings-export-preview.png",660,530));
        assertEquals(profiles.export(),result.toString());verify(file,times(1)).openBufferedWriter(CREATE_NEW,WRITE);ui.close();
    }

    @Test public void fileImportPreservesCustomAssetsAcrossProfilesAndUndo() throws Exception {
        LootProfiles source=LootProfilesTest.profiles();ConfigProfile sourceProfile=profile(11);when(source.icons.configs.getProfile()).thenReturn(sourceProfile);Map<String,String> original=new HashMap<>();bind(source,original);
        original.put("lootSounds","true");original.put("priceTiersEnabled","true");original.put("alertVolume","37");
        original.put("groundStyle","CARDS");original.put("minimumLabelValue","25000");
        PriceTiers.Setup tiers=PriceTiers.defaults();tiers.tiers[6].sound=PriceTiers.Sound.CUSTOM;
        ByteArrayOutputStream audio=new ByteArrayOutputStream();
        javax.sound.sampled.AudioSystem.write(new javax.sound.sampled.AudioInputStream(new ByteArrayInputStream(new byte[800]),
                new javax.sound.sampled.AudioFormat(8000,8,1,true,false),800),javax.sound.sampled.AudioFileFormat.Type.WAVE,audio);
        tiers.tiers[6].audio=Base64.getEncoder().encodeToString(audio.toByteArray());tiers.tiers[6].name="tier-seven.wav";
        original.put(PriceTiers.KEY,source.icons.gson.toJson(tiers));
        original.put(FilterIcons.KEY,source.parse(source.starter(true)).settings.get(FilterIcons.KEY));
        ItemIcons.Assignment picture=new ItemIcons.Assignment();picture.itemId=995;picture.kind=ItemIcons.Kind.IMAGE;
        picture.image=IconImages.encode(new BufferedImage(2,2,BufferedImage.TYPE_INT_ARGB));
        original.put(ItemIcons.KEY,source.icons.gson.toJson(Collections.singletonList(picture)));String exported=source.export();
        LootProfiles target=LootProfilesTest.profiles();Map<String,String> destination=new HashMap<>();destination.put("alertVolume","10");bind(target,destination);String before=target.export();
        AtomicReference<ConfigProfile> current=new AtomicReference<>(profile(22));
        when(target.icons.configs.getProfile()).thenAnswer(i->current.get());
        Filepath file=mock(Filepath.class);when(file.openBufferedReader()).thenAnswer(i->{assertFalse(SwingUtilities.isEventDispatchThread());current.set(profile(22));return new BufferedReader(new StringReader(exported));});
        Ui ui=new Ui(target);ui.ready();SwingUtilities.invokeAndWait(()->ui.editor.importFrom(file));ui.ready();
        assertEquals(before,target.export());
        SwingUtilities.invokeAndWait(()->assertTrue(button(ui.root,"Apply reviewed setup").isEnabled()));
        current.set(profile(22));
        ui.click("Apply reviewed setup");assertEquals(exported,target.export());assertEquals(before,target.undo());
        ui.click("Undo last change");ui.ready();assertEquals(exported,target.export());ui.click("Apply reviewed setup");
        assertEquals(before,target.export());assertEquals(exported,source.export());ui.close();
    }

    @Test public void savedSetupsAndStartersUseOneReviewAndRetainExistingLibrary() throws Exception {
        LootProfiles profiles=LootProfilesTest.profiles();Map<String,String> values=new HashMap<>();values.put("alertVolume","37");bind(profiles,values);
        Ui ui=new Ui(profiles);ui.ready();SwingUtilities.invokeAndWait(()->field(ui.root,"Setup name").setText("Bossing"));ui.click("Save current");ui.ready();
        assertEquals("37",profiles.library().presets.get("Bossing").settings.get("alertVolume"));
        values.put("alertVolume","20");ui.click("Review");ui.ready();assertEquals("20",values.get("alertVolume"));
        ui.click("Apply reviewed setup");assertEquals("37",values.get("alertVolume"));
        ui.click("Review starter");ui.ready();assertNull(values.get(FilterIcons.KEY));ui.click("Apply reviewed setup");assertNotNull(values.get(FilterIcons.KEY));
        ui.click("Delete");assertTrue(profiles.library().presets.containsKey("Bossing"));ui.click("Confirm delete");ui.ready();assertTrue(profiles.library().presets.isEmpty());ui.close();
    }

    @Test public void delayedReviewCannotApplyAfterCloseOrProfileSwitch() throws Exception {
        for(boolean close:new boolean[]{false,true}) {
            LootProfiles profiles=LootProfilesTest.profiles();AtomicBoolean switched=new AtomicBoolean();
            when(profiles.icons.configs.getProfile()).thenAnswer(i->switched.get()?mock(i.getMethod().getReturnType()):null);
            Ui ui=new Ui(profiles);ui.ready();String raw=profiles.export();CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1),finished=new CountDownLatch(1);
            SwingUtilities.invokeAndWait(()->ui.editor.review("Delayed import",()->{started.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));finished.countDown();return raw;}));
            assertTrue(started.await(5,TimeUnit.SECONDS));SwingUtilities.invokeAndWait(()->{if(close)ui.editor.close();else switched.set(true);});release.countDown();assertTrue(finished.await(5,TimeUnit.SECONDS));
            if(!close)ui.ready();ui.click("Apply reviewed setup");verify(profiles.icons.configs,never()).setConfiguration(anyString(),anyString(),anyString());ui.close();
        }
    }

    @Test public void changedSettingsAndInvalidFileNeverApply() throws Exception {
        LootProfiles profiles=LootProfilesTest.profiles();Map<String,String> values=new HashMap<>();values.put("alertVolume","10");bind(profiles,values);
        Ui ui=new Ui(profiles);ui.ready();String raw=profiles.export();SwingUtilities.invokeAndWait(()->ui.editor.review("Old setup",()->raw));ui.ready();values.put("alertVolume","20");ui.click("Apply reviewed setup");
        assertEquals("20",values.get("alertVolume"));assertNull(profiles.undo());
        SwingUtilities.invokeAndWait(()->ui.editor.review("Invalid file",()->"invalid"));ui.ready();
        SwingUtilities.invokeAndWait(()->assertFalse(button(ui.root,"Apply reviewed setup").isEnabled()));assertNull(profiles.undo());ui.close();
    }

    @Test public void fileErrorsRecoverAndExistingExportIsNeverOverwritten() throws Exception {
        LootProfiles profiles=LootProfilesTest.profiles();Ui ui=new Ui(profiles);ui.ready();Filepath file=mock(Filepath.class);
        when(file.openBufferedWriter(CREATE_NEW,WRITE)).thenThrow(new java.nio.file.FileAlreadyExistsException("backup.json"));
        SwingUtilities.invokeAndWait(()->ui.editor.exportTo(file));ui.ready();
        SwingUtilities.invokeAndWait(()->assertTrue(area(ui.root,"Settings status").getText().contains("new name")));
        when(file.openBufferedReader()).thenThrow(new IOException("Unavailable file"));SwingUtilities.invokeAndWait(()->ui.editor.importFrom(file));ui.ready();
        SwingUtilities.invokeAndWait(()->assertFalse(button(ui.root,"Apply reviewed setup").isEnabled()));verify(profiles.icons.configs,never()).setConfiguration(anyString(),anyString(),anyString());ui.close();
    }

    @Test public void oversizedFileIsRejectedBeforeParsing()throws Exception {
        try{LootProfilesEditor.read(new StringReader("x".repeat(24_000_001)));fail();}catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("24 MB"));}
    }

    private static ConfigProfile profile(long id){ConfigProfile p=mock(ConfigProfile.class);when(p.getId()).thenReturn(id);return p;}

    private static final class Ui {
        LootProfilesEditor editor;JPanel root;
        Ui(LootProfiles profiles)throws Exception{SwingUtilities.invokeAndWait(()->{try{UIManager.setLookAndFeel(new net.runelite.client.ui.laf.RuneLiteLAF());}catch(Exception ex){throw new AssertionError(ex);}editor=new LootProfilesEditor(profiles);root=editor.build();});}
        void click(String label)throws Exception{SwingUtilities.invokeAndWait(()->button(root,label).doClick());}
        void close()throws Exception{SwingUtilities.invokeAndWait(editor::close);}
        void ready()throws Exception{await(()->button(root,"Export settings…").isEnabled());}
    }
    private static void await(BooleanSupplier condition)throws Exception {
        CountDownLatch done=new CountDownLatch(1);javax.swing.Timer[] timer=new javax.swing.Timer[1];
        SwingUtilities.invokeAndWait(()->{timer[0]=new javax.swing.Timer(20,e->{if(condition.getAsBoolean()){((javax.swing.Timer)e.getSource()).stop();done.countDown();}});timer[0].start();});
        try{assertTrue("Background action completes",done.await(8,TimeUnit.SECONDS));}finally{SwingUtilities.invokeAndWait(()->timer[0].stop());}
    }
    private static void bind(LootProfiles profiles,Map<String,String> values) {
        when(profiles.icons.configs.getConfiguration(eq(ImprovedLootingConfig.GROUP),anyString())).thenAnswer(i->values.get(i.getArgument(1)));
        doAnswer(i->{values.put(i.getArgument(1),i.getArgument(2));return null;}).when(profiles.icons.configs).setConfiguration(eq(ImprovedLootingConfig.GROUP),anyString(),anyString());
        doAnswer(i->{values.remove(i.getArgument(1));return null;}).when(profiles.icons.configs).unsetConfiguration(eq(ImprovedLootingConfig.GROUP),anyString());
    }
    private static JButton button(Container c,String label){return find(c,JButton.class,label);}
    private static JTextArea area(Container c,String label){return find(c,JTextArea.class,label);}
    private static JTextField field(Container c,String label){return find(c,JTextField.class,label);}
    private static JComboBox<?> combo(Container c,String label){return find(c,JComboBox.class,label);}
    private static <T extends JComponent>T find(Container c,Class<T> type,String label){for(Component child:c.getComponents()){if(type.isInstance(child)&&label.equals(child instanceof JButton?((JButton)child).getText():child.getAccessibleContext().getAccessibleName()))return type.cast(child);if(child instanceof Container){T found=find((Container)child,type,label);if(found!=null)return found;}}return null;}
    private static void assertSmallText(Container c){for(Component child:c.getComponents()){if(child instanceof JTextArea){assertTrue(((JTextArea)child).getText().length()<1000);assertFalse(((JTextArea)child).isEditable());}if(child instanceof Container)assertSmallText((Container)child);}}
    private static void render(JPanel root,String file,int width,int height){root.setSize(width,height);layout(root);layout(root);layout(root);BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();root.printAll(g);g.dispose();try{javax.imageio.ImageIO.write(image,"png",new File("build/"+file));}catch(IOException e){throw new AssertionError(e);}}
    private static void layout(Container c){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container)layout((Container)child);}
}
