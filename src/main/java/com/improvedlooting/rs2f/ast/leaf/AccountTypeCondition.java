package com.improvedlooting.rs2f.ast.leaf;

import com.improvedlooting.rs2f.FilterContext;
import com.improvedlooting.rs2f.ast.LeafCondition;
import com.improvedlooting.rs2f.model.ItemFacts;
import net.runelite.api.gameval.VarbitID;

public class AccountTypeCondition extends LeafCondition {
    private final int type;

    public AccountTypeCondition(int type) {
        this.type = type;
    }

    @Override
    public boolean test(FilterContext plugin, ItemFacts item) {
        return item.getAccountType() == type;
    }
}
