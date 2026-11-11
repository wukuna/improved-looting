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
    @Inject TierSounds sounds;
    @Inject SpecialDropSounds specials;
    private volatile Ready current;
    private volatile Long currentProfile;
    private String loaded;
    private long generation;
    private volatile String status="";
    static Setup defaults() {
        Setup s=new Setup();s.tiers=new Tier[7];long[] values={0,1000,10000,100000,1000000,10000000,100000000};
        int[] colors={0xffffff,0x80ff80,0x80c0ff,0xc080ff,0xffa040,0xff6090,0xffff60};
        for(int i=0;i<7;i++){s.tiers[i]=new Tier();s.tiers[i].minimum=values[i];s.tiers[i].color=colors[i];}return s;
    }
    Ready prepare(Setup s) { return prepare(s,false); }
    private Ready prepare(Setup s,boolean allowMissing) {
        if(s==null || s.version!=1 || s.tiers==null || s.tiers.length!=7 || s.seconds<2 || s.seconds>30)throw new IllegalArgumentException("Use seven tiers and a cycle of 2–30 seconds.");
        byte[][] audio=new byte[7][];long total=0,previous=-1;
        for(int i=0;i<7;i++) {Tier t=s.tiers[i];
            if(t==null || t.minimum<0 || t.minimum<=previous || i==0 && t.minimum!=0 || t.effect==null || t.sound==null || t.color<0 || t.color>0xffffff)throw new IllegalArgumentException("Tier 1 must start at 0; each later minimum must be higher than the previous one.");
            previous=t.minimum;
            if(t.name!=null && (t.name.length()>255 || t.name.chars().anyMatch(c->c<32)))throw new IllegalArgumentException("Invalid sound filename for tier "+(i+1)+".");
            if(t.audio!=null) {
                try {audio[i]=decodeAudio(t.audio);total+=audio[i].length;}
                catch(IllegalArgumentException e){if(!allowMissing || !TierSounds.validReference(t.audio))throw e;}
            }
            if(t.sound==Sound.CUSTOM && audio[i]==null && !(allowMissing && TierSounds.validReference(t.audio)))throw new IllegalArgumentException("Choose a sound file for tier "+(i+1)+".");
        }
        if(total>4000000)throw new IllegalArgumentException("Tier sounds exceed 4 MB combined.");
        return new Ready(s,audio);
    }
    static Setup copy(Setup source) {Setup s=new Setup();s.seconds=source.seconds;s.version=source.version;s.tiers=new Tier[source.tiers.length];for(int i=0;i<s.tiers.length;i++){Tier a=source.tiers[i],b=new Tier();b.minimum=a.minimum;b.color=a.color;b.bold=a.bold;b.lootBeam=a.lootBeam;b.effect=a.effect;b.sound=a.sound;b.audio=a.audio;b.name=a.name;s.tiers[i]=b;}return s;}
    byte[] decodeAudio(String value){return TierSounds.reference(value)?sounds.read(value):FilterSounds.decode(value);}
    private Setup settings(String raw) {if(raw==null || raw.isBlank())return defaults();if(raw.length()>6000000)throw new IllegalArgumentException("Tier settings are too large.");try{return gson.fromJson(raw,Setup.class);}catch(com.google.gson.JsonParseException e){throw new IllegalArgumentException("Invalid tier settings",e);}}
    Ready parse(String raw) {return prepare(settings(raw));}
    Ready prepareForSave(Setup setup) {
        Ready checked=prepare(setup);Ready ready=new Ready(copy(checked.setup),checked.audio);
        for(int i=0;i<7;i++)if(ready.audio[i]!=null)ready.setup.tiers[i].audio=sounds.store(ready.audio[i]);
        return ready;
    }
    String stored(String raw) {
        if(raw==null || raw.isBlank())return raw;
        Ready ready=prepare(settings(raw),true);
        for(int i=0;i<7;i++)if(ready.audio[i]!=null&&!TierSounds.reference(ready.setup.tiers[i].audio))ready.setup.tiers[i].audio=sounds.store(ready.audio[i]);
        return gson.toJson(ready.setup);
    }
    String portable(String raw) {
        if(raw==null || raw.isBlank())return raw;
        Ready ready=parse(raw);Setup copy=copy(ready.setup);
        for(int i=0;i<7;i++)if(ready.audio[i]!=null)copy.tiers[i].audio=Base64.getEncoder().encodeToString(ready.audio[i]);
        return gson.toJson(copy);
    }
    private static boolean embedded(Setup setup){for(Tier tier:setup.tiers)if(tier.audio!=null&&!TierSounds.reference(tier.audio))return true;return false;}
    private static String missing(Ready ready){List<String> tiers=new ArrayList<>();for(int i=0;i<7;i++)if(ready.setup.tiers[i].audio!=null&&ready.audio[i]==null)tiers.add(Integer.toString(i+1));return tiers.isEmpty()?"":"Sound files unavailable for tiers "+String.join(", ",tiers)+". Choose their files again or import a settings export containing them.";}
    synchronized void reload() {
        String raw=raw();Long profile=ProfileIdentity.current(configs);long token=++generation;loaded=raw;current=null;status="Loading price tiers…";
        executor.execute(()->{Ready next=null,migrated=null;String message="";
            try{next=prepare(settings(raw),true);message=missing(next);if(embedded(next.setup)&&message.isEmpty())try{migrated=prepareForSave(next.setup);}catch(IllegalArgumentException e){message="Sounds are loaded but could not be preserved: "+e.getMessage();}}
            catch(IllegalArgumentException e){message="Price tiers unavailable: "+e.getMessage();}
            synchronized(this){if(token!=generation || !Objects.equals(profile,ProfileIdentity.current(configs)) || !Objects.equals(raw,raw()))return;currentProfile=profile;current=next;status=message;}
            if(migrated!=null){Ready saved=migrated;javax.swing.SwingUtilities.invokeLater(()->{synchronized(this){if(token==generation&&Objects.equals(profile,ProfileIdentity.current(configs))&&Objects.equals(raw,raw()))save(saved);}});}
        });
    }
    synchronized void save(Ready ready) {if(embedded(ready.setup))throw new IllegalArgumentException("Save sound files before updating tier settings.");String value=gson.toJson(ready.setup);configs.setConfiguration(ImprovedLootingConfig.GROUP,KEY,value);generation++;loaded=value;currentProfile=ProfileIdentity.current(configs);current=ready;status="";}
    String raw(){return configs.getConfiguration(ImprovedLootingConfig.GROUP,KEY);}
    String status(){return status;}
    synchronized void stop(){generation++;current=null;}
    Ready snapshot(){Ready ready=current;return Objects.equals(currentProfile,ProfileIdentity.current(configs))?ready:null;}
    static int index(Setup setup,long value) {for(int i=6;i>=0;i--)if(value>=setup.tiers[i].minimum)return i;return 0;}
    static long value(long price,long quantity) {if(price<=0 || quantity<=0)return 0;return price>Long.MAX_VALUE/quantity?Long.MAX_VALUE:price*quantity;}
}
