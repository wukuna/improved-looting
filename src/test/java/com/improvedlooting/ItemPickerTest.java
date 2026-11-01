package com.improvedlooting;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;
import javax.swing.*;
import com.improvedlooting.rs2f.LootFilter;
import com.improvedlooting.rs2f.model.*;
import net.runelite.api.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ItemPickerTest
{
    @Test public void suggestionsRankExactThenPrefixAndAreBounded()
    {
        List<ItemSearch.Choice> choices=new ArrayList<>();
        choices.add(new ItemSearch.Choice(1,"Cooked shark")); choices.add(new ItemSearch.Choice(2,"Shark tooth")); choices.add(new ItemSearch.Choice(3,"Shark"));
        assertEquals(3,ItemSearch.rank(choices,"SHARK").get(0).id);
        assertEquals(2,ItemSearch.rank(choices,"shark").get(1).id);
        for(int i=0;i<60;i++) choices.add(new ItemSearch.Choice(100+i,"Shark "+i));
        assertEquals(40,ItemSearch.rank(choices,"shark").size()); assertTrue(ItemSearch.rank(choices,"s").isEmpty());
    }
    @Test public void catalogueIncludesUntradeableItemsAndCancelsObsoleteSearches() throws Exception
    {
        ItemSearch search=new ItemSearch(); search.client=mock(Client.class); search.thread=mock(ClientThread.class); search.items=mock(ItemManager.class);
        when(search.client.getItemCount()).thenReturn(1);
        ItemComposition item=mock(ItemComposition.class); when(item.getName()).thenReturn("Fire cape"); when(item.getNote()).thenReturn(-1); when(item.getPlaceholderTemplateId()).thenReturn(-1);
        when(search.items.getItemComposition(0)).thenReturn(item);
        AtomicReference<BooleanSupplier> job=new AtomicReference<>(); doAnswer(i -> { job.set(i.getArgument(0)); return null; }).when(search.thread).invokeLater(any(BooleanSupplier.class));
        AtomicBoolean wanted=new AtomicBoolean(true); AtomicReference<List<ItemSearch.Choice>> result=new AtomicReference<>();
        search.search("fire",wanted::get,result::set); assertTrue(job.get().getAsBoolean()); SwingUtilities.invokeAndWait(() -> {});
        assertEquals("Fire cape",result.get().get(0).name); verify(item,never()).isTradeable();
        result.set(null); search.search("fire",wanted::get,result::set); wanted.set(false); assertTrue(job.get().getAsBoolean()); SwingUtilities.invokeAndWait(() -> {}); assertNull(result.get());
    }
    @Test public void visualSelectionsCompileToActualIconProviders()
    {
        ItemFacts facts=ItemFacts.builder().id(385).name("Shark").quantity(1).build();
        IconPalette.Selection selection=new IconPalette.Selection(ItemIcons.Kind.ITEM,995,null,null);
        assertEquals(new BufferedImageProvider.Item(995),LootFilter.compile(FilterEditor.rule("Shark",selection)).find(facts));
        selection=new IconPalette.Selection(ItemIcons.Kind.HEART,0,"improved-icons/heart.png","unused");
        assertEquals(new BufferedImageProvider.File("improved-icons/heart.png"),LootFilter.compile(FilterEditor.rule("Shark",selection)).find(facts));
        assertNull(LootFilter.compile(FilterEditor.rule("Shark",new IconPalette.Selection(null,0,null,null))).find(facts));
    }
    @Test public void builderFitsAndRendersAtNormalEditorSize() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            try{UIManager.setLookAndFeel(new net.runelite.client.ui.laf.RuneLiteLAF());}catch(Exception ex){throw new AssertionError(ex);}
            ItemIcons icons=new ItemIcons(); icons.search=new ItemSearch();
            FilterEditor editor=new FilterEditor(icons); JPanel panel=editor.builder(new JTabbedPane()); panel.setSize(850,550); layout(panel);
            BufferedImage image=new BufferedImage(850,550,BufferedImage.TYPE_INT_RGB); Graphics2D g=image.createGraphics(); panel.printAll(g); g.dispose();
            try { ImageIO.write(image,"png",new java.io.File("build/item-picker-preview.png")); } catch(java.io.IOException ex) { throw new AssertionError(ex); }
            assertTrue("Item and icon controls must remain visible",panel.getComponent(1).getHeight()>200); editor.close();
            FilterEditor menuEditor=new FilterEditor(icons);menuEditor.menuOnly();JPanel menuPanel=menuEditor.builder(new JTabbedPane());menuPanel.setSize(850,550);layout(menuPanel);
            BufferedImage menuImage=new BufferedImage(850,550,BufferedImage.TYPE_INT_RGB);Graphics2D menuGraphics=menuImage.createGraphics();menuPanel.printAll(menuGraphics);menuGraphics.dispose();
            try{ImageIO.write(menuImage,"png",new java.io.File("build/menu-rule-builder-preview.png"));}catch(java.io.IOException ex){throw new AssertionError(ex);}
            assertTrue(menuPanel.getComponent(1).getHeight()>200);menuEditor.close();
        });
    }
    private static void layout(Container container) { container.doLayout(); for(Component c:container.getComponents()) if(c instanceof Container) layout((Container)c); }
}
