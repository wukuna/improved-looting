// Adapted from riktenx/loot-filters (BSD-2-Clause); see loot-filters-LICENSE.txt.
package com.improvedlooting.rs2f.ast.leaf;

import com.improvedlooting.rs2f.FilterContext;
import com.improvedlooting.rs2f.ast.LeafCondition;
import com.improvedlooting.rs2f.model.ItemFacts;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode(callSuper = false)
@ToString
public class ItemGeTradeableCondition extends LeafCondition {
    private final boolean target;

    public ItemGeTradeableCondition(boolean target) {
        this.target = target;
    }

    @Override
    public boolean test(FilterContext plugin, ItemFacts item) {
        return item.isGeTradeable() == target;
    }
}
