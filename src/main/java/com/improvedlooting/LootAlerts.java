package com.improvedlooting;

import com.improvedlooting.rs2f.model.SoundProvider;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.client.Notifier;
import net.runelite.client.audio.AudioPlayer;

/** Client-thread alert decisions, bounded history; imported audio plays off the client thread. */
@Singleton
final class LootAlerts
{
    @Inject Client client;
    @Inject ImprovedLootingConfig config;
    @Inject Notifier notifier;
    @Inject AudioPlayer audio;
    @Inject ScheduledExecutorService executor;
    @Inject PriceTiers tiers;
    private final AtomicBoolean playing = new AtomicBoolean();
    private final Map<String, Integer> recent = new LinkedHashMap<>();
    private final Deque<Card> cards = new ArrayDeque<>();
    private int lastSound = Integer.MIN_VALUE / 2;
    private int notificationTick=-1, notifications;
    private volatile int generation;
    static final class Card
    {
        final String text; final int expires;
        Card(String text, int expires) { this.text = text; this.expires = expires; }
    }
    void clear() { generation++; recent.clear(); cards.clear(); lastSound = Integer.MIN_VALUE / 2; }
    void tick()
    {
        int tick = client.getTickCount();
        recent.values().removeIf(expiry -> expiry < tick);
        cards.removeIf(card -> card.expires < tick);
    }
    List<Card> cards() { return new ArrayList<>(cards); }
    void drop(GroundLoot.Entry entry, FilterIcons.Prepared filter)
    {
        if (entry.display == null || entry.display.isHidden() || LootDisplayOptions.ownershipHidden(config,entry)) return;
        int tick = client.getTickCount();
        String key = entry.item.getId() + ":" + entry.tile.getLocalLocation() + ":" + entry.facts.getQuantity();
        if (recent.containsKey(key)) return;
        if (recent.size() >= 256) recent.remove(recent.keySet().iterator().next());
        recent.put(key, tick + 3);
        String message = "Loot: " + entry.facts.getName() + " × " + entry.facts.getQuantity();
        if (entry.display.isNotify())
        {
            if (config.lootNotifications()) notifyBounded(message);
            card(message);
        }
        SoundProvider sound = entry.display.getSound();
        byte[] custom = null;
        PriceTiers.Ready ready = config.priceTiersEnabled() ? tiers.snapshot() : null;
        if (ready != null)
        {
            int index = PriceTiers.index(ready.setup, PriceTiers.value(entry.facts.getGePrice(), entry.facts.getQuantity()));
            PriceTiers.Sound mode = ready.setup.tiers[index].sound;
            if (mode != PriceTiers.Sound.RULE) sound = null;
            if (mode == PriceTiers.Sound.CUSTOM) custom = ready.audio[index];
        }
        if (custom == null && sound != null && sound.getFile() != null) custom = filter.sounds.get(sound.getFile());
        if (!config.lootSounds() || clamp(config.alertVolume(), 0, 100) == 0
            || tick - lastSound < Math.ceil(clamp(config.soundCooldown(), 0, 60) / 0.6)) return;
        if (custom != null)
        {
            if (!playing.compareAndSet(false, true)) return;
            byte[] bytes = custom; int token = generation; float gain = gain(config.alertVolume());
            lastSound = tick;
            executor.execute(() -> {
                try { if (token == generation && config.lootSounds()) audio.play(new ByteArrayInputStream(bytes), gain); }
                catch (Exception ignored) { }
                finally { playing.set(false); }
            });
        }
        else if (sound != null && sound.getEffect() != null)
        {
            client.playSoundEffect(sound.getEffect(), clamp(config.alertVolume(), 0, 100) * 127 / 100);
            lastSound = tick;
        }
    }
    void despawn(GroundLoot.Entry entry, int seconds)
    {
        if (LootDisplayOptions.ownershipHidden(config,entry)) return;
        int threshold = clamp(config.despawnSeconds(), 1, 120);
        if (seconds > threshold) entry.warningArmed = true;
        if (!entry.warningArmed || entry.warned || seconds < 0 || seconds > threshold) return;
        entry.warned = true;
        if (!config.despawnWarnings() || entry.display.isHidden()
            || PriceTiers.value(entry.facts.getGePrice(), entry.facts.getQuantity()) < Math.max(0, config.despawnMinimum())) return;
        String message = entry.facts.getName() + " despawns in " + seconds + "s";
        notifyBounded(message); card(message);
    }
    private void notifyBounded(String message)
    {
        int tick=client.getTickCount();if(notificationTick!=tick){notificationTick=tick;notifications=0;}
        if(notifications++<3)notifier.notify(message);
    }
    private void card(String message)
    {
        if (!config.notificationCards()) return;
        while (cards.size() >= 5) cards.removeFirst();
        cards.addLast(new Card(message, client.getTickCount() + 13));
    }
    static float gain(int percent) { return percent <= 0 ? -80f : (float)(20 * Math.log10(clamp(percent, 1, 100) / 100.0)); }
    static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
