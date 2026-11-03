package com.improvedlooting.rs2f;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import com.improvedlooting.rs2f.lang.*;
import com.improvedlooting.rs2f.model.*;

public final class LootFilter {
 public final List<FilterRule> rules;
 private LootFilter(List<FilterRule> rules) { this.rules=Collections.unmodifiableList(new ArrayList<>(rules)); }
 public static Builder builder() { return new Builder(); }
 public static final class Builder {
  final List<FilterRule> rules=new ArrayList<>();
  public Builder setName(String s) { return this; } public Builder setDescription(String s) { return this; }
  public Builder addRule(FilterRule r) {
   BufferedImageProvider icon=r.getDisplay().getIcon();
   if(icon instanceof BufferedImageProvider.Item) { int id=((BufferedImageProvider.Item)icon).getItemId(); if(id<0 || id>100000) throw new IllegalArgumentException("Invalid item icon ID"); }
   if(icon instanceof BufferedImageProvider.Sprite) { BufferedImageProvider.Sprite sprite=(BufferedImageProvider.Sprite)icon; if(sprite.getSpriteId()<0 || (sprite.getSpriteId()>100000 && sprite.getSpriteId()!=9000000) || sprite.getIndex()<0 || sprite.getIndex()>255) throw new IllegalArgumentException("Invalid sprite icon"); }
   if(rules.size()>=10000) throw new IllegalArgumentException("Too many filter rules"); rules.add(r); return this; }
  public LootFilter build() { return new LootFilter(rules); }
 }
 public DisplayConfig evaluate(ItemFacts item) {
  DisplayConfig display=new DisplayConfig(java.awt.Color.WHITE);
  for(FilterRule rule:rules) {
   if(!rule.getCond().test(null,item)) continue;
   display=display.merge(rule.getDisplay());
   display.getEvalTrace().add(rule.getSourceLine());
   if(rule.isTerminal()) break;
  }
  return display;
 }
 public BufferedImageProvider find(ItemFacts item) {
  DisplayConfig display=evaluate(item);
  return display.isHideOverlay() ? null : display.getIcon();
 }
 public Set<String> files() {
  Set<String> names=new LinkedHashSet<>();
  for(FilterRule r:rules) if(r.getDisplay().getIcon() instanceof BufferedImageProvider.File) names.add(((BufferedImageProvider.File)r.getDisplay().getIcon()).getFilename());
  return names;
 }
 public static int preambleLines() {
  try(InputStream in=LootFilter.class.getResourceAsStream("/com/improvedlooting/preamble.rs2f")) {
   String preamble=new String(in.readAllBytes(),StandardCharsets.UTF_8).replace("\r\n","\n").replace('\r','\n');
   return (int)preamble.chars().filter(c->c=='\n').count()+1;
  } catch(IOException e) { throw new IllegalArgumentException("Cannot load filter constants",e); }
 }
 public static LootFilter compile(String source) {
  if(source==null || source.length()>1000000) throw new IllegalArgumentException("Filter exceeds 1 MB");
  try(InputStream in=LootFilter.class.getResourceAsStream("/com/improvedlooting/preamble.rs2f")) {
   String preamble=new String(in.readAllBytes(),StandardCharsets.UTF_8);
   String text=(preamble+"\n"+source+"\n").replace("\r\n","\n").replace('\r','\n');
   TokenStream tokens=new Preprocessor(new Lexer("imported filter",text).tokenize()).preprocess();

   int depth=0, unary=0;
   for(Token t:tokens.getTokens()) { if(t.is(Token.Type.EXPR_START) && ++depth>64) throw new IllegalArgumentException("Filter nesting exceeds 64"); if(t.is(Token.Type.EXPR_END)) depth--; if(t.is(Token.Type.OP_NOT)) { if(++unary>64) throw new IllegalArgumentException("Excessive negation nesting"); } else if(t.isSemantic()) unary=0; }
   return new Parser(tokens).parse();
  } catch(IOException e) { throw new IllegalArgumentException("Cannot load filter constants",e); }
  catch(RuntimeException e) { throw new IllegalArgumentException("Filter could not be imported: "+e.getMessage(),e); }
 }
}
