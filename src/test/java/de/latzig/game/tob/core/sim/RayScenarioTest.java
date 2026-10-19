package de.latzig.game.tob.core.sim;

import static de.latzig.game.tob.core.sim.TestTowers.pos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.ObjectType;

/**
 * Oracle §10 scenarios of Phase 2b in the full frame pipeline. Frame 0 is the READY-ending frame; frame k is the k-th
 * normal frame. "Start time" is a zapper at (7,7) facing N that fires into the void at frame 0 (oracle §10
 * conventions), so enemies first act in frame 1.
 */
class RayScenarioTest {

    private static final SpiderId Z = SpiderId.ZAPPER;
    private static final SpiderId P = SpiderId.PUSHER;
    private static final String FULL = "FFFFFFFF";

    private static SimRun startTime(TestTowers.Builder tower) {
        SimRun run = new SimRun(tower.object(7, 7, 0, ObjectType.ZAP).build());
        run.step(new Command.Fire(Z));
        assertEquals(0, run.sim.frame());
        return run;
    }

    /** Turns the spider in READY and waits out the shared 4-frame turn/fire throttle. */
    private static void turn(SimRun run, Command turn) {
        run.step(turn);
        run.step();
        run.step();
        run.step();
    }

    private static List<Integer> frames(List<? extends SimEvent> events) {
        return events.stream().map(SimEvent::frame).toList();
    }

    @Test
    void t02FixedPusherConveyor() {
        // F (0..7,0); fixed pusher E at (0,0) (slot 3), block at (1,0) (slot 4)
        SimRun run = startTime(TestTowers.tower().level(0, ".......F", "", "", "", "", "", "", FULL)
                .object(0, 0, 0, ObjectType.FPE).object(1, 0, 0, ObjectType.BLK));
        run.runUntilFrame(120);

        assertEquals(List.of(1, 17, 33, 49, 65, 81), frames(run.events(SimEvent.PushStarted.class)));
        assertEquals(List.of(9, 25, 41, 57, 73, 89), frames(run.events(SimEvent.CellChanged.class).stream()
                .filter(changed -> changed.slot() == 4).toList()));
        assertEquals(pos(7, 0, 0), run.sim.world().object(4).cell(), "no floor beyond (7,0)");
        assertTrue(run.events.contains(new SimEvent.RayHit(97, 3, 4, RayOutcome.ABSORBED)));
    }

    @Test
    void t03RotatingZapperWindow() {
        // rotating zapper (3,3) slot 3; flags (5,3) slot 4 and (3,5) slot 5; F on the whole level
        SimRun run = startTime(TestTowers.tower().level(0, FULL, FULL, FULL, FULL, FULL, FULL, FULL, FULL)
                .object(3, 3, 0, ObjectType.RZN).object(3, 5, 0, ObjectType.BFL).object(5, 3, 0, ObjectType.BFL)
                .conditions(0, false, 0, 3));
        run.runUntilFrame(20);

        assertEquals(List.of(new SimEvent.Destroyed(1, 5, pos(3, 5, 0)), new SimEvent.Destroyed(16, 4, pos(5, 3, 0))),
                run.events(SimEvent.Destroyed.class));
        assertEquals(2, run.sim.world().counters().robotsDestroyed());
    }

    @Test
    void t03ATargetEnteringTheNorthLineInTheTurningFramesIsHitOnlyAtFrame64() {
        // the pusher walks from (2,6) into (3,6): claimed in frame 9, current cell from frame 15
        SimRun run = new SimRun(TestTowers.tower().level(0, FULL, FULL, FULL, FULL, FULL, FULL, FULL, FULL)
                .object(3, 3, 0, ObjectType.RZN).object(2, 6, 0, ObjectType.PSH).build());
        turn(run, new Command.TurnRight(P));
        run.step(new Command.Forward(P));
        run.runUntilFrame(70);

        assertEquals(List.of(new SimEvent.Destroyed(64, 1, pos(3, 6, 0))), run.events(SimEvent.Destroyed.class));
    }

