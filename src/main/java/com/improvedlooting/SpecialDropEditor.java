package com.improvedlooting;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import javax.swing.*;
import net.runelite.client.audio.AudioPlayer;
import net.runelite.client.util.Filepath;

final class SpecialDropEditor
{
    private final SpecialDropSounds store;
    private final PriceTiers tiers;
    private final AudioPlayer audio;
    private final Function<Component, Filepath> chooser;
    private final AtomicBoolean playing = new AtomicBoolean();
    private final JTextArea status = new JTextArea(3, 70);
    private final JButton save = new JButton("Save special drops");
    private volatile boolean closed;
    private int busy;
    private final List<JSpinner> volumes = new ArrayList<>();
    private final Map<JSpinner, SpecialDropSounds.Choice> volumeChoices = new LinkedHashMap<>();

    SpecialDropEditor(SpecialDropSounds store, PriceTiers tiers, AudioPlayer audio, Function<Component, Filepath> chooser)
    { this.store = store; this.tiers = tiers; this.audio = audio; this.chooser = chooser; }

    JPanel build(Component owner)
    {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        SpecialDropSounds.Ready current = store.snapshot();
        if (current == null) { root.add(new JLabel(store.status()), BorderLayout.NORTH); return root; }
        SpecialDropSounds.Setup draft = store.copy(current.setup);
        Long profile = ProfileIdentity.current(store.configs); String[] before = {store.raw()};
        root.add(new JLabel("<html><b>Special drops</b><br>Priority: Pet → Collection log → Item override → Clue difficulty → Untradeable → Price tier.<br>Enable Drop sounds. Volume is a percentage of your main sound volume. Silent stops lower-priority sounds.<br>Collection-log alerts need the game's chat or popup notification enabled. Pet/log sounds bypass the ordinary cooldown.</html>"), BorderLayout.NORTH);
        JPanel body = new JPanel(); body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        for (SpecialDropSounds.Kind kind : SpecialDropSounds.Kind.values())
            body.add(row(owner, kind.label, draft.categories.get(kind), null));
        JPanel items = new JPanel(); items.setLayout(new BoxLayout(items, BoxLayout.Y_AXIS));
        for (Map.Entry<String, SpecialDropSounds.Choice> item : draft.items.entrySet())
            addItem(items, owner, draft, item.getKey(), item.getValue());
        JPanel add = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JTextField name = new JTextField(25); name.getAccessibleContext().setAccessibleName("Exact item name");
        JButton addItem = new JButton("Add item override");
        add.add(new JLabel("Exact item name")); add.add(name); add.add(addItem);
        addItem.addActionListener(e -> {
            String value = name.getText().trim();
            if (!LootPreferences.validLabel(value, 100) || value.isBlank()) { status.setText("Enter the exact item name (1–100 characters)."); return; }
            if (draft.items.size() >= 128) { status.setText("Keep at most 128 item overrides."); return; }
            if (draft.items.keySet().stream().anyMatch(key -> SpecialDropSounds.itemKey(key).equals(SpecialDropSounds.itemKey(value))))
            { status.setText("That item already has an override."); return; }
            SpecialDropSounds.Choice choice = new SpecialDropSounds.Choice(); draft.items.put(value, choice);
            addItem(items, owner, draft, value, choice); items.revalidate(); items.repaint(); name.setText("");
        });
        body.add(add); body.add(items);
        JScrollPane scroll = new JScrollPane(body); scroll.getVerticalScrollBar().setUnitIncrement(24);
        root.add(scroll, BorderLayout.CENTER);
        status.setLineWrap(true); status.setWrapStyleWord(true); status.setEditable(false); status.setOpaque(false);
        status.setText(current.status.isEmpty() ? "Save special drops separately; your price tiers are unchanged. Files stay on this computer; settings exports include them. WAV, AIFF or AU, up to 15 seconds / 1 MB each." : current.status);
        JPanel footer = new JPanel(new BorderLayout(8, 8)); footer.add(status, BorderLayout.CENTER); footer.add(save, BorderLayout.EAST); root.add(footer, BorderLayout.SOUTH);
        save.addActionListener(e -> {
            try { for (JSpinner spinner : volumes) { spinner.commitEdit(); volumeChoices.get(spinner).volume = ((Number) spinner.getValue()).intValue(); } }
            catch (java.text.ParseException ex) { error(ex); return; }
            SpecialDropSounds.Setup captured = store.copy(draft); begin();
            new SwingWorker<SpecialDropSounds.Ready, Void>()
            {
                protected SpecialDropSounds.Ready doInBackground() { return store.prepareForSave(captured); }
                protected void done()
                {
                    end(); if (closed) return;
                    try
                    {
                        SpecialDropSounds.Ready ready = get();
                        if (!Objects.equals(profile, ProfileIdentity.current(store.configs)) || !Objects.equals(before[0], store.raw()))
                            throw new IllegalArgumentException("Profile or special drops changed elsewhere. Reopen the editor.");
                        store.save(ready); before[0] = store.raw();
                        status.setText(ready.status.isEmpty() ? "Special drops saved." : "Saved. " + ready.status);
                    }
                    catch (Exception ex) { error(ex); }
                }
            }.execute();
        });
        return root;
    }
    private void addItem(JPanel items, Component owner, SpecialDropSounds.Setup draft, String name, SpecialDropSounds.Choice choice)
    {
        JPanel[] holder = new JPanel[1];
        holder[0] = row(owner, name, choice, () -> { draft.items.remove(name); volumes.removeIf(v -> volumeChoices.get(v) == choice); volumeChoices.values().removeIf(c -> c == choice); items.remove(holder[0]); items.revalidate(); items.repaint(); });
        items.add(holder[0]);
    }
    private JPanel row(Component owner, String label, SpecialDropSounds.Choice choice, Runnable remove)
    {
        JPanel row = new JPanel(new BorderLayout(6, 3));
        row.setBorder(BorderFactory.createTitledBorder(label)); row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 104));
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JComboBox<SpecialDropSounds.Mode> mode = new JComboBox<>(SpecialDropSounds.Mode.values());
        mode.setSelectedItem(choice.mode); mode.getAccessibleContext().setAccessibleName(label + " sound");
        JComboBox<String> tier = new JComboBox<>(new String[]{"Tier 1", "Tier 2", "Tier 3", "Tier 4", "Tier 5", "Tier 6", "Tier 7"});
        tier.setSelectedIndex(choice.tier - 1); tier.getAccessibleContext().setAccessibleName(label + " tier");
        JSpinner volume = new JSpinner(new SpinnerNumberModel(choice.volume, 0, 100, 5));
        volume.getAccessibleContext().setAccessibleName(label + " volume"); volumes.add(volume); volumeChoices.put(volume, choice);
        JButton choose = new JButton(choice.audio == null ? "Choose file" : "Replace file");
        JButton preview = new JButton("Preview"), clear = new JButton("Clear");
        JLabel filename = new JLabel(); filename.getAccessibleContext().setAccessibleName(label + " file");
        Runnable update = () -> {
            tier.setEnabled(choice.mode == SpecialDropSounds.Mode.TIER);
            filename.setText(choice.name == null ? "No custom file" : choice.name); filename.setToolTipText(choice.name);
            choose.setText(choice.audio == null ? "Choose file" : "Replace file");
            preview.setEnabled(choice.mode == SpecialDropSounds.Mode.TIER || choice.mode == SpecialDropSounds.Mode.CUSTOM && choice.audio != null);
        };
        mode.addActionListener(e -> { choice.mode = (SpecialDropSounds.Mode) mode.getSelectedItem(); update.run(); });
        tier.addActionListener(e -> choice.tier = tier.getSelectedIndex() + 1);
        volume.addChangeListener(e -> choice.volume = ((Number) volume.getValue()).intValue());
        choose.addActionListener(e -> {
            Filepath selected = chooser.apply(owner); if (selected == null) return; begin(); choose.setEnabled(false);
            new SwingWorker<String, Void>()
            {
                protected String doInBackground() throws Exception { return FilterSounds.read(selected); }
                protected void done()
                {
                    end(); if (closed) return; choose.setEnabled(true);
                    try { choice.audio = get(); choice.name = selected.getFileName(); choice.mode = SpecialDropSounds.Mode.CUSTOM; mode.setSelectedItem(choice.mode); update.run(); status.setText("Sound ready; save special drops to apply."); }
                    catch (Exception ex) { error(ex); }
                }
            }.execute();
        });
        clear.addActionListener(e -> { choice.audio = null; choice.name = null; choice.mode = SpecialDropSounds.Mode.INHERIT; mode.setSelectedItem(choice.mode); update.run(); });
        preview.addActionListener(e -> {
            if (!playing.compareAndSet(false, true)) return;
            SpecialDropSounds.Mode selectedMode = choice.mode; int selectedTier = choice.tier; String selectedAudio = choice.audio;
            int percent = choice.volume; PriceTiers.Ready price = tiers.snapshot(); preview.setEnabled(false);
            new SwingWorker<Void, Void>()
            {
                protected Void doInBackground() throws Exception
                {
                    byte[] bytes = selectedMode == SpecialDropSounds.Mode.CUSTOM && selectedAudio != null ? store.decode(selectedAudio)
                        : selectedMode == SpecialDropSounds.Mode.TIER && price != null && price.setup.tiers[selectedTier - 1].sound == PriceTiers.Sound.CUSTOM ? price.audio[selectedTier - 1] : null;
                    if (bytes == null) throw new IllegalArgumentException("The selected tier has no custom sound. Filter sound uses the matching drop's rule; Silent plays nothing.");
                    if (!closed) audio.play(new ByteArrayInputStream(bytes), LootAlerts.gain(volume() * percent / 100)); return null;
                }
                protected void done() { playing.set(false); if (closed) return; update.run(); try { get(); } catch (Exception ex) { error(ex); } }
            }.execute();
        });
        controls.add(mode); controls.add(tier); controls.add(new JLabel("Volume %")); controls.add(volume);
        JPanel files = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0)); files.add(choose); files.add(preview); files.add(clear);
        JPanel fileRow = new JPanel(new BorderLayout(6, 0)); fileRow.add(files, BorderLayout.WEST); fileRow.add(filename, BorderLayout.CENTER);
        if (remove != null) { JButton delete = new JButton("Remove"); delete.addActionListener(e -> remove.run()); controls.add(delete); }
        row.add(controls, BorderLayout.NORTH); row.add(fileRow, BorderLayout.SOUTH); update.run(); return row;
    }
    private int volume()
    {
        String raw = store.configs.getConfiguration(ImprovedLootingConfig.GROUP, "alertVolume");
        try { return raw == null ? 80 : LootAlerts.clamp(Integer.parseInt(raw), 0, 100); } catch (NumberFormatException e) { return 80; }
    }
    private void begin() { busy++; save.setEnabled(false); }
    private void end() { busy--; if (!closed) save.setEnabled(busy == 0); }
    private void error(Exception ex) { status.setText("Not saved: " + (ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage())); }
    void close() { closed = true; }
}
