package com.improvedlooting;

import java.awt.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import javax.swing.*;
import net.runelite.client.ui.PluginPanel;

final class ImprovedLootingPanel extends PluginPanel
{
    private final ItemIcons icons;
    private final LegacyImport migration;
    private final java.util.List<Runnable> refreshers=new ArrayList<>();
    private final javax.swing.Timer timer;
    private FilterEditor editor;
    private ItemIconEditor assets;
    private PriceTierEditor tierEditor;
    private LootBrowser browser;
    private LootProfilesEditor presets;
    private boolean syncing,closed;
    final JComboBox<String> page=new JComboBox<>(new String[]{"Display","Style","Rules and imports","Alerts"});
    ImprovedLootingPanel(ItemIcons icons,ImprovedLootingConfig config,GroundLoot loot,LegacyImport migration,net.runelite.client.callback.ClientThread thread,LootPreferences preferences,LootProfiles profiles)
    {
        this.icons=icons; this.migration=migration;
        setLayout(new BorderLayout(0,10)); setBorder(BorderFactory.createEmptyBorder(10,8,10,8));
        JPanel header=new JPanel(new BorderLayout(0,8));header.add(help("<b>Improved Looting</b><br>Beta • your filters, labels and alerts"),BorderLayout.NORTH);header.add(page,BorderLayout.SOUTH);add(header,BorderLayout.NORTH);
        CardLayout pages=new CardLayout();JPanel tabs=new JPanel(pages);
        JPanel rules=column(),display=column(),appearance=column(),alerts=column();
        JLabel status=help(icons.filters.status()); rules.add(status);
        JLabel ruleGround=help(loot.status());rules.add(ruleGround);
        button(rules,"Nearby loot / inspector",()->{if(browser!=null)browser.close();browser=new LootBrowser(loot,icons,thread,preferences);browser.open(this);});
        button(rules,"Presets / full backup",()->{if(presets!=null)presets.close();presets=new LootProfilesEditor(profiles);presets.open(this);});
        button(rules,"Edit rules",()->{ if(editor!=null && editor.isOpen()) { editor.focus(); return; } editor=new FilterEditor(icons); editor.open(this); });
        button(rules,"Import / assets / backups",()->{ if(assets!=null) assets.close(); assets=new ItemIconEditor(icons); assets.open(this); });
        button(rules,"Copy Right-Clicks setup",this::copyLegacy);
        rules.add(help("Copy your existing filter and images without changing the original. Search for items and pick icons in Edit rules."));
        section(display,"Ground items");
        toggle(display,"Show ground labels","lootOverlay",config::lootOverlay);
        JLabel ground=help(loot.status()); display.add(ground);
        choice(display,"Display style","groundStyle",ImprovedLootingConfig.GroundStyle.values(),config::groundStyle);
        toggle(display,"Pictures only (with quantity)","lootCompact",config::lootCompact);
        JComboBox<ImprovedLootingConfig.DetailMode> prices=choice(display,"Prices","priceDisplay",ImprovedLootingConfig.DetailMode.values(),config::priceDisplay);
        JComboBox<ImprovedLootingConfig.DetailMode> timers=choice(display,"Text timer","timerDisplay",ImprovedLootingConfig.DetailMode.values(),config::timerDisplay);
        display.add(help("Hidden never shows that detail. Follow rules uses your filter. Always shows it whenever known. Pictures only suppresses names, prices and text timers."));
        JCheckBox shortPrices=toggle(display,"Short prices (8.3k gp)","cardShortPrices",config::cardShortPrices);
        toggle(display,"Fade picture as loot expires","iconDespawnFade",config::iconDespawnFade);
        display.add(help("Picture fade and text timers are independent. Unknown lifetimes do not fade."));
        section(display,"Visibility");
        number(display,"Minimum value to appear (gp)","minimumLabelValue",config::minimumLabelValue,0,Integer.MAX_VALUE);
        display.add(help("Uses the visible stack's GE value; 0 shows all values. Hold Alt to reveal hidden labels. Alt-click a visible label to hide every copy; Alt-click a hidden label to always show every copy, even below the minimum. Item menu actions are unchanged."));
        button(display,"Nearby loot / hidden labels",()->{if(browser!=null)browser.close();browser=new LootBrowser(loot,icons,thread,preferences);browser.open(this);});
        toggle(display,"Group matching stacks on a tile","groupStacks",config::groupStacks);
        choice(display,"Item order","labelOrder",ImprovedLootingConfig.LabelOrder.values(),config::labelOrder);
        choice(display,"Alignment at the pile","labelAlignment",ImprovedLootingConfig.LabelAlignment.values(),config::labelAlignment);
        number(display,"Range (tiles)","labelRange",config::labelRange,1,64);
        number(display,"Maximum labels","maxLabels",config::maxLabels,10,1000);
        display.add(help("If labels overlap another plugin's labels, disable that other ground display."));
        section(appearance,"Text and pictures - both styles");
        number(appearance,"Text size (0: default)","labelFontSize",config::labelFontSize,0,32);
        number(appearance,"Space between rows (pixels)","labelSpacing",config::labelSpacing,0,16);
        toggle(appearance,"Avoid enlarging pictures","originalImages",config::originalImages);
        JPanel cardStyle=column();section(cardStyle,"Cards only");appearance.add(cardStyle);
        toggle(cardStyle,"Card background","cardBackground",config::cardBackground);
        toggle(cardStyle,"Thin card border","cardBorder",config::cardBorder);
        JSpinner opacity=number(cardStyle,"Background opacity (%)","cardOpacity",config::cardOpacity,0,100);
        JCheckBox autoWidth=toggle(cardStyle,"Fit full item names","cardAutoWidth",config::cardAutoWidth);
        JSpinner manualWidth=number(cardStyle,"Manual width (pixels)","cardWidth",config::cardWidth,120,2048);
        number(cardStyle,"Height (0: fit text)","cardHeight",config::cardHeight,0,96);
        number(cardStyle,"Rows before expanding (0: all)","cardRows",config::cardRows,0,20);
        cardStyle.add(help("Hover a stack to expand it. Automatic width fits the full line inside the game view. Pictures scale with height. Pictures-only cards always fit their contents."));
        JPanel labelStyle=column();section(labelStyle,"Labels only");appearance.add(labelStyle);
        number(labelStyle,"Picture size (pixels)","iconSize",config::iconSize,12,96);
        labelStyle.add(help("Plain labels use the icon, border and background selected by your rules. Pictures only supplies an item icon if no rule selects one."));
        section(appearance,"Price tiers and beams");
        toggle(appearance,"Loot beams","lootBeams",config::lootBeams);
        button(appearance,"Edit seven price tiers",()->{if(tierEditor!=null)tierEditor.close();tierEditor=new PriceTierEditor(icons.tiers,icons.audio);tierEditor.open(this);});
        toggle(appearance,"Tier animations and sounds","priceTiersEnabled",config::priceTiersEnabled);
        appearance.add(help("Tier colors, Bold and Beam switches apply without enabling animations. Beams use the Loot beams master switch. Sounds also need Drop sounds in Alerts."));
        section(rules,"Menu integration");
        toggle(rules,"Connect menu icons","shareMenuIcons",config::shareMenuIcons);
        rules.add(help("Shares rule icons, visibility and colors with compatible Improved Right-Clicks TEST. Alt-click label hiding and the minimum label value only affect this ground display."));
        refreshers.add(()->{
            boolean cards=config.groundStyle()==ImprovedLootingConfig.GroundStyle.CARDS;
            enableControls(cardStyle,cards);enableControls(labelStyle,!cards);
            manualWidth.setEnabled(cards && !config.cardAutoWidth() && !config.lootCompact());
            prices.setEnabled(!config.lootCompact());timers.setEnabled(!config.lootCompact());
            shortPrices.setEnabled(!config.lootCompact() && config.priceDisplay()!=ImprovedLootingConfig.DetailMode.HIDDEN);
            autoWidth.setEnabled(cards && !config.lootCompact());opacity.setEnabled(cards && config.cardBackground());
        });
        toggle(alerts,"Drop notifications","lootNotifications",config::lootNotifications);
        toggle(alerts,"Drop sounds","lootSounds",config::lootSounds);
        toggle(alerts,"Notification cards","notificationCards",config::notificationCards);
        number(alerts,"Sound volume (%)","alertVolume",config::alertVolume,0,100);
        number(alerts,"Sound cooldown (s)","soundCooldown",config::soundCooldown,0,60);
        toggle(alerts,"Despawn warnings","despawnWarnings",config::despawnWarnings);
        number(alerts,"Warning time (s)","despawnSeconds",config::despawnSeconds,1,120);
        number(alerts,"Minimum stack value","despawnMinimum",config::despawnMinimum,0,Integer.MAX_VALUE);
        alerts.add(help("Only new drops requested by your rules can alert. Existing piles are silent. Import custom audio through Assets."));
        tabs.add(scroll(display),"Display");tabs.add(scroll(appearance),"Style");tabs.add(scroll(rules),"Rules and imports");tabs.add(scroll(alerts),"Alerts");add(tabs,BorderLayout.CENTER);
        page.addActionListener(e->pages.show(tabs,(String)page.getSelectedItem()));
        syncing=true;refreshers.forEach(Runnable::run);syncing=false;
        timer=new javax.swing.Timer(500,e->{ if(isShowing()) { syncing=true; refreshers.forEach(Runnable::run); syncing=false; status.setText(html(icons.filters.status())); ground.setText(html(loot.status()));ruleGround.setText(html(loot.status())); }}); timer.start();
    }
    private void copyLegacy()
    {
        Object profile=icons.configs.getProfile();
        new SwingWorker<Map<String,String>,Void>() {
            protected Map<String,String> doInBackground() { return migration.prepare(); }
            protected void done() {
                if(closed) return;
                try {
                    if(!Objects.equals(profile,icons.configs.getProfile())) throw new IllegalArgumentException("Profile changed. Try again in the intended profile.");
                    Map<String,String> copy=get();
                    if(copy.isEmpty()) { JOptionPane.showMessageDialog(ImprovedLootingPanel.this,"No Right-Clicks loot setup exists in this RuneLite profile."); return; }
                    migration.apply(copy); JOptionPane.showMessageDialog(ImprovedLootingPanel.this,"Copied. Your original setup is unchanged.");
                } catch(Exception ex) { JOptionPane.showMessageDialog(ImprovedLootingPanel.this,ex.getCause()==null?ex.getMessage():ex.getCause().getMessage(),"Setup not copied",JOptionPane.WARNING_MESSAGE); }
            }
        }.execute();
    }
    private JCheckBox toggle(JPanel parent,String label,String key,BooleanSupplier value)
    {
        JCheckBox control=new JCheckBox(label,value.getAsBoolean()); parent.add(control);
        refreshers.add(()->control.setSelected(value.getAsBoolean()));
        control.addActionListener(e->{ if(!syncing) icons.configs.setConfiguration(ImprovedLootingConfig.GROUP,key,control.isSelected()); });
        return control;
    }
    private JSpinner number(JPanel parent,String label,String key,IntSupplier value,int min,int max)
    {
        parent.add(new JLabel(label));JSpinner input=new JSpinner(new SpinnerNumberModel(Math.max(min,Math.min(max,value.getAsInt())),min,max,1));parent.add(input);
        input.addChangeListener(e->{if(!syncing) {int n=(Integer)input.getValue();if(key.equals("labelFontSize")&&n>0&&n<8)n=8;icons.configs.setConfiguration(ImprovedLootingConfig.GROUP,key,n);}});
        refreshers.add(()->input.setValue(Math.max(min,Math.min(max,value.getAsInt()))));return input;
    }
    private <T extends Enum<T>> JComboBox<T> choice(JPanel parent,String label,String key,T[] values,Supplier<T> value)
    {
        parent.add(new JLabel(label));JComboBox<T> input=new JComboBox<>(values);input.setSelectedItem(value.get());parent.add(input);
        input.addActionListener(e->{if(!syncing)icons.configs.setConfiguration(ImprovedLootingConfig.GROUP,key,input.getSelectedItem());});
        refreshers.add(()->input.setSelectedItem(value.get()));return input;
    }
    private static void section(JPanel parent,String title){JLabel label=new JLabel(title);label.setFont(label.getFont().deriveFont(Font.BOLD));label.setBorder(BorderFactory.createEmptyBorder(8,0,2,0));parent.add(label);}
    private static void enableControls(Container parent,boolean enabled){for(Component child:parent.getComponents()){child.setEnabled(enabled);if(child instanceof Container)enableControls((Container)child,enabled);}}
    private static JPanel column() { JPanel p=new JPanel(new net.runelite.client.ui.DynamicGridLayout(0,1,0,7)); p.setBorder(BorderFactory.createEmptyBorder(10,2,2,2)); return p; }
    private static JScrollPane scroll(JPanel panel) { JPanel outer=new JPanel(new BorderLayout());outer.add(panel,BorderLayout.NORTH);JScrollPane scroll=new JScrollPane(outer);scroll.setBorder(null);scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);return scroll; }
    private static String html(String text) { return "<html><body style='width:150px'>"+text+"</body></html>"; }
    private static JLabel help(String text) { return new JLabel(html(text)); }
    private void button(JPanel p,String text,Runnable action) { JButton b=new JButton(text); b.addActionListener(e->action.run()); p.add(b); }
    void close() { closed=true; timer.stop(); if(browser!=null)browser.close();if(presets!=null)presets.close(); if(editor!=null) editor.close(); if(assets!=null) assets.close();if(tierEditor!=null)tierEditor.close(); }
}
