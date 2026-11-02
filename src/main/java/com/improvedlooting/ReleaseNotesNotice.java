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

/** Bundled release notes, shown once per release in each RuneLite profile. */
@Singleton
final class ReleaseNotesNotice
{
    static final String RELEASE = "2026-11-01-label-input-colors";
    static final String SEEN_KEY = "releaseNotesShown";
    private static final String[] RECENT = {
        "Simple Loot, Rules and Tools tabs keep the sidebar compact; expand label details, visibility, pile order, style and alerts as needed.",
        "Conditional menu hiding: item choices and name/value/ownership/region rules override the global default for hidden tags.",
        "Alt-click hide/show is more reliable during redraws; highlighted names and color strips keep their highlight color instead of tier effects.",
        "Prioritize valuable loot now also orders left-click Take; fresh ensouled heads use their normal tradeable tier.",
        "Display now offers Only show owned drops, an untradeable minimum-value exception, and an untradeable color picker defaulting to red.",
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
        send("Recent updates (newest first):");
        for (int i = 0; i < Math.min(5, RECENT.length); i++) send((i + 1) + ". " + RECENT[i]);
        configManager.setConfiguration(ImprovedLootingConfig.GROUP, SEEN_KEY, RELEASE);
    }

    private void send(String text)
    {
        String message = new ChatMessageBuilder().append(ChatColorType.NORMAL).append("[")
            .append(ChatColorType.HIGHLIGHT).append("Improved Looting")
            .append(ChatColorType.NORMAL).append("] ").append(text).build();
        chat.queue(QueuedMessage.builder().type(ChatMessageType.CONSOLE)
            .runeLiteFormattedMessage(message).build());
    }

    @Subscribe public void onGameStateChanged(GameStateChanged event)
    {
        if (running && event.getGameState() == GameState.LOGGED_IN) schedule();
    }

    @Subscribe public void onProfileChanged(ProfileChanged event)
    {
        // An old queued callback must not announce against a replacement profile.
        generation++;
        if (running) schedule();
    }
}
