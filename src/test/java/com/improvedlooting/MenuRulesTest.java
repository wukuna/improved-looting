package com.improvedlooting;

import com.improvedlooting.rs2f.DisplayConfig;
import com.improvedlooting.rs2f.LootFilter;
import com.improvedlooting.rs2f.model.ItemFacts;
import java.awt.Color;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import javax.swing.*;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MenuRulesTest
{
    private static <T> T field(Object object,String name,Class<T> type) throws Exception {
        Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return type.cast(f.get(object));
    }
    private static ItemFacts facts(int quantity,long price,int owner,WorldPoint point) {
        return ItemFacts.builder().id(385).name("Shark").quantity(quantity).gePrice(price).ownership(owner).worldPoint(point).build();
    }
    @Test public void visualMenuOnlyRuleCombinesConditionsWithoutChangingAppearance() throws Exception {
        SwingUtilities.invokeAndWait(()->{try {
            RuleOptions options=new RuleOptions(new ItemIcons(),new HashMap<>());options.menuOnly();
            field(options,"minimum",JSpinner.class).setValue(100);field(options,"maximum",JSpinner.class).setValue(200);
            field(options,"ownership",JComboBox.class).setSelectedIndex(3);
            field(options,"inRegion",JCheckBox.class).doClick();field(options,"region",JSpinner.class).setValue(12850);
            String rule=options.apply(FilterEditor.rule("Shark",0,0));
            assertTrue(rule.startsWith("apply("));assertFalse(rule.contains("icon="));assertFalse(rule.contains("showValue="));
            String appearance="rule(true){hideOverlay=true;textColor=\"#12ab34\";icon=CurrentItem();}";
            LootFilter filter=LootFilter.compile(rule+appearance);WorldPoint inside=new WorldPoint(3200,3200,0);
            for(int owner:new int[]{1,3})for(int quantity:new int[]{2,4}) {
                DisplayConfig result=filter.evaluate(facts(quantity,50,owner,inside));
                assertEquals(Boolean.TRUE,result.getHideMenu());assertTrue(result.isHideOverlay());assertEquals(new Color(0x12ab34),result.getTextColor());
                assertNotNull(result.getIcon());assertFalse(result.isNotify());
            }
            assertNull(filter.evaluate(facts(1,50,1,inside)).getHideMenu());
            assertNull(filter.evaluate(facts(5,50,1,inside)).getHideMenu());
            assertNull(filter.evaluate(facts(2,50,2,inside)).getHideMenu());
            assertNull(filter.evaluate(facts(2,50,1,new WorldPoint(3264,3200,0))).getHideMenu());
            assertNull(filter.evaluate(facts(2,50,1,null)).getHideMenu());
            field(options,"maximum",JSpinner.class).setValue(99);
            try{options.apply(FilterEditor.rule("Shark",0,0));fail();}catch(IllegalArgumentException expected){}
            field(options,"maximum",JSpinner.class).setValue(0);
            field(options,"menuChoice",JComboBox.class).setSelectedItem(LootMenuChoice.DEFAULT);
            try{options.apply(FilterEditor.rule("Shark",0,0));fail();}catch(IllegalArgumentException expected){}
            options.close();
        }catch(Exception ex){throw new AssertionError(ex);}});
    }
    @Test public void firstExplicitMenuChoiceWinsAndLegacyRulesStayUnset() {
        ItemFacts item=facts(1,10,1,null);
        assertEquals(Boolean.FALSE,LootFilter.compile("apply(true){hideMenu=false;}apply(true){hideMenu=true;}rule(true){hideOverlay=true;}").evaluate(item).getHideMenu());
        assertEquals(Boolean.TRUE,LootFilter.compile("apply(true){hideMenu=true;}apply(true){hideMenu=false;}rule(true){hideOverlay=true;}").evaluate(item).getHideMenu());
        assertNull(LootFilter.compile("rule(true){hideOverlay=true;}").evaluate(item).getHideMenu());
    }
    @Test public void exactItemMenuChoicesSurviveVisualEditsReloadAndFullBackups() {
        Map<String,String> saved=new HashMap<>();LootPreferences prefs=LootDisplayOptionsTest.preferences(saved);
        prefs.hideMenu(385,false);prefs.set(385,LootPreferences.Action.HIGHLIGHT,"Keep");prefs.labelHidden(385,true);
        LootPreferences loaded=LootDisplayOptionsTest.preferences(saved);assertEquals(Boolean.FALSE,loaded.hideMenu(385));
        loaded.set(385,LootPreferences.Action.FILTER,"");loaded.labelHidden(385,null);assertEquals(Boolean.FALSE,loaded.hideMenu(385));
        LootProfiles profiles=LootProfilesTest.profiles();
        when(profiles.icons.configs.getConfiguration(ImprovedLootingConfig.GROUP,LootPreferences.KEY)).thenAnswer(c->saved.get(LootPreferences.KEY));
        LootProfiles.Setup backup=profiles.parse(profiles.export());
        assertEquals(Boolean.FALSE,profiles.preferences.parse(backup.settings.get(LootPreferences.KEY)).items.get(385).hideMenu);
        profiles.applyValidated(backup,null,profiles.export());
        verify(profiles.icons.configs).setConfiguration(ImprovedLootingConfig.GROUP,LootPreferences.KEY,backup.settings.get(LootPreferences.KEY));
        loaded.hideMenu(385,null);assertFalse(loaded.snapshot().items.containsKey(385));
        assertNull(loaded.parse("{\"version\":1,\"items\":{\"385\":{\"action\":\"HIDE\",\"category\":\"Food\",\"labelHidden\":true}},\"sources\":{}}").items.get(385).hideMenu);
        loaded.hideMenu(1,true);assertNull(loaded.hideMenu(2));
    }
}
