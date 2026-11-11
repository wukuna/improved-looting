package com.improvedlooting;

import com.improvedlooting.rs2f.DisplayConfig;
import com.improvedlooting.rs2f.model.BufferedImageProvider;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.runelite.api.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MenuProviderTest
{
    @Test public void duplicateRequestsShareOneLoadAndCancelledConsumersReleaseCapacity()
    {
        Fixture f = new Fixture(); AsyncBufferedImage image = mock(AsyncBufferedImage.class);
        when(f.provider.icons.items.getImage(385)).thenReturn(image);
        List<CompletableFuture<BufferedImage>> waiting = new ArrayList<>();
        for (int i = 0; i < 128; i++) waiting.add(f.supplier(f.request()).get());
        verify(f.provider.icons.items, times(1)).getImage(385);
        waiting.forEach(future -> future.cancel(false));
        ItemIcons.Assignment a = new ItemIcons.Assignment(); a.kind = ItemIcons.Kind.IMAGE; a.itemId = 385;
        when(f.provider.icons.find(385)).thenReturn(a);
        when(f.provider.icons.image(a, 385)).thenReturn(new BufferedImage(16, 16, 2));
        assertNotNull(f.supplier(f.request()).get().join());
    }
    @Test public void expiredRequestsReleaseCapacityAndLateCallbacksCannotCompleteReplacement()
    {
        Fixture f = new Fixture(); long[] now = {0}; f.provider.clock = () -> now[0];
        AsyncBufferedImage image = mock(AsyncBufferedImage.class);
        when(f.provider.icons.items.getImage(anyInt())).thenReturn(image);
        List<Runnable> ready = new ArrayList<>();
        doAnswer(i -> { ready.add(i.getArgument(0)); return null; }).when(image).onLoaded(any(Runnable.class));
        List<CompletableFuture<BufferedImage>> waiting = new ArrayList<>();
        for (int id = 1; id <= 128; id++)
        {
            f.filter("rule(true){icon=Item(" + id + ");}"); waiting.add(f.supplier(f.request()).get());
        }
        assertEquals(128, ready.size());
        now[0] = java.util.concurrent.TimeUnit.SECONDS.toNanos(11);
        f.provider.onClientTick(new net.runelite.api.events.ClientTick());
        assertTrue(waiting.stream().allMatch(CompletableFuture::isDone));
        CompletableFuture<BufferedImage> replacement = f.supplier(f.request()).get();
        ready.get(127).run(); assertFalse(replacement.isDone());
        f.provider.clear(); assertNull(replacement.join());
    }
    static class Fixture
    {
        final MenuIconPublisher provider=new MenuIconPublisher();final Tile tile=mock(Tile.class);final TileItem item=mock(TileItem.class);final WorldView view=mock(WorldView.class);final Scene scene=mock(Scene.class);
        Fixture()
        {
            provider.client=mock(Client.class);provider.thread=mock(ClientThread.class);provider.config=mock(ImprovedLootingConfig.class);provider.icons=mock(ItemIcons.class);provider.icons.filters=mock(FilterIcons.class);provider.icons.items=mock(ItemManager.class);provider.bus=mock(EventBus.class);provider.loot=new GroundLoot();provider.loot.filters=provider.icons.filters;
            when(provider.client.isClientThread()).thenReturn(true);when(provider.client.getItemCount()).thenReturn(1000);when(provider.config.shareMenuIcons()).thenReturn(true);when(provider.icons.filters.snapshot()).thenReturn(new FilterIcons.Prepared());when(provider.client.getWorldView(0)).thenReturn(view);when(view.getScene()).thenReturn(scene);when(scene.getTiles()).thenReturn(new Tile[][][]{{{tile}}});when(tile.getGroundItems()).thenReturn(Arrays.asList(item));when(item.getId()).thenReturn(385);
            doAnswer(i->{((Runnable)i.getArgument(0)).run();return null;}).when(provider.thread).invokeLater(any(Runnable.class));
            filter("rule(true){textColor=\"#00ffff\";icon=CurrentItem();}");when(provider.icons.filters.facts(tile,item)).thenReturn(com.improvedlooting.rs2f.model.ItemFacts.builder().id(385).name("Shark").quantity(1).build());
        }
        void filter(String script){FilterIcons.Prepared filter=new FilterIcons.Prepared();filter.program=com.improvedlooting.rs2f.LootFilter.compile(script);when(provider.icons.filters.snapshot()).thenReturn(filter);}
        Map<String,Object> request(){Map<String,Object> data=new HashMap<>();data.put("worldViewId",0);data.put("x",0);data.put("y",0);data.put("z",0);data.put("itemId",385);provider.onPluginMessage(new PluginMessage("improved-looting","resolved-display-request-v1",data));return data;}
        @SuppressWarnings("unchecked") Supplier<CompletableFuture<BufferedImage>> supplier(Map<String,Object> data){return (Supplier<CompletableFuture<BufferedImage>>)((Map<?,?>)((Map<?,?>)data.get("display")).get("icon")).get("getImage");}
    }
    @Test public void providerIsOptionalAndHiddenItemsRetainVisibilityWithoutAnIcon()
    {
        Fixture f=new Fixture();when(f.provider.config.shareMenuIcons()).thenReturn(false);assertEquals("unavailable",f.request().get("status"));verify(f.provider.icons.filters,never()).facts(any(Tile.class),any(TileItem.class));
        when(f.provider.config.shareMenuIcons()).thenReturn(true);f.filter("rule(true){hidden=true;menuSort=-2;}");Map<?,?> display=(Map<?,?>)f.request().get("display");assertEquals(true,display.get("hidden"));assertEquals(-2,display.get("menuSort"));assertNull(display.get("icon"));verify(f.provider.client,never()).getMenu();
    }
    @Test public void ambiguousSameIdStacksDoNotInventAResolvedMenuDisplay()
    {
        Fixture f=new Fixture();TileItem other=mock(TileItem.class);when(other.getId()).thenReturn(385);when(f.tile.getGroundItems()).thenReturn(Arrays.asList(f.item,other));when(f.provider.icons.filters.facts(f.tile,other)).thenReturn(com.improvedlooting.rs2f.model.ItemFacts.builder().id(385).name("Shark").quantity(2).build());f.filter("rule(quantity:>1){hidden=true;} rule(true){icon=CurrentItem();}");assertEquals("ambiguous",f.request().get("status"));
    }
    @Test public void imagesAreIndependentAndAsyncCompletionCannotSurviveReload()
    {
        Fixture f=new Fixture();AsyncBufferedImage image=mock(AsyncBufferedImage.class);when(image.getWidth()).thenReturn(32);when(image.getHeight()).thenReturn(32);when(f.provider.icons.items.getImage(385)).thenReturn(image);List<Runnable> ready=new ArrayList<>();doAnswer(i->{ready.add(i.getArgument(0));return null;}).when(image).onLoaded(any(Runnable.class));
        Map<String,Object> data=f.request();CompletableFuture<BufferedImage> future=f.supplier(data).get();assertFalse(future.isDone());f.provider.clear();assertTrue(future.isDone());assertNull(future.join());ready.forEach(Runnable::run);assertNull(future.join());
        ItemIcons.Assignment a=new ItemIcons.Assignment();a.kind=ItemIcons.Kind.IMAGE;a.itemId=385;BufferedImage source=new BufferedImage(40,20,2);when(f.provider.icons.find(385)).thenReturn(a);when(f.provider.icons.image(a,385)).thenReturn(source);BufferedImage copy=f.supplier(f.request()).get().join();assertNotSame(source,copy);assertEquals(17,copy.getWidth());assertEquals(40,source.getWidth());
    }
}
