package com.improvedlooting;

import com.google.gson.Gson;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
final class SpecialDropSounds
{
    static final String KEY = "specialDropSoundsV1";
    enum Kind
    {
        PET("Pets"), COLLECTION_LOG("Collection-log unlocks"), UNTRADEABLE("Untradeables"),
        BEGINNER("Beginner clues"), EASY("Easy clues"), MEDIUM("Medium clues"),
        HARD("Hard clues"), ELITE("Elite clues"), MASTER("Master clues");
        final String label;
        Kind(String label) { this.label = label; }
    }
    enum Mode
    {
        INHERIT("Use next matching sound"), CUSTOM("Custom file"), TIER("Use existing tier sound"), SILENT("Silent");
        final String label;
        Mode(String label) { this.label = label; }
        public String toString() { return label; }
    }
    static final class Choice
    {
        Mode mode = Mode.INHERIT;
        int tier = 1, volume = 100;
        String audio, name;
    }
    static final class Setup
    {
        int version = 1;
        Map<Kind, Choice> categories = new EnumMap<>(Kind.class);
        Map<String, Choice> items = new LinkedHashMap<>();
    }
    static final class Ready
    {
        final Setup setup;
        final Map<String, byte[]> audio;
        final String status;
        Ready(Setup setup, Map<String, byte[]> audio, String status)
        { this.setup = setup; this.audio = audio; this.status = status; }
    }
    @Inject net.runelite.client.config.ConfigManager configs;
    @Inject Gson gson;
    @Inject TierSounds sounds;
    @Inject ScheduledExecutorService executor;
    private volatile Ready current;
    private volatile String status = "Loading special drops…";
    private long generation;

