package com.improvedlooting;

import com.improvedlooting.rs2f.model.ItemFacts;
import java.util.*;
import net.runelite.api.*;
import net.runelite.api.events.MenuOpened;
import java.util.function.Function;
import java.util.function.Predicate;
import net.runelite.client.util.Text;

final class LootPileOrder {
    private LootPileOrder() {}

    static int category(ItemFacts facts) {
        if (!facts.isTradeable()) return 2;
        String name = facts.getName().toLowerCase(Locale.ROOT);
        return name.equals("bones") || name.endsWith(" bones") || name.equals("ashes") || name.endsWith(" ashes") ? 1 : 0;
    }

    static int compare(ItemFacts a, long aq, ItemFacts b, long bq) {
        int category = Integer.compare(category(a), category(b));
        return category != 0 ? category : Long.compare(PriceTiers.value(b.getGePrice(), bq), PriceTiers.value(a.getGePrice(), aq));
    }

    static Comparator<GroundLoot.Entry> labels(Predicate<GroundLoot.Entry> hidden) {

        Map<GroundLoot.Entry, Boolean> visibility = new IdentityHashMap<>();
        return Comparator.comparing((GroundLoot.Entry e) -> visibility.computeIfAbsent(e, hidden::test))
            .thenComparing((a, b) -> compare(a.facts, a.renderQuantity, b.facts, b.renderQuantity));
    }

    static Map<Integer, Integer> ranks(Collection<GroundLoot.Entry> pile, Predicate<GroundLoot.Entry> hidden) {
        Comparator<GroundLoot.Entry> order = labels(hidden);
        Map<Integer, GroundLoot.Entry> best = new LinkedHashMap<>();
        for (GroundLoot.Entry entry : pile) {
            if (entry.grouped) continue;

            best.merge(entry.item.getId(), entry, (a, b) -> order.compare(a, b) <= 0 ? a : b);
        }
        List<Integer> ids = new ArrayList<>(best.keySet());
        ids.sort((a,b) -> order.compare(best.get(a), best.get(b)));
        Map<Integer,Integer> result = new HashMap<>();
        int rank = 0;
        for (int i = 0; i < ids.size(); i++) {
            int id = ids.get(i);
            if (i > 0 && order.compare(best.get(ids.get(i-1)), best.get(id)) != 0) rank = i;
            result.put(id, -rank);
        }
        return result;
    }

    static void opened(Client client, ImprovedLootingConfig config, MenuOpened event, Function<Tile, Map<Integer,Integer>> rankLookup) {
        if (!config.prioritizeLoot() || client.getGameState() != GameState.LOGGED_IN) return;
        MenuEntry[] original = client.getMenu().getMenuEntries(), sorted = original.clone();
        Map<List<Integer>, List<Integer>> piles = new LinkedHashMap<>();
        for (int i = 0; i < original.length; i++) {
            MenuEntry e = original[i];
            if (!take(e)) continue;
            List<Integer> key = Arrays.asList(e.getWorldViewId(), e.getParam0(), e.getParam1(), e.isDeprioritized() ? 1 : 0);
            piles.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
        }
        for (List<Integer> slots : piles.values()) {
            if (slots.size() < 2) continue;
            MenuEntry first = original[slots.get(0)];
            WorldView view = client.getWorldView(first.getWorldViewId());
            if (view == null || view.getScene() == null) continue;
            Tile[][][] tiles = view.getScene().getTiles();
            int p = view.getPlane(), x = first.getParam0(), y = first.getParam1();
            if (tiles == null || p < 0 || p >= tiles.length || tiles[p] == null || x < 0 || x >= tiles[p].length
                || tiles[p][x] == null || y < 0 || y >= tiles[p][x].length) continue;
            Map<Integer,Integer> ranks = rankLookup.apply(tiles[p][x][y]);
            List<Integer> known = new ArrayList<>();
            List<MenuEntry> entries = new ArrayList<>();
            for (int slot : slots) if (ranks.containsKey(original[slot].getIdentifier())) { known.add(slot); entries.add(original[slot]); }
            entries.sort(Comparator.comparingInt(e -> ranks.get(e.getIdentifier())));
            for (int i = 0; i < known.size(); i++) sorted[known.get(i)] = entries.get(i);
        }
        if (!Arrays.equals(original, sorted)) { client.getMenu().setMenuEntries(sorted); if (event != null) event.setMenuEntries(sorted); }
    }

    private static boolean take(MenuEntry entry) {
        MenuAction type = entry.getType();
        return entry.onClick() == null && entry.getSubMenu() == null && !entry.isForceLeftClick()
            && (type == MenuAction.GROUND_ITEM_FIRST_OPTION || type == MenuAction.GROUND_ITEM_SECOND_OPTION
            || type == MenuAction.GROUND_ITEM_THIRD_OPTION || type == MenuAction.GROUND_ITEM_FOURTH_OPTION
            || type == MenuAction.GROUND_ITEM_FIFTH_OPTION) && "Take".equals(Text.removeTags(entry.getOption()));
    }
}
