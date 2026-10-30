package com.improvedlooting;

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
    private LootDisplayOptions() {}
}
