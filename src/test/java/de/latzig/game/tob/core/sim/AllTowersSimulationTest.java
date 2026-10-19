package de.latzig.game.tob.core.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.latzig.game.tob.core.model.ObjectFamily;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.sim.element.HopperBehaviour;

/**
 * The Phase 2 completion fuzz (Phase 2d spec §5; earlier stages: Phase 2a spec §7, 2b §5, 2c §5). Every one of the 144
 * original towers runs for 2000 frames of seeded random input (or until its attempt ends), with all object types
 * active, for three seeds. After every frame the invariants of {@link #problem} hold; at the end the replay of the
 * recording yields the same events and the same state.
 */
class AllTowersSimulationTest {

    private static final int FRAMES = 2000;
    private static final List<ObjectFamily> MOVERS = List.of(ObjectFamily.WORM, ObjectFamily.WATCHER,
            ObjectFamily.HOPPER, ObjectFamily.LIZARD);
    private static final List<ObjectFamily> SPIDERS = List.of(ObjectFamily.ZAPPER, ObjectFamily.PUSHER,
            ObjectFamily.GRABBER);

    @ParameterizedTest
    @ValueSource(longs = {20261007L, 20262007L, 20263007L})
    void everyTowerKeepsTheInvariantsForTwoThousandFramesAndReplaysExactly(long seed) throws IOException {
        assumeTrue(TowerAssets.available(), "tower assets not generated");
        List<TowerDefinition> towers = TowerAssets.allTowers();
        assertEquals(144, towers.size());

        List<String> failures = new ArrayList<>();
        Map<String, Integer> counts = new TreeMap<>();
        Set<ObjectFamily> families = EnumSet.noneOf(ObjectFamily.class);
        for (int i = 0; i < towers.size(); i++) {
            TowerDefinition tower = towers.get(i);
            try {
                run(tower, new SimOptions(i % 2 == 1), new RandomCommands(seed + i), failures, counts, families);
            } catch (RuntimeException e) {
                failures.add(tower.id() + ": " + e);
            }
        }

        assertEquals(List.of(), failures);
        assertEquals(EnumSet.allOf(ObjectFamily.class), families, "every object type is in play");
        assertTrue(count(counts, "frames") > 144 * FRAMES / 2, "frames: " + counts);
        int spiderMoves = SPIDERS.stream().mapToInt(family -> count(counts, "MoveStarted:" + family)).sum();
        assertTrue(spiderMoves > 300, "random input must make the spiders walk: " + counts);
        for (ObjectFamily mover : MOVERS) {
            assertTrue(count(counts, "MoveStarted:" + mover) > 0, mover + " must walk: " + counts);
        }
        for (String event : List.of("LiftRideStarted", "PushStarted", "Destroyed", "TurnStarted", "FloorEaten",
                "Exploded", "FuseTick", "Frozen", "Thawed", "ForcerLiftStarted", "ForcerLiftFinished", "WiperSweep")) {
            assertTrue(count(counts, event) > 0, event + " must happen: " + counts);
        }
        assertTrue(count(counts, "RayFired:enemy") > 1000, "the enemies of the original towers must fire: " + counts);
        assertTrue(count(counts, "cameraMoved") > 0, "random camera commands must move the cameras: " + counts);
    }

    private static int count(Map<String, Integer> counts, String key) {
        return counts.getOrDefault(key, 0);
    }

    private static void run(TowerDefinition tower, SimOptions options, RandomCommands random, List<String> failures,
            Map<String, Integer> counts, Set<ObjectFamily> families) {
        Recorder recorder = new Recorder(Simulation.start(tower, options));
        WorldState world = recorder.simulation().world();
        for (SimObject object : world.objects()) {
            if (object.status() != ObjectStatus.ABSENT) {
                families.add(object.type().family());
            }
        }
        List<SimEvent> events = new ArrayList<>();
        Progress progress = new Progress(world);
        int frames = 0;
        while (frames < FRAMES && !recorder.simulation().isFinished()) {
            events.addAll(recorder.step(random.next()));
            frames++;
            String problem = problem(world, progress);
            if (problem == null) {
                problem = cameraProblem(recorder.simulation());
            }
            if (problem != null) {
                failures.add(tower.id() + " step " + frames + ": " + problem);
                return;
            }
            progress.remember(world);
        }
        List<SimEvent> replayed = new ArrayList<>();
        Simulation replay = Replay.run(recorder.recording(), tower, replayed);
        if (!events.equals(replayed)) {
            failures.add(tower.id() + ": the replay produced other events");
        }
        if (!Snapshots.of(replay).equals(Snapshots.of(recorder.simulation()))) {
            failures.add(tower.id() + ": the replay ended in another state");
        }
        counts.merge("frames", frames, Integer::sum);
        for (CameraId id : CameraId.values()) {
            CameraPose pose = recorder.simulation().camera(id);
            if (pose.height() != CameraPose.START_HEIGHT || pose.offset() != 0
                    || pose.distance() != CameraPose.START_DISTANCE) {
                counts.merge("cameraMoved", 1, Integer::sum);
            }
        }
        for (SimEvent event : events) {
            String key = switch (event) {
                case SimEvent.MoveStarted started -> "MoveStarted:" + world.object(started.slot()).type().family();
                case SimEvent.RayFired fired -> fired.slot() < WorldState.SPIDER_SLOTS ? "RayFired:spider"
                        : "RayFired:enemy";
                default -> event.getClass().getSimpleName();
            };
            counts.merge(key, 1, Integer::sum);
        }
    }

