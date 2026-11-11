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

@Singleton
final class LootAlerts
{
    @Inject Client client;
    @Inject ImprovedLootingConfig config;
    @Inject Notifier notifier;
    @Inject AudioPlayer audio;
    @Inject ScheduledExecutorService executor;
    @Inject PriceTiers tiers;
    @Inject SpecialDropSounds specials;
    private final AtomicBoolean playing = new AtomicBoolean();
    private final Map<String, Integer> recent = new LinkedHashMap<>();
    private final Deque<Card> cards = new ArrayDeque<>();
    private final List<Pending> pending = new ArrayList<>();
    private final Map<String, Integer> logEntries = new LinkedHashMap<>();
    private int lastPet = Integer.MIN_VALUE / 2;
    private int lastSound = Integer.MIN_VALUE / 2;
    private int notificationTick=-1, notifications;
    private volatile int generation;
    static final class Card
    {
        final String text; final int expires;
        Card(String text, int expires) { this.text = text; this.expires = expires; }
    }
    static final class Sound
    {
        final byte[] bytes; final Integer effect; final int volume;
        Sound(byte[] bytes, Integer effect, int volume) { this.bytes = bytes; this.effect = effect; this.volume = volume; }
    }
    static final class Pending
    {
        String name;
        final int tick;
        boolean pet, collection, untradeable, dropSettled;
        Sound rule, price;
        Pending(String name, int tick) { this.name = name; this.tick = tick; }
    }
    void clear() { generation++; recent.clear(); cards.clear(); pending.clear(); logEntries.clear(); lastPet = Integer.MIN_VALUE / 2; lastSound = Integer.MIN_VALUE / 2; }
    void tick()
    {
        int tick = client.getTickCount();
        recent.values().removeIf(expiry -> expiry < tick);
        cards.removeIf(card -> card.expires < tick);
        logEntries.values().removeIf(expiry -> expiry < tick);
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
        Sound rule = sound == null ? null : new Sound(sound.getFile() == null ? null : filter.sounds.get(sound.getFile()), sound.getEffect(), 100);
        Sound selected = rule;
        PriceTiers.Ready ready = config.priceTiersEnabled() ? tiers.snapshot() : null;
        if (ready != null)
        {
            int index = PriceTiers.index(ready.setup, LootValues.value(config,entry.facts,entry.facts.getQuantity()));
            PriceTiers.Sound mode = ready.setup.tiers[index].sound;
            if (mode != PriceTiers.Sound.RULE) selected = new Sound(mode == PriceTiers.Sound.CUSTOM ? ready.audio[index] : null, null, 100);
        }
        Pending next = matching(entry.facts.getName());
        if (next == null) { next = new Pending(entry.facts.getName(), tick); add(next); }
        next.dropSettled |= tick > entry.spawnTick;
        next.untradeable = !entry.facts.isTradeable(); next.rule = rule; next.price = selected;
    }
    private Pending matching(String name)
    {
        String key = SpecialDropSounds.itemKey(name);
        for (Pending item : pending) if (SpecialDropSounds.itemKey(item.name).equals(key)) return item;
        return null;
    }
    private void add(Pending next)
    {
        if (pending.size() >= 256)
        {
            Pending ordinary = pending.stream().filter(p -> !p.pet && !p.collection).findFirst().orElse(null);
            if (ordinary == null) return;
            pending.remove(ordinary);
        }
        pending.add(next);
    }
    void special(String name, boolean pet, boolean collection)
    {
        int tick = client.getTickCount();
        String key = SpecialDropSounds.itemKey(name);
        if (collection)
        {
            if (logEntries.containsKey(key)) return;
            if (logEntries.size() >= 256) logEntries.remove(logEntries.keySet().iterator().next());
            logEntries.put(key, tick + 100);
        }
        Pending next = matching(name);
        if (pet)
        {
            Pending priorPet = pending.stream().filter(p -> p.pet && tick - p.tick <= 5).findFirst().orElse(null);
            if (priorPet != null)
            {
                if (next != null && next != priorPet) { priorPet.rule = next.rule; priorPet.price = next.price; priorPet.dropSettled |= next.dropSettled; pending.remove(next); }
                next = priorPet;
                if (!name.isEmpty()) next.name = name;
            }
            else if (tick - lastPet <= 5) return;
            lastPet = tick;
        }
        if (next == null) { next = new Pending(name, tick); add(next); }
        next.pet |= pet; next.collection |= collection;
    }
    Sound resolve(Pending item)
    {
        SpecialDropSounds.Ready special = specials == null ? null : specials.snapshot();
        SpecialDropSounds.Choice choice = special == null ? null : SpecialDropSounds.match(special.setup, item.name, item.pet, item.collection, item.untradeable);
        if (choice == null) return item.price;
        if (choice.mode == SpecialDropSounds.Mode.CUSTOM) return new Sound(special.audio.get(choice.audio), null, choice.volume);
        if (choice.mode == SpecialDropSounds.Mode.TIER)
        {
            PriceTiers.Ready price = tiers == null ? null : tiers.snapshot();
            if (price != null)
            {
                int index = choice.tier - 1;
                if (price.setup.tiers[index].sound == PriceTiers.Sound.CUSTOM) return new Sound(price.audio[index], null, choice.volume);
                if (price.setup.tiers[index].sound == PriceTiers.Sound.RULE && item.rule != null) return new Sound(item.rule.bytes, item.rule.effect, choice.volume);
            }
        }
        return new Sound(null, null, choice.volume);
    }
    void flush()
    {
        int tick = client.getTickCount();
        if (!config.lootSounds()) { pending.clear(); return; }
        if (specials != null && specials.snapshot() == null) return;
        pending.removeIf(p -> tick - p.tick > (p.pet || p.collection ? 100 : 5));
        pending.sort(Comparator.comparingInt(this::priority));
        for (Iterator<Pending> it = pending.iterator(); it.hasNext();)
        {
            Pending next = it.next();
            Sound sound = resolve(next);
            boolean audible = sound != null && (sound.bytes != null || sound.effect != null) && sound.volume > 0 && config.alertVolume() > 0;
            if (!next.dropSettled && tick <= next.tick)
            {
                if ((next.pet || next.collection) && audible) break;
                continue;
            }
            if (!audible) { it.remove(); continue; }
            boolean urgent = next.pet || next.collection;
            if (!urgent && tick - lastSound < Math.ceil(clamp(config.soundCooldown(), 0, 60) / 0.6)) { it.remove(); continue; }
            if (playing.get()) { if (!urgent) it.remove(); continue; }
            play(sound, tick); it.remove();
        }
    }
    private int priority(Pending item)
    {
        if (item.pet) return 0;
        if (item.collection) return 1;
        SpecialDropSounds.Ready special = specials == null ? null : specials.snapshot();
        if (special != null)
        {
            for (Map.Entry<String, SpecialDropSounds.Choice> entry : special.setup.items.entrySet())
                if (entry.getValue().mode != SpecialDropSounds.Mode.INHERIT
                    && SpecialDropSounds.itemKey(entry.getKey()).equals(SpecialDropSounds.itemKey(item.name))) return 2;
            SpecialDropSounds.Kind clue = SpecialDropSounds.clue(item.name);
            if (clue != null && special.setup.categories.get(clue).mode != SpecialDropSounds.Mode.INHERIT) return 3;
            if (item.untradeable && special.setup.categories.get(SpecialDropSounds.Kind.UNTRADEABLE).mode != SpecialDropSounds.Mode.INHERIT) return 4;
        }
        return 5;
    }
    private void play(Sound sound, int tick)
    {
        int volume = clamp(config.alertVolume(), 0, 100) * sound.volume / 100;
        if (sound.bytes != null)
        {
            if (!playing.compareAndSet(false, true)) return;
            byte[] bytes = sound.bytes; int token = generation; float gain = gain(volume);
            lastSound = tick;
            executor.execute(() -> {
                try { if (token == generation && config.lootSounds()) audio.play(new ByteArrayInputStream(bytes), gain); }
                catch (Exception ignored) { }
                finally { playing.set(false); }
            });
        }
        else if (sound.effect != null)
        {
            client.playSoundEffect(sound.effect, volume * 127 / 100);
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
            || LootValues.value(config,entry.facts,entry.facts.getQuantity()) < Math.max(0, config.despawnMinimum())) return;
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
