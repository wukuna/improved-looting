package com.improvedlooting;

import com.improvedlooting.rs2f.model.ItemFacts;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.*;
import net.runelite.api.Menu;
import net.runelite.api.events.*;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemMapping;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class LootBatchTest {
    @Test public void itemMenuChoiceWinsOverRuleAndGlobalWithoutHidingVisibleTags() {
        LootRuntimeTest.Fixture f=new LootRuntimeTest.Fixture("apply(id:1){hideMenu=true;}\nrule(true){hideOverlay=true;}");
        f.loot.preferences=LootDisplayOptionsTest.preferences(new HashMap<>());
        f.items.add(f.item(1,1,200));f.items.add(f.item(2,1,200));f.tick(100);
        when(f.loot.config.prioritizeLoot()).thenReturn(false);
        MenuEntry one=entry(1,MenuAction.GROUND_ITEM_THIRD_OPTION),two=entry(2,MenuAction.GROUND_ITEM_THIRD_OPTION);
        MenuEntry[] original={one,two};AtomicReference<MenuEntry[]> current=menu(f,original);
        f.loot.onPostMenuSort(new PostMenuSort());assertArrayEquals(new MenuEntry[]{two},current.get());
        f.loot.preferences.hideMenu(1,false);f.loot.preferences.hideMenu(2,true);f.loot.refreshOverrides();
        current.set(original);f.loot.onPostMenuSort(new PostMenuSort());assertArrayEquals(new MenuEntry[]{one},current.get());
        when(f.loot.config.hideHiddenMenuItems()).thenReturn(true);current.set(original);
        f.loot.onPostMenuSort(new PostMenuSort());assertArrayEquals(new MenuEntry[]{one},current.get());
        f.loot.preferences.labelHidden(2,false);f.loot.refreshOverrides();current.set(original);
        f.loot.onPostMenuSort(new PostMenuSort());assertArrayEquals(original,current.get());
        f.loot.preferences.hideMenu(1,null);f.loot.refreshOverrides();current.set(original);
        f.loot.onPostMenuSort(new PostMenuSort());assertArrayEquals(new MenuEntry[]{two},current.get());
        when(f.loot.client.isKeyPressed(KeyCode.KC_ALT)).thenReturn(true);current.set(original);
        f.loot.onPostMenuSort(new PostMenuSort());assertArrayEquals(original,current.get());
    }
    @Test public void conditionalMenusFailVisibleForMixedUnresolvedAndTargetedEntries() {
        LootRuntimeTest.Fixture f=new LootRuntimeTest.Fixture("apply(quantity:>=2){hideMenu=true;}\nrule(true){hideOverlay=true;}");
        f.items.add(f.item(1,1,200));f.items.add(f.item(1,2,200));f.items.add(f.item(2,2,200));f.tick(100);
        when(f.loot.config.prioritizeLoot()).thenReturn(false);
        MenuEntry mixed=entry(1,MenuAction.GROUND_ITEM_THIRD_OPTION),hidden=entry(2,MenuAction.GROUND_ITEM_THIRD_OPTION);
        MenuEntry custom=entry(2,MenuAction.EXAMINE_ITEM_GROUND);when(custom.onClick()).thenReturn(e->{});
        MenuEntry submenu=entry(2,MenuAction.GROUND_ITEM_THIRD_OPTION);when(submenu.getSubMenu()).thenReturn(mock(Menu.class));
        MenuEntry forced=entry(2,MenuAction.GROUND_ITEM_THIRD_OPTION);when(forced.isForceLeftClick()).thenReturn(true);
        MenuEntry[] original={mixed,hidden,custom,submenu,forced};AtomicReference<MenuEntry[]> current=menu(f,original);
        f.loot.onPostMenuSort(new PostMenuSort());assertArrayEquals(new MenuEntry[]{mixed,custom,submenu,forced},current.get());
        for(MenuAction target:new MenuAction[]{MenuAction.WIDGET_TARGET_ON_GROUND_ITEM,MenuAction.ITEM_USE_ON_GROUND_ITEM}) {
            MenuEntry targeting=entry(2,target);MenuEntry[] targeted={hidden,targeting};current.set(targeted);
            f.loot.onPostMenuSort(new PostMenuSort());assertArrayEquals(targeted,current.get());
        }
        f.items.add(f.item(2,1,200));current.set(original);f.loot.onPostMenuSort(new PostMenuSort());assertArrayEquals(original,current.get());
    }
    @Test public void ruleKeepOverridesGlobalAndMissingMenuFieldInheritsLegacyDefault() {
        LootRuntimeTest.Fixture f=new LootRuntimeTest.Fixture("apply(id:1){hideMenu=false;}\nrule(true){hideOverlay=true;}");
        when(f.loot.config.hideHiddenMenuItems()).thenReturn(true);when(f.loot.config.prioritizeLoot()).thenReturn(false);
        f.items.add(f.item(1,1,200));f.items.add(f.item(2,1,200));f.tick(100);
        MenuEntry keep=entry(1,MenuAction.GROUND_ITEM_THIRD_OPTION),hide=entry(2,MenuAction.GROUND_ITEM_THIRD_OPTION);
        AtomicReference<MenuEntry[]> current=menu(f,keep,hide);MenuOpened opened=new MenuOpened();opened.setMenuEntries(current.get());
        f.loot.onMenuOpened(opened);assertArrayEquals(new MenuEntry[]{keep},current.get());
    }
    private static MenuEntry entry(int id,MenuAction action) {
        MenuEntry e=mock(MenuEntry.class);when(e.getType()).thenReturn(action);when(e.getIdentifier()).thenReturn(id);when(e.getOption()).thenReturn("Take");return e;
    }
    private static AtomicReference<MenuEntry[]> menu(LootRuntimeTest.Fixture f,MenuEntry... entries) {
        Menu menu=mock(Menu.class);when(f.loot.client.getMenu()).thenReturn(menu);when(f.loot.client.getWorldView(0)).thenReturn(f.view);
        AtomicReference<MenuEntry[]> current=new AtomicReference<>(entries);when(menu.getMenuEntries()).thenAnswer(i->current.get());
        doAnswer(i->{current.set(i.getArgument(0));return null;}).when(menu).setMenuEntries(any());return current;
    }
    @Test public void leftClickMatchesOpenedOrderWithoutChangingActionsOrTargeting() {
        LootRuntimeTest.Fixture f=new LootRuntimeTest.Fixture("rule(true){}");
        f.items.add(f.item(1,10,200));f.items.add(f.item(2,1,200));f.tick(100);
        MenuEntry rich=entry(1,MenuAction.GROUND_ITEM_THIRD_OPTION),cheap=entry(2,MenuAction.GROUND_ITEM_THIRD_OPTION);
        AtomicReference<MenuEntry[]> current=menu(f,rich,cheap);f.loot.onPostMenuSort(new PostMenuSort());
        assertArrayEquals(new MenuEntry[]{cheap,rich},current.get());
        MenuOpened opened=new MenuOpened();opened.setMenuEntries(current.get());f.loot.onMenuOpened(opened);assertSame(rich,current.get()[1]);
        MenuEntry cast=entry(1,MenuAction.WIDGET_TARGET_ON_GROUND_ITEM);current.set(new MenuEntry[]{rich,cheap,cast});
        f.loot.onPostMenuSort(new PostMenuSort());assertArrayEquals(new MenuEntry[]{rich,cheap,cast},current.get());
        verify(rich,never()).setIdentifier(anyInt());verify(cheap,never()).setType(any());
    }
    @Test public void hiddenMenuFilterIsOptionalRevealedByAltAndKeepsVisibleDuplicateCopies() {
        LootRuntimeTest.Fixture f=new LootRuntimeTest.Fixture("rule(quantity:>2){hideOverlay=true;}");
        f.items.add(f.item(1,3,200));f.items.add(f.item(2,3,200));f.items.add(f.item(2,1,200));f.tick(100);
        MenuEntry hidden=entry(1,MenuAction.GROUND_ITEM_THIRD_OPTION),examine=entry(1,MenuAction.EXAMINE_ITEM_GROUND),visible=entry(2,MenuAction.GROUND_ITEM_THIRD_OPTION);
        MenuEntry cancel=entry(0,MenuAction.CANCEL),callback=entry(1,MenuAction.GROUND_ITEM_THIRD_OPTION);when(callback.onClick()).thenReturn(e->{});
        MenuEntry[] original={cancel,examine,hidden,visible,callback};AtomicReference<MenuEntry[]> current=menu(f,original);
        when(f.loot.config.prioritizeLoot()).thenReturn(false);f.loot.onPostMenuSort(new PostMenuSort());assertArrayEquals(original,current.get());
        when(f.loot.config.hideHiddenMenuItems()).thenReturn(true);f.loot.onPostMenuSort(new PostMenuSort());assertArrayEquals(new MenuEntry[]{cancel,visible,callback},current.get());
        current.set(original);when(f.loot.client.isKeyPressed(KeyCode.KC_ALT)).thenReturn(true);f.loot.onPostMenuSort(new PostMenuSort());assertArrayEquals(original,current.get());
        assertTrue(LootProfiles.keys().containsAll(Arrays.asList("railDespawnTimer","cardIndividualWidth","hideHiddenMenuItems")));
    }
    @Test public void allFreshEnsouledHeadMappingsUseMarketCategoryWithoutRewritingIdentity() {
        FilterIcons filters=new FilterIcons();filters.client=mock(Client.class);filters.items=mock(ItemManager.class);
        WorldView view=mock(WorldView.class);Scene scene=mock(Scene.class);when(view.getScene()).thenReturn(scene);when(filters.client.getTopLevelWorldView()).thenReturn(view);
        when(filters.client.getWorldView(0)).thenReturn(view);
        Tile tile=mock(Tile.class);net.runelite.api.coords.LocalPoint location=new net.runelite.api.coords.LocalPoint(128,128,view);when(tile.getLocalLocation()).thenReturn(location);
        // Verify the actual supported initial IDs, including an expensive head.
        int[] ids={ItemID.ARCEUUS_CORPSE_ABYSSAL_INITIAL,ItemID.ARCEUUS_CORPSE_DRAGON_INITIAL,ItemID.ARCEUUS_CORPSE_GOBLIN_INITIAL};
        for(int id:ids) {
            ItemComposition raw=mock(ItemComposition.class),market=mock(ItemComposition.class);TileItem item=mock(TileItem.class);
            when(item.getId()).thenReturn(id);when(item.getQuantity()).thenReturn(1);when(filters.items.getItemComposition(id)).thenReturn(raw);
            when(raw.getName()).thenReturn("Ensouled head");when(raw.getLinkedNoteId()).thenReturn(-1);when(raw.getNote()).thenReturn(-1);
            int marketId=ItemMapping.map(id).iterator().next().getTradeableItem();when(filters.items.getItemComposition(marketId)).thenReturn(market);when(market.isTradeable()).thenReturn(true);when(market.isGeTradeable()).thenReturn(true);
            ItemFacts fact=filters.facts(tile,item);assertEquals(id,fact.getId());assertTrue(fact.isTradeable());assertTrue(fact.isGeTradeable());assertEquals(0,LootPileOrder.category(fact));
        }
    }
    @Test public void railTimerDrainsTopWithoutFadingTextForBothThemes() {
        for(ImprovedLootingConfig.CardTheme theme:ImprovedLootingConfig.CardTheme.values()) {
            BufferedImage image=new BufferedImage(220,32,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();g.setFont(new Font(Font.SANS_SERIF,0,12));
            LootCardPainter.row(g,new Rectangle(0,0,220,32),Color.RED,null,"Item",1,0,false,false,false,90,false,-1,theme,null,.5);g.dispose();
            assertEquals(0,image.getRGB(2,8)>>>24);assertTrue((image.getRGB(2,24)>>>24)>0);
            boolean text=false;for(int y=0;y<32;y++)for(int x=38;x<100;x++)if((image.getRGB(x,y)>>>24)>0)text=true;assertTrue(text);
        }
        Rectangle pile=new Rectangle(10,20,300,100);
        assertEquals(new Rectangle(110,40,100,20),GroundLootCards.rowBounds(pile,20,100,20,ImprovedLootingConfig.LabelAlignment.CENTER));
        assertEquals(10,GroundLootCards.rowBounds(pile,20,100,20,ImprovedLootingConfig.LabelAlignment.LEFT).x);
        assertEquals(210,GroundLootCards.rowBounds(pile,20,100,20,ImprovedLootingConfig.LabelAlignment.RIGHT).x);
    }
}
