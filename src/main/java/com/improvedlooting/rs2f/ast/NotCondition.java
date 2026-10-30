// Adapted from riktenx/loot-filters (BSD-2-Clause); see loot-filters-LICENSE.txt.
package com.improvedlooting.rs2f.ast;

import com.improvedlooting.rs2f.FilterContext;
import com.improvedlooting.rs2f.model.ItemFacts;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode(callSuper = false)
@ToString
public class NotCondition extends Condition {
    private final Condition inner;

    public NotCondition(Condition inner) {
        this.inner = inner;
    }

    @Override
    public boolean test(FilterContext plugin, ItemFacts item) {
        return !inner.test(plugin, item);
    }
}
