package com.improvedlooting.rs2f.model;
import lombok.Value;
import lombok.Builder;
import net.runelite.api.coords.WorldPoint;
@Value @Builder public class ItemFacts {
 int id, quantity, ownership, accountType; String name; long gePrice; int haPrice;
 boolean stackable, noted, tradeable, geTradeable; WorldPoint worldPoint;
 public boolean isMoney() { return id==995 || id==13204; }
}
