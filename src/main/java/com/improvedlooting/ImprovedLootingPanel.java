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
        JPanel header=new JPanel(new BorderLayout(0,8));header.add(help("<b>Improved Looting</b><br>This plugin is currently in Beta mode"),BorderLayout.NORTH);header.add(page,BorderLayout.SOUTH);add(header,BorderLayout.NORTH);
        CardLayout pages=new CardLayout();JPanel tabs=new JPanel(pages);
        JPanel rules=column(),display=column(),appearance=column(),alerts=column();
        JLabel status=help(icons.filters.status()); rules.add(status);
        JLabel ruleGround=help(loot.status());rules.add(ruleGround);
        button(rules,"Nearby loot / inspector",()->{if(browser!=null)browser.close();browser=new LootBrowser(loot,icons,thread,preferences);browser.open(this);});
        button(rules,"Presets / full backup",()->{if(presets!=null)presets.close();presets=new LootProfilesEditor(profiles);presets.open(this);});
        button(rules,"Edit rules",()->{ if(editor!=null && editor.isOpen()) { editor.focus(); return; } editor=new FilterEditor(icons); editor.open(this); });
        button(rules,"Import / assets / backups",()->{ if(assets!=null) assets.close(); assets=new ItemIconEditor(icons); assets.open(this); });
        hint(button(rules,"Copy Right-Clicks setup",this::copyLegacy),"Copy your filter and images here; the original setup is preserved.");
        section(display,"Ground items");
        hint(toggle(display,"Show ground labels","lootOverlay",config::lootOverlay),"Show this plugin's ground display. Disable overlapping ground labels in other plugins.");
        JLabel ground=help(loot.status()); display.add(ground);
        choice(display,"Display style","groundStyle",ImprovedLootingConfig.GroundStyle.values(),config::groundStyle);
        JComboBox<ImprovedLootingConfig.CardTheme> cardTheme=choice(display,"Card theme","cardTheme",ImprovedLootingConfig.CardTheme.values(),config::cardTheme);
        hint(cardTheme,"Native: rounded cards. OSRS: square bronze edges and RuneScape lettering. Applies to Cards only.");
        hint(toggle(display,"Pictures only (with quantity)","lootCompact",config::lootCompact),"Show the picture and quantity; hide names, prices and text timers. Supplies an item picture if no rule does.");
        JComboBox<ImprovedLootingConfig.DetailMode> prices=choice(display,"Prices","priceDisplay",ImprovedLootingConfig.DetailMode.values(),config::priceDisplay);
        JComboBox<ImprovedLootingConfig.DetailMode> timers=choice(display,"Text timer","timerDisplay",ImprovedLootingConfig.DetailMode.values(),config::timerDisplay);
        hint(prices,"Hidden: no prices. Follow rules: use your filter. Always: show known values.");
        hint(timers,"Hidden: no text timer. Follow rules: use your filter. Always: show known despawn times.");
        JCheckBox shortPrices=toggle(display,"Short prices (8.3k gp)","cardShortPrices",config::cardShortPrices);
        hint(toggle(display,"Fade picture as loot expires","iconDespawnFade",config::iconDespawnFade),"Fade pictures when the despawn time is known. Independent of the text timer.");
        section(display,"Visibility");
        hint(toggle(display,"Only show owned drops","onlyShowOwnedDrops",config::onlyShowOwnedDrops),"Show your drops and shared Group Ironman loot, using the game's ownership flag. Public/unowned and other-player drops are hidden, including beams and alerts. Hold Alt to reveal hidden labels temporarily.");
        hint(number(display,"Minimum value to appear (gp)","minimumLabelValue",config::minimumLabelValue,0,Integer.MAX_VALUE),"Minimum GE value for the visible stack. 0 shows all values; manually shown items bypass the minimum.");
        hint(toggle(display,"Keep untradeables visible","keepUntradeablesVisible",config::keepUntradeablesVisible),"Untradeable items bypass the minimum value, including items with no GE price. Your ownership filter and explicit hide rules still apply.");
        untradeableColor(display,config);
        JLabel shortcuts=help("Alt: reveal hidden<br>Alt-click: hide/show item");display.add(shortcuts);
        hint(shortcuts,"Alt-click toggles every copy of that item. Showing a hidden item also bypasses the minimum value. Item menu actions are unchanged.");
        button(display,"Nearby loot / hidden labels",()->{if(browser!=null)browser.close();browser=new LootBrowser(loot,icons,thread,preferences);browser.open(this);});
        toggle(display,"Group matching stacks on a tile","groupStacks",config::groupStacks);
        hint(toggle(display,"Prioritize valuable loot","prioritizeLoot",config::prioritizeLoot),"Top to bottom: normal loot by total GE value, bones/ashes, then untradeables. Applies to cards, labels and opened Take menus. Overrides Item order and filter menu sorting while enabled; normal left-click behavior is unchanged.");
        hint(choice(display,"Item order","labelOrder",ImprovedLootingConfig.LabelOrder.values(),config::labelOrder),"Used when Prioritize valuable loot is off.");
        choice(display,"Alignment at the pile","labelAlignment",ImprovedLootingConfig.LabelAlignment.values(),config::labelAlignment);
        number(display,"Range (tiles)","labelRange",config::labelRange,1,64);
        number(display,"Maximum labels","maxLabels",config::maxLabels,10,1000);
        section(appearance,"Text and pictures - both styles");
        number(appearance,"Text size (0: default)","labelFontSize",config::labelFontSize,0,32);
        number(appearance,"Space between rows (pixels)","labelSpacing",config::labelSpacing,0,16);
        toggle(appearance,"Avoid enlarging pictures","originalImages",config::originalImages);
        JPanel cardStyle=column();section(cardStyle,"Cards only");appearance.add(cardStyle);
        toggle(cardStyle,"Card background","cardBackground",config::cardBackground);
        JButton backgroundColor=backgroundColor(cardStyle,config);
        JButton resetColor=new JButton("Use theme background color");cardStyle.add(resetColor);
        resetColor.addActionListener(e->icons.configs.unsetConfiguration(ImprovedLootingConfig.GROUP,"cardBackgroundColor"));
        toggle(cardStyle,"Thin card border","cardBorder",config::cardBorder);
        JSpinner opacity=number(cardStyle,"Background opacity (%)","cardOpacity",config::cardOpacity,0,100);
        JCheckBox autoWidth=toggle(cardStyle,"Fit full item names","cardAutoWidth",config::cardAutoWidth);
        JSpinner manualWidth=number(cardStyle,"Manual width (pixels)","cardWidth",config::cardWidth,120,2048);
        hint(autoWidth,"Fit the full line inside the game view. Pictures-only cards always fit their contents.");
        hint(number(cardStyle,"Height (0: fit text)","cardHeight",config::cardHeight,0,96),"Minimum card height; pictures scale with it. 0 fits the text.");
        hint(number(cardStyle,"Rows before expanding (0: all)","cardRows",config::cardRows,0,20),"Rows shown per pile; hover to expand. 0 shows all rows that fit.");
        JPanel labelStyle=column();section(labelStyle,"Labels only");appearance.add(labelStyle);
        hint(number(labelStyle,"Picture size (pixels)","iconSize",config::iconSize,12,96),"Labels mode only. Rules select the picture, border and background.");
        section(appearance,"Price tiers and beams");
        hint(toggle(appearance,"Loot beams","lootBeams",config::lootBeams),"Show beams enabled by your rules or price tiers.");
        button(appearance,"Edit seven price tiers",()->{if(tierEditor!=null)tierEditor.close();tierEditor=new PriceTierEditor(icons.tiers,icons.audio);tierEditor.open(this);});
        hint(toggle(appearance,"Tier animations and sounds","priceTiersEnabled",config::priceTiersEnabled),"Adds tier effects. Colors, bold and beams work independently. Audio also needs Drop sounds under Alerts.");
        section(rules,"Menu integration");
        hint(toggle(rules,"Connect menu icons","shareMenuIcons",config::shareMenuIcons),"Share rule icons, visibility and colors with compatible Improved Right-Clicks. Alt-click hiding and minimum value stay local to ground labels.");
        refreshers.add(()->{
            boolean cards=config.groundStyle()==ImprovedLootingConfig.GroundStyle.CARDS;
            enableControls(cardStyle,cards);enableControls(labelStyle,!cards);
            cardTheme.setEnabled(cards);
            manualWidth.setEnabled(cards && !config.cardAutoWidth() && !config.lootCompact());
            prices.setEnabled(!config.lootCompact());timers.setEnabled(!config.lootCompact());
            shortPrices.setEnabled(!config.lootCompact() && config.priceDisplay()!=ImprovedLootingConfig.DetailMode.HIDDEN);
            autoWidth.setEnabled(cards && !config.lootCompact());opacity.setEnabled(cards && config.cardBackground());
            backgroundColor.setEnabled(cards && config.cardBackground());resetColor.setEnabled(cards && config.cardBackground() && config.cardBackgroundColor()!=null);
        });
        hint(toggle(alerts,"Drop notifications","lootNotifications",config::lootNotifications),"Notify for new drops requested by rules. Existing piles stay silent.");
        hint(toggle(alerts,"Drop sounds","lootSounds",config::lootSounds),"Play sounds requested by rules or enabled tiers for new drops. Import custom audio through Assets.");
        toggle(alerts,"Notification cards","notificationCards",config::notificationCards);
        number(alerts,"Sound volume (%)","alertVolume",config::alertVolume,0,100);
        number(alerts,"Sound cooldown (s)","soundCooldown",config::soundCooldown,0,60);
        toggle(alerts,"Despawn warnings","despawnWarnings",config::despawnWarnings);
        number(alerts,"Warning time (s)","despawnSeconds",config::despawnSeconds,1,120);
        number(alerts,"Minimum stack value","despawnMinimum",config::despawnMinimum,0,Integer.MAX_VALUE);
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
    private void untradeableColor(JPanel parent,ImprovedLootingConfig config)
    {
        parent.add(new JLabel("Untradeable color"));JButton control=new JButton();parent.add(control);control.setOpaque(true);
        hint(control,"Default color for untradeable names and card accents in both styles. Explicit rule colors take priority; price-tier animations do not replace this color.");
        refreshers.add(()->{
            Color color=config.untradeableColor();if(color==null)color=new Color(0xff5555);
            control.setText(String.format(Locale.ROOT,"Choose: #%06X",color.getRGB()&0xffffff));control.setBackground(color);
            control.setForeground(color.getRed()*299+color.getGreen()*587+color.getBlue()*114>145000?Color.BLACK:Color.WHITE);
        });
        control.addActionListener(e->{
            Object profile=icons.configs.getProfile();Color before=config.untradeableColor();
            Color chosen=JColorChooser.showDialog(this,"Untradeable color",before==null?new Color(0xff5555):before);
            if(chosen!=null&&!closed&&Objects.equals(profile,icons.configs.getProfile())&&Objects.equals(before,config.untradeableColor()))
                icons.configs.setConfiguration(ImprovedLootingConfig.GROUP,"untradeableColor",new Color(chosen.getRGB()));
        });
    }
    private JButton backgroundColor(JPanel parent,ImprovedLootingConfig config)
    {
        parent.add(new JLabel("Background color"));JButton control=new JButton();parent.add(control);control.setOpaque(true);
        hint(control,"Choose a card background color. Opacity is controlled separately.");
        refreshers.add(()->{
            Color color=LootCardPainter.background(config.cardTheme(),config.cardBackgroundColor());
            control.setText(String.format(Locale.ROOT,"Choose: #%06X",color.getRGB()&0xffffff));control.setBackground(color);
            control.setForeground(color.getRed()*299+color.getGreen()*587+color.getBlue()*114>145000?Color.BLACK:Color.WHITE);
        });
        control.addActionListener(e->{
            Object profile=icons.configs.getProfile();Color before=config.cardBackgroundColor();ImprovedLootingConfig.CardTheme theme=config.cardTheme();
            Color chosen=JColorChooser.showDialog(this,"Card background color",LootCardPainter.background(theme,before));
            if(chosen!=null&&!closed&&Objects.equals(profile,icons.configs.getProfile())&&Objects.equals(before,config.cardBackgroundColor())&&theme==config.cardTheme())
                icons.configs.setConfiguration(ImprovedLootingConfig.GROUP,"cardBackgroundColor",new Color(chosen.getRGB()));
        });return control;
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
    private static void hint(JComponent control,String text)
    {
        String tooltip=html(text);control.setToolTipText(tooltip);
        if(control instanceof JSpinner){JComponent editor=((JSpinner)control).getEditor();editor.setToolTipText(tooltip);if(editor instanceof JSpinner.DefaultEditor)((JSpinner.DefaultEditor)editor).getTextField().setToolTipText(tooltip);}
    }
    private JButton button(JPanel p,String text,Runnable action) { JButton b=new JButton(text); b.addActionListener(e->action.run()); p.add(b); return b; }
    void close() { closed=true; timer.stop(); if(browser!=null)browser.close();if(presets!=null)presets.close(); if(editor!=null) editor.close(); if(assets!=null) assets.close();if(tierEditor!=null)tierEditor.close(); }
}
