package com.improvedlooting;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.List;
import javax.swing.*;
import net.runelite.client.audio.AudioPlayer;
import net.runelite.client.util.Filepath;

final class PriceTierEditor
{
    private final PriceTiers store;
    private final AudioPlayer audio;
    private final java.util.function.Function<Component,Filepath> soundChooser;
    private JDialog dialog;
    private SpecialDropEditor specialEditor;
    private volatile boolean closed;
    private int busy;
    private JButton save;
    private final JTextArea status=new JTextArea("Changes apply only when saved. Sounds are kept on this computer; use a settings export to move them to another computer. WAV, AIFF or AU, up to 15 seconds / 1 MB each.",2,60);
    private final JSpinner[] minimum=new JSpinner[7];
    private final JLabel[] ranges=new JLabel[7];
    private final java.util.concurrent.atomic.AtomicBoolean playing=new java.util.concurrent.atomic.AtomicBoolean();
    PriceTierEditor(PriceTiers store,AudioPlayer audio){this(store,audio,PriceTierEditor::chooseSoundFile);}
    PriceTierEditor(PriceTiers store,AudioPlayer audio,java.util.function.Function<Component,Filepath> soundChooser){this.store=store;this.audio=audio;this.soundChooser=soundChooser;status.setLineWrap(true);status.setWrapStyleWord(true);status.setEditable(false);status.setOpaque(false);}
    void open(Component owner)
    {
        JPanel root=build(owner);if(root==null)return;
        dialog=new JDialog(SwingUtilities.getWindowAncestor(owner),"Drop tiers and sounds — Improved Looting",Dialog.ModalityType.MODELESS);dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);dialog.addWindowListener(new java.awt.event.WindowAdapter(){public void windowClosed(java.awt.event.WindowEvent e){closed=true;if(specialEditor!=null)specialEditor.close();}});dialog.setContentPane(root);dialog.setMinimumSize(new Dimension(880,470));dialog.setSize(1200,580);dialog.setLocationRelativeTo(owner);dialog.setVisible(true);
    }
    JPanel build(Component owner)
    {
        JPanel price = buildTiers(owner);
        if (price == null || store.specials == null) return price;
        JTabbedPane tabs = new JTabbedPane(); tabs.addTab("Price tiers", price);
        specialEditor = new SpecialDropEditor(store.specials, store, audio, soundChooser);
        tabs.addTab("Special drops", specialEditor.build(owner));
        JPanel root = new JPanel(new BorderLayout()); root.add(tabs); return root;
    }
    private JPanel buildTiers(Component owner)
    {
        PriceTiers.Ready current=store.snapshot();
        if(current==null){JOptionPane.showMessageDialog(owner,store.status());return null;}
        Long profile=ProfileIdentity.current(store.configs);String before=store.raw();
        PriceTiers.Setup draft=PriceTiers.copy(current.setup);
        if(!store.status().isEmpty())status.setText(store.status());
        JPanel root=new JPanel(new BorderLayout(10,10));root.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        JPanel heading=new JPanel(new BorderLayout(8,8));heading.add(new JLabel("<html><b>Seven drop tiers and their sounds</b><br>Total stack value using your selected Price source (GE or High Alchemy). Set each tier's sound beside its color and beam.<br>In Loot → Drop tiers &amp; sounds, enable Tier animations and sounds + Drop sounds for audio.<br>Beams need Loot beams enabled; unchecked tiers can still use explicit rule beams.</html>"),BorderLayout.CENTER);
        JPanel speed=new JPanel(new FlowLayout());speed.add(new JLabel("Cycle seconds"));JSpinner seconds=new JSpinner(new SpinnerNumberModel(draft.seconds,2,30,1));speed.add(seconds);heading.add(speed,BorderLayout.EAST);root.add(heading,BorderLayout.NORTH);
        JPanel rows=new JPanel(new GridBagLayout());GridBagConstraints c=new GridBagConstraints();c.insets=new Insets(5,5,5,5);c.fill=GridBagConstraints.HORIZONTAL;c.weightx=1;
        String[] headers={"Tier / value range","Minimum gp","Text effect","Color","Bold","Beam","Sound","Custom file"};
        for(int col=0;col<headers.length;col++){c.gridx=col;c.gridy=0;rows.add(new JLabel(headers[col]),c);}
        for(int i=0;i<7;i++) {
            final int index=i;PriceTiers.Tier tier=draft.tiers[i];c.gridy=i+1;c.gridx=0;ranges[i]=new JLabel();rows.add(ranges[i],c);
            c.gridx=1;minimum[i]=new JSpinner(new SpinnerNumberModel(Long.valueOf(tier.minimum),Long.valueOf(0),Long.valueOf(Long.MAX_VALUE),Long.valueOf(1000)));minimum[i].setPreferredSize(new Dimension(130,28));minimum[i].setEnabled(i!=0);rows.add(minimum[i],c);minimum[i].addChangeListener(e->updateRanges());
            c.gridx=2;JComboBox<PriceTiers.Effect> effect=new JComboBox<>(PriceTiers.Effect.values());effect.setSelectedItem(tier.effect);effect.addActionListener(e->tier.effect=(PriceTiers.Effect)effect.getSelectedItem());rows.add(effect,c);
            c.gridx=3;JButton color=new JButton("Choose");color.setForeground(new Color(tier.color));color.setToolTipText("Used when Text effect is Tier color");color.addActionListener(e->{Color next=JColorChooser.showDialog(dialog,"Tier "+(index+1)+" color",new Color(tier.color));if(next!=null){tier.color=next.getRGB()&0xffffff;color.setForeground(next);}});rows.add(color,c);
            c.gridx=4;JCheckBox bold=new JCheckBox("",tier.bold);bold.setHorizontalAlignment(SwingConstants.CENTER);bold.setToolTipText("Bold card text for tier "+(i+1));bold.getAccessibleContext().setAccessibleName("Bold tier "+(i+1));bold.addActionListener(e->tier.bold=bold.isSelected());rows.add(bold,c);
            c.gridx=5;JCheckBox beam=new JCheckBox("",tier.lootBeam);beam.setHorizontalAlignment(SwingConstants.CENTER);beam.setToolTipText("Add a beam for visible items in tier "+(i+1)+". No filter rule required; existing rule beams are preserved.");beam.getAccessibleContext().setAccessibleName("Beam tier "+(i+1));beam.addActionListener(e->tier.lootBeam=beam.isSelected());rows.add(beam,c);
            c.gridx=6;JComboBox<PriceTiers.Sound> sound=new JComboBox<>(PriceTiers.Sound.values());sound.setSelectedItem(tier.sound);sound.addActionListener(e->tier.sound=(PriceTiers.Sound)sound.getSelectedItem());rows.add(sound,c);
            c.gridx=7;JPanel files=new JPanel(new FlowLayout(FlowLayout.LEFT,3,0));JButton choose=new JButton(tier.audio==null?"Choose file":"Replace file"),preview=new JButton("▶"),clear=new JButton("Clear");files.add(choose);files.add(preview);files.add(clear);preview.setToolTipText("Preview custom sound");preview.setEnabled(current.audio[i]!=null);JLabel filename=new JLabel();filename.getAccessibleContext().setAccessibleName("Sound file tier "+(i+1));fileName(filename,tier.name);JPanel fileRow=new JPanel(new BorderLayout());fileRow.add(files,BorderLayout.NORTH);fileRow.add(filename,BorderLayout.SOUTH);rows.add(fileRow,c);
            choose.setToolTipText(tier.name);
            choose.addActionListener(e->{Filepath selected=soundChooser.apply(dialog);if(selected==null)return;busy++;save.setEnabled(false);choose.setEnabled(false);
                new SwingWorker<String,Void>(){protected String doInBackground()throws Exception{return FilterSounds.read(selected);}protected void done(){busy--;if(closed)return;choose.setEnabled(true);save.setEnabled(busy==0);try{tier.audio=get();tier.name=selected.getFileName();tier.sound=PriceTiers.Sound.CUSTOM;sound.setSelectedItem(tier.sound);choose.setText("Replace file");choose.setToolTipText(tier.name);fileName(filename,tier.name);preview.setEnabled(true);status.setText("Sound ready; save to apply.");}catch(Exception ex){error(ex);}}}.execute();});
            preview.addActionListener(e->{if(tier.audio==null || !playing.compareAndSet(false,true))return;String encoded=tier.audio;int gain=volume();preview.setEnabled(false);new SwingWorker<Void,Void>(){protected Void doInBackground()throws Exception{if(!closed)audio.play(new ByteArrayInputStream(store.decodeAudio(encoded)),LootAlerts.gain(gain));return null;}protected void done(){playing.set(false);if(closed)return;preview.setEnabled(tier.audio!=null);try{get();}catch(Exception ex){error(ex);}}}.execute();});
            clear.addActionListener(e->{tier.audio=null;tier.name=null;tier.sound=PriceTiers.Sound.RULE;sound.setSelectedItem(tier.sound);preview.setEnabled(false);choose.setText("Choose file");choose.setToolTipText(null);fileName(filename,null);});
        }
        updateRanges();JScrollPane scroll=new JScrollPane(rows);root.add(scroll,BorderLayout.CENTER);
        JPanel footer=new JPanel(new BorderLayout(8,8));footer.add(status,BorderLayout.CENTER);JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT));JButton cancel=new JButton("Cancel");cancel.addActionListener(e->close());save=new JButton("Save tiers");actions.add(cancel);actions.add(save);footer.add(actions,BorderLayout.SOUTH);root.add(footer,BorderLayout.SOUTH);
        save.addActionListener(e->{try{seconds.commitEdit();for(int i=0;i<7;i++){minimum[i].commitEdit();draft.tiers[i].minimum=((Number)minimum[i].getValue()).longValue();}draft.seconds=((Number)seconds.getValue()).intValue();}catch(java.text.ParseException ex){error(ex);return;}save.setEnabled(false);busy++;
            PriceTiers.Setup captured=PriceTiers.copy(draft);
            new SwingWorker<PriceTiers.Ready,Void>(){protected PriceTiers.Ready doInBackground(){return store.prepareForSave(captured);}protected void done(){busy--;if(closed)return;try{if(!Objects.equals(profile,ProfileIdentity.current(store.configs)) || !Objects.equals(before,store.raw()))throw new IllegalArgumentException("Profile or tiers changed elsewhere. Reopen the editor.");store.save(get());close();}catch(Exception ex){save.setEnabled(busy==0);error(ex);}}}.execute();});
        return root;
    }
    private int volume(){String raw=store.configs.getConfiguration(ImprovedLootingConfig.GROUP,"alertVolume");try{return raw==null?80:Integer.parseInt(raw);}catch(NumberFormatException e){return 80;}}
    private static Filepath chooseSoundFile(Component owner)
    {
        List<Filepath> selected=new Filepath.Chooser().setIsOpen().setAcceptsFiles().addExtensionFilter("Sound","wav","aiff","aif","au").showDialog(owner);
        return selected==null || selected.isEmpty()?null:selected.get(0);
    }
    private static void fileName(JLabel label,String name){label.setText(name==null?"No custom file":name.length()>30?name.substring(0,27)+"…":name);label.setToolTipText(name);}
    private void updateRanges(){for(int i=0;i<7;i++){if(ranges[i]==null)continue;String end=i==6?"and up":minimum[i+1]==null?"": "to "+java.text.NumberFormat.getIntegerInstance().format(((Number)minimum[i+1].getValue()).longValue()-1);ranges[i].setText("<html>Tier "+(i+1)+"<br>"+end+"</html>");}}
    private void error(Exception ex){status.setText("Not saved: "+(ex.getCause()==null?ex.getMessage():ex.getCause().getMessage()));}
    void close(){closed=true;if(specialEditor!=null)specialEditor.close();if(dialog!=null)dialog.dispose();}
}
