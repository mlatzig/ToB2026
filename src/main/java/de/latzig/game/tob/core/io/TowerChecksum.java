package de.latzig.game.tob.core.io;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import de.latzig.game.tob.core.model.TowerDefinition;

/**
 * The checksum of a tower that a recording was made on (Phase 3c, recording format version 2): SHA-256 over the
 * tower's canonical JSON ({@link TowerLibraryWriter#towerToString}, the writer of the tower libraries), UTF-8, as 64
 * lower-case hex digits. A replay on a tower with another checksum still runs; the client reports it, the solution
 * replay test fails.
 */
public final class TowerChecksum {

    private TowerChecksum() {
    }

    public static String of(TowerDefinition tower) {
        try {
            byte[] json = new TowerLibraryWriter().towerToString(tower).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
