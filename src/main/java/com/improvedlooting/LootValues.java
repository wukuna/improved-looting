package com.improvedlooting;

import com.improvedlooting.rs2f.model.ItemFacts;

final class LootValues
{
    static long value(ImprovedLootingConfig config, ItemFacts facts, long quantity)
    {
        return value(config.priceSource(), facts, quantity);
    }

    static long value(ImprovedLootingConfig.PriceSource source, ItemFacts facts, long quantity)
    {
        long price = facts.getGePrice();
        if (source == ImprovedLootingConfig.PriceSource.HIGH_ALCHEMY)
            price = facts.getId() == 995 ? 1 : facts.getId() == 13204 ? 1000 : facts.getHaPrice();
        return PriceTiers.value(price, quantity);
    }

    private LootValues() { }
}
