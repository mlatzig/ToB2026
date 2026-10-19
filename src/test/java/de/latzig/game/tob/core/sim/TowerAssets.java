package de.latzig.game.tob.core.sim;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import de.latzig.game.tob.core.io.TowerLibraryReader;
import de.latzig.game.tob.core.model.TowerDefinition;

/** The 144 converted original towers (Phase 1a output, svn-ignored); tests skip if they are missing. */
public final class TowerAssets {

    /** Library files in game order: 117 + 9 + 18 = 144 towers. */
    public static final List<String> LIBRARIES = List.of("original", "tutorial", "demo");

    private TowerAssets() {
    }

    public static Path directory() {
        return Path.of(System.getProperty("tob.towers", "src/main/resources/assets/towers"));
    }

    public static boolean available() {
        return LIBRARIES.stream().allMatch(name -> Files.isRegularFile(directory().resolve(name + ".json")));
    }

    public static List<TowerDefinition> allTowers() throws IOException {
        TowerLibraryReader reader = new TowerLibraryReader();
        List<TowerDefinition> towers = new ArrayList<>();
        for (String name : LIBRARIES) {
            towers.addAll(reader.read(directory().resolve(name + ".json")).towers());
        }
        return towers;
    }
}
