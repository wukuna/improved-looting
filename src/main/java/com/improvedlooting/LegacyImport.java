package com.improvedlooting;

import java.util.*;
import javax.inject.*;
import net.runelite.client.config.ConfigManager;

@Singleton
final class LegacyImport
{
    @Inject ConfigManager configs;
    @Inject FilterIcons filters;
    @Inject ItemIcons icons;
    static final String SOURCE="improvedmenus";
    Map<String,String> prepare()
    {
        Map<String,String> copy=new LinkedHashMap<>();
        String filter=configs.getConfiguration(SOURCE,FilterIcons.KEY);
        String assignments=configs.getConfiguration(SOURCE,ItemIcons.KEY);
        if(filter!=null && !filter.isBlank()) { filters.parse(filter); copy.put(FilterIcons.KEY,filter); }
        if(assignments!=null && !assignments.isBlank()) { icons.parse(assignments); copy.put(ItemIcons.KEY,assignments); }
        return copy;
    }
    void apply(Map<String,String> copy)
    {

        for(String key:copy.keySet()) {
            String existing=configs.getConfiguration(ImprovedLootingConfig.GROUP,key);
            if(existing!=null && !existing.isBlank()) throw new IllegalArgumentException("A setup already exists here. Export it and clear it before copying another setup.");
        }
        copy.forEach((key,value)->configs.setConfiguration(ImprovedLootingConfig.GROUP,key,value));
        filters.reload();
    }
}
