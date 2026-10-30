package com.improvedlooting;

import com.improvedlooting.rs2f.LootFilter;
import com.improvedlooting.rs2f.model.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class FilterEditorTest
{
    private final ItemFacts shark = ItemFacts.builder().id(385).name("Shark").quantity(1).build();
    @Test public void generatedRulesRespectPriorityAndVisibility()
    {
        String previous = "rule(true) {icon=Item(995);}";
        String hidden = FilterEditor.rule("Shark", 2, 995);
        assertNull(LootFilter.compile(hidden + previous).find(shark));
        assertEquals(new BufferedImageProvider.Item(995), LootFilter.compile(previous + hidden).find(shark));
        assertEquals(new BufferedImageProvider.CurrentItem(), LootFilter.compile("apply(true) {hidden=true;}" + FilterEditor.rule("*hark", 0, 995)).find(shark));
        assertEquals(new BufferedImageProvider.Item(4151), LootFilter.compile(FilterEditor.rule("Shark", 1, 4151)).find(shark));
    }
    @Test public void namesCannotInjectScript()
    {
        for(String name : new String[]{"", "a\nb", "a\"b", "a\\b"}) {
            try { FilterEditor.rule(name, 0, 995); fail(name); } catch(IllegalArgumentException expected) { }
        }
    }
}
