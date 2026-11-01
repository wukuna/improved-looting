package com.improvedlooting;

import java.awt.Canvas;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.*;
import net.runelite.client.callback.ClientThread;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class LootLabelInputTest
{
    @Test public void altClickConsumesCompleteGestureAndTogglesEveryCopyOfTheItem()
    {
        Fixture f=new Fixture();f.frame(false);f.gesture(true,30,30);f.flush();
        assertEquals(Boolean.TRUE,f.input.preferences.labelHidden(1));
        assertTrue(f.runtime.loot.entries().stream().filter(e->e.item.getId()==1).allMatch(f.runtime.loot::labelHidden));
        f.frame(true);f.gesture(true,30,30);f.flush();assertEquals(Boolean.FALSE,f.input.preferences.labelHidden(1));
        assertTrue(f.runtime.loot.entries().stream().noneMatch(f.runtime.loot::labelHidden));
    }
    @Test public void normalClicksMissesAndAltDragsDoNotEditPreferences()
    {
        Fixture f=new Fixture();f.frame(false);f.gesture(false,30,30);f.gesture(true,300,300);f.flush();assertNull(f.input.preferences.labelHidden(1));
        MouseEvent down=f.event(MouseEvent.MOUSE_PRESSED,true,30,30);f.input.mousePressed(down);assertTrue(down.isConsumed());
        f.input.mouseReleased(f.event(MouseEvent.MOUSE_RELEASED,true,60,30));f.flush();assertNull(f.input.preferences.labelHidden(1));
    }
    @Test public void queuedEditsAreCancelledByProfileResetShutdownOrMenuOpening()
    {
        for(int mode=0;mode<3;mode++) {
            Fixture f=new Fixture();f.frame(false);f.gesture(true,30,30);
            if(mode==0)f.input.reset();else if(mode==1)f.input.stop();else when(f.input.client.isMenuOpen()).thenReturn(true);
            f.flush();assertNull(f.input.preferences.labelHidden(1));
        }
    }
    @Test public void newEmptyFrameCannotLeaveInvisibleClickableLabels()
    {
        Fixture f=new Fixture();f.frame(false);f.input.beginFrame();f.input.finishFrame();f.gesture(true,30,30);f.flush();assertNull(f.input.preferences.labelHidden(1));
    }
    @Test public void displayedLabelsRemainClickableWhileNextFrameIsBeingBuilt()
    {
        Fixture f=new Fixture();f.frame(false);f.input.beginFrame();f.gesture(true,30,30);f.flush();
        assertEquals(Boolean.TRUE,f.input.preferences.labelHidden(1));
        f.input.finishFrame();f.gesture(true,30,30);f.flush();
        assertEquals(Boolean.TRUE,f.input.preferences.labelHidden(1));
    }
    @Test public void overlappingLabelsTargetTheLastPaintedItem()
    {
        Fixture f=new Fixture();net.runelite.api.TileItem item=f.runtime.item(2,1,200);
        f.runtime.loot.onItemSpawned(new net.runelite.api.events.ItemSpawned(f.runtime.tile,item));f.runtime.tick(101);
        f.frame(false);f.input.beginFrame();
        for(int id:new int[]{1,2})f.input.add(new Rectangle(20,20,80,20),f.runtime.loot.entries().stream().filter(e->e.item.getId()==id).findFirst().get(),false);
        f.input.finishFrame();f.gesture(true,30,30);f.flush();
        assertNull(f.input.preferences.labelHidden(1));assertEquals(Boolean.TRUE,f.input.preferences.labelHidden(2));
    }
    @Test public void draggingOutAndBackDoesNotToggleAndRepeatedReleaseDoesNotQueueTwice()
    {
        Fixture f=new Fixture();f.frame(false);
        f.input.mousePressed(f.event(MouseEvent.MOUSE_PRESSED,true,30,30));
        f.input.mouseDragged(f.event(MouseEvent.MOUSE_DRAGGED,true,60,30));
        f.input.mouseReleased(f.event(MouseEvent.MOUSE_RELEASED,true,30,30));f.flush();assertNull(f.input.preferences.labelHidden(1));
        f.gesture(true,30,30);f.input.mouseReleased(f.event(MouseEvent.MOUSE_RELEASED,true,30,30));
        assertEquals(1,f.queue.size());
    }
    private static class Fixture {
        final LootRuntimeTest.Fixture runtime=new LootRuntimeTest.Fixture("rule(true){}");
        final LootLabelInput input=new LootLabelInput();final List<Runnable> queue=new ArrayList<>();
        Fixture(){when(runtime.loot.config.lootOverlay()).thenReturn(true);runtime.items.add(runtime.item(1,1,200));runtime.items.add(runtime.item(1,2,200));runtime.tick(100);
            input.client=runtime.loot.client;input.loot=runtime.loot;input.preferences=LootDisplayOptionsTest.preferences(new HashMap<>());runtime.loot.preferences=input.preferences;
            input.thread=mock(ClientThread.class);doAnswer(c->{queue.add(c.getArgument(0));return null;}).when(input.thread).invokeLater(any(Runnable.class));input.start();}
        void frame(boolean hidden){input.beginFrame();input.add(new Rectangle(20,20,80,20),runtime.loot.entries().iterator().next(),hidden);input.finishFrame();}
        MouseEvent event(int type,boolean alt,int x,int y){return new MouseEvent(new Canvas(),type,0,alt?InputEvent.ALT_DOWN_MASK:0,x,y,1,false,MouseEvent.BUTTON1);}
        void gesture(boolean alt,int x,int y){MouseEvent down=event(MouseEvent.MOUSE_PRESSED,alt,x,y);input.mousePressed(down);MouseEvent up=event(MouseEvent.MOUSE_RELEASED,alt,x,y);input.mouseReleased(up);MouseEvent click=event(MouseEvent.MOUSE_CLICKED,alt,x,y);input.mouseClicked(click);assertEquals(down.isConsumed(),up.isConsumed());assertEquals(down.isConsumed(),click.isConsumed());}
        void flush(){new ArrayList<>(queue).forEach(Runnable::run);queue.clear();}
    }
}
