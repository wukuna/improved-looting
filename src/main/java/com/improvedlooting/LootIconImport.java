package com.improvedlooting;

import java.util.*;
import java.util.regex.*;

/** Conservative format reader, not a replacement for Loot Filters' evaluator. */
final class LootIconImport
{
    static List<ItemIcons.Assignment> parse(String source)
    {
        if(source.length()>1000000) { throw new IllegalArgumentException("Filter is too large."); }
        // Remove comments without treating comment markers inside strings as comments.
        String clean=source;
        StringBuilder stripped=new StringBuilder();
        boolean quote=false;
        for(int i=0;i<clean.length();i++)
        {
            char c=clean.charAt(i);
            if(c=='"' && (i==0 || clean.charAt(i-1)!='\\')) { quote=!quote; }
            if(!quote && c=='/' && i+1<clean.length() && clean.charAt(i+1)=='/') { while(i<clean.length() && clean.charAt(i)!='\n') { i++; } stripped.append('\n'); continue; }
            if(!quote && c=='/' && i+1<clean.length() && clean.charAt(i+1)=='*') { int end=clean.indexOf("*/",i+2); if(end<0) { throw new IllegalArgumentException("Unclosed comment."); } i=end+1; continue; }
            stripped.append(c);
        }
        clean=stripped.toString();
        if(Pattern.compile("(?m)^\\s*#").matcher(clean).find()) { throw new IllegalArgumentException("This filter uses directives/macros. Automatic icon import is not supported; assign its icons manually. Nothing was changed."); }
        Pattern block=Pattern.compile("\\G\\s*(meta|rule|if|apply)\\s*(?:\\(([^{}]*)\\))?\\s*\\{([^{}]*)\\}");
        Matcher m=block.matcher(clean); int end=0;
        LinkedHashMap<String,ItemIcons.Assignment> result=new LinkedHashMap<>();
        while(m.find())
        {
            end=m.end();
            if(m.group(1).equals("meta")) { continue; }
            if(m.group(1).equals("apply")) { throw unsupported(); }
            String condition=m.group(2);
            if(condition==null) { throw unsupported(); }
            Matcher selector=Pattern.compile("\\s*(id|name)\\s*:\\s*(.*?)\\s*",Pattern.DOTALL).matcher(condition);
            if(!selector.matches()) { throw unsupported(); }
            String values=selector.group(2).trim();
            if(values.startsWith("[") && values.endsWith("]")) { values=values.substring(1,values.length()-1); }
            List<String> keys=new ArrayList<>();
            Matcher value=Pattern.compile(selector.group(1).equals("id") ? "\\G\\s*(\\d+)\\s*(?:,|$)" : "\\G\\s*\"([^\"*?\\\\]+)\"\\s*(?:,|$)").matcher(values);
            int used=0;
            while(value.find()) { keys.add(value.group(1)); used=value.end(); }
            if(keys.isEmpty() || used!=values.length()) { throw unsupported(); }
            Matcher icon=Pattern.compile("(?:^|(?<=;))\\s*icon\\s*=\\s*(CurrentItem|Item|Sprite)\\s*\\(([^()]*)\\)\\s*;",Pattern.DOTALL).matcher(m.group(3));
            if(!icon.find()) { throw new IllegalArgumentException("Every rule must have an explicit CurrentItem(), Item(id), or Sprite(id,index) icon. File icons and rules without icons cannot be transferred safely."); }
            String type=icon.group(1), args=icon.group(2).trim();
            int iconId=0,index=0;
            if(type.equals("CurrentItem")) { if(!args.isEmpty()) { throw unsupported(); } }
            else
            {
                if(!args.matches(type.equals("Item") ? "\\d+" : "\\d+\\s*,\\s*\\d+")) { throw unsupported(); }
                String[] parts=args.split("\\s*,\\s*");
                try { iconId=Integer.parseInt(parts[0]); if(parts.length>1) { index=Integer.parseInt(parts[1]); } }
                catch(NumberFormatException e) { throw unsupported(); }
            }
            if(icon.find()) { throw new IllegalArgumentException("Multiple icon assignments in a rule are not supported."); }
            for(String key:keys)
            {
                ItemIcons.Assignment a=new ItemIcons.Assignment();
                try { if(selector.group(1).equals("id")) { a.itemId=Integer.parseInt(key); } else { a.name=key; } }
                catch(NumberFormatException e) { throw unsupported(); }
                a.kind=type.equals("CurrentItem") ? ItemIcons.Kind.CURRENT_ITEM : type.equals("Item") ? ItemIcons.Kind.ITEM : ItemIcons.Kind.SPRITE;
                a.iconId=iconId; a.spriteIndex=index;
                if(a.itemId>100000 || iconId>100000 || index>255 || result.size()>=500) { throw unsupported(); }
                // Terminal rules use the first match. Mixed name/id selectors can overlap.
                if(!result.isEmpty() && result.values().iterator().next().itemId>=0 != (a.itemId>=0)) { throw new IllegalArgumentException("Mixed name and ID rules need manual review; nothing was imported."); }
                result.putIfAbsent(a.key(),a);
            }
        }
        if(!clean.substring(end).trim().isEmpty() || result.isEmpty()) { throw unsupported(); }
        return new ArrayList<>(result.values());
    }
    private static IllegalArgumentException unsupported()
    { return new IllegalArgumentException("Only terminal rules with exact item IDs or names are supported. Conditional, wildcard, inherited or malformed rules require manual assignments. Nothing was changed."); }
}
