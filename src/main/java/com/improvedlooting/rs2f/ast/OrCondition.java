package com.improvedlooting.rs2f.ast;

import com.improvedlooting.rs2f.FilterContext;
import com.improvedlooting.rs2f.model.ItemFacts;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.List;

@EqualsAndHashCode(callSuper = false)
@ToString
public class OrCondition extends Condition {
    private final List<Condition> rules;

    public OrCondition(List<Condition> rules) {
        this.rules = rules;
    }

    public OrCondition(Condition left, Condition right) {
        this.rules = List.of(left, right);
    }

    @Override
    public boolean test(FilterContext plugin, ItemFacts item) {
        return rules.stream().anyMatch(it -> it.test(plugin, item));
    }
}
