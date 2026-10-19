package de.latzig.game.tob.client.logic.game;

import static de.latzig.game.tob.client.logic.game.GameAction.BACK;
import static de.latzig.game.tob.client.logic.game.GameAction.CAMERA_NORTH;
import static de.latzig.game.tob.client.logic.game.GameAction.FIRE;
import static de.latzig.game.tob.client.logic.game.GameAction.FORWARD;
import static de.latzig.game.tob.client.logic.game.GameAction.SELECT_GRABBER;
import static de.latzig.game.tob.client.logic.game.GameAction.SELECT_PUSHER;
import static de.latzig.game.tob.client.logic.game.GameAction.TURN_LEFT;
import static de.latzig.game.tob.client.logic.game.GameAction.TURN_RIGHT;
import static de.latzig.game.tob.client.logic.game.GameAction.ZOOM_OUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.io.RecordingWriter;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.sim.CameraId;
import de.latzig.game.tob.core.sim.Recording;
import de.latzig.game.tob.core.sim.RecordingMeta;
import de.latzig.game.tob.core.sim.Rules;
import de.latzig.game.tob.core.sim.SimOptions;
import de.latzig.game.tob.core.sim.Simulation;
import de.latzig.game.tob.core.sim.TowerAssets;

/**
 * Spec §6 (best effort): a keyboard solution of {@code original-000} "Softly Softly!" (3 klondikes, no robots, no
 * time limit), played through {@link InputMapper} like the keys of the game, one key every 40 frames.
 * <p>
 * The klondikes stand in a row north of the grabber, behind an empty row. Once the first is grabbed, the fixed pusher
 * at (4,6) pushes the block at (5,6) into the grabber's line. So: the zapper rides the lifts to (4,10) on level 0 and
 * zaps the fixed zapper at (4,8) that guards the down-lift (5,8); then it returns the up-lift (5,11) to the bottom; the
 * pusher rides up there, walks to the down-lift (5,8), rides down and pushes the block north to (5,5), out of the
 * fixed pusher's row; the grabber then grabs the three klondikes. The north camera is used on the way as a view.
 * <p>
 * Phase 3c: the run is recorded like an attempt in the client ({@link RecordingSession} around the mapper) and must be
 * a solution ({@link SolutionCheck}). With {@code -Dtob.writeSolutions=true} the recording is written to
 * {@code src/test/resources/solutions/original-000.json}, the first committed solution; the clock is fixed, so the
 * file is byte-identical on every regeneration.
 */
public class SoftlySoftlySolutionTest {

    /** Frames between two keys (public: the headless client test plays the same keys, Phase 3c). */
    public static final int FRAMES_PER_KEY = 40;
    /** {@code recordedAt} of the generated solution. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-09T00:00:00Z"), ZoneOffset.UTC);

    /** The keys of the solution, one every {@link #FRAMES_PER_KEY} frames. */
    public static final List<GameAction> KEYS = List.of(
            // zapper (selected at start): west to the up-lift (5,11), ride up, north to (5,10), west onto the
            // down-lift (4,10), ride down, face north and zap the fixed zapper at (4,8)
            TURN_LEFT, FORWARD, FORWARD, BACK, TURN_RIGHT, FORWARD, TURN_LEFT, FORWARD, BACK, TURN_RIGHT, FIRE,
            // zapper: ride up again, back to the up-lift (5,11), ride it down, leave it east to (7,11)
            BACK, TURN_RIGHT, FORWARD, TURN_RIGHT, FORWARD, BACK, TURN_LEFT, FORWARD, FORWARD,
            // the north camera: look at the tower from the south, zoom out twice
            CAMERA_NORTH, ZOOM_OUT, ZOOM_OUT,
            // pusher: south and west around to the up-lift (5,11), ride up, north over (5,10) and (5,9) onto the
            // down-lift (5,8), ride down, push the block at (5,6) north
            SELECT_PUSHER, TURN_RIGHT, TURN_RIGHT, FORWARD, FORWARD, TURN_RIGHT, FORWARD, TURN_LEFT, FORWARD,
            TURN_RIGHT, FORWARD, BACK, TURN_RIGHT, FORWARD, FORWARD, FORWARD, BACK, FIRE,
            // grabber: three grabs to the north
            SELECT_GRABBER, FIRE, FIRE, FIRE);

    @Test
    void theKeyboardSolutionCompletesSoftlySoftly() throws IOException {
        assumeTrue(TowerAssets.available(), "tower assets not generated");
        TowerDefinition tower = TowerAssets.allTowers().get(0);
        assertEquals("original-000", tower.id());
        Simulation sim = Simulation.start(tower, SimOptions.DEFAULT);
        InputMapper mapper = new InputMapper();
        RecordingSession session = new RecordingSession(tower, SimOptions.DEFAULT, CLOCK);
        CommandSource source = session.recording(mapper);
        List<String> sent = new ArrayList<>();

        for (GameAction key : KEYS) {
            mapper.press(key);
            for (int frame = 0; frame < FRAMES_PER_KEY && !sim.isFinished(); frame++) {
                source.next(sim).ifPresentOrElse(command -> {
                    sent.add(command.toString());
                    sim.step(List.of(command));
                }, () -> sim.step(List.of()));
            }
        }
        for (int frame = 0; frame < 100 && !sim.isFinished(); frame++) {
            session.onStep(Optional.empty());
            sim.step(List.of());
        }
        Recording recording = session.close(RecordingMeta.Outcome.ofExit(sim.exitCode()), sim.frame());
        if (Boolean.getBoolean("tob.writeSolutions")) {
            Path file = SolutionReplayTest.SOLUTIONS.resolve(tower.id() + ".json");
            new RecordingWriter().write(recording, file);
            System.out.println("SoftlySoftlySolutionTest: wrote " + file.toAbsolutePath());
        }

        assertEquals(KEYS.size(), sent.size(), "every key became a command: " + sent);
        assertTrue(sim.isFinished(), "finished");
        assertEquals(Rules.EXIT_COMPLETED, sim.exitCode());
        assertEquals(3, sim.world().counters().klondikesCollected());
        assertEquals(-3136 - 2 * 0x100, sim.camera(CameraId.NORTH).distance(), "the camera keys were used");
        assertEquals(KEYS.size(), recording.entries().size(), "every sent command is recorded");
        assertEquals(Optional.empty(), SolutionCheck.problem(recording, tower), "the recording is a solution");
    }
}
