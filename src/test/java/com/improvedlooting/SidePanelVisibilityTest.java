package com.improvedlooting;

import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SidePanelVisibilityTest
{
    static void set(Object target, String name, Object value) throws Exception
    {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); f.set(target, value);
    }
    @Test public void defaultIsVisibleAndRecoverySettingIsExposed() throws Exception
    {
        assertFalse(new ImprovedLootingConfig() {}.hideSidePanel());
        ConfigItem item = ImprovedLootingConfig.class.getMethod("hideSidePanel").getAnnotation(ConfigItem.class);
        assertFalse(item.hidden()); assertEquals("Hide side panel", item.name());
    }
    @Test public void liveSettingTogglesToolbarAndQueuedUpdateCannotRestoreStoppedPlugin() throws Exception
    {
        ImprovedLootingPlugin plugin = new ImprovedLootingPlugin(); ImprovedLootingConfig config = mock(ImprovedLootingConfig.class);
        ClientToolbar toolbar = mock(ClientToolbar.class);
        NavigationButton navigation = NavigationButton.builder().tooltip("Test").icon(new BufferedImage(16,16,2)).build();
        set(plugin, "config", config); set(plugin, "toolbar", toolbar); set(plugin, "navigation", navigation); set(plugin, "active", true);
        ConfigChanged event = new ConfigChanged(); event.setGroup("improvedlooting"); event.setKey("hideSidePanel");
        when(config.hideSidePanel()).thenReturn(true);
        plugin.onConfigChanged(event); SwingUtilities.invokeAndWait(() -> {});
        verify(toolbar).removeNavigation(navigation); verify(toolbar, never()).addNavigation(any());
        when(config.hideSidePanel()).thenReturn(false);
        plugin.onConfigChanged(event); SwingUtilities.invokeAndWait(() -> {});
        verify(toolbar).addNavigation(navigation); clearInvocations(toolbar);
        SwingUtilities.invokeAndWait(() -> {
            plugin.onConfigChanged(event);
            try { set(plugin, "active", false); } catch (Exception e) { throw new AssertionError(e); }
        });
        SwingUtilities.invokeAndWait(() -> {}); verifyNoInteractions(toolbar);
    }
}
