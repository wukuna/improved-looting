package com.improvedlooting;

import com.improvedlooting.rs2f.DisplayConfig;
import com.improvedlooting.rs2f.model.ItemFacts;
import java.util.*;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.game.ItemManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class HighAlchemyValuesTest
{
    @Test public void selectedSourceDrivesPricesTiersVisibilityBrowserAndPileOrderWithoutRespawning()
    {
        LootRuntimeTest.Fixture f = new LootRuntimeTest.Fixture("rule(true){showValue=true;}");
        add(f, 1, 2, 1000000, 600);
        add(f, 2, 1, 100, 30000);
        f.tick(100);
        GroundLoot.Entry low = f.loot.entries().stream().filter(e -> e.item.getId() == 1).findFirst().get();
        assertEquals(2000000, LootDisplayOptions.price(f.loot.config, low));
        assertEquals(1, f.loot.rows().get(0).itemId);
        when(f.loot.config.priceSource()).thenReturn(ImprovedLootingConfig.PriceSource.HIGH_ALCHEMY);
        f.loot.refreshSettings(); f.tick(101);
        assertEquals(1200, LootDisplayOptions.price(f.loot.config, low));
        assertEquals(2, f.loot.rows().get(0).itemId);
        assertEquals(30000, f.loot.rows().get(0).value);
        assertEquals(1, PriceTiers.index(PriceTiers.defaults(), LootValues.value(f.loot.config, low.facts, low.renderQuantity)));
        assertTrue(f.loot.explain(low.key).contains("Stack High Alchemy value: 1200 gp"));
        when(f.loot.config.minimumLabelValue()).thenReturn(2000);
        assertTrue(f.loot.labelHidden(low));
        when(f.loot.config.priceSource()).thenReturn(ImprovedLootingConfig.PriceSource.GE);
        f.loot.refreshSettings(); f.tick(102);
        assertFalse(f.loot.labelHidden(low)); assertEquals(1, f.loot.rows().get(0).itemId);
        assertEquals(100, low.spawnTick);
        verifyNoInteractions(f.loot.alerts.notifier);
    }

    @Test public void groupedStacksUseAlchemyTotalsAndZeroNeverFallsBackToGe()
    {
        LootRuntimeTest.Fixture f = new LootRuntimeTest.Fixture("rule(true){showValue=true;}");
        when(f.loot.config.priceSource()).thenReturn(ImprovedLootingConfig.PriceSource.HIGH_ALCHEMY);
        add(f, 1, 2, 1000000, 600); add(f, 1, 3, 1000000, 600); add(f, 2, 1, 10, 2000);
        f.tick(100);
        GroundLoot.Entry top = f.loot.entries().stream().filter(e -> !e.grouped).findFirst().get();
        assertEquals(1, top.item.getId()); assertEquals(5, top.renderQuantity);
        assertEquals(3000, LootDisplayOptions.price(f.loot.config, top));
        ItemFacts zero = ItemFacts.builder().id(4).gePrice(1000000000).haPrice(0).build();
        assertEquals(0, LootValues.value(f.loot.config, zero, 20));
        assertEquals(Long.MAX_VALUE, LootValues.value(f.loot.config, ItemFacts.builder().id(4).haPrice(Integer.MAX_VALUE).build(), Long.MAX_VALUE));
        assertEquals(123, LootValues.value(f.loot.config, ItemFacts.builder().id(995).build(), 123));
        assertEquals(123000, LootValues.value(f.loot.config, ItemFacts.builder().id(13204).build(), 123));
    }

    @Test public void alchemyTierSelectsItsSound() throws Exception
    {
        SpecialLootAlertsTest.Fixture f = new SpecialLootAlertsTest.Fixture();
        when(f.alerts.config.priceSource()).thenReturn(ImprovedLootingConfig.PriceSource.HIGH_ALCHEMY);
        TileItem item = mock(TileItem.class); when(item.getId()).thenReturn(1); when(item.getOwnership()).thenReturn(TileItem.OWNERSHIP_SELF);
        GroundLoot.Entry e = new GroundLoot.Entry(mock(Tile.class), item);
        e.facts = ItemFacts.builder().id(1).name("Rune item").quantity(2).gePrice(10000000).haPrice(600).tradeable(true).build();
        e.display = DisplayConfig.builder().build(); f.alerts.drop(e, new FilterIcons.Prepared()); f.tick(101);
        assertEquals(1, f.jobs.size()); f.jobs.get(0).run();
        org.mockito.ArgumentCaptor<java.io.InputStream> in = org.mockito.ArgumentCaptor.forClass(java.io.InputStream.class);
        verify(f.alerts.audio).play(in.capture(), anyFloat()); assertEquals(2, in.getValue().read());
    }

    @Test public void priceSourceSharesWithSettingsAndRejectsUnknownModes()
    {
        LootProfiles p = LootProfilesTest.profiles();
        LootProfiles.Setup s = new LootProfiles.Setup(); s.settings.put("priceSource", "HIGH_ALCHEMY");
        assertEquals("HIGH_ALCHEMY", p.parse(p.encode(s)).settings.get("priceSource"));
        assertEquals(ImprovedLootingConfig.PriceSource.GE, new ImprovedLootingConfig() {}.priceSource());
        assertNotNull(p.parse("{\"version\":1,\"settings\":{}}"));
        s.settings.put("priceSource", "LOW_ALCHEMY");
        try { p.validate(s); fail(); } catch (IllegalArgumentException expected) { }
    }

    @Test public void notesUseTheUnderlyingItemsAlchemyValueAndKeepSeparateGeFacts()
    {
        FilterIcons filters = new FilterIcons(); filters.client = mock(Client.class); filters.items = mock(ItemManager.class);
        WorldView view = mock(WorldView.class); when(view.getScene()).thenReturn(mock(Scene.class));
        when(filters.client.getTopLevelWorldView()).thenReturn(view); when(filters.client.getWorldView(0)).thenReturn(view);
        Tile tile = mock(Tile.class); LocalPoint point = new LocalPoint(128,128,view); when(tile.getLocalLocation()).thenReturn(point);
        ItemComposition note = mock(ItemComposition.class), base = mock(ItemComposition.class);
        when(note.getName()).thenReturn("Noted item"); when(note.getNote()).thenReturn(799); when(note.getLinkedNoteId()).thenReturn(100);
        when(note.getHaPrice()).thenReturn(0); when(base.getHaPrice()).thenReturn(600);
        when(filters.items.getItemComposition(101)).thenReturn(note); when(filters.items.getItemComposition(100)).thenReturn(base);
        when(filters.items.getItemPrice(101)).thenReturn(1000000L);
        TileItem item = mock(TileItem.class); when(item.getId()).thenReturn(101); when(item.getQuantity()).thenReturn(5);
        ItemFacts facts = filters.facts(tile,item);
        assertEquals(600, facts.getHaPrice()); assertEquals(1000000, facts.getGePrice()); assertEquals(101, facts.getId());
    }

    private static void add(LootRuntimeTest.Fixture f, int id, int quantity, long ge, int ha)
    {
        TileItem item = f.item(id, quantity, 300);
        when(f.loot.filters.facts(f.tile,item)).thenReturn(ItemFacts.builder().id(id).name("Item " + id).quantity(quantity).gePrice(ge).haPrice(ha).tradeable(true).build());
        f.items.add(item);
    }
}
