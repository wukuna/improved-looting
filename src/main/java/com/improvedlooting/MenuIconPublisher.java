package com.improvedlooting;

import com.improvedlooting.rs2f.DisplayConfig;
import com.improvedlooting.rs2f.model.BufferedImageProvider;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import javax.inject.*;
import net.runelite.api.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.*;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.util.AsyncBufferedImage;

@Singleton
final class MenuIconPublisher
{
    @Inject Client client;
    @Inject ClientThread thread;
    @Inject ItemIcons icons;
    @Inject GroundLoot loot;
    @Inject ImprovedLootingConfig config;
    @Inject EventBus bus;
    private long generation;
    private static final int MAX_PENDING = 128;
    private static final long TIMEOUT = TimeUnit.SECONDS.toNanos(10);
    LongSupplier clock = System::nanoTime;
    private final Map<String, Request> pending = new HashMap<>();
    private static final class Request
    {
        final String key;
        final long generation, started;
        final Set<CompletableFuture<BufferedImage>> waiting = new HashSet<>();
        Request(String key, long generation, long started)
        {
            this.key = key; this.generation = generation; this.started = started;
        }
    }
    void clear()
    {
        generation++;
        List<Request> requests = new ArrayList<>(pending.values());
        pending.clear();
        for (Request request : requests) finish(request, null);
        bus.post(new PluginMessage("improved-looting", "filter-reloaded", Collections.emptyMap()));
    }
    @Subscribe public void onClientTick(net.runelite.api.events.ClientTick event) { expire(); }
    private void expire()
    {
        long now = clock.getAsLong();
        for (Request request : new ArrayList<>(pending.values()))
        {
            request.waiting.removeIf(CompletableFuture::isDone);
            if (request.waiting.isEmpty() || now - request.started >= TIMEOUT)
            {
                pending.remove(request.key, request);
                finish(request, null);
            }
        }
    }
    private void finish(Request request, BufferedImage image)
    {
        List<CompletableFuture<BufferedImage>> waiting = new ArrayList<>(request.waiting);
        request.waiting.clear();
        for (CompletableFuture<BufferedImage> future : waiting) future.complete(image);
    }
    @Subscribe public void onPluginMessage(PluginMessage event)
    {
        if(!"improved-looting".equals(event.getNamespace())||!client.isClientThread())return;
        if("provider-status-v1".equals(event.getName())){event.getData().put("enabled",config.shareMenuIcons() && icons.filters.snapshot()!=null);return;}
        if(!"resolved-display-request-v1".equals(event.getName()))return;
        Map<String,Object> data=event.getData();data.put("status","unavailable");
        if(!config.shareMenuIcons()||icons.filters.snapshot()==null)return;
        for(String key:Arrays.asList("worldViewId","x","y","z","itemId"))if(!(data.get(key) instanceof Integer))return;
        WorldView view=client.getWorldView((Integer)data.get("worldViewId"));if(view==null||view.getScene()==null)return;
        int x=(Integer)data.get("x")-view.getBaseX(),y=(Integer)data.get("y")-view.getBaseY(),z=(Integer)data.get("z"),id=(Integer)data.get("itemId");
        Tile[][][] tiles=view.getScene().getTiles();if(tiles==null||z<0||z>=tiles.length||tiles[z]==null||x<0||x>=tiles[z].length||tiles[z][x]==null||y<0||y>=tiles[z][x].length)return;
        Tile tile=tiles[z][x][y];if(tile==null||tile.getGroundItems()==null)return;
        DisplayConfig chosen=null;
        for(TileItem item:tile.getGroundItems())if(item.getId()==id){DisplayConfig next=loot.resolved(tile,item);if(next==null)return;if(chosen!=null&&!equivalent(chosen,next)){data.put("status","ambiguous");return;}chosen=next;}
        if(chosen==null)return;
        Map<String,Object> display=new HashMap<>();display.put("hidden",chosen.isHidden());display.put("hideOverlay",chosen.isHideOverlay());display.put("menuSort",chosen.getMenuSort());display.put("menuTextColor",chosen.getMenuTextColor());
        if(config.prioritizeLoot())display.put("menuSort",loot.menuRank(tile,id,chosen.getMenuSort()));
        if(!chosen.isHideOverlay())display.put("icon",descriptor(chosen,id));
        data.put("display",Collections.unmodifiableMap(display));data.put("status","ok");
    }
    static boolean equivalent(DisplayConfig a,DisplayConfig b){return a.isHidden()==b.isHidden()&&a.isHideOverlay()==b.isHideOverlay()&&a.getMenuSort()==b.getMenuSort()&&Objects.equals(a.getMenuTextColor(),b.getMenuTextColor())&&Objects.equals(a.getIcon(),b.getIcon());}
    private Map<String,Object> descriptor(DisplayConfig display,int id)
    {
        ItemIcons.Assignment assignment=icons.find(id);BufferedImageProvider icon=display.getIcon();if(assignment==null&&icon==null)return null;
        Map<String,Object> out=new HashMap<>();boolean current=assignment!=null?assignment.kind==ItemIcons.Kind.CURRENT_ITEM||assignment.kind==ItemIcons.Kind.ITEM&&assignment.iconId==id:icon instanceof BufferedImageProvider.CurrentItem||icon instanceof BufferedImageProvider.Item&&((BufferedImageProvider.Item)icon).getItemId()==id;
        if(current){out.put("type","current-item");out.put("id",id);out.put("quantity",1);}
        else{out.put("type","file");out.put("filename","improved-looting/"+generation+"/"+id+"/"+(assignment==null?icon.toString():assignment.key()));}
        long token=generation;FilterIcons.Prepared filter=icons.filters.snapshot();
        String key = id + "/" + (assignment == null ? icon.toString() : assignment.key());
        out.put("getImage",(Supplier<CompletableFuture<BufferedImage>>)()->{
            CompletableFuture<BufferedImage> future=new CompletableFuture<>();thread.invokeLater(()->{
                expire();
                if(future.isDone())return;
                if(token!=generation||!config.shareMenuIcons()){future.complete(null);return;}
                Request existing = pending.get(key);
                if(existing != null)
                {
                    if(existing.waiting.size() < MAX_PENDING) existing.waiting.add(future);
                    else future.complete(null);
                    return;
                }
                if(pending.size() >= MAX_PENDING){future.complete(null);return;}
                Request request = new Request(key, token, clock.getAsLong());
                request.waiting.add(future);
                pending.put(key, request);
                try{
                    if(assignment!=null){if(assignment.kind==ItemIcons.Kind.SPRITE)icons.sprites.getSpriteAsync(assignment.iconId,assignment.spriteIndex,image->thread.invokeLater(()->complete(request,image)));else completeWhenReady(request,icons.image(assignment,id));}
                    else if(icon instanceof BufferedImageProvider.File)complete(request,filter.images.get(((BufferedImageProvider.File)icon).getFilename()));
                    else if(icon instanceof BufferedImageProvider.Sprite){BufferedImageProvider.Sprite sprite=(BufferedImageProvider.Sprite)icon;if(sprite.getSpriteId()==9000000)complete(request,null);else icons.sprites.getSpriteAsync(sprite.getSpriteId(),sprite.getIndex(),image->thread.invokeLater(()->complete(request,image)));}
                    else{int itemId=icon instanceof BufferedImageProvider.Item?((BufferedImageProvider.Item)icon).getItemId():id;completeWhenReady(request,itemId>=0&&itemId<client.getItemCount()?icons.items.getImage(itemId):null);}
                }catch(RuntimeException ex){complete(request,null);}
            });return future;});return Collections.unmodifiableMap(out);
    }
    private void completeWhenReady(Request request,BufferedImage image){if(image instanceof AsyncBufferedImage)((AsyncBufferedImage)image).onLoaded(()->thread.invokeLater(()->complete(request,image)));else complete(request,image);}
    private void complete(Request request,BufferedImage image)
    {
        if(!pending.remove(request.key, request))return;
        if(request.generation!=generation||!config.shareMenuIcons()||clock.getAsLong()-request.started>=TIMEOUT||image==null){finish(request,null);return;}
        BufferedImage copy=new BufferedImage(17,15,BufferedImage.TYPE_INT_ARGB);java.awt.Graphics2D g=copy.createGraphics();
        try{double scale=Math.min(17.0/image.getWidth(),15.0/image.getHeight());int w=Math.max(1,(int)(image.getWidth()*scale)),h=Math.max(1,(int)(image.getHeight()*scale));g.drawImage(image,(17-w)/2,(15-h)/2,w,h,null);}finally{g.dispose();}finish(request,copy);
    }
}
