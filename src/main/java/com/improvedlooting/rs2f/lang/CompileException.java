// Adapted from riktenx/loot-filters (BSD-2-Clause); see loot-filters-LICENSE.txt.
package com.improvedlooting.rs2f.lang;

public abstract class CompileException extends RuntimeException {
    protected CompileException(String message) {
        super(message);
    }
}
