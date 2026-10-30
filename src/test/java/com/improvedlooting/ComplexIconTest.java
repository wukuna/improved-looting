package com.improvedlooting;
import com.improvedlooting.rs2f.LootFilter;
import com.improvedlooting.rs2f.model.*;
import com.google.gson.Gson;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import org.junit.Assume;
import static org.junit.Assert.*;

public class ComplexIconTest {
 private ItemFacts fact(int account,int ownership,int quantity,long price) { return ItemFacts.builder().id(385).name("Shark").quantity(quantity).accountType(account).ownership(ownership).gePrice(price).haPrice(50).tradeable(true).geTradeable(true).worldPoint(new WorldPoint(3200,3200,0)).build(); }
 @Test public void macrosApplyAndTerminalOrdering() {
  String source="#define FOOD(name_) (name:name_)\n#define STYLE icon=Sprite(41,0);\napply (true) {icon=Item(995);} rule (FOOD(\"Shark\") && quantity:>=2) { STYLE } rule (true) {icon=CurrentItem();}";
  LootFilter f=LootFilter.compile(source);
  assertEquals(new BufferedImageProvider.Sprite(41,0),f.find(fact(0,1,2,100)));
  assertEquals(new BufferedImageProvider.CurrentItem(),f.find(fact(0,1,1,100)));
 }
 @Test public void hiddenTerminalSuppressesInheritedIconAndStops() {
  LootFilter f=LootFilter.compile("apply (true) {icon=Item(995);} rule(name:\"Shark\") {hidden=true;} rule(true) {icon=Sprite(41,0);}");
  assertNull(f.find(fact(0,1,1,10)));
 }
 @Test public void finalVisibilityOverridesAndCompactFollowFilterMerging() {
  ItemFacts item=fact(0,1,1,10);
  assertNull(LootFilter.compile("rule(true) {textColor=\"#ffffff\";}").find(item));
  assertNull(LootFilter.compile("apply(true) {icon=Item(995);} rule(true) {hideOverlay=true;}").find(item));
  assertEquals(new BufferedImageProvider.Item(995),LootFilter.compile("apply(true) {icon=Item(995); hidden=true; hideOverlay=true;} rule(true) {hidden=false; hideOverlay=false;}").find(item));
  assertNull(LootFilter.compile("apply(true) {icon=Item(995); hidden=true; hideOverlay=true;} rule(true) {hidden=false;}").find(item));
  com.improvedlooting.rs2f.FilterRule always=LootFilter.compile("rule(true) {icon=Item(995);}").rules.get(0);
  LootFilter compact=LootFilter.builder().addRule(new com.improvedlooting.rs2f.FilterRule(always.getCond(),com.improvedlooting.rs2f.DisplayConfig.builder().compact(true).build(),true,0)).build();
  assertEquals(new BufferedImageProvider.CurrentItem(),compact.find(item));

 }
 @Test public void ironmanOwnershipAndAreaAreLiveConditions() {
  LootFilter f=LootFilter.compile("rule(accountType:1 && !ownership:2 && area:[3190,3190,0,3210,3210,0]) {icon=File(\"food/heart.png\");} rule(true) {icon=CurrentItem();}");
  assertEquals(new BufferedImageProvider.File("food/heart.png"),f.find(fact(1,1,1,100)));
  assertEquals(new BufferedImageProvider.CurrentItem(),f.find(fact(0,1,1,100)));
  assertEquals(new BufferedImageProvider.CurrentItem(),f.find(fact(1,2,1,100)));
 }
 @Test public void valueUsesStackQuantityAndWildcard() {
  LootFilter f=LootFilter.compile("rule(name:\"*hark\" && gevalue:>=200 && tradeable:true && geTradeable:true && !noted:true) {icon=Item(995);}");
  assertNotNull(f.find(fact(0,1,2,100))); assertNull(f.find(fact(0,1,1,100)));
 }
 @Test public void rejectsMalformedAndExpansionBombs() {
  try { LootFilter.compile("rule (true) {icon=File(\"x\");"); fail(); } catch(IllegalArgumentException expected) {}
  StringBuilder source=new StringBuilder("#define A0 true\n");
  for(int i=1;i<24;i++) source.append("#define A").append(i).append(" A").append(i-1).append(" || A").append(i-1).append("\n");
  source.append("rule(A23) {icon=CurrentItem();}");
  try { LootFilter.compile(source.toString()); fail(); } catch(IllegalArgumentException expected) {}
 }
 @Test public void invalidIconIdsRejectBeforeSaving() {
  for(String source:new String[]{"rule(true) {icon=Item(-1);}","rule(true) {icon=Sprite(41,-1);}"}) {
   try { LootFilter.compile(source); fail(); } catch(IllegalArgumentException expected) {}
  }
 }
 @Test public void imageCopyPreservesTransparencyAndRejectsTraversal() throws Exception {
  BufferedImage input=new BufferedImage(40,20,BufferedImage.TYPE_INT_ARGB); input.setRGB(20,10,0xffff0000);
  String image=IconImages.encode(input); BufferedImage decoded=IconImages.decode(image);
  assertEquals(40,decoded.getWidth()); assertEquals(20,decoded.getHeight()); assertEquals(0xffff0000,decoded.getRGB(20,10)); assertEquals(0,decoded.getRGB(0,0));
  for(String name:new String[]{"../x.png","/x.png","C:\\x.png","folder/../x.png","a//b.png"}) {
   try { IconImages.safeName(name); fail(name); } catch(IllegalArgumentException expected) {}
  }
 }
 @Test public void backupContainsSourceAndImagesAndChecksMissingFiles() throws Exception {
  FilterIcons filters=new FilterIcons(); filters.gson=new Gson();
  FilterIcons.Bundle b=new FilterIcons.Bundle(); b.compressedSource=FilterIcons.compress("rule(true) {icon=File(\"heart.png\");}");
  try { filters.prepare(b); fail(); } catch(IllegalArgumentException expected) {}
  b.images.put("heart.png",IconImages.encode(new BufferedImage(17,15,BufferedImage.TYPE_INT_ARGB)));
  FilterIcons.Prepared p=filters.parse(filters.gson.toJson(b)); assertEquals(1,p.images.size()); assertEquals(1,p.program.rules.size());
  assertTrue(FilterIcons.source(p.bundle).contains("heart.png"));
 }
 private void preset(String name) throws Exception {
  String dir=System.getProperty("filterFixtures",""); Assume.assumeFalse(dir.isEmpty());
  String text=Files.readString(Paths.get(dir,name+".rs2f"));
  LootFilter f=LootFilter.compile(text); assertTrue(f.rules.size()>20);
  System.out.println(name+": "+f.rules.size()+" rules, "+f.files().size()+" file icons");
  for(int acc=0;acc<4;acc++) for(int owner=0;owner<4;owner++) f.find(fact(acc,owner,5,100));
 }
 @Test public void suppliedFilterscapePreset() throws Exception { preset("filterscape"); }
 @Test public void suppliedJoePreset() throws Exception { preset("joe"); }
 @Test public void suppliedIronmanPreset() throws Exception { preset("ironman"); }
}
