package com.improvedlooting;

import java.awt.*;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.FontManager;

final class LootCardsOverlay extends Overlay
{
    private final LootAlerts alerts;
    private final ImprovedLootingConfig config;
    @Inject LootCardsOverlay(LootAlerts alerts, ImprovedLootingConfig config)
    { this.alerts = alerts; this.config = config; setPosition(OverlayPosition.BOTTOM_RIGHT); setLayer(OverlayLayer.ABOVE_WIDGETS); }
    @Override public Dimension render(Graphics2D graphics)
    {
        if (!config.notificationCards()) return null;
        java.util.List<LootAlerts.Card> cards = alerts.cards(); if (cards.isEmpty()) return null;
        Graphics2D g = (Graphics2D)graphics.create(); int y = 0;
        try
        {
            g.setFont(FontManager.getRunescapeSmallFont());
            for (LootAlerts.Card card : cards)
            {
                g.setColor(new Color(25, 28, 32, 220)); g.fillRoundRect(0, y, 260, 30, 8, 8);
                g.setColor(new Color(231, 181, 70)); String text = card.text;
                while (text.length() > 1 && g.getFontMetrics().stringWidth(text) > 242) text = text.substring(0, text.length() - 1);
                g.drawString(text, 8, y + 20); y += 34;
            }
        }
        finally { g.dispose(); }
        return new Dimension(260, y);
    }
}
