package com.improvedlooting;

import com.google.inject.Provides;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.*;
import net.runelite.client.events.*;
import net.runelite.client.plugins.*;
import net.runelite.client.ui.*;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(name="Improved Looting",internalName="improved-looting",description="Loot filters, nearby browser, rule inspector, labels, beams and alerts",tags={"loot","filters","icons","ground"})
public class ImprovedLootingPlugin extends Plugin
{
    @Inject private ImprovedLootingConfig config;
    @Inject private ReleaseNotesNotice releaseNotes;
    @Inject private ItemIcons icons;
    @Inject private GroundLoot loot;
    @Inject private SpecialLootEvents specialEvents;
    @Inject private GroundLootOverlay overlay;
    @Inject private LootCardsOverlay cards;
    @Inject private LootPreferences preferences;
    @Inject private LootProfiles profiles;
    @Inject private LegacyImport migration;
    @Inject private MenuIconPublisher publisher;
    @Inject private EventBus events;
    @Inject private ClientThread thread;
    @Inject private ClientToolbar toolbar;
    @Inject private OverlayManager overlays;
    @Inject private LootLabelInput labelInput;
    @Inject private net.runelite.client.input.MouseManager mouseManager;
    private ImprovedLootingPanel panel;
    private NavigationButton navigation;
    private volatile boolean active;
    private volatile long lifecycle;
    @Provides ImprovedLootingConfig config(ConfigManager manager) { return manager.getConfig(ImprovedLootingConfig.class); }
    net.runelite.client.util.Filepath soundDirectory() throws java.io.IOException { return getPluginDirectory().joinSegment("tier-sounds"); }
    @Override protected void startUp()
    {
        long token=++lifecycle;active=true;thread.invoke(()->{if(token==lifecycle){loot.clear();specialEvents.clear();overlay.clear();publisher.clear();}});
        icons.filters.reload(); icons.tiers.reload(); icons.tiers.specials.reload(); events.register(specialEvents); events.register(loot); events.register(publisher); overlays.add(overlay); overlays.add(cards);
        events.register(releaseNotes);releaseNotes.start();
        labelInput.start();mouseManager.registerMouseListener(labelInput);
        SwingUtilities.invokeLater(()->{
            if(!active || token!=lifecycle) return;
            closePanel();
            panel=new ImprovedLootingPanel(icons,config,loot,migration,thread,preferences,profiles);
            navigation=NavigationButton.builder().tooltip("Improved Looting").icon(icon()).priority(7).panel(panel).build(); toolbar.addNavigation(navigation);
        });
    }
    @Override protected void shutDown()
    {
        icons.tiers.stop(); icons.tiers.specials.stop(); events.unregister(specialEvents);
        releaseNotes.stop();events.unregister(releaseNotes);
        labelInput.stop();mouseManager.unregisterMouseListener(labelInput);
        long token=++lifecycle;active=false; events.unregister(loot); events.unregister(publisher); overlays.remove(overlay); overlays.remove(cards); thread.invoke(()->{if(token==lifecycle){loot.clear();specialEvents.clear();overlay.clear(); publisher.clear();}});
        SwingUtilities.invokeLater(()->{if(token==lifecycle)closePanel();});
    }
    @Subscribe public void onConfigChanged(ConfigChanged e)
    {
        if(!ImprovedLootingConfig.GROUP.equals(e.getGroup())) return;
        if(ReleaseNotesNotice.SEEN_KEY.equals(e.getKey())) return;
        labelInput.reset();
        if(LootProfiles.SAVED.equals(e.getKey()) || LootProfiles.UNDO.equals(e.getKey()) || FilterIcons.BACKUP.equals(e.getKey()) || ItemIcons.BACKUP.equals(e.getKey())) return;
        if(FilterIcons.KEY.equals(e.getKey())) icons.filters.reload();
        if(PriceTiers.KEY.equals(e.getKey())) icons.tiers.reload();
        if(SpecialDropSounds.KEY.equals(e.getKey())) icons.tiers.specials.reload();
        long token=lifecycle;
        thread.invoke(()->{if(token==lifecycle && active){
            if(LootPreferences.KEY.equals(e.getKey()))loot.refreshOverrides();
            else {loot.refreshSettings();overlay.clear();}
            publisher.clear();
        }});
    }
    @Subscribe public void onProfileChanged(ProfileChanged e)
    {
        labelInput.reset();
        long token=++lifecycle;
        icons.filters.reload(); icons.tiers.reload(); icons.tiers.specials.reload(); thread.invoke(()->{if(token==lifecycle){loot.clear();specialEvents.clear();overlay.clear();publisher.clear();}});
        SwingUtilities.invokeLater(()->{if(token!=lifecycle)return;closePanel();if(active) rebuildPanel();});
    }
    @Subscribe public void onGameStateChanged(net.runelite.api.events.GameStateChanged e)
    {
        if(e.getGameState()!=net.runelite.api.GameState.LOGGED_IN){overlay.clear();labelInput.reset();}
    }
    private void closePanel(){if(panel!=null){panel.close();panel=null;}if(navigation!=null){toolbar.removeNavigation(navigation);navigation=null;}}
    private void rebuildPanel() { panel=new ImprovedLootingPanel(icons,config,loot,migration,thread,preferences,profiles); navigation=NavigationButton.builder().tooltip("Improved Looting").icon(icon()).priority(7).panel(panel).build(); toolbar.addNavigation(navigation); }
    static BufferedImage icon()
    {
        BufferedImage image=new BufferedImage(24,24,BufferedImage.TYPE_INT_ARGB); Graphics2D g=image.createGraphics();
        try {g.setColor(new Color(231,181,70));g.fillRoundRect(3,8,18,13,4,4);g.setColor(new Color(101,65,28));g.fillRect(3,12,18,2);g.setColor(Color.WHITE);g.fillRect(11,11,3,5);} finally {g.dispose();} return image;
    }
}