    @Test
    void t04RotatingPusher() {
        // rotating pusher (3,3) slot 3; blocks (4,3) E slot 4 and (3,4) N slot 5; F on the whole level
        SimRun run = startTime(TestTowers.tower().level(0, FULL, FULL, FULL, FULL, FULL, FULL, FULL, FULL)
                .object(3, 3, 0, ObjectType.RPN).object(3, 4, 0, ObjectType.BLK).object(4, 3, 0, ObjectType.BLK));
        run.runUntilFrame(64);

        assertEquals(List.of(new SimEvent.PushStarted(16, 4, pos(4, 3, 0), pos(5, 3, 0)),
                new SimEvent.PushStarted(64, 5, pos(3, 4, 0), pos(3, 5, 0))), run.events(SimEvent.PushStarted.class));
        assertEquals(List.of(new SimEvent.PushFinished(32, 4, pos(5, 3, 0))), run.events(SimEvent.PushFinished.class));
        assertEquals(List.of(16, 32, 48, 64), frames(run.events(SimEvent.RayFired.class).stream()
                .filter(fired -> fired.slot() == 3).toList()));
    }

    @Test
    void t18PrismCodes25And26() {
        for (ObjectType prism : List.of(ObjectType.PNE, ObjectType.PSE)) {
            // zapper (0,0) facing E; prism (3,0) slot 3; flag (3,2) slot 4
            SimRun run = new SimRun(TestTowers.tower().level(0, "...F", "...F", "FFFF").object(0, 0, 0, ObjectType.ZAP)
                    .object(3, 0, 0, prism).object(3, 2, 0, ObjectType.BFL).conditions(0, false, 0, 2).build());
            turn(run, new Command.TurnRight(Z));

            run.step(new Command.Fire(Z));

            SimEvent.RayFired fired = run.events(SimEvent.RayFired.class).get(0);
            Direction leaving = fired.path().get(fired.path().size() - 1).direction();
            assertEquals(prism == ObjectType.PNE ? Direction.SOUTH : Direction.NORTH, leaving, prism.code());
            assertEquals(prism == ObjectType.PNE ? ObjectStatus.ALIVE : ObjectStatus.DESTROYING,
                    run.sim.world().object(4).status(), prism.code());
        }
    }

    @Test
    void t19AReflectorKillsItsEmitter() {
        SimRun run = new SimRun(TestTowers.tower().level(0, FULL).object(0, 0, 0, ObjectType.ZAP)
                .object(7, 0, 0, ObjectType.REL).build());
        turn(run, new Command.TurnRight(Z));

        run.step(new Command.Fire(Z));

        List<RayStep> path = run.events(SimEvent.RayFired.class).get(0).path();
        assertEquals(14, path.size());
        assertEquals(pos(7, 0, 0), path.get(6).cell());
        assertEquals(pos(0, 0, 0), path.get(13).cell());
        assertEquals(ObjectStatus.DESTROYING, run.sim.world().spider(Z).status());
        assertEquals(Rules.EXIT_SPIDERS_DESTROYED, run.sim.exitCode(), "no other spider present");
    }

    @Test
    void t20MutualEnemyZappersAreDecidedByListOrder() {
        // FZ facing E at (0,0) is slot 3 (earlier in the list), FZ facing W at (5,0) is slot 4
        SimRun run = startTime(TestTowers.tower().level(0, "FFFFFF").object(0, 0, 0, ObjectType.FZE)
                .object(5, 0, 0, ObjectType.FZW).conditions(0, false, 0, 2));
        run.runUntilFrame(5);

        assertEquals(List.of(new SimEvent.Destroyed(1, 4, pos(5, 0, 0))), run.events(SimEvent.Destroyed.class));
        assertTrue(run.events(SimEvent.RayFired.class).stream().noneMatch(fired -> fired.slot() == 4));
        assertEquals(1, run.sim.world().counters().robotsDestroyed());
    }

