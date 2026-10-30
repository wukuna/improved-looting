package com.improvedlooting;

import com.google.gson.Gson;
import java.awt.Color;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.*;
import net.runelite.client.config.ConfigManager;

@Singleton
final class PriceTiers
{
    static final String KEY="priceTiersV1";
    enum Effect { FILTER("Filter color"), SOLID("Tier color"), RGB("RGB cycle"), RAINBOW("Rainbow"), WAVE_LEFT("Wave left"), WAVE_RIGHT("Wave right");
        private final String label; Effect(String label){this.label=label;} public String toString(){return label;} }
    enum Sound { RULE("Filter sound"), SILENT("Silent"), CUSTOM("Custom file");
        private final String label; Sound(String label){this.label=label;} public String toString(){return label;} }
    static final class Tier { long minimum; int color=0xffffff; boolean bold,lootBeam; Effect effect=Effect.FILTER; Sound sound=Sound.RULE; String audio,name; }
    static final class Setup { int version=1,seconds=8; Tier[] tiers; }
    static final class Ready { final Setup setup; final byte[][] audio; Ready(Setup setup,byte[][] audio){this.setup=setup;this.audio=audio;} }
    @Inject ConfigManager configs;
    @Inject Gson gson;
    @Inject ScheduledExecutorService executor;
    private volatile Ready current;
    private String loaded;
    private long generation;
    private volatile String status="";
    static Setup defaults() {
        Setup s=new Setup();s.tiers=new Tier[7];long[] values={0,1000,10000,100000,1000000,10000000,100000000};
        int[] colors={0xffffff,0x80ff80,0x80c0ff,0xc080ff,0xffa040,0xff6090,0xffff60};
        for(int i=0;i<7;i++){s.tiers[i]=new Tier();s.tiers[i].minimum=values[i];s.tiers[i].color=colors[i];}return s;
    }
    Ready prepare(Setup s) {
        if(s==null || s.version!=1 || s.tiers==null || s.tiers.length!=7 || s.seconds<2 || s.seconds>30)throw new IllegalArgumentException("Use seven tiers and a cycle of 2–30 seconds.");
        byte[][] audio=new byte[7][];long total=0,previous=-1;
        for(int i=0;i<7;i++) {Tier t=s.tiers[i];
            if(t==null || t.minimum<0 || t.minimum<=previous || i==0 && t.minimum!=0 || t.effect==null || t.sound==null || t.color<0 || t.color>0xffffff)throw new IllegalArgumentException("Tier 1 must start at 0; each later minimum must be higher than the previous one.");
            previous=t.minimum;
            if(t.audio!=null) {audio[i]=FilterSounds.decode(t.audio);total+=audio[i].length;}
            if(t.sound==Sound.CUSTOM && audio[i]==null)throw new IllegalArgumentException("Choose a sound file for tier "+(i+1)+".");
        }
        if(total>4000000)throw new IllegalArgumentException("Tier sounds exceed 4 MB combined.");
        return new Ready(s,audio);
    }
    static Setup copy(Setup source) {Setup s=new Setup();s.seconds=source.seconds;s.version=source.version;s.tiers=new Tier[source.tiers.length];for(int i=0;i<s.tiers.length;i++){Tier a=source.tiers[i],b=new Tier();b.minimum=a.minimum;b.color=a.color;b.bold=a.bold;b.lootBeam=a.lootBeam;b.effect=a.effect;b.sound=a.sound;b.audio=a.audio;b.name=a.name;s.tiers[i]=b;}return s;}
    Ready parse(String raw) {if(raw==null || raw.isBlank()) return prepare(defaults()); if(raw.length()>6000000)throw new IllegalArgumentException("Tier settings are too large.");try{return prepare(gson.fromJson(raw,Setup.class));}catch(com.google.gson.JsonParseException e){throw new IllegalArgumentException("Invalid tier settings",e);}}
    synchronized void reload() {
        String raw=configs.getConfiguration(ImprovedLootingConfig.GROUP,KEY);long token=++generation;loaded=raw;current=null;status="Loading price tiers…";
        executor.execute(()->{Ready next=null;String message="";try{next=parse(raw);}catch(IllegalArgumentException e){message="Price tiers unavailable: "+e.getMessage();}
            synchronized(this){if(token==generation){current=next;status=message;}}});
    }
    synchronized void save(Ready ready) {generation++;loaded=gson.toJson(ready.setup);current=ready;status="";configs.setConfiguration(ImprovedLootingConfig.GROUP,KEY,loaded);}
    String raw(){return configs.getConfiguration(ImprovedLootingConfig.GROUP,KEY);}
    String status(){return status;}
    Ready snapshot(){return current;}
    static int index(Setup setup,long value) {for(int i=6;i>=0;i--)if(value>=setup.tiers[i].minimum)return i;return 0;}
    static long value(long price,long quantity) {if(price<=0 || quantity<=0)return 0;return price>Long.MAX_VALUE/quantity?Long.MAX_VALUE:price*quantity;}
}
