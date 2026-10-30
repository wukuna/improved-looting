// Adapted from riktenx/loot-filters (BSD-2-Clause); see loot-filters-LICENSE.txt.
package com.improvedlooting.rs2f.ast.leaf;

import com.improvedlooting.rs2f.FilterContext;
import com.improvedlooting.rs2f.model.Comparator;
import com.improvedlooting.rs2f.model.ItemFacts;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = true)
public class ItemQuantityCondition extends ComparatorCondition {
    public ItemQuantityCondition(int value, Comparator cmp) {
        super(value, cmp);
    }

    @Override
    public long getLhs(FilterContext plugin, ItemFacts item) {
        return item.getQuantity();
    }
}
