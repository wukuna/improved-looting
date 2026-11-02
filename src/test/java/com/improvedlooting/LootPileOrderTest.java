package com.improvedlooting;

import com.improvedlooting.rs2f.model.ItemFacts;
import java.util.*;
import java.util.stream.Collectors;
import net.runelite.api.*;
import net.runelite.api.events.MenuOpened;
import net.runelite.client.events.PluginMessage;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class LootPileOrderTest {
    private static void add(LootRuntimeTest.Fixture f, int id, String name, int quantity, long price, boolean tradeable) {
        TileItem item = f.item(id, quantity, 200);
        when(item.getOwnership()).thenReturn(TileItem.OWNERSHIP_SELF);
        when(f.loot.filters.facts(f.tile,item)).thenReturn(ItemFacts.builder().id(id).name(name).quantity(quantity)
            .gePrice(price).tradeable(tradeable).build());
        f.items.add(item);
    }
    private static LootRuntimeTest.Fixture fixture() {
        LootRuntimeTest.Fixture f = new LootRuntimeTest.Fixture("rule(true){}");
        when(f.loot.client.getWorldView(0)).thenReturn(f.view);
        add(f,1,"Rune platebody",1,10000,true);
        add(f,2,"Coins",20000,1,true);
        add(f,3,"Dragon bones",1,50000,true);
        add(f,4,"Abyssal ashes",1,1600,true);
        add(f,4,"Abyssal ashes",1,1600,true);
        add(f,5,"Clue scroll (hard)",1,0,false);
        add(f,6,"Bone dagger",1,200,true);
        return f;
    }
    @Test public void actualGroupedLabelsUseCategoryThenTotalValueAndCanOptOut() {
        LootRuntimeTest.Fixture f=fixture(); f.tick(100);
        assertEquals(Arrays.asList(2,1,6,3,4,5),f.loot.entries().stream().filter(e->!e.grouped).map(e->e.item.getId()).collect(Collectors.toList()));
        assertEquals(2,f.loot.entries().stream().filter(e->!e.grouped&&e.item.getId()==4).findFirst().get().renderQuantity);
        when(f.loot.config.prioritizeLoot()).thenReturn(false); f.tick(101);
        assertEquals(3,f.loot.entries().iterator().next().item.getId());
        assertTrue(LootProfiles.keys().contains("prioritizeLoot"));
    }
    @Test public void openedMenuMatchesLabelsAndLeavesOtherRowsAndActionObjectsIntact() {
        LootRuntimeTest.Fixture f=fixture(); Menu menu=mock(Menu.class); when(f.loot.client.getMenu()).thenReturn(menu);
        MenuEntry cancel=mock(MenuEntry.class),examine=mock(MenuEntry.class),callback=take(3),unknown=take(999),otherTile=take(3);
        when(callback.onClick()).thenReturn(e->{}); when(otherTile.getParam0()).thenReturn(1);
        MenuEntry[] entries={cancel,take(1),examine,take(2),callback,take(3),take(4),unknown,take(5),take(6),otherTile};
        when(menu.getMenuEntries()).thenReturn(entries);
        MenuOpened event=new MenuOpened(); event.setMenuEntries(entries);
        f.loot.onMenuOpened(event);
        ArgumentCaptor<MenuEntry[]> cap=ArgumentCaptor.forClass(MenuEntry[].class); verify(menu).setMenuEntries(cap.capture());
        MenuEntry[] sorted=cap.getValue();
        assertSame(cancel,sorted[0]);assertSame(examine,sorted[2]);assertSame(callback,sorted[4]);assertSame(unknown,sorted[7]);assertSame(otherTile,sorted[10]);
        assertEquals(Arrays.asList(2,1,6,3,4,5),Arrays.asList(sorted[9],sorted[8],sorted[6],sorted[5],sorted[3],sorted[1]).stream().map(MenuEntry::getIdentifier).collect(Collectors.toList()));
        assertArrayEquals(sorted,event.getMenuEntries());
        for(MenuEntry e:entries) { verify(e,never()).setType(any());verify(e,never()).setIdentifier(anyInt());verify(e,never()).setTarget(anyString()); }
        clearInvocations(menu);when(f.loot.config.prioritizeLoot()).thenReturn(false);f.loot.onMenuOpened(event);verify(menu,never()).setMenuEntries(any());
    }
    @Test public void rightClicksProviderUsesSameRanksEvenWhenRulesRequestAnotherOrder() {
        LootRuntimeTest.Fixture f=fixture();when(f.loot.config.shareMenuIcons()).thenReturn(true);when(f.loot.client.isClientThread()).thenReturn(true);
        MenuIconPublisher publisher=new MenuIconPublisher();publisher.client=f.loot.client;publisher.config=f.loot.config;publisher.loot=f.loot;publisher.icons=f.loot.icons;publisher.icons.filters=f.loot.filters;
        Map<Integer,Integer> ranks=new HashMap<>();
        for(int id:new int[]{1,2,3,4,5,6})ranks.put(id,f.loot.menuRank(f.tile,id,99));
        for(int id:new int[]{1,2,3,4,5,6}) {
            Map<String,Object> data=new HashMap<>();data.put("worldViewId",0);data.put("x",0);data.put("y",0);data.put("z",0);data.put("itemId",id);
            publisher.onPluginMessage(new PluginMessage("improved-looting","resolved-display-request-v1",data));
            assertEquals("ok",data.get("status"));assertEquals(ranks.get(id),((Map<?,?>)data.get("display")).get("menuSort"));
        }
    }
    @Test public void ownershipAndGroupingDoNotInflateValueRank() {
        LootRuntimeTest.Fixture f=new LootRuntimeTest.Fixture("rule(true){}");
        add(f,1,"Coins",5,1,true); add(f,1,"Coins",100000,1,true); add(f,2,"Rune arrow",1,100,true);
        when(f.items.get(1).getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);when(f.loot.config.onlyShowOwnedDrops()).thenReturn(true);
        assertTrue(f.loot.menuRank(f.tile,2,99)>f.loot.menuRank(f.tile,1,99));
        when(f.loot.config.onlyShowOwnedDrops()).thenReturn(false);
        f.loot.refreshSettings();
        assertTrue(f.loot.menuRank(f.tile,1,99)>f.loot.menuRank(f.tile,2,99));
    }
    @Test public void untradeablesStayLastRegardlessOfValueAndBoneWeaponsAreNormalLoot() {
        ItemFacts clue=ItemFacts.builder().name("Clue scroll").gePrice(Long.MAX_VALUE).quantity(5).build();
        ItemFacts bones=ItemFacts.builder().name("Dragon bones").tradeable(true).gePrice(1).quantity(1).build();
        assertTrue(LootPileOrder.compare(clue,5,bones,1)>0);
        assertEquals(0,LootPileOrder.category(ItemFacts.builder().name("Bone dagger").tradeable(true).build()));
        assertEquals(1,LootPileOrder.category(ItemFacts.builder().name("Infernal ashes").tradeable(true).build()));
    }
    private static MenuEntry take(int id) {
        MenuEntry entry=mock(MenuEntry.class);when(entry.getIdentifier()).thenReturn(id);when(entry.getOption()).thenReturn("Take");when(entry.getType()).thenReturn(MenuAction.GROUND_ITEM_THIRD_OPTION);return entry;
    }
}
