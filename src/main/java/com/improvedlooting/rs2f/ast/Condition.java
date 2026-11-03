package com.improvedlooting.rs2f.ast;

import com.improvedlooting.rs2f.FilterContext;
import com.improvedlooting.rs2f.model.ItemFacts;

public abstract class Condition {
    public abstract boolean test(FilterContext plugin, ItemFacts item);
}
