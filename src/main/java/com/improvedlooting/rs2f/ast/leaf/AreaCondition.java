package com.improvedlooting.rs2f.ast.leaf;

import com.improvedlooting.rs2f.FilterContext;
import com.improvedlooting.rs2f.ast.LeafCondition;
import com.improvedlooting.rs2f.model.ItemFacts;
import net.runelite.api.coords.WorldPoint;

public class AreaCondition extends LeafCondition {
    private final WorldPoint p0, p1;

    public AreaCondition(WorldPoint p0, WorldPoint p1) {
        this.p0 = p0;
        this.p1 = p1;
    }

    @Override
    public boolean test(FilterContext plugin, ItemFacts item) {
        var p = item.getWorldPoint();
        return p != null && p.getX() >= p0.getX() && p.getY() >= p0.getY() && p.getPlane() >= p0.getPlane()
                && p.getX() <= p1.getX() && p.getY() <= p1.getY() && p.getPlane() <= p1.getPlane();
    }
}
