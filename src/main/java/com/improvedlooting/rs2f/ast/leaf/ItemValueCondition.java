// Adapted from riktenx/loot-filters (BSD-2-Clause); see loot-filters-LICENSE.txt.
package com.improvedlooting.rs2f.ast.leaf;

import com.improvedlooting.rs2f.FilterContext;
import com.improvedlooting.rs2f.model.Comparator;
import com.improvedlooting.rs2f.model.ValueType;
import com.improvedlooting.rs2f.model.ItemFacts;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = true)
public class ItemValueCondition extends ComparatorCondition {
    private final ValueType valueType;

    public ItemValueCondition(int value, Comparator cmp, ValueType valueType) {
       super(value, cmp);
       this.valueType = valueType;
    }

    @Override
    public long getLhs(FilterContext plugin, ItemFacts item) {
        return getValue(item) * item.getQuantity();
    }

    private long getValue(ItemFacts item) {
        switch (valueType) {
            case HIGHEST: return Math.max(item.getGePrice(), item.getHaPrice());
            case GE: return item.getGePrice();
            default: return item.getHaPrice();
        }
    }
}
