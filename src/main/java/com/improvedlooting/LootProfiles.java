package com.improvedlooting;

import java.util.*;
import javax.inject.*;

/** Validated, versioned copies of this plugin's settings only. Assets travel inside the backup. */
@Singleton
final class LootProfiles
{
    static final String SAVED="savedLootPresetsV1", UNDO="lootSetupUndoV1";
    static final String[] BOOLEAN_KEYS={"lootOverlay","lootBeams","lootCompact","lootReveal","priceTiersEnabled","shareMenuIcons","lootNotifications","lootSounds","originalImages","alwaysShowValue","alwaysShowTimer","groupStacks","despawnWarnings","notificationCards","iconDespawnFade","cardBackground","cardBorder","cardShortPrices","cardAutoWidth"};
    static final Map<String,int[]> NUMBERS=new LinkedHashMap<>();
    static { NUMBERS.put("labelRange",new int[]{1,64});NUMBERS.put("maxLabels",new int[]{10,1000});NUMBERS.put("labelSpacing",new int[]{0,16});NUMBERS.put("labelFontSize",new int[]{0,32});NUMBERS.put("iconSize",new int[]{12,96});NUMBERS.put("despawnSeconds",new int[]{1,120});NUMBERS.put("despawnMinimum",new int[]{0,Integer.MAX_VALUE});NUMBERS.put("alertVolume",new int[]{0,100});NUMBERS.put("soundCooldown",new int[]{0,60}); }
    static final class Setup { int version=1;Map<String,String> settings=new LinkedHashMap<>(); }
    static { NUMBERS.put("minimumLabelValue",new int[]{0,Integer.MAX_VALUE});NUMBERS.put("cardWidth",new int[]{120,2048});NUMBERS.put("cardHeight",new int[]{0,96});NUMBERS.put("cardRows",new int[]{0,20});NUMBERS.put("cardOpacity",new int[]{0,100}); }
    static final class Library { int version=1;Map<String,Setup> presets=new LinkedHashMap<>(); }
    @Inject ItemIcons icons;
    @Inject LootPreferences preferences;
    static Set<String> keys(){Set<String> keys=new LinkedHashSet<>(Arrays.asList(BOOLEAN_KEYS));keys.addAll(NUMBERS.keySet());Collections.addAll(keys,"cardTheme","cardBackgroundColor","priceDisplay","timerDisplay","groundStyle","labelOrder","labelAlignment",FilterIcons.KEY,ItemIcons.KEY,PriceTiers.KEY,LootPreferences.KEY);return keys;}
    Setup capture(){Setup s=new Setup();for(String key:keys())s.settings.put(key,icons.configs.getConfiguration(ImprovedLootingConfig.GROUP,key));return s;}
    String export(){return icons.gson.toJson(capture());}
    Setup parse(String raw)
    {
        if(raw==null||raw.length()>24000000)throw new IllegalArgumentException("Setup exceeds 24 MB");
        try{Setup setup=icons.gson.fromJson(raw,Setup.class);validate(setup);return setup;}
        catch(com.google.gson.JsonParseException e){throw new IllegalArgumentException("Invalid setup backup",e);}
    }
    void validate(Setup s)
    {
        if(s==null||s.version!=1||s.settings==null||!keys().containsAll(s.settings.keySet()))throw new IllegalArgumentException("Unsupported setup or setting");
        for(Map.Entry<String,String> e:s.settings.entrySet())
        {
            String key=e.getKey(),value=e.getValue();if(value==null)continue;
            if(Arrays.asList(BOOLEAN_KEYS).contains(key)&&!value.equals("true")&&!value.equals("false"))throw new IllegalArgumentException("Invalid switch: "+key);
            if(NUMBERS.containsKey(key)){try{int n=Integer.parseInt(value);int[] bounds=NUMBERS.get(key);if(n<bounds[0]||n>bounds[1]||key.equals("labelFontSize")&&n>0&&n<8)throw new NumberFormatException();}catch(NumberFormatException ex){throw new IllegalArgumentException("Out of range: "+key);}}
            if(key.equals("labelOrder"))ImprovedLootingConfig.LabelOrder.valueOf(value);
            if(key.equals("priceDisplay") || key.equals("timerDisplay"))ImprovedLootingConfig.DetailMode.valueOf(value);
            if(key.equals("cardTheme"))ImprovedLootingConfig.CardTheme.valueOf(value);
            if(key.equals("cardBackgroundColor")){
                try{Integer.parseInt(value);}catch(NumberFormatException ex){throw new IllegalArgumentException("Invalid card background color",ex);}
            }
            if(key.equals("groundStyle"))ImprovedLootingConfig.GroundStyle.valueOf(value);
            if(key.equals("labelAlignment"))ImprovedLootingConfig.LabelAlignment.valueOf(value);
            if(key.equals(FilterIcons.KEY)&&!value.isBlank())icons.filters.parse(value);
            if(key.equals(ItemIcons.KEY)&&!value.isBlank())icons.parse(value);
            if(key.equals(PriceTiers.KEY))icons.tiers.parse(value);
            if(key.equals(LootPreferences.KEY))preferences.parse(value);
        }
    }
    void applyValidated(Setup s,Object expectedProfile,String expectedCurrent)
    {
        if(!Objects.equals(expectedProfile,icons.configs.getProfile())||!Objects.equals(expectedCurrent,export()))throw new IllegalArgumentException("Profile or settings changed. Preview the setup again.");
        icons.configs.setConfiguration(ImprovedLootingConfig.GROUP,UNDO,expectedCurrent);
        for(String key:keys()){String value=s.settings.get(key);if(value==null)icons.configs.unsetConfiguration(ImprovedLootingConfig.GROUP,key);else icons.configs.setConfiguration(ImprovedLootingConfig.GROUP,key,value);}
    }
    String undo(){return icons.configs.getConfiguration(ImprovedLootingConfig.GROUP,UNDO);}
    Library library()
    {
        String raw=icons.configs.getConfiguration(ImprovedLootingConfig.GROUP,SAVED);if(raw==null||raw.isBlank())return new Library();
        if(raw.length()>24000000)throw new IllegalArgumentException("Preset library exceeds 24 MB");
        try{Library l=icons.gson.fromJson(raw,Library.class);if(l==null||l.version!=1||l.presets==null||l.presets.size()>12)throw new IllegalArgumentException("Invalid preset library");for(String name:l.presets.keySet())if(!LootPreferences.validLabel(name,60)||name.isBlank())throw new IllegalArgumentException("Invalid preset name");return l;}catch(com.google.gson.JsonParseException e){throw new IllegalArgumentException("Invalid preset library",e);}
    }
    void save(String name){name=name.trim();if(!LootPreferences.validLabel(name,60)||name.isBlank())throw new IllegalArgumentException("Use a name of 1–60 characters");Library l=library();l.presets.put(name,capture());persist(l);}
    void remove(String name){Library l=library();l.presets.remove(name);persist(l);}
    private void persist(Library l){String raw=icons.gson.toJson(l);if(raw.length()>24000000||l.presets.size()>12)throw new IllegalArgumentException("Keep at most 12 presets and 24 MB total");icons.configs.setConfiguration(ImprovedLootingConfig.GROUP,SAVED,raw);}
    String starter(boolean valuable)
    {
        Setup s=capture();FilterIcons.Bundle b=new FilterIcons.Bundle();b.compressedSource=FilterIcons.compress(valuable?
            "rule(gevalue:>=100000) {textColor=\"#ffcf60\"; icon=CurrentItem(); showValue=true; showDespawn=true; showLootbeam=true; notify=true;}\nrule(gevalue:>=1000) {showValue=true; showDespawn=true;}\nrule(true) {hideOverlay=true;}":
            "apply(true) {showValue=true; showDespawn=true; icon=CurrentItem();}\nrule(gevalue:>=100000) {textColor=\"#ffcf60\"; showLootbeam=true;}");
        s.settings.put(FilterIcons.KEY,icons.gson.toJson(b));s.settings.put(LootPreferences.KEY,null);return icons.gson.toJson(s);
    }
    String groundItemsPreview()
    {
        String highlights=icons.configs.getConfiguration("grounditems","highlightedItems"),hidden=icons.configs.getConfiguration("grounditems","hiddenItems");
        if((highlights==null||highlights.isBlank())&&(hidden==null||hidden.isBlank()))throw new IllegalArgumentException("No Ground Items highlight / hide lists found in this profile");
        StringBuilder rules=new StringBuilder("apply(true) {showValue=true; showDespawn=true;}\n");
        appendNames(rules,highlights,"hidden=false; hideOverlay=false; textColor=\"#ffcf60\"; showLootbeam=true;");
        appendNames(rules,hidden,"hidden=true; hideOverlay=true;");
        Setup s=capture();FilterIcons.Bundle bundle=new FilterIcons.Bundle();bundle.compressedSource=FilterIcons.compress(rules.toString());s.settings.put(FilterIcons.KEY,icons.gson.toJson(bundle));return icons.gson.toJson(s);
    }
    private static void appendNames(StringBuilder rules,String names,String display)
    {
        if(names==null)return;if(names.length()>100000)throw new IllegalArgumentException("Item list too large");
        for(String name:names.split(",")){name=name.trim();if(name.isEmpty())continue;FilterEditor.rule(name,0,0);rules.append("rule(name:\"").append(name).append("\") {").append(display).append("}\n");}
    }
}
