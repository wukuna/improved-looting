package com.improvedlooting;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ProfileChanged;

@Singleton
final class ReleaseNotesNotice
{
    static final String RELEASE = "2026-11-09-tier-sound-storage";
    static final String SEEN_KEY = "releaseNotesShown";
    private static final String[] UPDATES = {
        "Choose GE or High Alchemy in Loot > Drop tiers & sounds for values, tier colors, sounds and ordering.",
        "Add Special drops sounds for untradeables, clue difficulties, collection logs, pets and individual items.",
        "Choose custom files, existing tier sounds or silence, with pet and collection-log priority.",
        "Keep custom tier sounds on this computer across restarts and profile changes.",
        "Show selected sound filenames and report missing files without clearing your tiers.",
        "Include custom tier sounds in settings exports for another computer."
    };

    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private ConfigManager configManager;
    @Inject private ChatMessageManager chat;
    private volatile boolean running;
    private volatile int generation;

    void start() { running = true; generation++; schedule(); }
    void stop() { running = false; generation++; }

    private void schedule()
    {
        final int scheduledGeneration = generation;
        clientThread.invokeLater(() -> {
            if (running && generation == scheduledGeneration) showIfNeeded();
        });
    }

    private void showIfNeeded()
    {
        if (client.getGameState() != GameState.LOGGED_IN
            || RELEASE.equals(configManager.getConfiguration(ImprovedLootingConfig.GROUP, SEEN_KEY)))
            return;
        send("Updates:<br>* " + String.join("<br>* ", UPDATES));
        configManager.setConfiguration(ImprovedLootingConfig.GROUP, SEEN_KEY, RELEASE);
    }

    private void send(String text)
    {
        String message = new ChatMessageBuilder().append(ChatColorType.NORMAL).append("[")
            .append(ChatColorType.HIGHLIGHT).append("Improved Looting")
            .append(ChatColorType.NORMAL).append("] ").build() + text;
        chat.queue(QueuedMessage.builder().type(ChatMessageType.CONSOLE)
            .runeLiteFormattedMessage(message).build());
    }

    @Subscribe public void onGameStateChanged(GameStateChanged event)
    {
        if (running && event.getGameState() == GameState.LOGGED_IN) schedule();
    }

    @Subscribe public void onProfileChanged(ProfileChanged event)
    {

        generation++;
        if (running) schedule();
    }
}
