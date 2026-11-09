package com.improvedlooting;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sound.sampled.*;
import net.runelite.client.util.Filepath;

final class TierSoundTestFiles
{
    static TierSounds temporary()
    {
        try{Filepath root=Filepath.Unchecked.getRooted(Files.createTempDirectory(Path.of("build"),"tier-sounds-"));return new TierSounds(()->root);}
        catch(java.io.IOException e){throw new AssertionError(e);}
    }
    static byte[] wave(int frames,int seed) throws Exception
    {
        byte[] samples=new byte[frames*2];new java.util.Random(seed).nextBytes(samples);
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(AudioInputStream input=new AudioInputStream(new ByteArrayInputStream(samples),new AudioFormat(44100,16,1,true,false),frames))
        { AudioSystem.write(input,AudioFileFormat.Type.WAVE,out); }
        return out.toByteArray();
    }
}
