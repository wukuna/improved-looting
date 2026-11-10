package com.improvedlooting;

import java.util.*;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.gameval.VarClientID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

@Singleton
final class SpecialLootEvents
{
    private static final String COLLECTION = "New item added to your collection log: ";
    @Inject Client client;
    @Inject LootAlerts alerts;
    private final Set<String> petNames = new HashSet<>();
    private boolean notificationStarted;

    @Subscribe public void onChatMessage(ChatMessage event)
    {
        if (client.getGameState() != GameState.LOGGED_IN
            || event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM) return;
        String message = Text.removeTags(event.getMessage());
        if (message.startsWith(COLLECTION)) collection(message.substring(COLLECTION.length()));
        else if (message.startsWith("You have a funny feeling like you're being followed")
            || message.startsWith("You feel something weird sneaking into your backpack")
            || message.startsWith("You have a funny feeling like you would have been followed"))
            alerts.special("", true, false);
    }
    @Subscribe public void onScriptPreFired(ScriptPreFired event)
    {
        if (client.getGameState() != GameState.LOGGED_IN) return;
        if (event.getScriptId() == ScriptID.NOTIFICATION_START) notificationStarted = true;
        else if (event.getScriptId() == ScriptID.NOTIFICATION_DELAY && notificationStarted)
        {
            notificationStarted = false;
            String title = client.getVarcStrValue(VarClientID.NOTIFICATION_TITLE);
            String body = client.getVarcStrValue(VarClientID.NOTIFICATION_MAIN);
            if (title != null && body != null && Text.removeTags(title).equalsIgnoreCase("Collection log"))
            {
                String clean = Text.removeTags(body);
                if (clean.startsWith("New item:")) collection(clean.substring("New item:".length()));
            }
        }
    }
    void collection(String name)
    {
        name = name.trim();
        if (name.isEmpty() || name.length() > 100) return;
        loadPets(); alerts.special(name, petNames.contains(SpecialDropSounds.itemKey(name)), true);
    }
    private void loadPets()
    {
        if (!petNames.isEmpty()) return;
        EnumComposition pets = client.getEnum(EnumID.PETS);
        if (pets == null || pets.size() > 512) return;
        for (int i = 0; i < pets.size(); i++)
        {
            ItemComposition item = client.getItemDefinition(pets.getIntValue(i));
            if (item != null && item.getName() != null) petNames.add(SpecialDropSounds.itemKey(item.getName()));
        }
    }
    @Subscribe(priority = -1) public void onGameTick(GameTick event)
    {
        if (client.getGameState() == GameState.LOGGED_IN) { alerts.tick(); alerts.flush(); }
    }
    @Subscribe public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING
            || event.getGameState() == GameState.LOGGING_IN || event.getGameState() == GameState.CONNECTION_LOST) clear();
    }
    void clear() { notificationStarted = false; petNames.clear(); alerts.clear(); }
}
