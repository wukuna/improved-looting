package com.improvedlooting;

import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.input.MouseAdapter;

/** User Alt-clicks edit only this plugin's label preferences; never game actions. */
@Singleton
final class LootLabelInput extends MouseAdapter
{
    @Inject Client client;
    @Inject ClientThread thread;
    @Inject GroundLoot loot;
    @Inject LootPreferences preferences;
    private volatile List<Hit> hits=Collections.emptyList();
    private final List<Hit> frame=new ArrayList<>();
    private volatile long generation, renderedAt;
    private volatile boolean active;
    private long frameGeneration;
    private Hit pressed;
    private int pressX,pressY;
    private long pressGeneration;
    private boolean consumeClick;

    void start(){active=true;reset();}
    void stop(){active=false;reset();}
    void reset(){generation++;hits=Collections.emptyList();}
    void beginFrame(){frameGeneration=generation;frame.clear();hits=Collections.emptyList();}
    void add(Rectangle bounds,GroundLoot.Entry entry,boolean hidden){frame.add(new Hit(bounds,entry.item.getId(),hidden));}
    void finishFrame(){if(active && frameGeneration==generation){renderedAt=System.nanoTime();hits=Collections.unmodifiableList(new ArrayList<>(frame));}}

    @Override public MouseEvent mousePressed(MouseEvent event)
    {
        pressed=null;consumeClick=false;
        if(!active || event.isConsumed() || event.getButton()!=MouseEvent.BUTTON1 || !event.isAltDown()
            || System.nanoTime()-renderedAt>500_000_000L)return event;
        for(Hit hit:hits)if(hit.bounds.contains(event.getPoint())) {
            pressed=hit;pressX=event.getX();pressY=event.getY();pressGeneration=generation;
            consumeClick=true;event.consume();break;
        }
        return event;
    }
    @Override public MouseEvent mouseReleased(MouseEvent event)
    {
        if(!consumeClick || event.getButton()!=MouseEvent.BUTTON1)return event;
        event.consume();Hit hit=pressed;pressed=null;long token=pressGeneration;
        if(hit!=null && Math.abs(event.getX()-pressX)<=5 && Math.abs(event.getY()-pressY)<=5)
            thread.invokeLater(()->{
                if(active && token==generation && loot.groundAllowed() && client.getGameState()==GameState.LOGGED_IN && !client.isMenuOpen()) {
                    preferences.labelHidden(hit.id,!hit.hidden);
                    reset();
                }
            });
        return event;
    }
    @Override public MouseEvent mouseClicked(MouseEvent event)
    {
        if(consumeClick && event.getButton()==MouseEvent.BUTTON1){event.consume();consumeClick=false;}
        return event;
    }
    private static final class Hit {
        final Rectangle bounds;final int id;final boolean hidden;
        Hit(Rectangle bounds,int id,boolean hidden){this.bounds=new Rectangle(bounds);this.id=id;this.hidden=hidden;}
    }
}
