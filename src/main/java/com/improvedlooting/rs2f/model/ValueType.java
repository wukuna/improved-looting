// Adapted from riktenx/loot-filters (BSD-2-Clause); see loot-filters-LICENSE.txt.
package com.improvedlooting.rs2f.model;

public enum ValueType {
    HIGHEST, GE, HA;

    @Override
    public String toString() {
        switch (this) {
            case HIGHEST: return "highest";
            case GE: return "grand exchange";
            default: return "high alchemy";
        }
    }
}
