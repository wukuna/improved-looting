package com.improvedlooting;

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.*;
import javax.imageio.ImageIO;
import net.runelite.client.callback.ClientThread;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class LootBrowserLayoutTest
{
    @Test public void browserRendersHiddenAndVisibleRowsInBothLayouts()throws Exception
    {
        SwingUtilities.invokeAndWait(()->{
            try{UIManager.setLookAndFeel(new net.runelite.client.ui.laf.RuneLiteLAF());}catch(Exception e){throw new AssertionError(e);}
            LootRuntimeTest.Fixture f=new LootRuntimeTest.Fixture("rule(id:2){hidden=true;}");f.items.add(f.item(1,10,200));f.items.add(f.item(2,1,250));f.tick(100);
            LootProfiles profiles=LootProfilesTest.profiles();LootBrowser browser=new LootBrowser(f.loot,profiles.icons,mock(ClientThread.class),profiles.preferences);JPanel panel=browser.content();panel.setSize(960,650);layout(panel);
            assertEquals(2,f.loot.rows().size());render(panel,"browser-list-preview.png");findGrid(panel).doClick();layout(panel);render(panel,"browser-grid-preview.png");browser.close();
            RuleOptions options=new RuleOptions(profiles.icons,new java.util.HashMap<>());String script=options.apply(FilterEditor.rule("Shark",0,0));assertTrue(com.improvedlooting.rs2f.LootFilter.compile(script).evaluate(com.improvedlooting.rs2f.model.ItemFacts.builder().id(385).name("Shark").quantity(1).build()).isShowValue());options.close();
        });
    }
    private static JCheckBox findGrid(Container c){for(Component child:c.getComponents()){if(child instanceof JCheckBox&&((JCheckBox)child).getText().equals("Grid"))return (JCheckBox)child;if(child instanceof Container){JCheckBox found=findGrid((Container)child);if(found!=null)return found;}}return null;}
    private static void layout(Container c){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container)layout((Container)child);}
    private static void render(JPanel p,String name){BufferedImage image=new BufferedImage(p.getWidth(),p.getHeight(),2);Graphics2D g=image.createGraphics();p.printAll(g);g.dispose();try{ImageIO.write(image,"png",new java.io.File("build/"+name));}catch(Exception e){throw new AssertionError(e);}}
}
