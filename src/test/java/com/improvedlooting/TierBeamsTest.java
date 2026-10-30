package com.improvedlooting;

import java.awt.Color;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.*;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TierBeamsTest
{
    private static PriceTiers.Setup prepare(LootRuntimeTest.Fixture f)
    {
        when(f.loot.config.lootOverlay()).thenReturn(true);
        ModelData model=mock(ModelData.class,RETURNS_SELF);when(model.light(anyInt(),anyInt(),anyInt(),anyInt(),anyInt())).thenReturn(mock(Model.class));when(f.loot.client.loadModelData(43330)).thenReturn(model);
        f.loot.tiers=new PriceTiers();f.loot.tiers.configs=mock(ConfigManager.class);f.loot.tiers.gson=new com.google.gson.Gson();
        PriceTiers.Setup setup=PriceTiers.defaults();f.loot.tiers.save(f.loot.tiers.prepare(setup));return setup;
    }
    private static GroundLoot.Entry active(LootRuntimeTest.Fixture f){return f.loot.entries().stream().filter(e->e.beam!=null).findFirst().orElse(null);}
    @Test public void checkboxCreatesRealBeamWithoutRuleOrEffectsAndMasterCanStopIt()
    {
        LootRuntimeTest.Fixture f=new LootRuntimeTest.Fixture("");PriceTiers.Setup setup=prepare(f);setup.tiers[2].lootBeam=true;
        assertFalse(f.loot.config.priceTiersEnabled());f.items.add(f.item(1,1,200));f.tick(100);
        GroundLoot.Entry e=active(f);assertNotNull(e);assertEquals(new Color(setup.tiers[2].color),e.beamColor);assertEquals(f.view.getId(),e.beam.getWorldView());
        verify(f.loot.client).registerRuneLiteObject(e.beam);assertTrue(f.loot.explain(e.key).contains("Beam rule: false"));assertTrue(f.loot.explain(e.key).contains("Beam on this tile: true"));
        RuneLiteObject first=e.beam;setup.tiers[2].lootBeam=false;f.tick(101);assertNull(active(f));verify(f.loot.client).removeRuneLiteObject(first);
        setup.tiers[2].lootBeam=true;f.tick(102);assertNotNull(active(f));when(f.loot.config.lootBeams()).thenReturn(false);f.tick(103);assertNull(active(f));
        when(f.loot.config.lootBeams()).thenReturn(true);f.tick(104);assertNotNull(active(f));when(f.loot.config.lootOverlay()).thenReturn(false);f.tick(105);assertNull(active(f));
    }
    @Test public void hiddenItemsNeverGainTierBeamsAndExplicitBeamOnlyRulesStillWork()
    {
        for(String script:new String[]{"rule(true){hidden=true;showLootbeam=true;}","rule(true){hideOverlay=true;}"}){
            LootRuntimeTest.Fixture f=new LootRuntimeTest.Fixture(script);PriceTiers.Setup setup=prepare(f);setup.tiers[2].lootBeam=true;f.items.add(f.item(1,1,200));f.tick(100);assertNull(active(f));verify(f.loot.client,never()).registerRuneLiteObject(any());
        }
        com.improvedlooting.rs2f.DisplayConfig hidden=LootPreferences.apply(new com.improvedlooting.rs2f.DisplayConfig(Color.WHITE),LootPreferences.Action.HIDE);
        PriceTiers.Setup all=PriceTiers.defaults();for(PriceTiers.Tier tier:all.tiers)tier.lootBeam=true;assertNull(GroundLoot.beamColor(hidden,1000000,all));
        LootRuntimeTest.Fixture f=new LootRuntimeTest.Fixture("rule(true){hideOverlay=true;showLootbeam=true;lootbeamColor=\"#123456\";}");prepare(f);f.items.add(f.item(1,1,200));f.tick(100);assertEquals(new Color(0x123456),active(f).beamColor);
    }
    @Test public void groupedCardValueChoosesTierAndRemovingItemsReevaluatesIt()
    {
        LootRuntimeTest.Fixture f=new LootRuntimeTest.Fixture("");PriceTiers.Setup setup=prepare(f);setup.tiers[3].lootBeam=true;
        TileItem first=f.item(1,6,200),second=f.item(1,6,200);f.items.add(first);f.tick(100);assertNull(active(f));
        f.loot.onItemSpawned(new ItemSpawned(f.tile,second));f.tick(101);assertNotNull(active(f));assertEquals(12,active(f).renderQuantity);assertEquals(new Color(setup.tiers[3].color),active(f).beamColor);
        f.loot.onItemDespawned(new ItemDespawned(f.tile,second));f.tick(102);assertNull(active(f));
    }
    @Test public void oneBeamPerTileUsesHighestEligibleValueAndFallsBackToRule()
    {
        LootRuntimeTest.Fixture f=new LootRuntimeTest.Fixture("rule(id:1){showLootbeam=true;lootbeamColor=\"#12ab34\";}");PriceTiers.Setup setup=prepare(f);setup.tiers[4].lootBeam=true;
        TileItem valuable=f.item(2,100,200);f.items.add(f.item(1,1,200));f.items.add(valuable);f.tick(100);
        assertEquals(1,f.loot.entries().stream().filter(e->e.beam!=null).count());assertEquals(2,active(f).item.getId());assertEquals(new Color(setup.tiers[4].color),active(f).beamColor);
        f.loot.onItemDespawned(new ItemDespawned(f.tile,valuable));f.tick(101);assertEquals(1,active(f).item.getId());assertEquals(new Color(0x12ab34),active(f).beamColor);
        RuneLiteObject remaining=active(f).beam;f.loot.clear();verify(f.loot.client).removeRuneLiteObject(remaining);assertTrue(f.loot.entries().isEmpty());
    }
    @Test public void denseBeamsRemainCappedAndWorldUnloadCleansUp()
    {
        LootRuntimeTest.Fixture f=new LootRuntimeTest.Fixture("");PriceTiers.Setup setup=prepare(f);setup.tiers[2].lootBeam=true;f.tick(100);
        for(int i=0;i<65;i++){
            Tile tile=mock(Tile.class);LocalPoint point=new LocalPoint(128+(i%9)*128,128+(i/9)*128,f.view);when(tile.getLocalLocation()).thenReturn(point);TileItem item=f.item(i+1,1,200);
            when(f.loot.filters.facts(tile,item)).thenReturn(com.improvedlooting.rs2f.model.ItemFacts.builder().id(i+1).name("Item "+i).quantity(1).gePrice(10000).build());
            f.loot.onItemSpawned(new ItemSpawned(tile,item));
        }
        f.tick(101);assertEquals(64,f.loot.entries().stream().filter(e->e.beam!=null).count());verify(f.loot.client,times(64)).registerRuneLiteObject(any());
        f.loot.onWorldViewUnloaded(new WorldViewUnloaded(f.view));verify(f.loot.client,times(64)).removeRuneLiteObject(any());f.tick(102);assertTrue(f.loot.entries().isEmpty());
    }
}