    @Test
    void t21Converter() {
        for (ObjectType converter : List.of(ObjectType.CVE, ObjectType.CVN)) {
            // zapper (0,0) facing E; converter (2,0) slot 3; block (4,0) slot 4; F (0..7,0)
            SimRun run = new SimRun(TestTowers.tower().level(0, FULL).object(0, 0, 0, ObjectType.ZAP)
                    .object(2, 0, 0, converter).object(4, 0, 0, ObjectType.BLK).build());
            turn(run, new Command.TurnRight(Z));
            run.step(new Command.Fire(Z));
            run.runUntilFrame(20);

            assertEquals(converter == ObjectType.CVE ? pos(5, 0, 0) : pos(4, 0, 0), run.sim.world().object(4).cell(),
                    converter.code());
        }
    }

    @Test
    void t26ALiftRiderBlocksTheEnemyRayAndIsImmune() {
        // up-lift at (2,0) with the zapper on it; fixed zapper E at (0,0) (slot 3); F (0..3,0)
        SimRun run = new SimRun(TestTowers.tower().level(0, "FFUF").level(1, "..u.")
                .object(2, 0, 0, ObjectType.ZAP).object(0, 0, 0, ObjectType.FZE).build());
        run.step(new Command.Back(Z));
        run.runUntilFrame(40);

        List<SimEvent.RayHit> hits = run.events(SimEvent.RayHit.class).stream().filter(hit -> hit.slot() == 3)
                .toList();
        assertEquals(30, hits.size());
        for (int i = 0; i < 30; i++) {
            assertEquals(new SimEvent.RayHit(i + 1, 3, 0, RayOutcome.BLOCKED), hits.get(i));
        }
        assertEquals(ObjectStatus.ALIVE, run.sim.world().spider(Z).status());
        assertEquals(pos(2, 0, 1), run.sim.world().spider(Z).cell());
        List<SimEvent.RayFired> fired = run.events(SimEvent.RayFired.class);
        SimEvent.RayFired last = fired.get(fired.size() - 1);
        assertEquals(List.of(pos(1, 0, 0), pos(2, 0, 0)), last.path().stream().map(RayStep::cell).toList(),
                "from frame 31 the lower lift cell is blocked");
    }

    @Test
    void aPushBeforeAWalkersClaimMakesItReverseAndAPushAfterItIsAbsorbed() {
        for (int fireFrame : new int[] {5, 10}) {
            // pusher (0,0) facing E, block (1,0) slot 3, zapper (3,0) facing W walking into (2,0) from frame 0
            SimRun run = new SimRun(TestTowers.tower().level(0, "FFFF").object(0, 0, 0, ObjectType.PSH)
                    .object(1, 0, 0, ObjectType.BLK).object(3, 0, 0, ObjectType.ZAP).build());
            turn(run, new Command.TurnRight(P));
            turn(run, new Command.TurnLeft(Z));
            run.step(new Command.Forward(Z));
            run.runUntilFrame(fireFrame - 1);

            run.step(new Command.Fire(P));
            run.runUntilFrame(12);

            if (fireFrame == 5) {
                assertTrue(run.events.contains(new SimEvent.PushStarted(5, 3, pos(1, 0, 0), pos(2, 0, 0))));
                assertTrue(run.events.contains(new SimEvent.MoveReversed(9, 0)), "claim frame of the zapper");
            } else {
                assertTrue(run.events.contains(new SimEvent.MoveClaimed(9, 0, pos(2, 0, 0))));
                assertTrue(run.events.contains(new SimEvent.RayHit(10, 1, 3, RayOutcome.ABSORBED)));
                assertTrue(run.events(SimEvent.PushStarted.class).isEmpty());
            }
            assertEquals(List.of(), run.sim.world().occupancyProblems());
        }
    }
}
