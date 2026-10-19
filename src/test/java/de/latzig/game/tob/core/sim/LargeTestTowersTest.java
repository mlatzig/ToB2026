package de.latzig.game.tob.core.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.latzig.game.tob.core.io.TowerLibraryReader;
import de.latzig.game.tob.core.io.TowerLibraryWriter;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.model.TowerExtent;
import de.latzig.game.tob.core.model.TowerLibrary;

/**
 * Phase 6a spec decision 6: the four large test towers in {@code src/test/resources/towers/large.json} are the
 * generator's ({@link LargeTestTowers}), pass {@link TowerCheck} without errors and unplayable findings, have the
 * specified sizes, and survive 2000 frames of random input with an exact replay.
 */
class LargeTestTowersTest {

    private static final int FRAMES = 2000;

    @Test
    void theCommittedLibraryIsTheGeneratorsOutput() throws IOException {
        TowerLibrary library = LargeTestTowers.library();
        String json = new TowerLibraryWriter().writeToString(library);
        if (Boolean.getBoolean("tob.writeTowers")) {
            Files.createDirectories(LargeTestTowers.FILE.getParent());
            new TowerLibraryWriter().write(library, LargeTestTowers.FILE);
            System.out.println("LargeTestTowersTest: wrote " + LargeTestTowers.FILE.toAbsolutePath());
        }

        assertTrue(Files.isRegularFile(LargeTestTowers.FILE),
                "generate it: mvn -o test -Dtest=LargeTestTowersTest -Dtob.writeTowers=true");
        assertEquals(json, Files.readString(LargeTestTowers.FILE, StandardCharsets.UTF_8),
                "the file is the generator's output; regenerate it with -Dtob.writeTowers=true");
        assertEquals(library, new TowerLibraryReader().read(LargeTestTowers.FILE), "the strict reader accepts it");
    }

    @Test
    void everyTowerPassesTheTowerCheckWithoutErrorsOrUnplayableFindings() {
        for (TowerDefinition tower : LargeTestTowers.library().towers()) {
            List<TowerCheck.Finding> findings = TowerCheck.check(tower).stream()
                    .filter(finding -> finding.severity() != TowerCheck.Severity.HINT).toList();

            assertEquals(List.of(), findings, tower.id());
        }
    }

    @Test
    void theTowersHaveTheirSpecifiedSizes() {
        TowerExtent full = extent(LargeTestTowers.FULL);
        TowerExtent narrow = extent(LargeTestTowers.NARROW);
        TowerExtent crowded = extent(LargeTestTowers.CROWDED);
        TowerExtent solvable = extent(LargeTestTowers.SOLVABLE);

        assertEquals(new TowerExtent(0, 0, 15, 15, 5), full, "the full 16 x 16 x 6 grid");
        assertEquals(new TowerExtent(0, 6, 15, 9, 3), narrow, "16 x 4 on four levels");
        assertEquals(new TowerExtent(3, 3, 12, 12, 4), crowded, "10 x 10 x 5, just beyond 8 x 8");
        assertEquals(new TowerExtent(0, 0, 15, 15, 1), solvable);
        for (TowerDefinition tower : LargeTestTowers.library().towers()) {
            assertFalse(TowerExtent.isOriginalSize(tower.grid()), tower.id() + " is large");
        }
        assertEquals(35, LargeTestTowers.tower(LargeTestTowers.CROWDED).grid().objectCount(), "more than 20");
        assertEquals(5, liftShafts(LargeTestTowers.tower(LargeTestTowers.FULL)) - 1,
                "five up lifts chain level 0 to level 5, plus one down lift");
    }

    @ParameterizedTest
    @ValueSource(strings = {LargeTestTowers.FULL, LargeTestTowers.NARROW, LargeTestTowers.CROWDED,
        LargeTestTowers.SOLVABLE})
    void everyTowerRunsTwoThousandRandomFramesAndReplaysExactly(String id) {
        TowerDefinition tower = LargeTestTowers.tower(id);
        Recorder recorder = new Recorder(Simulation.start(tower, SimOptions.DEFAULT));
        RandomCommands random = new RandomCommands(20261014L);
        List<SimEvent> events = new ArrayList<>();
        int frames = 0;
        while (frames < FRAMES && !recorder.simulation().isFinished()) {
            events.addAll(recorder.step(random.next()));
            frames++;
            assertEquals(List.of(), recorder.simulation().world().occupancyProblems(), id + " step " + frames);
        }
        List<SimEvent> replayed = new ArrayList<>();
        Simulation replay = Replay.run(recorder.recording(), tower, replayed);

        assertTrue(frames > 100, id + " ran " + frames + " frames");
        assertEquals(events, replayed, id + ": the replay produced the same events");
        assertEquals(Snapshots.of(recorder.simulation()), Snapshots.of(replay), id + ": the same end state");
    }

    private static TowerExtent extent(String id) {
        return TowerExtent.of(LargeTestTowers.tower(id).grid()).orElseThrow();
    }

    private static int liftShafts(TowerDefinition tower) {
        return WorldState.load(tower, SimOptions.DEFAULT).lifts().size();
    }
}
