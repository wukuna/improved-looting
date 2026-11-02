package com.improvedlooting;

import com.improvedlooting.rs2f.model.ItemFacts;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.events.PluginMessage;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class VisibleLootPriorityTest {
    private static LootRuntimeTest.Fixture fixture(String rules) {
        LootRuntimeTest.Fixture f=new LootRuntimeTest.Fixture(rules);
        when(f.loot.client.getWorldView(0)).thenReturn(f.view);
        return f;
    }
    private static TileItem add(LootRuntimeTest.Fixture f,int id,String name,int quantity,long price,boolean tradeable) {
        TileItem item=f.item(id,quantity,200);
        when(item.getOwnership()).thenReturn(TileItem.OWNERSHIP_SELF);
        when(f.loot.filters.facts(f.tile,item)).thenAnswer(i->ItemFacts.builder().id(id).name(name)
            .quantity(item.getQuantity()).ownership(item.getOwnership()).gePrice(price).tradeable(tradeable).build());
        f.items.add(item);return item;
    }
    private static List<Integer> labels(LootRuntimeTest.Fixture f) {
        return f.loot.entries().stream().filter(e->!e.grouped).map(e->e.item.getId()).collect(Collectors.toList());
    }
    private static AtomicReference<MenuEntry[]> menu(LootRuntimeTest.Fixture f,int... ids) {
        Menu menu=mock(Menu.class);when(f.loot.client.getMenu()).thenReturn(menu);
        MenuEntry[] entries=Arrays.stream(ids).mapToObj(id->{
            MenuEntry e=mock(MenuEntry.class);when(e.getIdentifier()).thenReturn(id);when(e.getOption()).thenReturn("Take");
            when(e.getType()).thenReturn(MenuAction.GROUND_ITEM_THIRD_OPTION);return e;
        }).toArray(MenuEntry[]::new);
        AtomicReference<MenuEntry[]> current=new AtomicReference<>(entries);
        when(menu.getMenuEntries()).thenAnswer(i->current.get());
        doAnswer(i->{current.set(i.getArgument(0));return null;}).when(menu).setMenuEntries(any());
        return current;
    }
    @Test public void visibleAshesBeatRuleHiddenArrowsAndLowValueCoinsInEveryPresentation() {
        LootRuntimeTest.Fixture f=fixture("rule(id:1){hidden=true;} rule(true){}");
        when(f.loot.config.minimumLabelValue()).thenReturn(500);
        add(f,1,"Amethyst arrow",1,10000,true);add(f,2,"Coins",100,1,true);add(f,3,"Malicious ashes",1,1000,true);
        // Menus can be requested before the next game tick has indexed these drops.
        AtomicReference<MenuEntry[]> current=menu(f,3,2,1);
        f.loot.onPostMenuSort(new PostMenuSort());assertEquals(3,current.get()[2].getIdentifier());
        current=menu(f,3,2,1);MenuOpened opened=new MenuOpened();opened.setMenuEntries(current.get());
        f.loot.onMenuOpened(opened);assertEquals(3,current.get()[2].getIdentifier());assertArrayEquals(current.get(),opened.getMenuEntries());
        f.tick(100);assertEquals(Arrays.asList(3,1,2),labels(f));
        when(f.loot.config.shareMenuIcons()).thenReturn(true);when(f.loot.client.isClientThread()).thenReturn(true);
        MenuIconPublisher publisher=new MenuIconPublisher();publisher.client=f.loot.client;publisher.config=f.loot.config;
        publisher.loot=f.loot;publisher.icons=f.loot.icons;publisher.icons.filters=f.loot.filters;
        Map<Integer,Integer> ranks=new HashMap<>();
        for(int id:new int[]{1,2,3}) {
            Map<String,Object> data=new HashMap<>();data.put("worldViewId",0);data.put("x",0);data.put("y",0);data.put("z",0);data.put("itemId",id);
            publisher.onPluginMessage(new PluginMessage("improved-looting","resolved-display-request-v1",data));
            assertEquals("ok",data.get("status"));ranks.put(id,(Integer)((Map<?,?>)data.get("display")).get("menuSort"));
        }
        assertTrue(ranks.get(3)>ranks.get(1));assertTrue(ranks.get(3)>ranks.get(2));
        when(f.loot.config.prioritizeLoot()).thenReturn(false);current=menu(f,3,2,1);
        f.loot.onPostMenuSort(new PostMenuSort());assertEquals(1,current.get()[2].getIdentifier());
    }
    @Test public void altClickRefreshesCachedPriorityImmediatelyAndRevealKeepsHiddenLootBelow() {
        LootRuntimeTest.Fixture f=fixture("rule(id:1){hidden=true;} rule(true){}");
        f.loot.preferences=LootDisplayOptionsTest.preferences(new HashMap<>());
        add(f,1,"Amethyst arrow",1,10000,true);add(f,2,"Malicious ashes",1,1000,true);f.tick(100);
        assertTrue(f.loot.menuRank(f.tile,2,99)>f.loot.menuRank(f.tile,1,99));
        f.loot.preferences.labelHidden(1,false);f.loot.refreshOverrides();
        assertTrue(f.loot.menuRank(f.tile,1,99)>f.loot.menuRank(f.tile,2,99));assertEquals(Arrays.asList(1,2),labels(f));
        f.loot.preferences.labelHidden(1,true);f.loot.refreshOverrides();
        assertTrue(f.loot.menuRank(f.tile,2,99)>f.loot.menuRank(f.tile,1,99));assertEquals(Arrays.asList(2,1),labels(f));
        when(f.loot.client.isKeyPressed(KeyCode.KC_ALT)).thenReturn(true);
        assertTrue(f.loot.menuRank(f.tile,2,99)>f.loot.menuRank(f.tile,1,99));
    }
    @Test public void groupedValueControlsVisibilityAndHiddenCopiesDoNotInflateVisibleStacks() {
        LootRuntimeTest.Fixture f=fixture("rule(quantity:>1000){hidden=true;} rule(true){}");
        when(f.loot.config.minimumLabelValue()).thenReturn(600);
        add(f,1,"Coins",400,1,true);add(f,1,"Coins",400,1,true);add(f,1,"Coins",100000,1,true);
        add(f,2,"Malicious ashes",1,1000,true);add(f,3,"Rune platebody",1,10000,true);f.tick(100);
        assertTrue(f.loot.menuRank(f.tile,1,99)>f.loot.menuRank(f.tile,2,99));
        assertTrue(f.loot.menuRank(f.tile,3,99)>f.loot.menuRank(f.tile,1,99));
        assertEquals(800,f.loot.entries().stream().filter(e->e.item.getId()==1&&!e.grouped&&!f.loot.labelHidden(e)).findFirst().get().renderQuantity);
        when(f.loot.config.groupStacks()).thenReturn(false);f.loot.refreshSettings();f.tick(101);
        assertTrue(f.loot.menuRank(f.tile,2,99)>f.loot.menuRank(f.tile,1,99));
        assertEquals(Arrays.asList(3,2,1,1,1),labels(f));
    }
    @Test public void ownedVisibleUntradeablesBeatHiddenTradeablesAndOwnershipChangesRefresh() {
        LootRuntimeTest.Fixture f=fixture("rule(true){}");
        when(f.loot.config.onlyShowOwnedDrops()).thenReturn(true);when(f.loot.config.minimumLabelValue()).thenReturn(600);
        TileItem arrow=add(f,1,"Amethyst arrow",1,10000,true);when(arrow.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        add(f,2,"Clue scroll",1,0,false);f.tick(100);
        assertEquals(Arrays.asList(2,1),labels(f));assertTrue(f.loot.menuRank(f.tile,2,99)>f.loot.menuRank(f.tile,1,99));
        when(arrow.getOwnership()).thenReturn(TileItem.OWNERSHIP_SELF);f.tick(101);
        assertEquals(Arrays.asList(1,2),labels(f));assertTrue(f.loot.menuRank(f.tile,1,99)>f.loot.menuRank(f.tile,2,99));
    }
    @Test public void quantityEventsRefreshMinimumVisibilityBeforeNextTick() {
        LootRuntimeTest.Fixture f=fixture("rule(true){}");when(f.loot.config.minimumLabelValue()).thenReturn(600);
        TileItem coins=add(f,1,"Coins",400,1,true);add(f,2,"Malicious ashes",1,1000,true);f.tick(100);
        assertTrue(f.loot.menuRank(f.tile,2,99)>f.loot.menuRank(f.tile,1,99));
        when(coins.getQuantity()).thenReturn(800);f.loot.onItemQuantityChanged(new ItemQuantityChanged(coins,f.tile,400,800));
        assertTrue(f.loot.menuRank(f.tile,1,99)>f.loot.menuRank(f.tile,2,99));
    }
    @Test public void unresolvedFiltersLeaveMenuPrioritiesAlone() {
        LootRuntimeTest.Fixture f=fixture("rule(true){}");add(f,1,"Coins",100,1,true);add(f,2,"Malicious ashes",1,1000,true);
        when(f.loot.filters.snapshot()).thenReturn(null);AtomicReference<MenuEntry[]> current=menu(f,1,2);
        f.loot.onPostMenuSort(new PostMenuSort());assertEquals(2,current.get()[1].getIdentifier());
        assertEquals(123,f.loot.menuRank(f.tile,1,123));
    }
}
