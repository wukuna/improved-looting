package com.improvedlooting.rs2f.ast.leaf;

import com.improvedlooting.rs2f.FilterContext;
import com.improvedlooting.rs2f.ast.LeafCondition;
import com.improvedlooting.rs2f.model.Ownership;
import com.improvedlooting.rs2f.model.ItemFacts;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@EqualsAndHashCode(callSuper = false)
@ToString
public class ItemOwnershipCondition extends LeafCondition {
    private final Ownership ownership;

    public ItemOwnershipCondition(int ownership) {
        this.ownership = Ownership.fromOrdinal(ownership);
    }

    @Override
    public boolean test(FilterContext plugin, ItemFacts item) {
        return item.getOwnership() == ownership.ordinal();
    }
}
