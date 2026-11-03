package com.improvedlooting.rs2f.ast.leaf;

import com.improvedlooting.rs2f.FilterContext;
import com.improvedlooting.rs2f.ast.LeafCondition;
import com.improvedlooting.rs2f.model.ItemFacts;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

@Getter
@EqualsAndHashCode(callSuper = false)
@ToString
public class ItemIdCondition extends LeafCondition {
    private final List<Integer> ids;

    public ItemIdCondition(List<Integer> ids) {
        this.ids = ids;
    }

    public ItemIdCondition(int id) {
        this.ids = List.of(id);
    }

    @Override
    public boolean test(FilterContext plugin, ItemFacts item) {
        return ids.stream().anyMatch(it -> item.getId() == it);
    }
}
