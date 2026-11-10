package com.improvedlooting;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.*;
import net.runelite.client.audio.AudioPlayer;
import net.runelite.client.config.ConfigProfile;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SpecialDropEditorTest
{
    @Test public void editorShowsEveryCategoryAndSavesOnlySpecialSettings() throws Exception
    {
        SpecialDropSounds store = SpecialDropSoundsTest.store();
        AtomicReference<String> raw = new AtomicReference<>();
        when(store.configs.getConfiguration(ImprovedLootingConfig.GROUP, SpecialDropSounds.KEY)).thenAnswer(i -> raw.get());
        CountDownLatch saved = new CountDownLatch(1);
        doAnswer(i -> { raw.set(i.getArgument(2)); saved.countDown(); return null; }).when(store.configs)
            .setConfiguration(eq(ImprovedLootingConfig.GROUP), eq(SpecialDropSounds.KEY), any(String.class));
        store.save(store.prepareForSave(SpecialDropSounds.defaults())); clearInvocations(store.configs);
        PriceTiers tiers = new PriceTiers(); tiers.configs = store.configs; tiers.gson = store.gson; tiers.specials = store;
        SpecialDropEditor editor = new SpecialDropEditor(store, tiers, mock(AudioPlayer.class), owner -> null);
        AtomicReference<JPanel> panel = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            panel.set(editor.build(new JPanel())); panel.get().setSize(1160, 550); layout(panel.get());
            List<JComboBox> modes = new ArrayList<>();
            for (JComboBox c : components(panel.get(), JComboBox.class)) if (c.getItemAt(0) == SpecialDropSounds.Mode.INHERIT) modes.add(c);
            assertEquals(9, modes.size()); modes.get(2).setSelectedItem(SpecialDropSounds.Mode.TIER);
            BufferedImage image = new BufferedImage(1160, 550, BufferedImage.TYPE_INT_RGB); Graphics2D g = image.createGraphics(); panel.get().printAll(g); g.dispose();
            try { ImageIO.write(image, "png", new java.io.File("build/special-drops-editor.png")); } catch (Exception e) { throw new AssertionError(e); }
            button(panel.get(), "Save special drops").doClick();
        });
        awaitSave(store, raw);
        assertEquals(SpecialDropSounds.Mode.TIER, store.parse(raw.get()).setup.categories.get(SpecialDropSounds.Kind.UNTRADEABLE).mode);
        verify(store.configs, never()).setConfiguration(eq(ImprovedLootingConfig.GROUP), eq(PriceTiers.KEY), any());
        SwingUtilities.invokeAndWait(editor::close);
    }
    @Test public void profileSwitchRejectsAnOpenEditorSave() throws Exception
    {
        SpecialDropSounds store = SpecialDropSoundsTest.store(); store.save(store.prepareForSave(SpecialDropSounds.defaults()));
        clearInvocations(store.configs); ConfigProfile first = mock(ConfigProfile.class), second = mock(ConfigProfile.class);
        when(first.getId()).thenReturn(1L); when(second.getId()).thenReturn(2L); when(store.configs.getProfile()).thenReturn(first);
        SpecialDropEditor editor = new SpecialDropEditor(store, new PriceTiers(), mock(AudioPlayer.class), owner -> null);
        AtomicReference<JPanel> panel = new AtomicReference<>(); CountDownLatch done = new CountDownLatch(1);
        SwingUtilities.invokeAndWait(() -> {
            panel.set(editor.build(new JPanel())); when(store.configs.getProfile()).thenReturn(second);
            JButton save = button(panel.get(), "Save special drops"); save.doClick();
            javax.swing.Timer timer = new javax.swing.Timer(10, null);
            timer.addActionListener(e -> { if (save.isEnabled()) { timer.stop(); done.countDown(); } }); timer.start();
        });
        assertTrue(done.await(3, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> { assertTrue(components(panel.get(), JTextArea.class).get(0).getText().contains("changed elsewhere")); editor.close(); });
        verify(store.configs, never()).setConfiguration(anyString(), anyString(), any());
    }
    private static void awaitSave(SpecialDropSounds store, AtomicReference<String> raw) throws Exception
    {
        CountDownLatch done = new CountDownLatch(1); AtomicReference<javax.swing.Timer> timer = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            timer.set(new javax.swing.Timer(10, e -> {
                if (store.parse(raw.get()).setup.categories.get(SpecialDropSounds.Kind.UNTRADEABLE).mode == SpecialDropSounds.Mode.TIER) done.countDown();
            })); timer.get().start();
        });
        boolean complete = done.await(3, TimeUnit.SECONDS); SwingUtilities.invokeAndWait(() -> timer.get().stop()); assertTrue(complete);
    }
    private static JButton button(Container root, String text)
    { return components(root, JButton.class).stream().filter(b -> text.equals(b.getText())).findFirst().get(); }
    private static <T> List<T> components(Container root, Class<T> type)
    {
        List<T> result = new ArrayList<>();
        for (Component child : root.getComponents()) { if (type.isInstance(child)) result.add(type.cast(child)); if (child instanceof Container) result.addAll(components((Container) child, type)); }
        return result;
    }
    private static void layout(Container c) { c.doLayout(); for (Component child : c.getComponents()) if (child instanceof Container) layout((Container) child); }
}