    /** The longest movement of each kind in frames: walk and lift ride 32, push and wait 16. */
    private static int maxCounter(MoveKind kind) {
        return switch (kind) {
            case WALK, LIFT_UP, LIFT_DOWN -> Walk.FRAMES;
            case PUSH -> Push.FRAMES;
            case IDLE -> HopperBehaviour.WAIT_FRAMES;
            case NONE -> 0;
        };
    }

    /**
     * The invariants after a frame, or null:
     * <ul>
     * <li>the occupancy map is consistent (including the cells of moving forcer lifts);</li>
     * <li>every object that holds a cell is inside the grid;</li>
     * <li>every alive object that stands (or waits) stands on a floor, which includes lift platforms;</li>
     * <li>the freeze counter equals exactly {@code (isReady ? -1 : 0) - (alive freezers with classTimer > 64)}
     * (so a lost thaw, e.g. a missing {@code ctx.thaw()}, is caught), and the counters never exceed their required
     * values;</li>
     * <li>no movement exceeds its maximum duration: a moving object's counter is within 1..max of its kind (walk 32,
     * push 16, ride 32, wait 16), the forcer timer within 0..32, and each of them changes in every frame in which it
     * must advance (spiders always, everything else when the world was unfrozen at the end of this and of the
     * previous frame), so no object can be stuck.</li>
     * </ul>
     */
    private static String problem(WorldState world, Progress progress) {
        List<String> occupancy = world.occupancyProblems();
        if (!occupancy.isEmpty()) {
            return occupancy.toString();
        }
        int activeFreezers = 0;
        for (SimObject object : world.objects()) {
            if (object.isAlive() && object.type().family() == ObjectFamily.FREEZER
                    && object.classTimer() > Rules.FREEZER_THAW) {
                activeFreezers++;
            }
        }
        int expectedFreeze = (world.isReady() ? -1 : 0) - activeFreezers;
        if (world.freezeCounter() != expectedFreeze) {
            return "freeze counter " + world.freezeCounter() + " but expected " + expectedFreeze + " (ready="
                    + world.isReady() + ", active freezers=" + activeFreezers + ")";
        }
        Counters counters = world.counters();
        if (counters.klondikesCollected() > counters.klondikesRequired()
                || counters.robotsDestroyed() > counters.robotsRequired()) {
            return "counters above their required values";
        }
        boolean unfrozen = !progress.frozen && !world.isFrozen();
        for (SimObject object : world.objects()) {
            if (!object.holdsCell()) {
                continue;
            }
            CellPos second = object.secondCell();
            if (object.cell() == null || !object.cell().inGrid() || second != null && !second.inGrid()) {
                return "slot " + object.slot() + " outside the grid: " + object.cell() + " " + second;
            }
            if (!object.isAlive()) {
                continue;
            }
            boolean standing = !object.isMoving() || object.moveKind() == MoveKind.IDLE;
            if (standing && !world.hasFloor(object.cell())) {
                return "slot " + object.slot() + " stands without floor in " + object.cell();
            }
            if (!object.isMoving()) {
                continue;
            }
            if (object.moveCounter() < 1 || object.moveCounter() > maxCounter(object.moveKind())) {
                return "slot " + object.slot() + " " + object.moveKind() + " counter " + object.moveCounter();
            }
            int slot = object.slot();
            if ((object.isSpider() || unfrozen) && progress.kinds[slot] == object.moveKind()
                    && progress.counters[slot] == object.moveCounter()) {
                return "slot " + slot + " stuck at " + object.moveKind() + " " + object.moveCounter();
            }
        }
        int timer = world.forcerLifts().timer();
        if (timer < 0 || timer > ForcerLifts.FRAMES) {
            return "forcer timer " + timer;
        }
        if (timer != 0 && timer == progress.forcerTimer && unfrozen) {
            return "forcer lifts stuck at " + timer;
        }
        return null;
    }

    /** Every camera pose stays inside the original ranges, or a problem text. */
    private static String cameraProblem(Simulation sim) {
        for (CameraId id : CameraId.values()) {
            CameraPose pose = sim.camera(id);
            if (pose.height() < CameraPose.MIN_HEIGHT || pose.height() >= CameraPose.HEIGHT_LIMIT
                    || Math.abs(pose.offset()) > CameraPose.OFFSET_LIMIT || pose.distance() > CameraPose.NEAREST
                    || pose.distance() < CameraPose.FARTHEST) {
                return "camera " + id + " out of range: " + pose.height() + "," + pose.offset() + ","
                        + pose.distance();
            }
        }
        return null;
    }

    /** The movement state after the previous frame. */
    private static final class Progress {

        private final int[] counters;
        private final MoveKind[] kinds;
        private boolean frozen;
        private int forcerTimer;

        Progress(WorldState world) {
            counters = new int[world.objects().size()];
            kinds = new MoveKind[world.objects().size()];
            remember(world);
        }

        void remember(WorldState world) {
            for (SimObject object : world.objects()) {
                boolean moving = object.isAlive() && object.isMoving();
                counters[object.slot()] = moving ? object.moveCounter() : 0;
                kinds[object.slot()] = moving ? object.moveKind() : MoveKind.NONE;
            }
            frozen = world.isFrozen();
            forcerTimer = world.forcerLifts().timer();
        }
    }
}
