package com.improvedlooting;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ProfileChanged;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ReleaseNotesNoticeTest
{
    private final ReleaseNotesNotice notice=new ReleaseNotesNotice();
    private final Client client=mock(Client.class);
    private final ClientThread thread=mock(ClientThread.class);
    private final ConfigManager config=mock(ConfigManager.class);
    private final ChatMessageManager chat=mock(ChatMessageManager.class);
    private final List<Runnable> pending=new ArrayList<>();
    private String seen;

    @Before public void setup() throws Exception
    {
        inject("client",client); inject("clientThread",thread); inject("configManager",config); inject("chat",chat);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(config.getConfiguration("improvedlooting",ReleaseNotesNotice.SEEN_KEY)).thenAnswer(i -> seen);
        doAnswer(i -> { seen=i.getArgument(2); return null; }).when(config)
            .setConfiguration(eq("improvedlooting"),eq(ReleaseNotesNotice.SEEN_KEY),any(String.class));
        doAnswer(i -> { pending.add(i.getArgument(0)); return null; }).when(thread).invokeLater(any(Runnable.class));
    }

    @Test public void printsOneBulletedUpdateOnceAcrossLoginAndReenable()
    {
        notice.start(); flush();
        ArgumentCaptor<QueuedMessage> messages=ArgumentCaptor.forClass(QueuedMessage.class);
        verify(chat,times(1)).queue(messages.capture());
        String message=messages.getValue().getRuneLiteFormattedMessage();
        assertTrue(message.contains("multiple custom tier sounds"));
        assertEquals(4,message.split("<br>\\* ",-1).length-1);
        assertFalse(message.contains("Recent updates"));
        assertFalse(message.contains("<lt>br<gt>"));
        assertEquals(ReleaseNotesNotice.RELEASE,seen);
        notice.onGameStateChanged(loggedIn()); flush();
        notice.stop(); notice.start(); flush();
        verify(chat,times(1)).queue(any(QueuedMessage.class));
    }

    @Test public void waitsForLoginAndDoesNotMarkUnshownNotesAsRead()
    {
        when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        notice.start(); flush(); verifyNoInteractions(chat); assertNull(seen);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        notice.onGameStateChanged(loggedIn()); flush();
        verify(chat,times(1)).queue(any(QueuedMessage.class));
    }

    @Test public void currentReceiptSuppressesNoticeButOlderReleaseDoesNot()
    {
        seen=ReleaseNotesNotice.RELEASE; notice.start(); flush(); verifyNoInteractions(chat);
        seen="previous-release"; notice.onProfileChanged(new ProfileChanged()); flush();
        verify(chat,times(1)).queue(any(QueuedMessage.class));
    }

    @Test public void shutdownAndProfileChangeInvalidateOldCallbacks()
    {
        notice.start(); notice.stop(); flush(); verifyNoInteractions(chat); assertNull(seen);
        notice.start(); notice.onProfileChanged(new ProfileChanged()); flush();
        verify(chat,times(1)).queue(any(QueuedMessage.class));
        seen=null; notice.onProfileChanged(new ProfileChanged()); flush();
        verify(chat,times(2)).queue(any(QueuedMessage.class));
    }

    private GameStateChanged loggedIn() { GameStateChanged event=new GameStateChanged(); event.setGameState(GameState.LOGGED_IN); return event; }
    private void flush() { List<Runnable> work=new ArrayList<>(pending); pending.clear(); work.forEach(Runnable::run); }
    private void inject(String name,Object value) throws Exception
    { Field field=ReleaseNotesNotice.class.getDeclaredField(name); field.setAccessible(true); field.set(notice,value); }
}
