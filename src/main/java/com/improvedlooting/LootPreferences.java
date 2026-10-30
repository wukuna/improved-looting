package com.improvedlooting;

import com.google.gson.Gson;
import com.improvedlooting.rs2f.DisplayConfig;
import java.awt.Color;
import java.util.*;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;

/** Explicit user overrides, separate from imported RS2F source. */
@Singleton
final class LootPreferences
{
    static final String KEY = "lootOverridesV1";
    enum Action { FILTER, SHOW, HIDE, HIGHLIGHT }
    static final class ItemRule { Action action = Action.FILTER; String category = ""; Boolean labelHidden; }
    static final class Data
    {
        int version = 1;
        Map<Integer, ItemRule> items = new LinkedHashMap<>();
        Map<String, Action> sources = new LinkedHashMap<>();
    }
    @Inject ConfigManager configs;
    @Inject Gson gson;
    private String loaded;
    private Data current = new Data();
    private long revision;
    synchronized Data snapshot()
    {
        String raw = configs.getConfiguration(ImprovedLootingConfig.GROUP, KEY);
        if (!Objects.equals(raw, loaded))
        {
            loaded = raw;
            try { current = parse(raw); } catch (IllegalArgumentException ex) { current = new Data(); }
            revision++;
        }
        return current;
    }
    synchronized long revision() { snapshot(); return revision; }
    Data parse(String raw)
    {
        if (raw == null || raw.isBlank()) return new Data();
        if (raw.length() > 150000) throw new IllegalArgumentException("Too many overrides");
        try
        {
            Data data = gson.fromJson(raw, Data.class);
            if (data == null || data.version != 1 || data.items == null || data.sources == null
                || data.items.size() > 512 || data.sources.size() > 100) throw new IllegalArgumentException("Invalid overrides");
            data.items.forEach((id, rule) -> {
                if (id == null || id < 0 || id > 100000 || rule == null || rule.action == null || !validLabel(rule.category, 40))
                    throw new IllegalArgumentException("Invalid item override");
            });
            data.sources.forEach((name, action) -> {
                if (!validLabel(name, 100) || name.isBlank() || action == null) throw new IllegalArgumentException("Invalid source rule");
            });
            return data;
        }
        catch (com.google.gson.JsonParseException ex) { throw new IllegalArgumentException("Invalid overrides", ex); }
    }
    static boolean validLabel(String text, int limit)
    { return text != null && text.length() <= limit && text.chars().noneMatch(c -> Character.isISOControl(c) || c == '<' || c == '>'); }
    synchronized void set(int id, Action action, String category)
    {
        Data next = parse(gson.toJson(snapshot()));
        ItemRule rule = next.items.getOrDefault(id,new ItemRule()); rule.action = action; rule.category = category.trim();
        if (action == Action.FILTER && rule.category.isEmpty() && rule.labelHidden==null) next.items.remove(id); else next.items.put(id, rule);
        save(next);
    }
    synchronized void labelHidden(int id,Boolean value)
    {
        Data next=parse(gson.toJson(snapshot()));ItemRule rule=next.items.getOrDefault(id,new ItemRule());
        rule.labelHidden=value;
        if(rule.action==Action.FILTER && rule.category.isEmpty() && value==null)next.items.remove(id);else next.items.put(id,rule);
        save(next);
    }
    Boolean labelHidden(int id){ItemRule rule=snapshot().items.get(id);return rule==null?null:rule.labelHidden;}
    synchronized void source(String name, Action action)
    {
        Data next = parse(gson.toJson(snapshot()));
        name = name.trim();
        if (action == Action.FILTER) next.sources.remove(name); else next.sources.put(name, action);
        save(next);
    }
    private void save(Data next)
    {
        String raw = gson.toJson(next); parse(raw);
        current = next; loaded = raw; revision++;
        configs.setConfiguration(ImprovedLootingConfig.GROUP, KEY, raw);
    }
    String category(int id) { ItemRule rule = snapshot().items.get(id); return rule == null ? "" : rule.category; }
    Action action(int id, String source)
    {
        Data data = snapshot(); ItemRule rule = data.items.get(id);
        if (rule != null && rule.action != Action.FILTER) return rule.action;
        return source == null ? Action.FILTER : data.sources.getOrDefault(source, Action.FILTER);
    }
    static DisplayConfig apply(DisplayConfig display, Action action)
    {
        switch (action)
        {
            case HIDE: return display.toBuilder().hidden(true).hideOverlay(true).build();
            case SHOW: return display.toBuilder().hidden(false).hideOverlay(false).build();
            case HIGHLIGHT: return display.toBuilder().hidden(false).hideOverlay(false).textColor(Color.YELLOW)
                .highlightTile(true).tileStrokeColor(Color.YELLOW).tileFillColor(new Color(255, 220, 0, 30))
                .showLootbeam(true).lootbeamColor(Color.YELLOW).build();
            default: return display;
        }
    }
}
