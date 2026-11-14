package com.improvedlooting;

import com.improvedlooting.anchors.AnchorParticipant;
import com.improvedlooting.anchors.OverlayLease;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.ui.overlay.OverlayManager;

@Singleton
final class LootCardsAnchorAdapter implements AnchorParticipant.Target
{
    @Inject private LootCardsOverlay cards;
    @Inject private ImprovedLootingConfig config;
    @Inject private Client client;
    @Inject private ClientThread thread;
    @Inject private EventBus events;
    @Inject private OverlayManager overlays;
    private AnchorParticipant participant;
    private OverlayLease lease;
    void start() {participant=new AnchorParticipant("Improved Looting",events,thread,()->List.of(this));participant.start();}
    void stop() {if(participant!=null)participant.stop();participant=null;}
    public String id() {return "looting.notifications";}
    public String name() {return "Loot notification cards";}
    public Rectangle bounds() {Rectangle result=new Rectangle(cards.getBounds());result.setSize(260,170);return result;}
    public String state() {return !config.notificationCards()?"HIDDEN":client.getGameState()!=GameState.LOGGED_IN?"UNAVAILABLE":"VISIBLE";}
    public boolean available() {return state().equals("VISIBLE");}
    public List<String> aliases() {return List.of(cards.getName());}
    public boolean claim() {if(lease==null)lease=new OverlayLease(cards,null,overlays);return true;}
    public void move(Point point) {lease.move(point);}
    public void release(boolean keep) {if(lease!=null){lease.release(keep,overlays);lease=null;if(keep)overlays.saveOverlay(cards);}}
}
