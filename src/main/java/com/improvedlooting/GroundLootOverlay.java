package com.improvedlooting;

import com.improvedlooting.rs2f.DisplayConfig;
import com.improvedlooting.rs2f.util.TextComponent;
import java.awt.*;
import java.util.*;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Tile;
import net.runelite.client.ui.overlay.*;

final class GroundLootOverlay extends Overlay
{
    private final GroundLoot loot;
    private final Client client;
    private final ImprovedLootingConfig config;
    @Inject private PriceTiers tiers;
    @Inject private GroundLootCards groundCards;
    @Inject private LootLabelInput input;
    void clear() { groundCards.clear();if(input!=null)input.reset(); }
    @Inject GroundLootOverlay(GroundLoot loot, Client client, ImprovedLootingConfig config)
    {
        this.loot = loot; this.client = client; this.config = config;
        setPosition(OverlayPosition.DYNAMIC); setLayer(OverlayLayer.ABOVE_SCENE);
    }
    @Override public Dimension render(Graphics2D original)
    {
        if(input!=null)input.beginFrame();
        if (!loot.groundAllowed()) { if(input!=null)input.reset();return null; }
        if (config.groundStyle() == ImprovedLootingConfig.GroundStyle.CARDS) {
            try { groundCards.render(original); }
            finally { finishInput(); }
            return null;
        }
        Graphics2D g = (Graphics2D)original.create();
        PriceTiers.Ready tierSetup=tiers!=null?tiers.snapshot():null;
        long animationTime=System.nanoTime();
        try {
            g.clipRect(client.getViewportXOffset(), client.getViewportYOffset(), client.getViewportWidth(), client.getViewportHeight());
            Map<Tile, Integer> offsets = new IdentityHashMap<>();
            Set<Tile> highlighted = Collections.newSetFromMap(new IdentityHashMap<>());
            int count = 0;
            java.util.List<GroundLoot.Entry> paintOrder = new ArrayList<>();
            for (GroundLoot.Entry entry : loot.entries()) {
                if (entry.grouped || entry.display == null || !loot.inRange(entry, LootAlerts.clamp(config.labelRange(),1,64))
                    || entry.tile.getItemLayer() == null || loot.labelHidden(entry) && !loot.reveal()) continue;
                if (paintOrder.size() >= LootAlerts.clamp(config.maxLabels(),10,1000)) break;
                paintOrder.add(entry);
            }
            // Labels grow upwards; cards grow downwards. Select highest-priority rows
            // before reversing so a label limit never favors the lowest-value items.
            if (config.prioritizeLoot()) Collections.reverse(paintOrder);
            for (GroundLoot.Entry entry : paintOrder) {
                if (loot.selected(entry)) {
                    Polygon poly=Perspective.getCanvasTilePoly(client,entry.tile.getLocalLocation());
                    if(poly!=null){g.setColor(new Color(80,220,255,50));g.fill(poly);g.setColor(Color.CYAN);g.draw(poly);}
                }
                if (count >= LootAlerts.clamp(config.maxLabels(),10,1000)) continue;
                DisplayConfig display = entry.display;
                if (entry.grouped || display == null || !loot.inRange(entry, LootAlerts.clamp(config.labelRange(),1,64)) || entry.tile.getItemLayer() == null || loot.labelHidden(entry) && !loot.reveal()) continue;
                if (display.isHighlightTile() && highlighted.add(entry.tile)) {
                    Polygon poly = Perspective.getCanvasTilePoly(client, entry.tile.getLocalLocation());
                    if (poly != null) {
                        if (display.getTileFillColor() != null) { g.setColor(display.getTileFillColor()); g.fill(poly); }
                        g.setColor(display.getTileStrokeColor()); g.draw(poly);
                    }
                }
                g.setFont(config.labelFontSize()==0?display.getFont():display.getFont().deriveFont((float)LootAlerts.clamp(config.labelFontSize(),8,32)));
                PriceTiers.Tier tier=tierSetup==null?null:tierSetup.setup.tiers[PriceTiers.index(tierSetup.setup,PriceTiers.value(entry.facts.getGePrice(),entry.renderQuantity))];
                if(tier!=null && tier.bold)g.setFont(g.getFont().deriveFont(g.getFont().getStyle()|Font.BOLD));
                String text = entry.facts.getName();
                text += LootCardPainter.quantity(entry.renderQuantity);
                String details=LootCardPainter.details(LootDisplayOptions.price(config,entry),config.cardShortPrices(),LootDisplayOptions.seconds(config,entry,client.getTickCount()));
                if(!details.isEmpty())text+=" "+details;
                boolean compact = config.lootCompact();
                if (compact) text = LootCardPainter.quantity(entry.renderQuantity).trim();
                FontMetrics metrics = g.getFontMetrics();
                java.awt.image.BufferedImage image = compact ? lootItemImage(entry) : entry.image;
                int maxIcon=LootAlerts.clamp(config.iconSize(),12,96);
                if(image!=null && config.iconDespawnFade())image=groundCards.image(entry,maxIcon,LootCardPainter.remaining(entry.renderSpawnTick,entry.renderDespawn,loot.renderTick()));
                int iw=0,ih=0;
                if(image!=null){double scale=Math.min((double)maxIcon/image.getWidth(),(double)maxIcon/image.getHeight());if(config.originalImages())scale=Math.min(1,scale);iw=Math.max(1,(int)(image.getWidth()*scale));ih=Math.max(1,(int)(image.getHeight()*scale));}
                int iconWidth = image==null?0:iw+3;
                int height = Math.max(metrics.getHeight(), ih) + 4;
                int width = Math.max(12, metrics.stringWidth(text) + iconWidth + 6);
                net.runelite.api.Point location = Perspective.getCanvasTextLocation(client, g, entry.tile.getLocalLocation(), "", entry.tile.getItemLayer().getHeight() + 20);
                if (location == null) continue;
                int offset = offsets.getOrDefault(entry.tile, 0);
                int x = location.getX() - (config.labelAlignment()==ImprovedLootingConfig.LabelAlignment.LEFT?0:config.labelAlignment()==ImprovedLootingConfig.LabelAlignment.RIGHT?width:width/2), y = location.getY() - offset - height;
                if (y < client.getViewportYOffset() || x < client.getViewportXOffset() || x + width > client.getViewportXOffset() + client.getViewportWidth()) continue;
                count++; offsets.put(entry.tile, offset + height + LootAlerts.clamp(config.labelSpacing(),0,16));
                if (display.getBackgroundColor() != null) { g.setColor(display.getBackgroundColor()); g.fillRect(x, y, width, height); }
                if (display.getBorderColor() != null) { g.setColor(display.getBorderColor()); g.drawRect(x, y, width, height); }
                if (image != null) g.drawImage(image, x + 2, y + (height - ih) / 2, iw, ih, null);
                if(input!=null)input.add(new Rectangle(x,y,width,height),entry,loot.labelHidden(entry));
                Color color=LootDisplayOptions.color(config,entry,tier==null?Color.WHITE:new Color(tier.color));
                TextComponent label = new TextComponent(); label.setText(text); label.setColor(loot.labelHidden(entry) ? Color.GRAY : color);
                if(tier!=null && LootDisplayOptions.tierEffects(config,entry) && !loot.labelHidden(entry)) {
                    label.setForegroundPaint(LootTextPaint.paint(tier,tierSetup.setup.seconds,animationTime,x+iconWidth+3,metrics.stringWidth(text)));
                }
                if (display.getTextAccent() != null) label.setTextAccent(display.getTextAccent());
                if (display.getTextAccentColor() != null) label.setAccentColor(display.getTextAccentColor());
                label.setPosition(new Point(x + iconWidth + 3, y + (height - metrics.getHeight()) / 2 + metrics.getAscent())); label.render(g);
            }
        } finally { g.dispose();finishInput(); }
        return null;
    }
    private void finishInput(){if(input!=null){if(client.isMenuOpen())input.reset();else input.finishFrame();}}
    private java.awt.image.BufferedImage lootItemImage(GroundLoot.Entry entry) { return loot.icons.items.getImage(entry.item.getId()); }
}
