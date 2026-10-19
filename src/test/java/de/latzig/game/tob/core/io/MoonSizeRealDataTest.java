package de.latzig.game.tob.core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.model.TowerLibrary;
import de.latzig.game.tob.core.sim.TowerAssets;

/**
 * Phase 6b-2 Task 0: the optional stored moon size must leave the original towers untouched. None of the 144
 * converted towers (117 + 9 + 18) has a stored size (the converter never writes it), so re-writing them through
 * {@link TowerLibraryWriter} must reproduce the files byte for byte - and therefore leaves {@link TowerChecksum}
 * (SHA-256 over that same JSON) unchanged, which is what the recorded solutions rely on.
 */
class MoonSizeRealDataTest {

    private final TowerLibraryReader reader = new TowerLibraryReader();
    private final TowerLibraryWriter writer = new TowerLibraryWriter();

    @Test
    void realTowersHaveNoStoredMoonSize() throws IOException {
        assumeTrue(TowerAssets.available(), "tower assets not generated");

        int checked = 0;
        for (TowerDefinition tower : TowerAssets.allTowers()) {
            assertEquals(Optional.empty(), tower.moon().size(), tower.id() + ": no original tower stores a size");
            checked++;
        }
        assertEquals(144, checked);
    }

    @Test
    void realLibraryFilesAreRewrittenByteIdentical() throws IOException {
        assumeTrue(TowerAssets.available(), "tower assets not generated");

        for (String name : TowerAssets.LIBRARIES) {
            String original = Files.readString(TowerAssets.directory().resolve(name + ".json"), StandardCharsets.UTF_8);
            TowerLibrary library = reader.readFromString(original);

            String rewritten = writer.writeToString(library);

            assertEquals(original, rewritten, name + ".json must stay byte-identical with the new optional field");
        }
    }
}
