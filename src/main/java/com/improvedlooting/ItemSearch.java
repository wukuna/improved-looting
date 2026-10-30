package com.improvedlooting;

import java.awt.image.BufferedImage;
import java.util.*;
import java.util.function.*;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;

/** Read-only, incremental catalogue. Includes untradeables; never scans on the EDT. */
@Singleton
final class ItemSearch
{
    static final class Choice
    {
        final int id;
        final String name, lower;
        BufferedImage image;
        Choice(int id, String name) { this.id=id; this.name=name; this.lower=name.toLowerCase(Locale.ROOT); }
        @Override public String toString() { return name; }
    }
    @Inject Client client;
    @Inject ClientThread thread;
    @Inject ItemManager items;
    private final Map<String, Choice> catalogue=new LinkedHashMap<>();
    private int cursor, size;

    void search(String query, BooleanSupplier wanted, Consumer<java.util.List<Choice>> done)
    {
        thread.invokeLater(() -> {
            if(!wanted.getAsBoolean()) return true;
            int count=Math.min(client.getItemCount(),100000);
            if(count<=0) { SwingUtilities.invokeLater(() -> done.accept(Collections.emptyList())); return true; }
            if(count!=size) { catalogue.clear(); cursor=0; size=count; }
            int end=Math.min(cursor+512,size);
            while(cursor<end) {
                int id=cursor++; ItemComposition item=items.getItemComposition(id);
                String name=item.getName();
                if(name!=null && !name.isBlank() && !name.equalsIgnoreCase("null") && item.getNote()==-1 && item.getPlaceholderTemplateId()==-1)
                    catalogue.putIfAbsent(name.toLowerCase(Locale.ROOT),new Choice(id,name));
            }
            if(cursor<size) return false;
            java.util.List<Choice> result=rank(catalogue.values(),query);
            for(Choice choice:result) choice.image=items.getImage(choice.id);
            SwingUtilities.invokeLater(() -> { if(wanted.getAsBoolean()) done.accept(result); });
            return true;
        });
    }
    static java.util.List<Choice> rank(Collection<Choice> choices,String query)
    {
        String q=query.trim().toLowerCase(Locale.ROOT);
        if(q.length()<2) return Collections.emptyList();
        java.util.List<Choice> result=new ArrayList<>();
        for(Choice choice:choices) if(choice.lower.contains(q)) result.add(choice);
        result.sort(Comparator.comparingInt((Choice c) -> c.lower.equals(q)?0:c.lower.startsWith(q)?1:2).thenComparing(c -> c.lower));
        return new ArrayList<>(result.subList(0,Math.min(40,result.size())));
    }
}
