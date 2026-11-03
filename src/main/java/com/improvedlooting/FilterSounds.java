package com.improvedlooting;

import java.io.*;
import java.util.Base64;
import net.runelite.client.util.Filepath;

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

        if (id(bytes, 0, "RIFF") && id(bytes, 8, "WAVE")) wave(bytes);
        else if (id(bytes, 0, "FORM") && (id(bytes, 8, "AIFF") || id(bytes, 8, "AIFC"))) aiff(bytes);
        else if (id(bytes, 0, ".snd")) au(bytes);
        else throw unsupported();
    }

    private static void wave(byte[] b)
    {
        int end = containerEnd(b, true), channels = 0, frameSize = 0;
        long rate = 0, dataSize = -1;
        boolean format = false;
        for (int at = 12; at < end;)
        {
            int size = chunkSize(b, at, end, true), body = at + 8;
            if (id(b, at, "fmt "))
            {
                if (format || size < 16) throw unsupported();
                format = true;
                int code = u16(b, body, true), bits = u16(b, body + 14, true);
                channels = u16(b, body + 2, true);
                rate = u32(b, body + 4, true);
                frameSize = u16(b, body + 12, true);
                if (code == 0xfffe)
                {

                    if (size < 40 || u16(b, body + 16, true) < 22
                        || u32(b, body + 26, true) != 0 || u16(b, body + 30, true) != 0x10
                        || u32(b, body + 32, false) != 0x800000aaL
                        || u32(b, body + 36, false) != 0x00389b71L) throw unsupported();
                    code = u16(b, body + 24, true);
                }
                boolean pcm = code == 1 && bits >= 8 && bits <= 32 && bits % 8 == 0;
                boolean floating = code == 3 && (bits == 32 || bits == 64);
                boolean companded = (code == 6 || code == 7) && bits == 8;
                if (!(pcm || floating || companded) || frameSize != channels * (bits / 8)
                    || u32(b, body + 8, true) != rate * frameSize) throw unsupported();
            }
            else if (id(b, at, "data"))
            {
                if (!format || dataSize >= 0) throw unsupported();
                dataSize = size;
            }
            at = body + size + (size & 1);
        }
        limits(channels, rate, frameSize, dataSize);
    }

    private static void aiff(byte[] b)
    {
        int end = containerEnd(b, false), channels = 0, frameSize = 0;
        long frames = -1, dataSize = -1;
        double rate = 0;
        boolean compressed = id(b, 8, "AIFC"), format = false;
        for (int at = 12; at < end;)
        {
            int size = chunkSize(b, at, end, false), body = at + 8;
            if (id(b, at, "COMM"))
            {
                if (format || size < (compressed ? 22 : 18)) throw unsupported();
                format = true;
                channels = u16(b, body, false);
                frames = u32(b, body + 2, false);
                int bits = u16(b, body + 6, false), exponent = u16(b, body + 8, false);
                if ((exponent & 0x8000) != 0 || exponent == 0 || exponent == 0x7fff) throw unsupported();
                rate = Math.scalb(u32(b, body + 10, false) * 4294967296.0
                    + u32(b, body + 14, false), exponent - 16383 - 63);
                boolean pcm = !compressed || id(b, body + 18, "NONE") || id(b, body + 18, "sowt");
                boolean companded = compressed && (id(b, body + 18, "ulaw") || id(b, body + 18, "alaw"));
                if (pcm && bits >= 1 && bits <= 32) frameSize = channels * ((bits + 7) / 8);
                else if (companded) frameSize = channels;
                else throw unsupported();
            }
            else if (id(b, at, "SSND"))
            {
                if (dataSize >= 0 || size < 8) throw unsupported();
                long offset = u32(b, body, false);
                if (offset > size - 8) throw unsupported();
                dataSize = size - 8 - offset;
            }
            at = body + size + (size & 1);
        }
        limits(channels, rate, frameSize, dataSize);
        if (!format || frames < 0 || frames * frameSize > dataSize || frames > rate * 15) throw unsupported();
    }

    private static void au(byte[] b)
    {
        if (b.length < 24) throw unsupported();
        long offset = u32(b, 4, false), dataSize = u32(b, 8, false);
        long encoding = u32(b, 12, false), rate = u32(b, 16, false), channels = u32(b, 20, false);
        int sampleBytes;
        if (encoding == 1 || encoding == 2 || encoding == 27) sampleBytes = 1;
        else if (encoding >= 3 && encoding <= 5) sampleBytes = (int)encoding - 1;
        else if (encoding == 6) sampleBytes = 4;
        else throw unsupported();
        if (offset < 24 || offset > b.length || dataSize > b.length - offset || channels > 2) throw unsupported();
        limits((int)channels, rate, (int)channels * sampleBytes, dataSize);
    }

    private static void limits(int channels, double rate, int frameSize, long dataSize)
    {
        if (channels < 1 || channels > 2 || !Double.isFinite(rate) || rate <= 0 || rate > 96000
            || frameSize <= 0 || dataSize < 0 || dataSize % frameSize != 0
            || dataSize / frameSize > rate * 15)
            throw new IllegalArgumentException("Use sounds of at most 15 seconds, stereo and 96 kHz");
    }

    private static int containerEnd(byte[] b, boolean little)
    {
        if (b.length < 12) throw unsupported();
        long size = u32(b, 4, little);
        if (size < 4 || size > b.length - 8) throw unsupported();
        return (int)size + 8;
    }

    private static int chunkSize(byte[] b, int at, int end, boolean little)
    {
        if (end - at < 8) throw unsupported();
        long size = u32(b, at + 4, little);
        if (size + (size & 1) > end - at - 8) throw unsupported();
        return (int)size;
    }

    private static boolean id(byte[] b, int at, String value)
    {
        if (at < 0 || at + 4 > b.length) return false;
        for (int i = 0; i < 4; i++) if (b[at + i] != value.charAt(i)) return false;
        return true;
    }
    private static int u16(byte[] b, int at, boolean little)
    {
        return little ? (b[at] & 255) | (b[at + 1] & 255) << 8 : (b[at] & 255) << 8 | (b[at + 1] & 255);
    }
    private static long u32(byte[] b, int at, boolean little)
    {
        return little ? u16(b, at, true) | (long)u16(b, at + 2, true) << 16
            : (long)u16(b, at, false) << 16 | u16(b, at + 2, false);
    }
    private static IllegalArgumentException unsupported()
    {
        return new IllegalArgumentException("Unsupported or malformed sound. Use a PCM WAV, AIFF or AU file.");
    }
}
