package com.improvedlooting;

import net.runelite.client.config.*;

/** The side panel is the single home for these controls. */
@ConfigGroup(ImprovedLootingConfig.GROUP)
public interface ImprovedLootingConfig extends Config
{
    String GROUP="improvedlooting";
    @ConfigItem(keyName="priceTiersEnabled",name="Price tier effects",description="Configured in the side panel",hidden=true)
    default boolean priceTiersEnabled() { return false; }
    @ConfigItem(keyName="lootOverlay",name="Ground labels",description="Configured in the side panel",hidden=true)
    default boolean lootOverlay() { return false; }
    @ConfigItem(keyName="lootBeams",name="Loot beams",description="Configured in the side panel",hidden=true)
    default boolean lootBeams() { return true; }
    @ConfigItem(keyName="lootCompact",name="Compact pictures",description="Configured in the side panel",hidden=true)
    default boolean lootCompact() { return false; }
    @ConfigItem(keyName="lootReveal",name="Alt reveals labels",description="Configured in the side panel",hidden=true)
    default boolean lootReveal() { return true; }
    @ConfigItem(keyName="lootNotifications",name="Notifications",description="Configured in the side panel",hidden=true)
    default boolean lootNotifications() { return false; }
    @ConfigItem(keyName="lootSounds",name="Sounds",description="Configured in the side panel",hidden=true)
    default boolean lootSounds() { return false; }
    @ConfigItem(keyName="shareMenuIcons",name="Share menu icons",description="Configured in the side panel",hidden=true)
    default boolean shareMenuIcons() { return false; }

    enum LabelOrder { VALUE, DISTANCE, NAME, TIME }
    enum LabelAlignment { CENTER, LEFT, RIGHT }
    enum GroundStyle { CARDS, LABELS }
    enum DetailMode {
        HIDDEN("Hidden"), RULES("Follow rules"), ALWAYS("Always");
        private final String label;
        DetailMode(String label){this.label=label;}
        @Override public String toString(){return label;}
    }
    @ConfigItem(keyName="priceDisplay",name="Item prices",description="Configured in the side panel",hidden=true)
    default DetailMode priceDisplay(){return alwaysShowValue()?DetailMode.ALWAYS:DetailMode.RULES;}
    @ConfigItem(keyName="timerDisplay",name="Text timer",description="Configured in the side panel",hidden=true)
    default DetailMode timerDisplay(){return alwaysShowTimer()?DetailMode.ALWAYS:DetailMode.HIDDEN;}
    @ConfigItem(keyName="minimumLabelValue",name="Minimum value to appear",description="Configured in the side panel",hidden=true)
    default int minimumLabelValue(){return 0;}
    @ConfigItem(keyName="groundStyle",name="Ground style",description="Configured in the side panel",hidden=true)
    default GroundStyle groundStyle() { return GroundStyle.CARDS; }
    @ConfigItem(keyName="iconDespawnFade",name="Despawn picture fade",description="Configured in the side panel",hidden=true)
    default boolean iconDespawnFade() { return true; }
    @ConfigItem(keyName="cardBackground",name="Card background",description="Configured in the side panel",hidden=true)
    default boolean cardBackground() { return true; }
    @ConfigItem(keyName="cardBorder",name="Thin card border",description="Configured in the side panel",hidden=true)
    default boolean cardBorder() { return true; }
    @ConfigItem(keyName="cardShortPrices",name="Short prices",description="Configured in the side panel",hidden=true)
    default boolean cardShortPrices() { return false; }
    @ConfigItem(keyName="cardOpacity",name="Background opacity",description="Configured in the side panel",hidden=true)
    default int cardOpacity() { return 90; }
    @ConfigItem(keyName="cardRows",name="Rows before expanding",description="Configured in the side panel",hidden=true)
    default int cardRows() { return 4; }
    @ConfigItem(keyName="cardWidth",name="Card width",description="Configured in the side panel",hidden=true)
    default int cardWidth() { return 260; }
    @ConfigItem(keyName="cardAutoWidth",name="Fit full item names",description="Configured in the side panel",hidden=true)
    default boolean cardAutoWidth() { return true; }
    @ConfigItem(keyName="cardHeight",name="Card height",description="Configured in the side panel",hidden=true)
    default int cardHeight() { return 0; }
    @ConfigItem(keyName="labelOrder",name="labelOrder",description="Configured in the side panel",hidden=true)
    default LabelOrder labelOrder() { return LabelOrder.VALUE; }
    @ConfigItem(keyName="labelAlignment",name="labelAlignment",description="Configured in the side panel",hidden=true)
    default LabelAlignment labelAlignment() { return LabelAlignment.CENTER; }
    @ConfigItem(keyName="labelRange",name="labelRange",description="Configured in the side panel",hidden=true)
    default int labelRange() { return 24; }
    @ConfigItem(keyName="maxLabels",name="maxLabels",description="Configured in the side panel",hidden=true)
    default int maxLabels() { return 300; }
    @ConfigItem(keyName="labelSpacing",name="labelSpacing",description="Configured in the side panel",hidden=true)
    default int labelSpacing() { return 2; }
    @ConfigItem(keyName="labelFontSize",name="labelFontSize",description="Configured in the side panel",hidden=true)
    default int labelFontSize() { return 0; }
    @ConfigItem(keyName="iconSize",name="iconSize",description="Configured in the side panel",hidden=true)
    default int iconSize() { return 20; }
    @ConfigItem(keyName="originalImages",name="originalImages",description="Configured in the side panel",hidden=true)
    default boolean originalImages() { return false; }
    @ConfigItem(keyName="alwaysShowValue",name="alwaysShowValue",description="Configured in the side panel",hidden=true)
    default boolean alwaysShowValue() { return false; }
    @ConfigItem(keyName="alwaysShowTimer",name="alwaysShowTimer",description="Configured in the side panel",hidden=true)
    default boolean alwaysShowTimer() { return false; }
    @ConfigItem(keyName="groupStacks",name="groupStacks",description="Configured in the side panel",hidden=true)
    default boolean groupStacks() { return true; }
    @ConfigItem(keyName="despawnWarnings",name="despawnWarnings",description="Configured in the side panel",hidden=true)
    default boolean despawnWarnings() { return false; }
    @ConfigItem(keyName="despawnSeconds",name="despawnSeconds",description="Configured in the side panel",hidden=true)
    default int despawnSeconds() { return 15; }
    @ConfigItem(keyName="despawnMinimum",name="despawnMinimum",description="Configured in the side panel",hidden=true)
    default int despawnMinimum() { return 10000; }
    @ConfigItem(keyName="alertVolume",name="alertVolume",description="Configured in the side panel",hidden=true)
    default int alertVolume() { return 80; }
    @ConfigItem(keyName="soundCooldown",name="soundCooldown",description="Configured in the side panel",hidden=true)
    default int soundCooldown() { return 2; }
    @ConfigItem(keyName="notificationCards",name="notificationCards",description="Configured in the side panel",hidden=true)
    default boolean notificationCards() { return false; }
}
