package com.improvedlooting;

import com.improvedlooting.rs2f.DisplayConfig;
import com.improvedlooting.rs2f.model.BufferedImageProvider;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import javax.inject.*;
import net.runelite.api.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.*;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.util.AsyncBufferedImage;

/** Optional resolved-display provider. Menu mutation belongs entirely to the consumer. */
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
    private final Set<CompletableFuture<BufferedImage>> pending=new HashSet<>();
    void clear(){generation++;for(CompletableFuture<BufferedImage> future:pending)future.complete(null);pending.clear();bus.post(new PluginMessage("improved-looting","filter-reloaded",Collections.emptyMap()));}
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
        out.put("getImage",(Supplier<CompletableFuture<BufferedImage>>)()->{
            CompletableFuture<BufferedImage> future=new CompletableFuture<>();thread.invokeLater(()->{
                if(token!=generation||!config.shareMenuIcons()||pending.size()>=128){future.complete(null);return;}pending.add(future);
                try{
                    if(assignment!=null){if(assignment.kind==ItemIcons.Kind.SPRITE)icons.sprites.getSpriteAsync(assignment.iconId,assignment.spriteIndex,image->complete(future,image,token));else completeWhenReady(future,icons.image(assignment,id),token);}
                    else if(icon instanceof BufferedImageProvider.File)complete(future,filter.images.get(((BufferedImageProvider.File)icon).getFilename()),token);
                    else if(icon instanceof BufferedImageProvider.Sprite){BufferedImageProvider.Sprite sprite=(BufferedImageProvider.Sprite)icon;if(sprite.getSpriteId()==9000000)complete(future,null,token);else icons.sprites.getSpriteAsync(sprite.getSpriteId(),sprite.getIndex(),image->complete(future,image,token));}
                    else{int itemId=icon instanceof BufferedImageProvider.Item?((BufferedImageProvider.Item)icon).getItemId():id;completeWhenReady(future,itemId>=0&&itemId<client.getItemCount()?icons.items.getImage(itemId):null,token);}
                }catch(RuntimeException ex){pending.remove(future);future.complete(null);}
            });return future;});return Collections.unmodifiableMap(out);
    }
    private void completeWhenReady(CompletableFuture<BufferedImage> future,BufferedImage image,long token){if(image instanceof AsyncBufferedImage)((AsyncBufferedImage)image).onLoaded(()->thread.invokeLater(()->complete(future,image,token)));else complete(future,image,token);}
    private void complete(CompletableFuture<BufferedImage> future,BufferedImage image,long token)
    {
        pending.remove(future);if(token!=generation||image==null){future.complete(null);return;}
        BufferedImage copy=new BufferedImage(17,15,BufferedImage.TYPE_INT_ARGB);java.awt.Graphics2D g=copy.createGraphics();
        try{double scale=Math.min(17.0/image.getWidth(),15.0/image.getHeight());int w=Math.max(1,(int)(image.getWidth()*scale)),h=Math.max(1,(int)(image.getHeight()*scale));g.drawImage(image,(17-w)/2,(15-h)/2,w,h,null);}finally{g.dispose();}future.complete(copy);
    }
}
