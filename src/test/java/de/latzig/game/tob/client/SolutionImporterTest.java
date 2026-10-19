package de.latzig.game.tob.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.latzig.game.tob.client.SolutionImporter.Line;
import de.latzig.game.tob.client.SolutionImporter.Result;
import de.latzig.game.tob.client.logic.game.RecordingStore;
import de.latzig.game.tob.core.io.RecordingReader;
import de.latzig.game.tob.core.io.TowerChecksum;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.sim.Command;
import de.latzig.game.tob.core.sim.Recorder;
import de.latzig.game.tob.core.sim.Recording;
import de.latzig.game.tob.core.sim.RecordingMeta;
import de.latzig.game.tob.core.sim.RecordingMeta.Outcome;
import de.latzig.game.tob.core.sim.SimOptions;
import de.latzig.game.tob.core.sim.Simulation;
import de.latzig.game.tob.core.sim.SpiderId;
import de.latzig.game.tob.core.sim.TestTowers;

class SolutionImporterTest {

    private static final Instant T0 = Instant.parse("2026-10-09T18:30:05Z");

    /** One robot to destroy: the zapper turns east and zaps the klondike in front of it. */
    private static TowerDefinition tower(String id) {
        return TestTowers.tower().level(0, "FFF").object(0, 0, 0, ObjectType.ZAP).object(2, 0, 0, ObjectType.KLD)
                .conditions(0, false, 0, 1).id(id).build();
    }

    /** The solution of {@link #tower}, recorded at {@code at} with the given checksum. */
    private static Recording solution(TowerDefinition tower, Instant at, String checksum) {
        Recorder recorder = new Recorder(Simulation.start(tower, SimOptions.DEFAULT));
        recorder.step(List.of(new Command.TurnRight(SpiderId.ZAPPER)));
        for (int i = 0; i < 4; i++) {
            recorder.step(List.of());
        }
        recorder.step(List.of(new Command.Fire(SpiderId.ZAPPER)));
        while (!recorder.simulation().isFinished()) {
            recorder.step(List.of());
        }
        return recorder.recording().withMeta(new RecordingMeta(Outcome.COMPLETED, at,
                recorder.simulation().frame(), checksum, "test"));
    }

    @Test
    void copiesTheNewestSolutionPerTowerSkipsExistingOnesAndRejectsNonSolutions(@TempDir Path userDir,
            @TempDir Path target) throws IOException {
        RecordingStore store = new RecordingStore(userDir, ZoneOffset.UTC);
        TowerDefinition fresh = tower("tower-a");
        TowerDefinition existing = tower("tower-b");
        TowerDefinition changed = tower("tower-c");
        TowerDefinition unplayed = tower("tower-d");
        store.save(solution(fresh, T0, TowerChecksum.of(fresh)));
        Path newest = store.save(solution(fresh, T0.plusSeconds(60), TowerChecksum.of(fresh)));
        store.save(solution(existing, T0, TowerChecksum.of(existing)));
        store.save(solution(changed, T0, "0".repeat(64)));
        Files.writeString(target.resolve("tower-b.json"), "the old solution", StandardCharsets.UTF_8);

        List<Line> lines = SolutionImporter.run(store, target, List.of(fresh, existing, changed, unplayed), Set.of());

        assertEquals(List.of(Result.COPIED, Result.SKIPPED, Result.FAILED), lines.stream().map(Line::result).toList());
        assertEquals(List.of("tower-a", "tower-b", "tower-c"), lines.stream().map(Line::towerId).toList());
        assertTrue(lines.get(2).detail().contains("tower changed"), lines.get(2).detail());
        assertEquals(Files.readString(newest), Files.readString(target.resolve("tower-a.json")), "the newest one");
        assertEquals("the old solution", Files.readString(target.resolve("tower-b.json")));
        assertFalse(Files.exists(target.resolve("tower-c.json")));

        List<Line> replaced = SolutionImporter.run(store, target, List.of(existing), Set.of("tower-b"));

        assertEquals(Result.COPIED, replaced.get(0).result());
        assertEquals("tower-b", new RecordingReader().read(target.resolve("tower-b.json")).towerId());
    }

    @Test
    void theOnlyArgumentIsReplaceWithTowerIds() {
        assertEquals(Set.of(), SolutionImporter.replaceSet(new String[0]));
        assertEquals(Set.of("original-001", "original-002", "tutorial-003"), SolutionImporter.replaceSet(
                new String[] {"--replace", "original-001,original-002", "tutorial-003"}));
        assertThrows(IllegalArgumentException.class, () -> SolutionImporter.replaceSet(new String[] {"--all"}));
        assertThrows(IllegalArgumentException.class, () -> SolutionImporter.replaceSet(new String[] {"--replace"}));
        assertThrows(IllegalArgumentException.class, () -> SolutionImporter.replaceSet(new String[] {"x"}));
    }
}
