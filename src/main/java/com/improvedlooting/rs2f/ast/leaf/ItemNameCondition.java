// Adapted from riktenx/loot-filters (BSD-2-Clause); see loot-filters-LICENSE.txt.
package com.improvedlooting.rs2f.ast.leaf;

import com.improvedlooting.rs2f.FilterContext;
import com.improvedlooting.rs2f.ast.LeafCondition;
import com.improvedlooting.rs2f.model.ItemFacts;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.List;

import static com.improvedlooting.rs2f.util.TextUtil.isInfixWildcard;

@EqualsAndHashCode(callSuper = false)
@ToString
public class ItemNameCondition extends LeafCondition {
    private final List<String> names;

    public ItemNameCondition(List<String> names) {
        this.names = names;
    }

    public ItemNameCondition(String name) {
        this.names = List.of(name);
    }

    @Override
    public boolean test(FilterContext plugin, ItemFacts item) {
        return names.stream().anyMatch(it -> test(item.getName(), it));
    }

    private boolean test(String name, String target) {
        if (target.equals("*")) {
            return true;
        } else if (target.startsWith("*") && target.endsWith("*")) {
            return name.toLowerCase().contains(target.toLowerCase().substring(1, target.length() - 1));
        } else if (target.startsWith("*")) {
            return name.toLowerCase().endsWith(target.toLowerCase().substring(1));
        } else if (target.endsWith("*")) {
            return name.toLowerCase().startsWith(target.toLowerCase().substring(0, target.length() - 1));
        } else if (isInfixWildcard(target)) {
            var lowercase = name.toLowerCase();
            var index = target.indexOf('*');
            var before = target.substring(0, index).toLowerCase();
            var after = target.substring(index + 1).toLowerCase();
            return lowercase.startsWith(before) && lowercase.endsWith(after);
        }
        return name.equalsIgnoreCase(target);
    }
}
