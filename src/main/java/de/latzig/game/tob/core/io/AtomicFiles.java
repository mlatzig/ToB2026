package de.latzig.game.tob.core.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Atomic text files (roadmap §6, Phase 5a spec decision 16): the text goes to a temporary file in the same folder,
 * which then replaces the target in one move, so a crash leaves either the old or the new file, never half of one.
 */
public final class AtomicFiles {

    private AtomicFiles() {
    }

    /** Writes {@code text} as UTF-8 to {@code file}, creating its folder. */
    public static void writeString(Path file, String text) throws IOException {
        Path target = file.toAbsolutePath();
        Path folder = target.getParent();
        Files.createDirectories(folder);
        Path temporary = Files.createTempFile(folder, target.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
