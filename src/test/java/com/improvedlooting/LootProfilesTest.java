package com.improvedlooting;

import com.google.gson.Gson;
import com.improvedlooting.rs2f.model.ItemFacts;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class LootProfilesTest
{
    static LootProfiles profiles()
    {
        LootProfiles p=new LootProfiles();p.icons=new ItemIcons();p.icons.gson=new Gson();p.icons.configs=mock(ConfigManager.class);p.icons.filters=new FilterIcons();p.icons.filters.gson=p.icons.gson;p.icons.filters.configs=p.icons.configs;p.icons.filters.executor=mock(ScheduledExecutorService.class);p.icons.tiers=new PriceTiers();p.icons.tiers.gson=p.icons.gson;p.preferences=new LootPreferences();p.preferences.gson=p.icons.gson;p.preferences.configs=p.icons.configs;return p;
    }
    @Test public void backupRoundTripChecksAllAssetsBeforeAnyWrite()
    {
        LootProfiles p=profiles();LootProfiles.Setup s=p.parse(p.starter(false));assertTrue(s.settings.get(FilterIcons.KEY).contains("compressedSource"));String before=p.export();p.applyValidated(s,null,before);verify(p.icons.configs).setConfiguration(ImprovedLootingConfig.GROUP,LootProfiles.UNDO,before);
        LootProfiles p2=profiles();s.settings.put("anotherPluginsSetting","true");try{p2.parse(p2.icons.gson.toJson(s));fail();}catch(IllegalArgumentException expected){}verifyNoInteractions(p2.icons.configs);
    }
    @Test public void staleSettingsAndInvalidAssetCannotOverwriteCurrentSetup()
    {
        LootProfiles p=profiles();LootProfiles.Setup s=p.parse(p.starter(true));try{p.applyValidated(s,null,"stale");fail();}catch(IllegalArgumentException expected){}verify(p.icons.configs,never()).setConfiguration(anyString(),anyString(),any());
        s.settings.put(FilterIcons.KEY,"{\"version\":1,\"compressedSource\":\"broken\"}");try{p.parse(p.icons.gson.toJson(s));fail();}catch(IllegalArgumentException expected){}verify(p.icons.configs,never()).setConfiguration(anyString(),anyString(),any());
    }
    @Test public void overridesPrioritizeItemOverSourceAndKeepUnknownSourceUnchanged()
    {
        LootProfiles p=profiles();p.preferences.source("Abyssal Sire",LootPreferences.Action.HIDE);org.mockito.ArgumentCaptor<String> first=org.mockito.ArgumentCaptor.forClass(String.class);verify(p.icons.configs).setConfiguration(eq(ImprovedLootingConfig.GROUP),eq(LootPreferences.KEY),first.capture());String saved=first.getValue();
        when(p.icons.configs.getConfiguration(ImprovedLootingConfig.GROUP,LootPreferences.KEY)).thenReturn(saved);
        assertEquals(LootPreferences.Action.HIDE,p.preferences.action(1,"Abyssal Sire"));assertEquals(LootPreferences.Action.FILTER,p.preferences.action(1,null));
        p.preferences.set(1,LootPreferences.Action.SHOW,"Boss supplies");
        // ConfigManager mock does not retain writes; read the validated payload actually saved.
        org.mockito.ArgumentCaptor<String> raw=org.mockito.ArgumentCaptor.forClass(String.class);verify(p.icons.configs,times(2)).setConfiguration(eq(ImprovedLootingConfig.GROUP),eq(LootPreferences.KEY),raw.capture());when(p.icons.configs.getConfiguration(ImprovedLootingConfig.GROUP,LootPreferences.KEY)).thenReturn(raw.getValue());assertEquals(LootPreferences.Action.SHOW,p.preferences.action(1,"Abyssal Sire"));assertEquals("Boss supplies",p.preferences.category(1));
    }
    @Test public void blankConfigurationLoadsDefaultAndUndoRestoresDefault()
    {
        LootProfiles p=profiles();p.icons.filters.reload();assertNotNull(p.icons.filters.snapshot());assertFalse(p.icons.filters.snapshot().program.evaluate(ItemFacts.builder().name("Coins").id(995).quantity(1).build()).isHidden());p.icons.filters.save(null);assertNotNull(p.icons.filters.snapshot());
    }
    @Test public void inspectorShowsRulePriorityOverrideAndUnknownFacts()
    {
        LootProfiles p=profiles();FilterIcons.Bundle b=new FilterIcons.Bundle();b.compressedSource=FilterIcons.compress("apply(true){showValue=true;}\nrule(id:1){hidden=true;}\nrule(true){notify=true;}");FilterIcons.Prepared f=p.icons.filters.prepare(b);ItemFacts item=ItemFacts.builder().id(1).name("Test").quantity(1).build();String explanation=LootResolution.explain(f,item,LootPreferences.apply(f.program.evaluate(item),LootPreferences.Action.SHOW),LootPreferences.Action.SHOW,null,-1);assertTrue(explanation.contains("source line 2"));assertTrue(explanation.contains("Label: visible"));assertTrue(explanation.contains("Source: Unavailable"));assertFalse(explanation.contains("#3 matched"));
    }
    @Test public void migrationReadsOnlyNamedListsAndMakesAReviewableCopy()
    {
        LootProfiles p=profiles();when(p.icons.configs.getConfiguration("grounditems","highlightedItems")).thenReturn("Rune*,Shark");when(p.icons.configs.getConfiguration("grounditems","hiddenItems")).thenReturn("Bones");LootProfiles.Setup s=p.parse(p.groundItemsPreview());assertNotNull(s.settings.get(FilterIcons.KEY));verify(p.icons.configs,never()).setConfiguration(anyString(),anyString(),any());
    }
    @Test public void missingSoundAndOversizedImagesRejectBeforeSave()throws Exception
    {
        LootProfiles p=profiles();FilterIcons.Bundle b=new FilterIcons.Bundle();b.compressedSource=FilterIcons.compress("rule(true){sound=\"missing.wav\";}");try{p.icons.filters.prepare(b);fail();}catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("Missing sound"));}
        b.compressedSource=FilterIcons.compress("rule(true){}");String large=IconImages.encode(new java.awt.image.BufferedImage(2048,2048,2));for(int i=0;i<4;i++)b.images.put(i+".png",large);try{p.icons.filters.prepare(b);fail();}catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("combined"));}
    }
    @Test public void wikiLinkHasFixedOriginAndEscapesNames(){assertEquals("https://oldschool.runescape.wiki/w/Abyssal_Sire#Drops",LootBrowser.wikiUrl("Abyssal Sire"));assertTrue(LootBrowser.wikiUrl("//evil.example/?").startsWith("https://oldschool.runescape.wiki/w/%2F%2F"));}
    @Test public void cardOptionsValidateAndOlderBackupsRemainReadable()
    {
        LootProfiles p=profiles();LootProfiles.Setup s=p.parse("{\"version\":1,\"settings\":{\"lootCompact\":\"true\"}}");
        s.settings.put("groundStyle","CARDS");s.settings.put("iconDespawnFade","true");s.settings.put("alwaysShowTimer","false");s.settings.put("cardOpacity","55");s.settings.put("cardRows","4");s.settings.put("cardWidth","260");p.validate(s);
        s.settings.put("cardAutoWidth","false");s.settings.put("cardHeight","44");s.settings.put("cardWidth","1600");p.validate(s);
        s.settings.put("cardRows","2000");try{p.validate(s);fail();}catch(IllegalArgumentException expected){}verifyNoInteractions(p.icons.configs);
    }
}
