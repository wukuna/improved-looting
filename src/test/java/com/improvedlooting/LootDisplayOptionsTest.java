package com.improvedlooting;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class LootDisplayOptionsTest
{
    @Test public void pricesAndTimersUseExplicitPrecedenceAndPicturesOnlySuppressesText()
    {
        LootRuntimeTest.Fixture f=new LootRuntimeTest.Fixture("rule(true){showValue=true;showDespawn=true;}");
        f.items.add(f.item(1,2,200));f.tick(100);GroundLoot.Entry e=f.loot.entries().iterator().next();
        for(ImprovedLootingConfig.DetailMode mode:ImprovedLootingConfig.DetailMode.values()) {
            when(f.loot.config.priceDisplay()).thenReturn(mode);when(f.loot.config.timerDisplay()).thenReturn(mode);
            assertEquals(mode==ImprovedLootingConfig.DetailMode.HIDDEN?0:20000,LootDisplayOptions.price(f.loot.config,e));
            assertEquals(mode==ImprovedLootingConfig.DetailMode.HIDDEN?-1:60,LootDisplayOptions.seconds(f.loot.config,e,100));
        }
        when(f.loot.config.lootCompact()).thenReturn(true);
        assertEquals(0,LootDisplayOptions.price(f.loot.config,e));assertEquals(-1,LootDisplayOptions.seconds(f.loot.config,e,100));
        assertFalse(LootDisplayOptions.detail(ImprovedLootingConfig.DetailMode.RULES,false));
        assertTrue(LootDisplayOptions.detail(ImprovedLootingConfig.DetailMode.ALWAYS,false));
    }
    @Test public void minimumValueUsesStackTotalAndManualShowWinsWithoutChangingMenuRules()
    {
        LootRuntimeTest.Fixture f=new LootRuntimeTest.Fixture("rule(true){hidden=true;}");
        f.loot.preferences=preferences(new HashMap<>());
        when(f.loot.config.minimumLabelValue()).thenReturn(15000);
        f.items.add(f.item(1,1,200));f.items.add(f.item(2,2,200));f.tick(100);
        GroundLoot.Entry one=f.loot.entries().stream().filter(e->e.item.getId()==1).findFirst().get();
        assertTrue(f.loot.labelHidden(one));f.loot.preferences.labelHidden(1,false);
        assertFalse(f.loot.labelHidden(one));assertTrue(one.display.isHidden()); // Menu/filter outcome preserved.
        assertFalse(LootDisplayOptions.hidden(false,null,15000,15000));
        assertTrue(LootDisplayOptions.hidden(false,null,14999,15000));
        assertTrue(LootDisplayOptions.hidden(false,true,999999,0));
    }
    @Test public void labelOverridesSurviveReloadAndKeepExistingCategoryAndRuleOverrides()
    {
        Map<String,String> saved=new HashMap<>();LootPreferences prefs=preferences(saved);
        prefs.set(1,LootPreferences.Action.HIGHLIGHT,"Useful");prefs.labelHidden(1,true);
        LootPreferences loaded=preferences(saved);assertEquals(Boolean.TRUE,loaded.labelHidden(1));
        assertEquals("Useful",loaded.category(1));assertEquals(LootPreferences.Action.HIGHLIGHT,loaded.action(1,null));
        loaded.labelHidden(1,false);assertEquals(Boolean.FALSE,preferences(saved).labelHidden(1));
        loaded.labelHidden(1,null);assertNull(loaded.labelHidden(1));assertEquals("Useful",loaded.category(1));
    }
    @Test public void groupCheckboxReallyDisablesCardGrouping()
    {
        LootRuntimeTest.Fixture f=new LootRuntimeTest.Fixture("rule(true){}");f.items.add(f.item(1,1,200));f.items.add(f.item(1,2,200));
        when(f.loot.config.groupStacks()).thenReturn(false);f.tick(100);
        assertEquals(2,f.loot.entries().stream().filter(e->!e.grouped).count());
        when(f.loot.config.groupStacks()).thenReturn(true);f.tick(101);
        assertEquals(1,f.loot.entries().stream().filter(e->!e.grouped).count());
    }
    @Test public void legacySettingsAndNewEnumsWorkThroughRuneliteProxy() throws Exception
    {
        assertEquals(ImprovedLootingConfig.DetailMode.ALWAYS,ImprovedLootingConfigProxyTest.proxy(Map.of("alwaysShowValue","true")).priceDisplay());
        assertEquals(ImprovedLootingConfig.DetailMode.ALWAYS,ImprovedLootingConfigProxyTest.proxy(Map.of("alwaysShowTimer","true")).timerDisplay());
        assertEquals(ImprovedLootingConfig.DetailMode.HIDDEN,ImprovedLootingConfigProxyTest.proxy(Map.of("alwaysShowValue","true","priceDisplay","HIDDEN")).priceDisplay());
    }
    static LootPreferences preferences(Map<String,String> saved) {
        LootPreferences p=new LootPreferences();p.gson=new Gson();p.configs=mock(ConfigManager.class);
        when(p.configs.getConfiguration(eq(ImprovedLootingConfig.GROUP),anyString())).thenAnswer(c->saved.get(c.getArgument(1)));
        doAnswer(c->{saved.put(c.getArgument(1),c.getArgument(2));return null;}).when(p.configs).setConfiguration(eq(ImprovedLootingConfig.GROUP),anyString(),any(String.class));return p;
    }
}
