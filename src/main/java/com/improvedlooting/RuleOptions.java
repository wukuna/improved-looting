package com.improvedlooting;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.util.*;
import javax.swing.*;
import net.runelite.client.util.Filepath;

/** Visual editor for standard RS2F fields; no changes to the filter language. */
final class RuleOptions extends JPanel
{
    private final JCheckBox value=new JCheckBox("Value",true),timer=new JCheckBox("Timer",true),beam=new JCheckBox("Beam"),tile=new JCheckBox("Tile highlight"),notify=new JCheckBox("Notify"),colorEnabled=new JCheckBox("Text color");
    private final JSpinner minimum=new JSpinner(new SpinnerNumberModel(0,0,Integer.MAX_VALUE,1000));
    private final JComboBox<String> sound=new JComboBox<>();
    private final Map<String,String> sounds;
    private final ItemIcons icons;
    private Color color=Color.YELLOW;
    private volatile boolean closed;
    private boolean loading,playing;
    RuleOptions(ItemIcons icons,Map<String,String> sounds)
    {
        super(new GridLayout(0,4,5,4));this.icons=icons;this.sounds=sounds;
        add(value);add(timer);add(beam);add(tile);add(notify);add(colorEnabled);
        JButton chooseColor=new JButton("Choose color");chooseColor.addActionListener(e->{Color next=JColorChooser.showDialog(this,"Rule text color",color);if(next!=null){color=next;colorEnabled.setSelected(true);chooseColor.setForeground(next);}});add(chooseColor);JPanel threshold=new JPanel(new BorderLayout());threshold.add(new JLabel("Min stack GE value"),BorderLayout.NORTH);threshold.add(minimum,BorderLayout.CENTER);add(threshold);
        add(new JLabel("Drop sound"));sound.addItem("Keep filter sound");for(String name:sounds.keySet())sound.addItem(name);add(sound);
        JButton file=new JButton("Add sound file"),preview=new JButton("Preview sound");add(file);add(preview);
        file.addActionListener(e->{java.util.List<Filepath> chosen=new Filepath.Chooser().setIsOpen().setAcceptsFiles().addExtensionFilter("Audio","wav","aiff","aif","au").showDialog(this);if(chosen.isEmpty())return;
            loading=true;file.setEnabled(false);new SwingWorker<String,Void>(){protected String doInBackground()throws Exception{return FilterSounds.read(chosen.get(0));}protected void done(){loading=false;if(closed)return;file.setEnabled(true);try{String name="sounds/"+chosen.get(0).getFileName();IconImages.safeName(name);String encoded=get();if(sounds.containsKey(name)&&!sounds.get(name).equals(encoded))throw new IllegalArgumentException("A different sound has this filename. Rename the file first.");sounds.put(name,encoded);boolean exists=false;for(int i=0;i<sound.getItemCount();i++)if(name.equals(sound.getItemAt(i)))exists=true;if(!exists)sound.addItem(name);sound.setSelectedItem(name);}catch(Exception ex){error(ex);}}}.execute();});
        preview.addActionListener(e->{String encoded=sounds.get(sound.getSelectedItem());if(encoded==null||playing)return;playing=true;preview.setEnabled(false);
            new SwingWorker<Void,Void>(){protected Void doInBackground()throws Exception{if(!closed)icons.audio.play(new ByteArrayInputStream(FilterSounds.decode(encoded)),LootAlerts.gain(volume()));return null;}protected void done(){playing=false;if(closed)return;preview.setEnabled(true);try{get();}catch(Exception ex){error(ex);}}}.execute();});
    }
    private int volume(){String raw=icons.configs.getConfiguration(ImprovedLootingConfig.GROUP,"alertVolume");try{return raw==null?80:Integer.parseInt(raw);}catch(NumberFormatException e){return 80;}}
    String apply(String rule)
    {
        if(loading)throw new IllegalArgumentException("Wait for the sound import to finish");
        int min=(Integer)minimum.getValue();if(min>0){int end=rule.indexOf(") {");rule=rule.substring(0,end)+" && gevalue:>="+min+rule.substring(end);}
        String fields=" showValue="+value.isSelected()+"; showDespawn="+timer.isSelected()+"; showLootbeam="+beam.isSelected()+"; highlightTile="+tile.isSelected()+"; notify="+notify.isSelected()+";";
        if(colorEnabled.isSelected())fields+=" textColor=\""+String.format("#%06x",color.getRGB()&0xffffff)+"\";";
        if(sound.getSelectedIndex()>0)fields+=" sound=\""+sound.getSelectedItem()+"\";";
        int close=rule.lastIndexOf('}');return rule.substring(0,close)+fields+rule.substring(close);
    }
    void close(){closed=true;}
    private void error(Exception e){JOptionPane.showMessageDialog(this,e.getCause()==null?e.getMessage():e.getCause().getMessage(),"Sound unavailable",JOptionPane.WARNING_MESSAGE);}
}
