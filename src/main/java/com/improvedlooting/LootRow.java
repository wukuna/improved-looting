package com.improvedlooting;

import java.awt.image.BufferedImage;

/** Immutable browser data; no game objects cross onto Swing's event thread. */
final class LootRow
{
    final long key, value;
    final int itemId, quantity, distance, seconds;
    final String name, category, source, explanation;
    final boolean hidden;
    final BufferedImage image;
    LootRow(long key, int itemId, int quantity, long value, int distance, int seconds, String name,
        String category, String source, String explanation, boolean hidden, BufferedImage image)
    {
        this.key = key; this.itemId = itemId; this.quantity = quantity; this.value = value;
        this.distance = distance; this.seconds = seconds; this.name = name; this.category = category;
        this.source = source; this.explanation = explanation; this.hidden = hidden; this.image = image;
    }
    @Override public String toString() { return name + " × " + quantity + (hidden ? " [hidden]" : ""); }
}
