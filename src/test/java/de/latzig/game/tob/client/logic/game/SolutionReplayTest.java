package de.latzig.game.tob.client.logic.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import de.latzig.game.tob.core.io.RecordingReader;
import de.latzig.game.tob.core.io.TowerLibraryReader;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.sim.LargeTestTowers;
import de.latzig.game.tob.core.sim.Recording;
import de.latzig.game.tob.core.sim.TowerAssets;

/**
 * Phase 3c spec §2 "Replay test": every committed solution {@code src/test/resources/solutions/**}{@code /*.json}
 * replays headless to its recorded length and ends with COMPLETED on a tower with the recorded checksum
 * ({@link SolutionCheck}). The solutions are named {@code <tower-id>.json}; the importer adds them. Phase 6a: the
 * towers of the large test library ({@link LargeTestTowers#FILE}) can have solutions too.
 */
class SolutionReplayTest {

    static final Path SOLUTIONS = Path.of("src/test/resources/solutions");

    private static Map<String, TowerDefinition> towers;

    static List<Path> solutions() throws IOException {
        try (Stream<Path> files = Files.walk(SOLUTIONS)) {
            return files.filter(file -> file.getFileName().toString().endsWith(".json")).sorted().toList();
        }
    }

    private static synchronized Map<String, TowerDefinition> towers() throws IOException {
        if (towers == null) {
            towers = new HashMap<>();
            TowerAssets.allTowers().forEach(tower -> towers.put(tower.id(), tower));
            new TowerLibraryReader().read(LargeTestTowers.FILE).towers()
                    .forEach(tower -> towers.put(tower.id(), tower));
        }
        return towers;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("solutions")
    void everyCommittedSolutionCompletesOnItsTower(Path file) throws IOException {
        assumeTrue(TowerAssets.available(), "tower assets not generated");
        Recording recording = new RecordingReader().read(file);
        TowerDefinition tower = towers().get(recording.towerId());

        assertNotNull(tower, "unknown tower " + recording.towerId());
        assertEquals(recording.towerId() + ".json", file.getFileName().toString());
        assertEquals(Optional.empty(), SolutionCheck.problem(recording, tower), file.toString());
    }

    @Test
    void theFirstSolutionIsCommitted() {
        assertTrue(Files.isRegularFile(SOLUTIONS.resolve("original-000.json")),
                "generate it: mvn test -Dtest=SoftlySoftlySolutionTest -Dtob.writeSolutions=true");
    }
}
