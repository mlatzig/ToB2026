package de.latzig.game.tob.client.logic.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.io.TowerChecksum;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.sim.Command;
import de.latzig.game.tob.core.sim.Recorder;
import de.latzig.game.tob.core.sim.Recording;
import de.latzig.game.tob.core.sim.RecordingMeta;
import de.latzig.game.tob.core.sim.RecordingMeta.Outcome;
import de.latzig.game.tob.core.sim.Rules;
import de.latzig.game.tob.core.sim.SimOptions;
import de.latzig.game.tob.core.sim.Simulation;
import de.latzig.game.tob.core.sim.SpiderId;
import de.latzig.game.tob.core.sim.TestTowers;

class SolutionCheckTest {

    /** One robot to destroy: the zapper turns east and zaps the klondike in front of it (oracle T-17). */
    private static TowerDefinition tower() {
        return TestTowers.tower().level(0, "FFF").object(0, 0, 0, ObjectType.ZAP).object(2, 0, 0, ObjectType.KLD)
                .conditions(0, false, 0, 1).build();
    }

    private static Recording solution(TowerDefinition tower) {
        Recorder recorder = new Recorder(Simulation.start(tower, SimOptions.DEFAULT));
        recorder.step(List.of(new Command.TurnRight(SpiderId.ZAPPER)));
        for (int i = 0; i < 4; i++) {
            recorder.step(List.of());
        }
        recorder.step(List.of(new Command.Fire(SpiderId.ZAPPER)));
        while (!recorder.simulation().isFinished()) {
            recorder.step(List.of());
        }
        assertEquals(Rules.EXIT_COMPLETED, recorder.simulation().exitCode(), "the scripted solution works");
        return recorder.recording().withMeta(meta(TowerChecksum.of(tower)));
    }

    private static RecordingMeta meta(String checksum) {
        return new RecordingMeta(Outcome.COMPLETED, Instant.parse("2026-10-09T18:30:05Z"), 30, checksum, "test");
    }

    @Test
    void aRecordingThatCompletesAtItsLastStepOnTheSameTowerIsASolution() {
        TowerDefinition tower = tower();

        assertEquals(Optional.empty(), SolutionCheck.problem(solution(tower), tower));
    }

    @Test
    void everyOtherRecordingIsReportedWithItsProblem() {
        TowerDefinition tower = tower();
        Recording solution = solution(tower);
        RecordingMeta meta = solution.meta().orElseThrow();

        assertProblem("recorded on test-000, not on other",
                solution, TestTowers.tower().level(0, "FFF").object(0, 0, 0, ObjectType.ZAP).id("other").build());
        assertProblem("no metadata", new Recording(solution.towerId(), solution.options(), solution.length(),
                solution.entries()), tower);
        assertProblem("tower changed", solution.withMeta(meta("0".repeat(64))), tower);
        assertProblem("not finished after all 6 steps", new Recording(solution.towerId(), solution.options(), 6,
                solution.entries(), solution.meta()), tower);
        assertProblem("completed at step " + solution.length() + " of " + (solution.length() + 5),
                new Recording(solution.towerId(), solution.options(), solution.length() + 5, solution.entries(),
                        Optional.of(meta)), tower);
        List<Recording.Entry> abandon = new ArrayList<>(List.of(new Recording.Entry(0, new Command.Abandon())));
        assertProblem("ends with exit 5 (abandoned) at step 1",
                new Recording(solution.towerId(), solution.options(), 3, abandon, Optional.of(meta)), tower);
    }

    private static void assertProblem(String expected, Recording recording, TowerDefinition tower) {
        String problem = SolutionCheck.problem(recording, tower).orElseThrow();
        assertTrue(problem.startsWith(expected), problem);
    }
}
