package com.improvedlooting;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Base64;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.junit.Test;
import static org.junit.Assert.*;

/** Independent JDK writers generate fixtures; no device is opened or sound played. */
public class FilterSoundsTest
{
    private static final AudioFileFormat.Type[] TYPES = {
        AudioFileFormat.Type.WAVE, AudioFileFormat.Type.AIFF, AudioFileFormat.Type.AU
    };
    private byte[] sound(AudioFileFormat.Type type, int rate, int channels, int seconds) throws Exception
    {
        byte[] samples = new byte[rate * channels * 2 * seconds];
        AudioFormat format = new AudioFormat(rate, 16, channels, true, type != AudioFileFormat.Type.WAVE);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (AudioInputStream input = new AudioInputStream(new ByteArrayInputStream(samples), format, rate * seconds))
        { AudioSystem.write(input, type, output); }
        return output.toByteArray();
    }
    private byte[] decode(byte[] bytes) { return FilterSounds.decode(Base64.getEncoder().encodeToString(bytes)); }
    private void reject(byte[] bytes)
    {
        try { decode(bytes); fail("Accepted invalid audio"); }
        catch (IllegalArgumentException expected) { assertNotNull(expected.getMessage()); }
    }
    @Test public void acceptsRealPcmFilesWithoutChangingThePayload() throws Exception
    {
        for (AudioFileFormat.Type type : TYPES) for (int channels : new int[]{1, 2})
        { byte[] bytes = sound(type, 44100, channels, 1); assertArrayEquals(bytes, decode(bytes)); }
    }
    @Test public void durationBoundaryIsEnforcedForEveryContainer() throws Exception
    {
        for (AudioFileFormat.Type type : TYPES)
        { byte[] bytes = sound(type, 8000, 1, 15); assertArrayEquals(bytes, decode(bytes)); reject(sound(type, 8000, 1, 16)); }
    }
    @Test public void rejectsExcessSizeChannelsAndSampleRate() throws Exception
    {
        reject(new byte[1000001]);
        for (AudioFileFormat.Type type : TYPES)
        { reject(sound(type, 8000, 3, 1)); reject(sound(type, 96001, 1, 1)); }
    }
    @Test public void rejectsTruncatedFilesAndUnknownFormats() throws Exception
    {
        for (AudioFileFormat.Type type : TYPES)
        { byte[] bytes = sound(type, 8000, 1, 1); reject(Arrays.copyOf(bytes, bytes.length - 10)); }
        for (int size = 0; size < 24; size++) reject(new byte[size]);
    }
    @Test public void rejectsOversizedChunksAndInvalidWaveFrameSize() throws Exception
    {
        byte[] bytes = sound(AudioFileFormat.Type.WAVE, 8000, 1, 1);
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(16, -1); reject(bytes);
        bytes = sound(AudioFileFormat.Type.WAVE, 8000, 1, 1);
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putShort(32, (short)0); reject(bytes);
    }
    @Test public void waveSkipsOddPaddedMetadataChunks() throws Exception
    {
        byte[] bytes = sound(AudioFileFormat.Type.WAVE, 8000, 1, 1), padded = new byte[bytes.length + 10];
        System.arraycopy(bytes, 0, padded, 0, 12); System.arraycopy(bytes, 12, padded, 22, bytes.length - 12);
        ByteBuffer b = ByteBuffer.wrap(padded).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(4, padded.length - 8); b.position(12); b.put(new byte[]{'J','U','N','K'}); b.putInt(1); b.put((byte)7); b.put((byte)0);
        assertArrayEquals(padded, decode(padded));
    }
    @Test public void rejectsUnknownAuSizeAndInvalidAiffRate() throws Exception
    {
        byte[] bytes = sound(AudioFileFormat.Type.AU, 8000, 1, 1);
        ByteBuffer.wrap(bytes).putInt(8, -1); reject(bytes);
        bytes = sound(AudioFileFormat.Type.AIFF, 8000, 1, 1);
        // AIFF COMM rate is the 80-bit field at byte 28 in this independent fixture.
        ByteBuffer.wrap(bytes).putShort(28, (short)0x7fff); reject(bytes);
    }
}
