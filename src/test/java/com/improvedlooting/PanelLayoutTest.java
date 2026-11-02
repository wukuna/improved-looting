package com.improvedlooting;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.*;
import javax.imageio.ImageIO;
import org.junit.Test;
import static org.mockito.Mockito.*;
import static org.junit.Assert.*;
public class PanelLayoutTest
{
    @Test public void sidePanelFitsAndDisposes() throws Exception {
        SwingUtilities.invokeAndWait(()->{try{UIManager.setLookAndFeel(new net.runelite.client.ui.laf.RuneLiteLAF());}catch(Exception ex){throw new AssertionError(ex);}
            ItemIcons icons=new ItemIcons();icons.configs=mock(net.runelite.client.config.ConfigManager.class);icons.filters=mock(FilterIcons.class);when(icons.filters.status()).thenReturn("Ready to import or create a filter");
            GroundLoot loot=new GroundLoot();
            ImprovedLootingPanel panel=new ImprovedLootingPanel(icons,mock(ImprovedLootingConfig.class,CALLS_REAL_METHODS),loot,new LegacyImport(),mock(net.runelite.client.callback.ClientThread.class),new LootPreferences(),new LootProfiles());panel.setSize(263,650);layout(panel);
            BufferedImage image=new BufferedImage(263,650,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();panel.printAll(g);g.dispose();
            try{ImageIO.write(image,"png",new java.io.File("build/panel-preview.png"));}catch(Exception e){throw new AssertionError(e);}
            panel.setSize(263,1100);layout(panel);image=new BufferedImage(263,1100,BufferedImage.TYPE_INT_RGB);g=image.createGraphics();panel.printAll(g);g.dispose();
            try{ImageIO.write(image,"png",new java.io.File("build/visibility-panel-preview.png"));}catch(Exception e){throw new AssertionError(e);}
            assertEquals(3,panel.page.getTabCount());
            assertEquals("Loot",panel.page.getTitleAt(0));assertEquals("Rules",panel.page.getTitleAt(1));assertEquals("Tools",panel.page.getTitleAt(2));
            assertEquals(1,panel.page.getTabRunCount());
            JButton style=button(panel,"Style…");
            assertFalse(((Container)style.getParent()).getComponent(1).isVisible());
            style.doClick();assertTrue(((Container)style.getParent()).getComponent(1).isVisible());
            panel.setSize(263,1050);layout(panel);image=new BufferedImage(263,1050,BufferedImage.TYPE_INT_RGB);g=image.createGraphics();panel.printAll(g);g.dispose();
            try{ImageIO.write(image,"png",new java.io.File("build/card-appearance-preview.png"));}catch(Exception e){throw new AssertionError(e);}
            style.doClick();JButton tiers=button(panel,"Drop tiers & sounds…");tiers.doClick();
            Container tierControls=(Container)((Container)tiers.getParent()).getComponent(1);
            assertNotNull(button(tierControls,"Edit tiers and sounds…"));
            checkbox(tierControls,"Drop sounds").doClick();checkbox(tierControls,"Tier animations and sounds").doClick();
            verify(icons.configs).setConfiguration(ImprovedLootingConfig.GROUP,"lootSounds",true);
            verify(icons.configs).setConfiguration(ImprovedLootingConfig.GROUP,"priceTiersEnabled",true);
            panel.setSize(263,850);layout(panel);image=new BufferedImage(263,850,BufferedImage.TYPE_INT_RGB);g=image.createGraphics();panel.printAll(g);g.dispose();
            try{ImageIO.write(image,"png",new java.io.File("build/tier-sounds-panel.png"));}catch(Exception e){throw new AssertionError(e);}
            panel.page.setSelectedIndex(2);layout(panel);assertNotNull(button(panel,"Settings…"));assertNull(button(panel,"Presets and starters…"));
            image=new BufferedImage(263,850,BufferedImage.TYPE_INT_RGB);g=image.createGraphics();panel.printAll(g);g.dispose();
            try{ImageIO.write(image,"png",new java.io.File("build/tools-export-panel.png"));}catch(Exception e){throw new AssertionError(e);}panel.close();
        });
    }
    private static JButton button(Container c,String label) {
        for(Component child:c.getComponents()) {
            if(child instanceof JButton && ((JButton)child).getText().contains(label)) return (JButton)child;
            if(child instanceof Container) {JButton b=button((Container)child,label);if(b!=null)return b;}
        }return null;
    }
    private static JCheckBox checkbox(Container c,String label) {
        for(Component child:c.getComponents()) {
            if(child instanceof JCheckBox && label.equals(((JCheckBox)child).getText()))return (JCheckBox)child;
            if(child instanceof Container){JCheckBox found=checkbox((Container)child,label);if(found!=null)return found;}
        }return null;
    }
    private static void layout(Container c) {c.doLayout();for(Component child:c.getComponents())if(child instanceof Container)layout((Container)child);}
}
