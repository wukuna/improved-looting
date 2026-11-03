package com.improvedlooting;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.util.*;
import javax.swing.*;
import net.runelite.client.util.Filepath;

final class RuleOptions extends JPanel
{
    private final JCheckBox value=new JCheckBox("Value",true),timer=new JCheckBox("Timer",true),beam=new JCheckBox("Beam"),tile=new JCheckBox("Tile highlight"),notify=new JCheckBox("Notify"),colorEnabled=new JCheckBox("Text color");
    private final JSpinner minimum=new JSpinner(new SpinnerNumberModel(0,0,Integer.MAX_VALUE,1000));
    private final JSpinner maximum=new JSpinner(new SpinnerNumberModel(0,0,Integer.MAX_VALUE,1000));
    private final JComboBox<String> ownership=new JComboBox<>(new String[]{"Any owner","Yours","Group","Yours or group","Other player","Public/unassigned"});
    private final JCheckBox inRegion=new JCheckBox("In region"),menuOnly=new JCheckBox("Menu choice only");
    private final JSpinner region=new JSpinner(new SpinnerNumberModel(0,0,65535,1));
    private final JComboBox<LootMenuChoice> menuChoice=new JComboBox<>(LootMenuChoice.values());
    private Runnable menuModeChanged=()->{};
    private final JComboBox<String> sound=new JComboBox<>();
    private final Map<String,String> sounds;
    private final ItemIcons icons;
    private Color color=Color.YELLOW;
    private volatile boolean closed;
    private boolean loading,playing;
    RuleOptions(ItemIcons icons,Map<String,String> sounds)
    {
        super(new BorderLayout());this.icons=icons;this.sounds=sounds;
        JPanel appearance=new JPanel(new GridLayout(0,4,5,4)),conditions=new JPanel(new GridLayout(0,4,5,4));
        JTabbedPane tabs=new JTabbedPane();tabs.addTab("Appearance",appearance);tabs.addTab("Conditions and menu",conditions);add(tabs,BorderLayout.CENTER);
        appearance.add(value);appearance.add(timer);appearance.add(beam);appearance.add(tile);appearance.add(notify);appearance.add(colorEnabled);
        JButton chooseColor=new JButton("Choose color");chooseColor.addActionListener(e->{Color next=JColorChooser.showDialog(this,"Rule text color",color);if(next!=null){color=next;colorEnabled.setSelected(true);chooseColor.setForeground(next);}});appearance.add(chooseColor);appearance.add(new JLabel());
        appearance.add(new JLabel("Drop sound"));sound.addItem("Keep filter sound");for(String name:sounds.keySet())sound.addItem(name);appearance.add(sound);
        JButton file=new JButton("Add sound file"),preview=new JButton("Preview sound");appearance.add(file);appearance.add(preview);
        file.addActionListener(e->{java.util.List<Filepath> chosen=new Filepath.Chooser().setIsOpen().setAcceptsFiles().addExtensionFilter("Audio","wav","aiff","aif","au").showDialog(this);if(chosen.isEmpty())return;
            loading=true;file.setEnabled(false);new SwingWorker<String,Void>(){protected String doInBackground()throws Exception{return FilterSounds.read(chosen.get(0));}protected void done(){loading=false;if(closed)return;file.setEnabled(true);try{String name="sounds/"+chosen.get(0).getFileName();IconImages.safeName(name);String encoded=get();if(sounds.containsKey(name)&&!sounds.get(name).equals(encoded))throw new IllegalArgumentException("A different sound has this filename. Rename the file first.");sounds.put(name,encoded);boolean exists=false;for(int i=0;i<sound.getItemCount();i++)if(name.equals(sound.getItemAt(i)))exists=true;if(!exists)sound.addItem(name);sound.setSelectedItem(name);}catch(Exception ex){error(ex);}}}.execute();});
        preview.addActionListener(e->{String encoded=sounds.get(sound.getSelectedItem());if(encoded==null||playing)return;playing=true;preview.setEnabled(false);
            new SwingWorker<Void,Void>(){protected Void doInBackground()throws Exception{if(!closed)icons.audio.play(new ByteArrayInputStream(FilterSounds.decode(encoded)),LootAlerts.gain(volume()));return null;}protected void done(){playing=false;if(closed)return;preview.setEnabled(true);try{get();}catch(Exception ex){error(ex);}}}.execute();});
        conditions.add(new JLabel("Min stack GE value"));conditions.add(minimum);conditions.add(new JLabel("Max stack GE value"));conditions.add(maximum);maximum.setToolTipText("0 means no upper limit; inclusive, per native ground stack.");
        conditions.add(new JLabel("Ownership"));conditions.add(ownership);
        conditions.add(inRegion);conditions.add(region);region.setEnabled(false);inRegion.addActionListener(e->region.setEnabled(inRegion.isSelected()));
        inRegion.setToolTipText("Match the drop's map region, on any plane. Instance drops use their template location.");
        region.setToolTipText("Map region ID, 0–65535. Leave In region unchecked for anywhere.");
        conditions.add(new JLabel("Hidden tag in menu"));conditions.add(menuChoice);
        menuChoice.setToolTipText("Follow default leaves menu handling to other rules or the global checkbox. Hide/Keep applies only to matching hidden tags; per-item choices take priority.");
        conditions.add(menuOnly);menuOnly.setToolTipText("Change only menu handling; keep existing appearance rules. The first matching menu choice wins. Place before existing rules so they cannot stop it being reached.");
        menuOnly.addActionListener(e->{for(JComponent c:new JComponent[]{value,timer,beam,tile,notify,colorEnabled,chooseColor,sound,file,preview})c.setEnabled(!menuOnly.isSelected());tabs.setEnabledAt(0,!menuOnly.isSelected());if(menuOnly.isSelected())tabs.setSelectedIndex(1);menuModeChanged.run();});
    }
    void onMenuModeChanged(Runnable action){menuModeChanged=action;}
    void menuOnly(){if(!menuOnly.isSelected())menuOnly.doClick();menuChoice.setSelectedItem(LootMenuChoice.HIDE);}
    boolean isMenuOnly(){return menuOnly.isSelected();}
    private int volume(){String raw=icons.configs.getConfiguration(ImprovedLootingConfig.GROUP,"alertVolume");try{return raw==null?80:Integer.parseInt(raw);}catch(NumberFormatException e){return 80;}}
    String apply(String rule)
    {
        if(loading)throw new IllegalArgumentException("Wait for the sound import to finish");
        int min=(Integer)minimum.getValue(),max=(Integer)maximum.getValue();
        if(max>0 && max<min)throw new IllegalArgumentException("Maximum value must be at least the minimum, or 0 for no limit.");
        String conditions=min>0?" && gevalue:>="+min:"";
        if(max>0)conditions+=" && gevalue:<="+max;
        switch(ownership.getSelectedIndex()) {
            case 1:conditions+=" && ownership:OWNERSHIP_SELF";break;
            case 2:conditions+=" && ownership:OWNERSHIP_GROUP";break;
            case 3:conditions+=" && (ownership:OWNERSHIP_SELF || ownership:OWNERSHIP_GROUP)";break;
            case 4:conditions+=" && ownership:OWNERSHIP_OTHER";break;
            case 5:conditions+=" && ownership:OWNERSHIP_NONE";break;
            default:break;
        }
        if(inRegion.isSelected()){int id=(Integer)region.getValue(),x=(id>>8)*64,y=(id&255)*64;conditions+=" && area:["+x+","+y+",0,"+(x+63)+","+(y+63)+",3]";}
        int end=rule.indexOf(") {");rule=rule.substring(0,end)+conditions+rule.substring(end);
        Boolean menu=((LootMenuChoice)menuChoice.getSelectedItem()).hide;
        if(isMenuOnly()){
            if(menu==null)throw new IllegalArgumentException("Choose Hide when tag hidden or Keep in menu for a menu-only rule.");
            return "apply"+rule.substring(rule.indexOf('('),rule.indexOf(") {")+1)+" { hideMenu="+menu+"; }\n";
        }
        String fields=" showValue="+value.isSelected()+"; showDespawn="+timer.isSelected()+"; showLootbeam="+beam.isSelected()+"; highlightTile="+tile.isSelected()+"; notify="+notify.isSelected()+";";
        if(colorEnabled.isSelected())fields+=" textColor=\""+String.format("#%06x",color.getRGB()&0xffffff)+"\";";
        if(sound.getSelectedIndex()>0)fields+=" sound=\""+sound.getSelectedItem()+"\";";
        if(menu!=null)fields+=" hideMenu="+menu+";";
        int close=rule.lastIndexOf('}');return rule.substring(0,close)+fields+rule.substring(close);
    }
    void close(){closed=true;}
    private void error(Exception e){JOptionPane.showMessageDialog(this,e.getCause()==null?e.getMessage():e.getCause().getMessage(),"Sound unavailable",JOptionPane.WARNING_MESSAGE);}
}
