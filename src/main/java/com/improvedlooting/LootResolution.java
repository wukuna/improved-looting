package com.improvedlooting;

import com.improvedlooting.rs2f.DisplayConfig;
import com.improvedlooting.rs2f.FilterRule;
import com.improvedlooting.rs2f.model.ItemFacts;
import java.util.*;

final class LootResolution
{
    static DisplayConfig resolve(FilterIcons.Prepared filter, ItemFacts facts, LootPreferences preferences, String source)
    {
        DisplayConfig display = filter.program.evaluate(facts);
        return preferences == null ? display : LootPreferences.apply(display, preferences.action(facts.getId(), source));
    }
    static int secondsRemaining(int despawnTick, int currentTick)
    { return despawnTick <= 0 ? -1 : (int)Math.max(0, (((long)despawnTick - currentTick) * 3 + 4) / 5); }
    static String explain(FilterIcons.Prepared filter, ItemFacts facts, DisplayConfig finalDisplay,
        LootPreferences.Action override, String source, int seconds)
    {
        StringBuilder out = new StringBuilder();
        out.append(facts.getName()).append(" (#").append(facts.getId()).append(")\n")
            .append("Quantity: ").append(facts.getQuantity()).append("\nStack GE value: ")
            .append(PriceTiers.value(facts.getGePrice(), facts.getQuantity())).append(" gp\n")
            .append("Ownership: ").append(ownership(facts.getOwnership())).append("\n")
            .append("Source: ").append(source == null ? "Unavailable: RuneLite does not identify each pile’s monster source" : source + " (server loot event)").append("\n")
            .append("Despawn: ").append(seconds < 0 ? "Unknown; not supplied by client" : seconds + " seconds").append("\n\nRule evaluation, highest priority first:\n");
        int index = 0, matched = 0;
        for (FilterRule rule : filter.program.rules)
        {
            index++;
            if (!rule.getCond().test(null, facts)) continue;
            matched++;
            out.append('#').append(index).append(" matched, source line ").append(filter.userLine(rule.getSourceLine()))
                .append(rule.isTerminal() ? " — final matching rule\n" : " — apply and continue\n");
            if (rule.isTerminal()) break;
            if (matched >= 100) { out.append("Further matching rules omitted from this explanation.\n"); break; }
        }
        if (matched == 0) out.append("No matching rule; default visible white label.\n");
        out.append("\nManual/source override: ").append(override).append("\nFinal result:\n")
            .append("Label: ").append(finalDisplay.isHideOverlay() ? "hidden" : "visible")
            .append("\nHidden item rule: ").append(finalDisplay.isHidden())
            .append("\nColor: ").append(String.format("#%06X", finalDisplay.getTextColor().getRGB() & 0xffffff))
            .append("\nIcon: ").append(finalDisplay.getIcon() == null ? "none" : finalDisplay.getIcon())
            .append("\nBeam rule: ").append(finalDisplay.isShowLootbeam())
            .append("\nNotification rule: ").append(finalDisplay.isNotify())
            .append("\nSound rule: ").append(finalDisplay.getSound() == null ? "silent" : finalDisplay.getSound())
            .append("\nMenu priority: ").append(finalDisplay.getMenuSort())
            .append("\n\nAppearance, tier beam settings, seven-tier effects and master alert switches also apply. Hidden drops never alert.\n");
        return out.toString();
    }
    private static String ownership(int value)
    {
        switch (value) { case 1: return "Yours"; case 2: return "Other player"; case 3: return "Group"; default: return "Public/unassigned"; }
    }
    private LootResolution() { }
}
