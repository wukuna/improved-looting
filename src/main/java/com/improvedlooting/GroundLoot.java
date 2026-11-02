package com.improvedlooting;

import com.improvedlooting.rs2f.DisplayConfig;
import com.improvedlooting.rs2f.model.*;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.Comparator;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.*;
import net.runelite.client.Notifier;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.PluginManager;

/** Client-thread ground-item index. Rules run when facts change, never during painting. */
@Singleton
final class GroundLoot
{
    @Inject Client client;
    @Inject ImprovedLootingConfig config;
    @Inject FilterIcons filters;
    @Inject ItemIcons icons;
    @Inject PluginManager plugins;
    @Inject Notifier notifier;
    @Inject LootPreferences preferences;
    @Inject LootAlerts alerts;
    @Inject PriceTiers tiers;
    private final Map<TileItem, Entry> entries = new IdentityHashMap<>();
    private final Map<Tile, Map<Integer,Integer>> menuRanks = new IdentityHashMap<>();
    private final Set<Tile> beamTiles = Collections.newSetFromMap(new IdentityHashMap<>());
    private volatile FilterIcons.Prepared prepared;
    private volatile boolean running;
    private boolean groundAllowed;
    private boolean conditionalMenuHiding;
    private long sequence, preferencesRevision, lastTickNanos;
    private volatile List<LootRow> rows = Collections.emptyList();
    private List<Entry> ordered = Collections.emptyList();
    private long selected = -1;
    private int selectedUntil, localViewId=Integer.MIN_VALUE, accountType=-1, evaluationsRemaining;
    private volatile String status = "Ground display is off";
    static final int MAX_ITEMS = 4096;
    static final class Entry
    {
        final Tile tile;
        final TileItem item;
        ItemFacts facts;
        DisplayConfig display;
        DisplayConfig filterDisplay;
        BufferedImage image;
        RuneLiteObject beam;
        Color beamColor;
        long key, renderQuantity;
        int renderDespawn, renderSpawnTick;
        boolean grouped;
        int spawnTick, quantity, ownership, distance;
        boolean pendingDrop, warningArmed, warned;
        String source;
        Entry(Tile tile, TileItem item) { this.tile = tile; this.item = item; }
    }
    Collection<Entry> entries() { return ordered; }
    private Map<Integer,Integer> ranks(Tile tile) { return menuRanks.computeIfAbsent(tile,this::rankPile); }
    private Map<Integer,Integer> rankPile(Tile tile)
    {
        if(tile==null || tile.getGroundItems()==null)return Collections.emptyMap();
        List<Entry> pile=new ArrayList<>();
        for(TileItem item:tile.getGroundItems()) {
            Entry entry=new Entry(tile,item),cached=entries.get(item);
            entry.facts=filters.facts(tile,item);entry.display=resolved(tile,item);
            if(entry.display==null)return Collections.emptyMap();
            entry.source=cached==null?null:cached.source;
            pile.add(entry);
        }
        // Share label grouping and visibility, including minimum stack value and
        // Alt-click overrides. Fresh drops can have menus before their first tick.
        group(pile);
        return LootPileOrder.ranks(pile,this::labelHidden);
    }
    int menuRank(Tile tile, int id, int fallback) { return ranks(tile).getOrDefault(id,fallback); }
    // A menu entry cannot distinguish duplicate copies. Keep it if any copy is
    // visible or unresolved, so filtering never strands a visible drop.
    private boolean menuHidden(Tile tile, int id) {
        if(tile==null || tile.getGroundItems()==null)return false;
        boolean found=false;
        for(TileItem item:tile.getGroundItems())if(item.getId()==id) {
            Entry e=entries.get(item);
            if(e==null || e.facts==null || e.display==null || !menuHidden(e))return false;
            found=true;
        }
        return found;
    }
    boolean menuHidden(Entry e)
    {
        Boolean choice=preferences==null?null:preferences.hideMenu(e.item.getId());
        if(choice==null)choice=e.display.getHideMenu();
        return (choice==null?config.hideHiddenMenuItems():choice) && labelHidden(e);
    }
    private void updateMenu(MenuOpened event) {
        if(client.getGameState()!=GameState.LOGGED_IN)return;
        MenuEntry[] original=client.getMenu().getMenuEntries();
        // Never change selected-item or selected-spell targeting menus.
        for(MenuEntry e:original)if(e.getType()==MenuAction.WIDGET_TARGET_ON_GROUND_ITEM
            || e.getType()==MenuAction.ITEM_USE_ON_GROUND_ITEM)return;
        if((config.hideHiddenMenuItems() || conditionalMenuHiding) && !reveal()) {
            List<MenuEntry> visible=new ArrayList<>();
            for(MenuEntry e:original) {
                boolean hide=false;
                if(e.onClick()==null && e.getSubMenu()==null && !e.isForceLeftClick() && ordinaryGroundAction(e.getType())) {
                    WorldView view=client.getWorldView(e.getWorldViewId());
                    Tile[][][] tiles=view==null || view.getScene()==null?null:view.getScene().getTiles();
                    int plane=view==null?-1:view.getPlane(),x=e.getParam0(),y=e.getParam1();
                    if(tiles!=null && plane>=0 && plane<tiles.length && tiles[plane]!=null && x>=0 && x<tiles[plane].length
                        && tiles[plane][x]!=null && y>=0 && y<tiles[plane][x].length)hide=menuHidden(tiles[plane][x][y],e.getIdentifier());
                }
                if(!hide)visible.add(e);
            }
            if(visible.size()!=original.length) {
                MenuEntry[] next=visible.toArray(new MenuEntry[0]);client.getMenu().setMenuEntries(next);
                if(event!=null)event.setMenuEntries(next);
            }
        }
        LootPileOrder.opened(client,config,event,this::ranks);
    }
    private static boolean ordinaryGroundAction(MenuAction type) {
        return type==MenuAction.GROUND_ITEM_FIRST_OPTION || type==MenuAction.GROUND_ITEM_SECOND_OPTION
            || type==MenuAction.GROUND_ITEM_THIRD_OPTION || type==MenuAction.GROUND_ITEM_FOURTH_OPTION
            || type==MenuAction.GROUND_ITEM_FIFTH_OPTION || type==MenuAction.EXAMINE_ITEM_GROUND;
    }
    List<LootRow> rows() { return rows; }
    void select(long key) { selected = key; selectedUntil = client.getTickCount() + 17; }
    boolean selected(Entry e) { return e.key == selected && client.getTickCount() <= selectedUntil; }
    String explain(long key)
    {
        for (Entry e : entries.values()) if (e.key == key && prepared != null && e.facts != null)
            return LootResolution.explain(prepared, e.facts, e.display, preferences == null ? LootPreferences.Action.FILTER : preferences.action(e.item.getId(), e.source),
                e.source, LootResolution.secondsRemaining(e.item.getDespawnTime(), client.getTickCount())) + "\nFinal ground label: "+(labelHidden(e)?"hidden":"visible")+"\nAlt-click label override: "+(preferences==null?null:preferences.labelHidden(e.item.getId()))+"\nItem menu choice: "+LootMenuChoice.of(preferences==null?null:preferences.hideMenu(e.item.getId()))+"\nRule menu choice: "+LootMenuChoice.of(e.display.getHideMenu())+"\nOrdinary menu actions: "+(menuHidden(e)?"hidden (Alt reveals)":"kept")+"\nMinimum label value: "+config.minimumLabelValue()+" gp\nBeam on this tile: "+beamTiles.contains(e.tile) + (icons.find(e.item.getId())==null?"":"\nManual icon override: "+icons.find(e.item.getId()));
        return "This pile is no longer nearby. Refresh the browser.";
    }
    DisplayConfig resolved(Tile tile, TileItem item)
    {
        Entry entry = entries.get(item);
        if (entry != null && entry.facts != null && entry.display != null && entry.facts.getQuantity()==item.getQuantity() && entry.facts.getOwnership()==item.getOwnership()) return entry.display;
        FilterIcons.Prepared snapshot = filters.snapshot();
        return snapshot == null ? null : LootResolution.resolve(snapshot, filters.facts(tile, item), preferences, entry==null?null:entry.source);
    }
    String status() { return status; }
    boolean isRunning() { return running; }
    boolean groundAllowed() { return running && groundAllowed; }
    boolean reveal() { return client.isKeyPressed(KeyCode.KC_ALT); }
    boolean labelHidden(Entry e) {
        Boolean manual=preferences==null?null:preferences.labelHidden(e.item.getId());
        return LootDisplayOptions.ownershipHidden(config,e) || LootDisplayOptions.hidden(e.display.isHideOverlay(),manual,PriceTiers.value(e.facts.getGePrice(),e.renderQuantity),LootDisplayOptions.minimum(config,e));
    }
    boolean locallyHidden(Entry e) {
        Boolean manual=preferences==null?null:preferences.labelHidden(e.item.getId());
        return LootDisplayOptions.ownershipHidden(config,e) || LootDisplayOptions.hidden(false,manual,PriceTiers.value(e.facts.getGePrice(),e.renderQuantity),LootDisplayOptions.minimum(config,e));
    }
    double renderTick() { return client.getTickCount() + (lastTickNanos == 0 ? 0 : Math.min(1, (System.nanoTime()-lastTickNanos)/600000000.0)); }
    // Overrides do not change item facts or icons. Reuse the evaluated filter so
    // editing one label cannot blank every pile until the next game tick.
    void refreshOverrides()
    {
        menuRanks.clear();
        if(!running || prepared==null)return;
        applyOverrides();
        publishRows();
        updateBeams();
    }
    private void applyOverrides()
    {
        preferencesRevision=preferences==null?0:preferences.revision();
        for(Entry e:entries.values())if(e.facts!=null && e.filterDisplay!=null)
            e.display=preferences==null?e.filterDisplay:LootPreferences.apply(e.filterDisplay,preferences.action(e.item.getId(),e.source));
    }
    void refreshSettings()
    {
        conditionalMenuHiding=false;
        menuRanks.clear();
        prepared=null;
        for(Entry e:entries.values()){e.facts=null;e.image=null;beamOff(e);}
        ordered=Collections.emptyList();rows=Collections.emptyList();
    }
    void clear()
    {
        conditionalMenuHiding=false;
        menuRanks.clear();
        for (Entry e : entries.values()) beamOff(e);
        entries.clear(); beamTiles.clear(); prepared = null; running = false; localViewId=Integer.MIN_VALUE; rows = Collections.emptyList(); ordered = Collections.emptyList(); selected = -1; if(alerts != null) alerts.clear();
    }
    private String competing()
    {
        List<String> names=new ArrayList<>();
        for (net.runelite.client.plugins.Plugin plugin : plugins.getPlugins()) {
            if (("Loot Filters".equals(plugin.getName()) || "Loot Filters TEST".equals(plugin.getName()) || "Ground Items".equals(plugin.getName())) && plugins.isPluginActive(plugin)) names.add(plugin.getName());
        }
        return String.join(", ",names);
    }
    @Subscribe public void onGameTick(GameTick event)
    {
        menuRanks.clear();
        evaluationsRemaining=256;
        lastTickNanos=System.nanoTime();
        if(alerts != null) alerts.tick();
        if (client.getGameState() != GameState.LOGGED_IN) { clear(); status="Log in to see nearby loot"; return; }
        groundAllowed = config.lootOverlay();
        FilterIcons.Prepared next = filters.snapshot();
        if (next == null) { refreshSettings(); status = "Waiting for a valid loot filter"; return; }
        boolean starting = !running;
        long overrideVersion = preferences == null ? 0 : preferences.revision();
        if (overrideVersion != preferencesRevision) applyOverrides();
        if (next != prepared) { for (Entry e : entries.values()) { e.facts = null; beamOff(e); } prepared = next; }
        int nextAccount=client.getVarbitValue(net.runelite.api.gameval.VarbitID.IRONMAN);
        if(accountType!=nextAccount){accountType=nextAccount;for(Entry e:entries.values())e.facts=null;}
        running = true;
        WorldView localView=client.getLocalPlayer()==null?null:client.getLocalPlayer().getWorldView();
        int nextView=localView==null?Integer.MIN_VALUE:localView.getId();
        if (starting || nextView!=localViewId) {
            seed(client.getTopLevelWorldView());
            if (localView != client.getTopLevelWorldView()) seed(localView);
            localViewId=nextView;
        }
        for (Entry e : entries.values())
        {
            update(e, false);
            if (e.pendingDrop && e.display != null && client.getTickCount() > e.spawnTick)
            { e.pendingDrop = false; if(alerts != null) alerts.drop(e, prepared); }
            if(alerts != null && config.despawnWarnings() && e.display != null) alerts.despawn(e, LootResolution.secondsRemaining(e.item.getDespawnTime(), client.getTickCount()));
        }
        publishRows();
        updateBeams();
        String overlap=groundAllowed?competing():"";
        status = (groundAllowed ? "Ground display on · " : "Ground display off · ") + entries.size() + " items tracked" + (entries.size() >= MAX_ITEMS ? " (tracking limit reached)" : "")
            + (overlap.isEmpty()?"":". Possible duplicate labels: "+overlap+" is also active.");
    }
    private void seed(WorldView view)
    {
        if (view == null || view.getScene() == null) return;
        if(view.getScene().getTiles() == null) return;
        for (Tile[][] plane : view.getScene().getTiles()) for (Tile[] row : plane) for (Tile tile : row) {
            if (tile != null && tile.getGroundItems() != null) for (TileItem item : tile.getGroundItems()) add(tile, item, false);
        }
    }
    private void add(Tile tile, TileItem item, boolean alert)
    {
        menuRanks.remove(tile);
        if (!running || entries.containsKey(item) || entries.size() >= MAX_ITEMS) return;
        Entry entry = new Entry(tile, item); entry.key = ++sequence; entry.spawnTick = client.getTickCount(); entry.pendingDrop = alert; entries.put(item, entry); update(entry, false);
    }
    @Subscribe public void onItemSpawned(ItemSpawned e) { add(e.getTile(), e.getItem(), true); }
    @Subscribe(priority=1) public void onMenuOpened(MenuOpened e) { menuRanks.clear(); updateMenu(e); }
    @Subscribe(priority=-10) public void onPostMenuSort(PostMenuSort e) { if(!client.isMenuOpen())updateMenu(null); }
    @Subscribe public void onItemDespawned(ItemDespawned e) { menuRanks.remove(e.getTile()); Entry old = entries.remove(e.getItem()); if (old != null) beamOff(old); }
    @Subscribe public void onItemQuantityChanged(ItemQuantityChanged e) { menuRanks.remove(e.getTile()); Entry old = entries.get(e.getItem()); if (old != null) {old.facts=null;update(old, false);} }
    @Subscribe public void onGameStateChanged(GameStateChanged e) { if (e.getGameState() != GameState.LOGGED_IN) clear(); }
    @Subscribe public void onWorldViewUnloaded(WorldViewUnloaded e)
    {
        menuRanks.clear();
        entries.values().removeIf(entry -> { if (entry.tile.getLocalLocation().getWorldView() == e.getWorldView().getId()) { beamOff(entry); return true; } return false; });
    }
    private void update(Entry e, boolean alert)
    {
        if (prepared == null) return;
        if(e.facts==null && evaluationsRemaining<=0)return;
        int quantity=e.item.getQuantity(), ownership=e.item.getOwnership();
        // Quantity/ownership are cheap live facts; refresh GE values in staggered batches.
        if(evaluationsRemaining>0 && (e.facts==null || e.quantity!=quantity || e.ownership!=ownership || (e.key & 63)==(client.getTickCount() & 63))) {
            evaluationsRemaining--;
            ItemFacts facts=filters.facts(e.tile,e.item);e.quantity=quantity;e.ownership=ownership;
            if(!facts.equals(e.facts)){
                e.facts=facts;e.filterDisplay=prepared.program.evaluate(facts);
                e.display=preferences==null?e.filterDisplay:LootPreferences.apply(e.filterDisplay,preferences.action(facts.getId(),e.source));
                e.image=itemImage(e);
            }
        }
        if(e.image==null && e.display.getIcon()!=null)e.image=itemImage(e);
        if(e.display!=null && explicitlyHidesMenu(e))conditionalMenuHiding=true;
    }
    private boolean explicitlyHidesMenu(Entry e){Boolean itemChoice=preferences==null?null:preferences.hideMenu(e.item.getId());return Boolean.TRUE.equals(itemChoice==null?e.display.getHideMenu():itemChoice);}
    private BufferedImage itemImage(Entry e)
    {
        ItemIcons.Assignment manual = icons.find(e.item.getId());
        return manual == null ? image(e.display.getIcon(), e.item.getId()) : icons.image(manual, e.item.getId());
    }
    private void group(List<Entry> pile)
    {
        Map<List<Object>, Entry> groups = new HashMap<>();boolean group=config.groupStacks();
        for(Entry e : pile)
        {
            e.grouped=false; e.renderQuantity=e.facts.getQuantity(); e.renderDespawn=e.item.getDespawnTime();e.renderSpawnTick=e.spawnTick;
            if(!group) continue;
            List<Object> key=Arrays.asList(e.tile,e.item.getId(),e.display,e.source,LootDisplayOptions.ownershipHidden(config,e));
            Entry lead=groups.putIfAbsent(key,e);
            if(lead!=null) {e.grouped=true;lead.renderQuantity+=e.renderQuantity;
                if(e.renderDespawn>0 && (lead.renderDespawn<=0 || e.renderDespawn<lead.renderDespawn)){lead.renderDespawn=e.renderDespawn;lead.renderSpawnTick=e.spawnTick;}}
        }
    }
    private void publishRows()
    {
        conditionalMenuHiding=false;
        List<Entry> nearby = new ArrayList<>();
        Player player=client.getLocalPlayer();LocalPoint point=player==null?null:player.getLocalLocation();WorldView view=player==null?null:player.getWorldView();
        int range=LootAlerts.clamp(config.labelRange(),1,64),world=view==null?Integer.MIN_VALUE:view.getId(),plane=view==null?-1:view.getPlane();
        for(Entry e:entries.values()) {
            if(e.facts!=null && e.display!=null && explicitlyHidesMenu(e))conditionalMenuHiding=true;
            LocalPoint at=e.tile.getLocalLocation();if(e.facts==null || point==null || at==null || at.getWorldView()!=world || e.tile.getPlane()!=plane)continue;
            e.distance=Math.max(Math.abs(point.getX()-at.getX()),Math.abs(point.getY()-at.getY()))/128;if(e.distance<=range)nearby.add(e); }
        Comparator<Entry> byName = Comparator.comparing(e -> e.facts.getName(), String.CASE_INSENSITIVE_ORDER);
        ImprovedLootingConfig.LabelOrder order = config.labelOrder();
        Comparator<Entry> sorting = order == ImprovedLootingConfig.LabelOrder.NAME ? byName
            : order == ImprovedLootingConfig.LabelOrder.DISTANCE ? Comparator.comparingInt(e -> e.distance)
            : order == ImprovedLootingConfig.LabelOrder.TIME ? Comparator.comparingInt(e -> e.item.getDespawnTime() <= 0 ? Integer.MAX_VALUE : e.item.getDespawnTime())
            : Comparator.<Entry>comparingLong(e -> PriceTiers.value(e.facts.getGePrice(), e.facts.getQuantity())).reversed();
        nearby.sort(sorting.thenComparing(byName).thenComparingLong(e -> e.key)); ordered = nearby;
        group(nearby);
        if(config.groupStacks() && order==ImprovedLootingConfig.LabelOrder.VALUE)nearby.sort(Comparator.<Entry>comparingLong(e->PriceTiers.value(e.facts.getGePrice(),e.renderQuantity)).reversed().thenComparing(byName).thenComparingLong(e->e.key));
        if(config.prioritizeLoot())nearby.sort(LootPileOrder.labels(this::labelHidden).thenComparing(byName).thenComparingLong(e->e.key));
        List<LootRow> next = new ArrayList<>();
        for (Entry e : nearby)
        {
            if(next.size() >= 512) break;
            next.add(new LootRow(e.key, e.item.getId(), e.facts.getQuantity(), PriceTiers.value(e.facts.getGePrice(), e.facts.getQuantity()),
                e.distance, LootResolution.secondsRemaining(e.item.getDespawnTime(), client.getTickCount()), e.facts.getName(),
                preferences == null ? "" : preferences.category(e.item.getId()), e.source, null, labelHidden(e), e.image));
        }
        rows = Collections.unmodifiableList(next);
    }
    // RuneLite 1.13.4 ItemStack.getLocation() always returns null. No supported
    // event identifies a specific ground pile's monster source; never infer it.
    private BufferedImage image(BufferedImageProvider icon, int itemId)
    {
        if (icon == null) return null;
        if (icon instanceof BufferedImageProvider.File) return prepared.images.get(((BufferedImageProvider.File)icon).getFilename());
        if (icon instanceof BufferedImageProvider.CurrentItem) return icons.items.getImage(itemId);
        if (icon instanceof BufferedImageProvider.Item) { int id = ((BufferedImageProvider.Item)icon).getItemId(); return id < client.getItemCount() ? icons.items.getImage(id) : null; }
        BufferedImageProvider.Sprite sprite = (BufferedImageProvider.Sprite)icon;
        if (sprite.getSpriteId() == 9000000) return null;
        try { return icons.sprites.getSprite(sprite.getSpriteId(), sprite.getIndex()); } catch (IndexOutOfBoundsException ex) { return null; }
    }
    static Color beamColor(DisplayConfig display,long value,PriceTiers.Setup setup)
    {
        if(display==null || display.isHidden())return null;
        if(display.isShowLootbeam()){Color color=display.getLootbeamColor();return color==null?display.getTextColor():color;}
        if(display.isHideOverlay() || setup==null)return null;
        PriceTiers.Tier tier=setup.tiers[PriceTiers.index(setup,value)];return tier.lootBeam?new Color(tier.color):null;
    }
    private void updateBeams()
    {
        Map<Entry,Color> desired=new LinkedHashMap<>();
        if(groundAllowed && config.lootBeams()){
            PriceTiers.Ready ready=tiers==null?null:tiers.snapshot();PriceTiers.Setup setup=ready==null?null:ready.setup;
            List<Entry> candidates=new ArrayList<>();
            for(Entry e:ordered)if(!e.grouped && !locallyHidden(e) && inRange(e,24) && beamColor(e.display,PriceTiers.value(e.facts.getGePrice(),e.renderQuantity),setup)!=null)candidates.add(e);
            candidates.sort(Comparator.<Entry>comparingLong(e->PriceTiers.value(e.facts.getGePrice(),e.renderQuantity)).reversed().thenComparingLong(e->e.key));
            Set<Tile> selected=Collections.newSetFromMap(new IdentityHashMap<>());
            for(Entry e:candidates){if(selected.add(e.tile)){desired.put(e,beamColor(e.display,PriceTiers.value(e.facts.getGePrice(),e.renderQuantity),setup));if(desired.size()==64)break;}}
        }
        // Remove old owners before creating replacements, including when a grouped pile changes tier.
        for(Entry e:entries.values())if(!desired.containsKey(e) || !Objects.equals(e.beamColor,desired.get(e)))beamOff(e);
        desired.forEach(this::updateBeam);
    }
    private void updateBeam(Entry e,Color color)
    {
        if(e.beam!=null || beamTiles.size()>=64 || beamTiles.contains(e.tile))return;
        ModelData data = client.loadModelData(43330);
        if (data == null) return;
        short hsl = JagexColor.rgbToHSL(color.getRGB(), 1);
        int h = JagexColor.unpackHue(hsl), s = JagexColor.unpackSaturation(hsl), l = JagexColor.unpackLuminance(hsl);
        Model model = data.cloneVertices().cloneColors()
            .recolor(JagexColor.packHSL(25, 6, 64), JagexColor.packHSL(h, Math.max(0, s - 1), l))
            .recolor(JagexColor.packHSL(25, 7, 88), JagexColor.packHSL(h, s, Math.min(l + 24, JagexColor.LUMINANCE_MAX)))
            .light(75 + ModelData.DEFAULT_AMBIENT, 1875 + ModelData.DEFAULT_CONTRAST, ModelData.DEFAULT_X, ModelData.DEFAULT_Y, ModelData.DEFAULT_Z);
        e.beam = new RuneLiteObject(client); e.beam.setModel(model); e.beam.setLocation(e.tile.getLocalLocation(), e.tile.getPlane());
        e.beam.setAnimation(client.loadAnimation(net.runelite.api.gameval.AnimationID.FX_BEAM_IDLE)); e.beam.setActive(true); e.beamColor = color; beamTiles.add(e.tile);
    }
    private void beamOff(Entry e) { if (e.beam != null) { e.beam.setActive(false); beamTiles.remove(e.tile); } e.beam = null; e.beamColor = null; }
    boolean inRange(Entry e, int distance)
    {
        Player player = client.getLocalPlayer(); if (player == null || player.getWorldView() == null || player.getLocalLocation() == null || e.tile.getLocalLocation() == null || player.getWorldView().getId() != e.tile.getLocalLocation().getWorldView() || player.getWorldView().getPlane() != e.tile.getPlane()) return false;
        LocalPoint a = player.getLocalLocation(), b = e.tile.getLocalLocation();
        return Math.abs(a.getX() - b.getX()) <= distance * 128 && Math.abs(a.getY() - b.getY()) <= distance * 128;
    }
}
