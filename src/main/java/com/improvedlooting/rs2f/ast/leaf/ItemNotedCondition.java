package com.improvedlooting.rs2f.ast.leaf;

import com.improvedlooting.rs2f.FilterContext;
import com.improvedlooting.rs2f.ast.LeafCondition;
import com.improvedlooting.rs2f.model.ItemFacts;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode(callSuper = false)
@ToString
public class ItemNotedCondition extends LeafCondition {
    private final boolean target;

    public ItemNotedCondition(boolean target) {
        this.target = target;
    }

    @Override
    public boolean test(FilterContext plugin, ItemFacts item) {
        return item.isNoted() == target;
    }
}
