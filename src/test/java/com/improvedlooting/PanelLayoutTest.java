package com.improvedlooting;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.*;
import javax.imageio.ImageIO;
import org.junit.Test;
import static org.mockito.Mockito.*;
public class PanelLayoutTest
{
    @Test public void sidePanelFitsAndDisposes() throws Exception {
        SwingUtilities.invokeAndWait(()->{try{UIManager.setLookAndFeel(new net.runelite.client.ui.laf.RuneLiteLAF());}catch(Exception ex){throw new AssertionError(ex);}
            ItemIcons icons=new ItemIcons();icons.filters=mock(FilterIcons.class);when(icons.filters.status()).thenReturn("Ready to import or create a filter");
            GroundLoot loot=new GroundLoot();
            ImprovedLootingPanel panel=new ImprovedLootingPanel(icons,mock(ImprovedLootingConfig.class,CALLS_REAL_METHODS),loot,new LegacyImport(),mock(net.runelite.client.callback.ClientThread.class),new LootPreferences(),new LootProfiles());panel.setSize(263,650);layout(panel);
            BufferedImage image=new BufferedImage(263,650,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();panel.printAll(g);g.dispose();
            try{ImageIO.write(image,"png",new java.io.File("build/panel-preview.png"));}catch(Exception e){throw new AssertionError(e);}
            panel.page.setSelectedItem("Style");
            panel.setSize(263,1050);layout(panel);image=new BufferedImage(263,1050,BufferedImage.TYPE_INT_RGB);g=image.createGraphics();panel.printAll(g);g.dispose();panel.close();
            try{ImageIO.write(image,"png",new java.io.File("build/card-appearance-preview.png"));}catch(Exception e){throw new AssertionError(e);}
        });
    }
    private static void layout(Container c) {c.doLayout();for(Component child:c.getComponents())if(child instanceof Container)layout((Container)child);}
}
