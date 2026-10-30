package com.improvedlooting;

import java.io.*;
import java.util.Base64;
import javax.sound.sampled.*;
import net.runelite.client.util.Filepath;

/** Bounded, explicitly imported audio assets; no paths are opened during gameplay. */
final class FilterSounds
{
    static byte[] decode(String encoded)
    {
        if (encoded == null || encoded.length() > 1400000) throw new IllegalArgumentException("Sound exceeds 1 MB");
        byte[] bytes = Base64.getDecoder().decode(encoded);
        validate(bytes); return bytes;
    }
    static String read(Filepath path) throws IOException
    {
        try (InputStream in = path.openInputStream()) {
            byte[] bytes = in.readNBytes(1000001); validate(bytes); return Base64.getEncoder().encodeToString(bytes);
        }
    }
    private static void validate(byte[] bytes)
    {
        if (bytes.length > 1000000) throw new IllegalArgumentException("Sound exceeds 1 MB");
        try (AudioInputStream audio = AudioSystem.getAudioInputStream(new ByteArrayInputStream(bytes))) {
            AudioFormat f = audio.getFormat();
            if (audio.getFrameLength() < 0 || f.getFrameRate() <= 0 || audio.getFrameLength() / f.getFrameRate() > 15 || f.getChannels() > 2 || f.getSampleRate() > 96000)
                throw new IllegalArgumentException("Use sounds of at most 15 seconds, stereo and 96 kHz");
        } catch (IOException | UnsupportedAudioFileException ex) { throw new IllegalArgumentException("Unsupported sound. Use a WAV, AIFF or AU file.", ex); }
    }
}
