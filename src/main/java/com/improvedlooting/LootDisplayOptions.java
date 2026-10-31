package com.improvedlooting;

import java.awt.Color;
import net.runelite.api.TileItem;

/** One set of label decisions for both cards and plain labels. */
final class LootDisplayOptions
{
    static boolean detail(ImprovedLootingConfig.DetailMode mode,boolean rule)
    { return mode==ImprovedLootingConfig.DetailMode.ALWAYS || mode==ImprovedLootingConfig.DetailMode.RULES && rule; }
    static long price(ImprovedLootingConfig config,GroundLoot.Entry entry)
    { return !config.lootCompact() && detail(config.priceDisplay(),entry.display.isShowValue()) ? PriceTiers.value(entry.facts.getGePrice(),entry.renderQuantity):0; }
    static int seconds(ImprovedLootingConfig config,GroundLoot.Entry entry,int tick)
    { return !config.lootCompact() && detail(config.timerDisplay(),entry.display.isShowDespawn()) && entry.renderDespawn>0 && entry.renderDespawn<Integer.MAX_VALUE ? LootResolution.secondsRemaining(entry.renderDespawn,tick):-1; }
    static boolean hidden(boolean ruleHidden,Boolean manual,long price,int minimum)
    { return manual!=null?manual:ruleHidden || price<Math.max(0,minimum); }
    static boolean untradeable(GroundLoot.Entry entry)
    { return entry.facts != null && !entry.facts.isTradeable(); }
    static int minimum(ImprovedLootingConfig config,GroundLoot.Entry entry)
    { return config.keepUntradeablesVisible() && untradeable(entry) ? 0 : config.minimumLabelValue(); }
    static boolean ownershipHidden(ImprovedLootingConfig config,GroundLoot.Entry entry)
    {
        int ownership=entry.item.getOwnership();
        return config.onlyShowOwnedDrops() && ownership!=TileItem.OWNERSHIP_SELF && ownership!=TileItem.OWNERSHIP_GROUP;
    }
    static Color color(ImprovedLootingConfig config,GroundLoot.Entry entry,Color tierColor)
    {
        Color filter=entry.display.getTextColor();
        if(!Color.WHITE.equals(filter))return filter;
        if(untradeable(entry))return config.untradeableColor()==null?new Color(0xff5555):config.untradeableColor();
        return tierColor;
    }
    static boolean tierEffects(ImprovedLootingConfig config,GroundLoot.Entry entry)
    { return config.priceTiersEnabled() && !untradeable(entry); }
    private LootDisplayOptions() {}
}