    static Setup defaults()
    {
        Setup setup = new Setup();
        for (Kind kind : Kind.values()) setup.categories.put(kind, new Choice());
        return setup;
    }
    static String itemKey(String name) { return name == null ? "" : name.trim().toLowerCase(Locale.ROOT); }
    static Kind clue(String name)
    {
        String lower = itemKey(name);
        if (!(lower.startsWith("clue scroll (") || lower.startsWith("clue bottle (")
            || lower.startsWith("clue nest (") || lower.startsWith("clue geode (")
            || lower.startsWith("clue scroll box ("))) return null;
        for (Kind kind : Kind.values())
            if (kind.ordinal() >= Kind.BEGINNER.ordinal() && lower.endsWith("(" + kind.name().toLowerCase(Locale.ROOT) + ")")) return kind;
        return null;
    }
    Setup copy(Setup setup) { return gson.fromJson(gson.toJson(setup), Setup.class); }
    private Setup settings(String raw)
    {
        if (raw == null || raw.isBlank()) return defaults();
        if (raw.length() > 12000000) throw new IllegalArgumentException("Special-drop settings exceed 12 MB.");
        try { return gson.fromJson(raw, Setup.class); }
        catch (com.google.gson.JsonParseException e) { throw new IllegalArgumentException("Invalid special-drop settings.", e); }
    }
    Ready prepare(Setup input, boolean allowMissing)
    {
        Setup setup = copy(input);
        if (setup == null || setup.version != 1 || setup.categories == null || setup.items == null
            || setup.items.size() > 128 || setup.categories.size() > Kind.values().length || setup.categories.containsKey(null))
            throw new IllegalArgumentException("Invalid special drops; keep at most 128 item overrides.");
        for (Kind kind : Kind.values()) setup.categories.putIfAbsent(kind, new Choice());
        Map<String, Choice> all = new LinkedHashMap<>();
        setup.categories.forEach((kind, choice) -> all.put(kind.label, choice));
        Set<String> names = new HashSet<>();
        setup.items.forEach((name, choice) -> {
            if (!LootPreferences.validLabel(name, 100) || name.isBlank() || !names.add(itemKey(name)))
                throw new IllegalArgumentException("Use unique, exact item names of 1–100 characters.");
            all.put("Item: " + name, choice);
        });
        Map<String, byte[]> audio = new HashMap<>();
        List<String> missing = new ArrayList<>();
        long total = 0;
        for (Map.Entry<String, Choice> entry : all.entrySet())
        {
            Choice choice = entry.getValue();
            if (choice == null || choice.mode == null || choice.tier < 1 || choice.tier > 7
                || choice.volume < 0 || choice.volume > 100 || choice.name != null
                && (choice.name.length() > 255 || choice.name.chars().anyMatch(c -> c < 32)))
                throw new IllegalArgumentException("Invalid sound choice: " + entry.getKey());
            if (choice.audio != null && !audio.containsKey(choice.audio))
            {
                try { byte[] data = decode(choice.audio); audio.put(choice.audio, data); total += data.length; }
                catch (IllegalArgumentException e)
                {
                    if (!allowMissing || !TierSounds.validReference(choice.audio)) throw e;
                    missing.add(entry.getKey());
                }
            }
            if (choice.mode == Mode.CUSTOM && choice.audio == null)
                throw new IllegalArgumentException("Choose a sound file for " + entry.getKey() + ".");
        }
        if (total > 8000000) throw new IllegalArgumentException("Special-drop sounds exceed 8 MB combined.");
        return new Ready(setup, audio, missing.isEmpty() ? "" : "Missing sound files: " + String.join(", ", missing)
            + ". Choices are retained; replace the files or import a complete settings export.");
    }
    byte[] decode(String value) { return TierSounds.reference(value) ? sounds.read(value) : FilterSounds.decode(value); }
    private Collection<Choice> choices(Setup setup)
    {
        List<Choice> all = new ArrayList<>(setup.categories.values()); all.addAll(setup.items.values()); return all;
    }
    Ready prepareForSave(Setup setup)
    {
        Ready ready = prepare(setup, true);
        Map<String, byte[]> stored = new HashMap<>();
        for (Choice choice : choices(ready.setup))
        {
            byte[] data = ready.audio.get(choice.audio);
            if (data != null) { choice.audio = sounds.store(data); stored.put(choice.audio, data); }
        }
        return new Ready(ready.setup, stored, ready.status);
    }
    Ready parse(String raw) { return prepare(settings(raw), false); }
    String stored(String raw) { return raw == null || raw.isBlank() ? raw : gson.toJson(prepareForSave(settings(raw)).setup); }
    String portable(String raw)
    {
        if (raw == null || raw.isBlank()) return raw;
        Ready ready = parse(raw);
        for (Choice choice : choices(ready.setup))
            if (choice.audio != null) choice.audio = Base64.getEncoder().encodeToString(ready.audio.get(choice.audio));
        return gson.toJson(ready.setup);
    }
    synchronized void reload()
    {
        long token = ++generation; Long profile = ProfileIdentity.current(configs); String raw = raw();
        current = null; status = "Loading special drops…";
        executor.execute(() -> {
            Ready next = null; String message;
            try { next = prepare(settings(raw), true); message = next.status; }
            catch (IllegalArgumentException e) { message = "Special drops unavailable: " + e.getMessage(); }
            synchronized (this)
            {
                if (token != generation || !Objects.equals(profile, ProfileIdentity.current(configs)) || !Objects.equals(raw, raw())) return;
                current = next; status = message;
            }
        });
    }
    synchronized void save(Ready ready)
    {
        for (Choice choice : choices(ready.setup))
            if (choice.audio != null && !TierSounds.validReference(choice.audio)) throw new IllegalArgumentException("Store sound files before saving.");
        configs.setConfiguration(ImprovedLootingConfig.GROUP, KEY, gson.toJson(ready.setup));
        generation++; current = ready; status = ready.status;
    }
    synchronized void stop() { generation++; current = null; }
    Ready snapshot() { return current; }
    String status() { return status; }
    String raw() { return configs.getConfiguration(ImprovedLootingConfig.GROUP, KEY); }
    static Choice match(Setup setup, String name, boolean pet, boolean collection, boolean untradeable)
    {
        List<Choice> candidates = new ArrayList<>();
        if (pet) candidates.add(setup.categories.get(Kind.PET));
        if (collection) candidates.add(setup.categories.get(Kind.COLLECTION_LOG));
        for (Map.Entry<String, Choice> item : setup.items.entrySet())
            if (itemKey(item.getKey()).equals(itemKey(name))) { candidates.add(item.getValue()); break; }
        Kind clue = clue(name);
        if (clue != null) candidates.add(setup.categories.get(clue));
        if (untradeable) candidates.add(setup.categories.get(Kind.UNTRADEABLE));
        for (Choice choice : candidates) if (choice != null && choice.mode != Mode.INHERIT) return choice;
        return null;
    }
}
