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

@Singleton
final class LootLabelInput extends MouseAdapter
{
    @Inject Client client;
    @Inject ClientThread thread;
    @Inject GroundLoot loot;
    @Inject LootPreferences preferences;
    private volatile Frame displayed=new Frame(Collections.emptyList(),0,0);
    private final List<Hit> frame=new ArrayList<>();
    private volatile long generation;
    private volatile boolean active;
    private long frameGeneration;
    private Hit pressed;
    private int pressX,pressY;
    private long pressGeneration;
    private boolean consumeClick;

    void start(){active=true;reset();}
    void stop(){active=false;reset();}
    synchronized void reset(){generation++;displayed=new Frame(Collections.emptyList(),generation,0);}

    void beginFrame(){frameGeneration=generation;frame.clear();}
    void add(Rectangle bounds,GroundLoot.Entry entry,boolean hidden){frame.add(new Hit(bounds,entry.item.getId(),hidden));}
    synchronized void finishFrame(){if(active && frameGeneration==generation)displayed=new Frame(Collections.unmodifiableList(new ArrayList<>(frame)),generation,System.nanoTime());}

    @Override public MouseEvent mousePressed(MouseEvent event)
    {
        pressed=null;consumeClick=false;
        Frame snapshot=displayed;
        if(!active || event.isConsumed() || event.getButton()!=MouseEvent.BUTTON1 || !event.isAltDown()
            || snapshot.generation!=generation || System.nanoTime()-snapshot.renderedAt>500_000_000L)return event;

        for(int i=snapshot.hits.size()-1;i>=0;i--) {
            Hit hit=snapshot.hits.get(i);if(!hit.bounds.contains(event.getPoint()))continue;
            pressed=hit;pressX=event.getX();pressY=event.getY();pressGeneration=snapshot.generation;
            consumeClick=true;event.consume();break;
        }
        return event;
    }
    @Override public MouseEvent mouseDragged(MouseEvent event)
    {
        if(consumeClick) {
            event.consume();
            if(Math.abs(event.getX()-pressX)>5 || Math.abs(event.getY()-pressY)>5)pressed=null;
        }
        return event;
    }
    @Override public MouseEvent mouseExited(MouseEvent event){pressed=null;return event;}
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
    private static final class Frame {
        final List<Hit> hits;final long generation,renderedAt;
        Frame(List<Hit> hits,long generation,long renderedAt){this.hits=hits;this.generation=generation;this.renderedAt=renderedAt;}
    }
}
