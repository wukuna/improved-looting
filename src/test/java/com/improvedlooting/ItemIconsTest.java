package com.improvedlooting;
import com.google.gson.Gson;
import java.util.*;
import net.runelite.api.*;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ItemIconsTest
{
    @Test public void importsExactNamesAndSpritesPreservingFirstTerminalRule()
    {
        List<ItemIcons.Assignment> a=LootIconImport.parse("// comment\nmeta { name=\"demo\"; } rule (name: [\"Shark\",\"Lobster\"]) { icon=Sprite(107,0); menuTextColor=\"#ff0000\"; } rule (name:\"Shark\") { icon=Item(995); }");
        assertEquals(2,a.size()); assertEquals(ItemIcons.Kind.SPRITE,a.get(0).kind); assertEquals(107,a.get(0).iconId);
    }
    @Test public void importsItemIdsAndCurrentItem()
    { List<ItemIcons.Assignment> a=LootIconImport.parse("if (id:[385,379]) { icon=CurrentItem(); }"); assertEquals(2,a.size()); assertEquals(385,a.get(0).itemId); }
    @Test public void unsafeFiltersNeverPartiallyImport()
    {
        String[] bad={"rule (name:\"Shark\" && quantity:>1) {icon=Item(995);}","#define FOO 1\nrule (id:1) {icon=Item(2);}","rule (name:\"*Shark*\") {icon=Item(2);}","rule (id:1) {icon=File(\"heart.png\");}","apply (id:1) {icon=Item(2);}","rule (id:1) {icon=Item(2);} broken", "rule(id:1) {icon=Item(2);} rule(name:\"Shark\") {icon=Item(3);}","rule(id:1) {hidden=true;}"};
        for(String source:bad) { try { LootIconImport.parse(source); fail(source); } catch(IllegalArgumentException expected) {} }
    }
    @Test public void rejectsMalformedAndOversizeIconData()
    {
        ItemIcons store=new ItemIcons(); store.gson=new Gson();
        for(String json:new String[]{"null","[null]","[{\"name\":\"Shark\",\"kind\":\"UNKNOWN\"}]","[{\"name\":\"<img=1>\",\"kind\":\"HEART\"}]"})
        { try { store.parse(json); fail(json); } catch(IllegalArgumentException expected) {} }
    }
    @Test public void savingAndUndoOnlyWriteOurConfiguration()
    {
        ItemIcons store=new ItemIcons(); store.gson=new Gson(); store.configs=mock(ConfigManager.class);
        Map<String,String> data=new HashMap<>();
        when(store.configs.getConfiguration(eq(ImprovedLootingConfig.GROUP),anyString())).thenAnswer(i -> data.get(i.getArgument(1)));
        doAnswer(i -> { data.put(i.getArgument(1),String.valueOf((Object)i.getArgument(2))); return null; }).when(store.configs).setConfiguration(eq(ImprovedLootingConfig.GROUP),anyString(),any());
        ItemIcons.Assignment a=new ItemIcons.Assignment(); a.name="Shark"; a.kind=ItemIcons.Kind.HEART;
        store.save(Collections.singletonList(a)); assertEquals(1,store.snapshot().size()); store.undo(); assertTrue(store.snapshot().isEmpty()); store.undo(); assertEquals(1,store.snapshot().size());
        verify(store.configs,never()).setConfiguration(eq("lootfilters"),anyString(),any());
        data.put(ItemIcons.KEY,"[]"); assertTrue(store.snapshot().isEmpty());
    }
}
