package com.improvedlooting;

import com.improvedlooting.rs2f.model.ItemFacts;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.TileItem;
import net.runelite.api.events.ItemSpawned;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class LootVisibilityTest
{
    private TileItem drop(LootRuntimeTest.Fixture f,int id,int quantity,int ownership,boolean tradeable,long price)
    {
        TileItem item=f.item(id,quantity,200);
        when(item.getOwnership()).thenReturn(ownership);
        when(f.loot.filters.facts(f.tile,item)).thenAnswer(i->ItemFacts.builder().id(id).name("Drop "+id)
            .quantity(quantity).ownership(item.getOwnership()).tradeable(tradeable).geTradeable(false).gePrice(price).build());
        return item;
    }
    private GroundLoot.Entry entry(LootRuntimeTest.Fixture f,int id)
    {return f.loot.entries().stream().filter(e->e.item.getId()==id&&!e.grouped).findFirst().get();}

    @Test public void zeroValueUntradeablesBypassMinimumButTradeableZeroValuesDoNot()
    {
        LootRuntimeTest.Fixture f=new LootRuntimeTest.Fixture("rule(true){}");
        when(f.loot.config.minimumLabelValue()).thenReturn(600);
        f.items.add(drop(f,1,1,1,false,0));f.items.add(drop(f,2,1,1,true,0));f.tick(100);
        assertFalse(f.loot.labelHidden(entry(f,1)));assertFalse(f.loot.locallyHidden(entry(f,1)));
        assertTrue(f.loot.labelHidden(entry(f,2)));
        assertEquals(new Color(0xff5555),LootDisplayOptions.color(f.loot.config,entry(f,1),Color.WHITE));
        when(f.loot.config.keepUntradeablesVisible()).thenReturn(false);
        assertTrue(f.loot.labelHidden(entry(f,1)));
    }

    @Test public void untradeableRuleColorsAndManualHidingRemainAuthoritative()
    {
        LootRuntimeTest.Fixture f=new LootRuntimeTest.Fixture("rule(id:1){hidden=true;}\nrule(id:2){textColor=\"#12ab34\";}");
        when(f.loot.config.minimumLabelValue()).thenReturn(600);
        when(f.loot.config.priceTiersEnabled()).thenReturn(true);
        f.items.add(drop(f,1,1,1,false,0));f.items.add(drop(f,2,1,1,false,0));f.items.add(drop(f,3,1,1,false,0));
        f.tick(100);assertTrue(f.loot.labelHidden(entry(f,1)));
        assertEquals(new Color(0x12ab34),LootDisplayOptions.color(f.loot.config,entry(f,2),Color.WHITE));
        when(f.loot.config.untradeableColor()).thenReturn(Color.CYAN);
        assertEquals(Color.CYAN,LootDisplayOptions.color(f.loot.config,entry(f,3),Color.WHITE));
        assertFalse(LootDisplayOptions.tierEffects(f.loot.config,entry(f,3)));
        f.loot.preferences=LootDisplayOptionsTest.preferences(new HashMap<>());
        f.loot.preferences.labelHidden(3,true);assertTrue(f.loot.labelHidden(entry(f,3)));
        f.loot.preferences.labelHidden(3,false);assertFalse(f.loot.labelHidden(entry(f,3)));
    }

    @Test public void ownershipUsesGameFlagsAndManualShowCannotBypassIt()
    {
        LootRuntimeTest.Fixture f=new LootRuntimeTest.Fixture("rule(true){}");
        f.loot.preferences=LootDisplayOptionsTest.preferences(new HashMap<>());
        for(int owner:new int[]{0,1,2,3,99})f.items.add(drop(f,owner+1,1,owner,false,0));
        f.tick(100);
        for(GroundLoot.Entry e:f.loot.entries())assertFalse(f.loot.labelHidden(e));
        when(f.loot.config.onlyShowOwnedDrops()).thenReturn(true);
        for(GroundLoot.Entry e:f.loot.entries())
        {
            boolean expected=e.item.getOwnership()!=1&&e.item.getOwnership()!=3;
            f.loot.preferences.labelHidden(e.item.getId(),false);
            assertEquals(expected,f.loot.labelHidden(e));assertEquals(expected,f.loot.locallyHidden(e));
        }
    }

    @Test public void mixedOwnershipStacksDoNotInflateOwnedQuantityAndChangesReevaluate()
    {
        LootRuntimeTest.Fixture f=new LootRuntimeTest.Fixture("rule(true){}");
        when(f.loot.config.onlyShowOwnedDrops()).thenReturn(true);
        TileItem own=drop(f,1,2,1,true,1000),other=drop(f,1,3,2,true,1000);
        f.items.add(own);f.items.add(other);f.tick(100);
        GroundLoot.Entry shown=f.loot.entries().stream().filter(e->!e.grouped&&!f.loot.labelHidden(e)).findFirst().get();
        assertEquals(2,shown.renderQuantity);
        when(other.getOwnership()).thenReturn(TileItem.OWNERSHIP_GROUP);f.tick(101);
        shown=f.loot.entries().stream().filter(e->!e.grouped&&!f.loot.labelHidden(e)).findFirst().get();
        assertEquals(5,shown.renderQuantity);
        when(other.getOwnership()).thenReturn(TileItem.OWNERSHIP_NONE);f.tick(102);
        shown=f.loot.entries().stream().filter(e->!e.grouped&&!f.loot.labelHidden(e)).findFirst().get();
        assertEquals(2,shown.renderQuantity);
        when(f.loot.config.onlyShowOwnedDrops()).thenReturn(false);f.tick(103);
        assertEquals(5,entry(f,1).renderQuantity);
    }

    @Test public void otherPlayerDropsCannotProduceNotificationCardsSoundsOrWarnings()
    {
        LootRuntimeTest.Fixture f=new LootRuntimeTest.Fixture("rule(true){notify=true;sound=123;}");
        when(f.loot.config.onlyShowOwnedDrops()).thenReturn(true);
        when(f.loot.config.lootNotifications()).thenReturn(true);when(f.loot.config.notificationCards()).thenReturn(true);
        when(f.loot.config.lootSounds()).thenReturn(true);when(f.loot.config.despawnWarnings()).thenReturn(true);
        f.tick(100);f.loot.onItemSpawned(new ItemSpawned(f.tile,drop(f,1,1,2,true,10000)));f.tick(101);f.tick(180);
        verifyNoInteractions(f.loot.alerts.notifier);verify(f.loot.client,never()).playSoundEffect(anyInt(),anyInt());
        assertTrue(f.loot.alerts.cards().isEmpty());
        f.loot.onItemSpawned(new ItemSpawned(f.tile,drop(f,2,1,1,true,10000)));f.tick(181);
        verify(f.loot.alerts.notifier).notify(contains("Drop 2"));assertEquals(1,f.loot.alerts.cards().size());
    }

    @Test public void visibilityDefaultsAndColorSurviveRealConfigProxyAndFullBackup() throws Exception
    {
        ImprovedLootingConfig defaults=ImprovedLootingConfigProxyTest.proxy(Map.of());
        assertTrue(defaults.keepUntradeablesVisible());assertFalse(defaults.onlyShowOwnedDrops());
        assertEquals(new Color(0xff5555),defaults.untradeableColor());
        Map<String,String> saved=Map.of("keepUntradeablesVisible","false","onlyShowOwnedDrops","true","untradeableColor",Integer.toString(Color.CYAN.getRGB()));
        ImprovedLootingConfig config=ImprovedLootingConfigProxyTest.proxy(saved);
        assertFalse(config.keepUntradeablesVisible());assertTrue(config.onlyShowOwnedDrops());assertEquals(Color.CYAN,config.untradeableColor());
        LootProfiles profiles=LootProfilesTest.profiles();
        saved.forEach((key,value)->when(profiles.icons.configs.getConfiguration(ImprovedLootingConfig.GROUP,key)).thenReturn(value));
        String backup=profiles.export();LootProfiles.Setup setup=profiles.parse(backup);profiles.applyValidated(setup,null,backup);
        saved.forEach((key,value)->verify(profiles.icons.configs).setConfiguration(ImprovedLootingConfig.GROUP,key,value));
        setup.settings.put("untradeableColor","oops");
        try{profiles.validate(setup);fail();}catch(IllegalArgumentException expected){}
        assertTrue(profiles.parse("{\"version\":1,\"settings\":{}}").settings.isEmpty());
    }
}
