package com.improvedlooting.rs2f.ast.leaf;

import com.improvedlooting.rs2f.FilterContext;
import com.improvedlooting.rs2f.model.Comparator;
import com.improvedlooting.rs2f.ast.LeafCondition;
import com.improvedlooting.rs2f.model.ItemFacts;
import lombok.ToString;

@ToString
public abstract class ComparatorCondition extends LeafCondition {
    private final int rhs;
    private final Comparator cmp;

    protected ComparatorCondition(int rhs, Comparator cmp) {
        this.rhs = rhs;
        this.cmp = cmp;
    }

    @Override
    public final boolean test(FilterContext plugin, ItemFacts item) {
        var lhs = getLhs(plugin, item);
        switch (cmp) {
            case GT:
                return lhs > rhs;
            case LT:
                return lhs < rhs;
            case EQ:
                return lhs == rhs;
            case GT_EQ:
                return lhs >= rhs;
            case LT_EQ:
                return lhs <= rhs;
        }
        return false;
    }

    public abstract long getLhs(FilterContext plugin, ItemFacts item);
}
