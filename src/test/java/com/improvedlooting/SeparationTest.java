package com.improvedlooting;

import java.util.*;
import java.awt.image.BufferedImage;
import net.runelite.api.*;
import net.runelite.api.events.MenuOpened;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SeparationTest
{
    @Test public void migrationCopiesValidatedDataOnlyIntoNewGroupAndNeverOverwrites()
    {
        LegacyImport migration=new LegacyImport(); migration.configs=mock(ConfigManager.class); migration.filters=mock(FilterIcons.class); migration.icons=mock(ItemIcons.class);
        when(migration.configs.getConfiguration("improvedmenus",FilterIcons.KEY)).thenReturn("filter");
        when(migration.configs.getConfiguration("improvedmenus",ItemIcons.KEY)).thenReturn("icons");
        Map<String,String> copy=migration.prepare(); verify(migration.filters).parse("filter"); verify(migration.icons).parse("icons");
        migration.apply(copy);
        verify(migration.configs).setConfiguration("improvedlooting",FilterIcons.KEY,"filter");
        verify(migration.configs,never()).setConfiguration(eq("improvedmenus"),anyString(),any());
        clearInvocations(migration.configs);
        when(migration.configs.getConfiguration("improvedlooting",ItemIcons.KEY)).thenReturn("existing");
        try {migration.apply(copy);fail();} catch(IllegalArgumentException expected) {}
        verify(migration.configs,never()).setConfiguration(anyString(),anyString(),any());
    }
}
