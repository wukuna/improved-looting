package com.improvedlooting;

import com.google.common.hash.Hashing;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.util.Filepath;

@Singleton
final class TierSounds
{
    private static final String PREFIX = "file:";
    interface Directory { Filepath get() throws IOException; }
    private final Directory directory;

    @Inject TierSounds(ImprovedLootingPlugin plugin) { this(plugin::soundDirectory); }
    TierSounds(Directory directory) { this.directory = directory; }

    static boolean reference(String value) { return value != null && value.startsWith(PREFIX); }
    static boolean validReference(String value) { return value != null && value.matches("file:[0-9a-f]{64}"); }

    byte[] read(String value)
    {
        if (!reference(value)) return FilterSounds.decode(value);
        if (!validReference(value)) throw new IllegalArgumentException("Invalid stored sound reference.");
        String id = value.substring(PREFIX.length());
        try (InputStream input = directory.get().joinSegment(id + ".audio").openInputStream())
        {
            byte[] data = input.readNBytes(1000001);
            if (!Hashing.sha256().hashBytes(data).toString().equals(id)) throw new IOException("Stored sound changed");
            FilterSounds.decode(Base64.getEncoder().encodeToString(data));
            return data;
        }
        catch (IOException e) { throw new IllegalArgumentException("Stored sound unavailable. Choose its file again or import a settings export containing it.", e); }
    }

    synchronized String store(byte[] data)
    {
        String id = Hashing.sha256().hashBytes(data).toString();
        Filepath temporary = null;
        try
        {
            Filepath root = directory.get();
            root.createDirectories();
            Filepath target = root.joinSegment(id + ".audio");
            if (target.exists())
            {
                try { read(PREFIX + id); return PREFIX + id; }
                catch (IllegalArgumentException ignored) { }
            }
            temporary = root.createTempFile("sound-", ".tmp");
            temporary.write(data);
            try { temporary.moveTo(target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { temporary.moveTo(target, StandardCopyOption.REPLACE_EXISTING); }
            read(PREFIX + id);
            return PREFIX + id;
        }
        catch (IOException e) { throw new IllegalArgumentException("Could not save the sound. Your previous tier settings are unchanged.", e); }
        finally
        {
            if (temporary != null) try { temporary.deleteIfExists(); } catch (IOException ignored) { }
        }
    }
}
