package de.latzig.game.tob.client.logic.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.client.logic.Motion;
import de.latzig.game.tob.client.logic.Pose;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.sim.CameraId;
import de.latzig.game.tob.core.sim.Command;
import de.latzig.game.tob.core.sim.SimOptions;
import de.latzig.game.tob.core.sim.Simulation;
import de.latzig.game.tob.core.sim.SpiderId;
import de.latzig.game.tob.core.sim.TestTowers;
import de.latzig.game.tob.core.sim.WorldState;

class SpiderPickTest {

    /** Zapper at (0,0), pusher at (2,0), grabber at (2,2) on level 0, all in one tower. */
    private static WorldState world() {
        return Simulation.start(TestTowers.tower().level(0, "FFF", "FFF", "FFF").object(0, 0, 0, ObjectType.ZAP)
                .object(2, 0, 0, ObjectType.PSH).object(2, 2, 0, ObjectType.GRB).build(), SimOptions.DEFAULT)
                .world();
    }

    private static Pose at(WorldState world, SpiderId id) {
        return Motion.position(world.spider(id), world.isFrozen(), 0f);
    }

    /** A ray from straight above the point, looking down. */
    private static SpiderPick.Ray down(float x, float z) {
        return new SpiderPick.Ray(x, 20f, z, 0f, -1f, 0f);
    }

    @Test
    void aRayThroughASpidersBoxPicksItAndAMissPicksNothing() {
        WorldState world = world();
        Pose pusher = at(world, SpiderId.PUSHER);

        assertEquals(Optional.of(SpiderId.PUSHER),
                SpiderPick.pick(world, 0f, CameraId.NORTH, down(pusher.x() + 0.24f, pusher.z() - 0.24f)));
        assertEquals(Optional.empty(),
                SpiderPick.pick(world, 0f, CameraId.NORTH, down(pusher.x() + 0.26f, pusher.z())), "just beside");
        assertEquals(Optional.empty(), SpiderPick.pick(world, 0f, CameraId.NORTH,
                new SpiderPick.Ray(pusher.x(), pusher.y() + 0.6f, pusher.z() + 5f, 0f, 0f, -1f)), "just above");
        assertEquals(Optional.of(SpiderId.PUSHER), SpiderPick.pick(world, 0f, CameraId.NORTH,
                new SpiderPick.Ray(pusher.x(), pusher.y() + 0.4f, pusher.z() + 5f, 0f, 0f, -1f)), "from the side");
    }

    @Test
    void theNearestOfTwoSpidersInLineWins() {
        WorldState world = world();
        Pose zapper = at(world, SpiderId.ZAPPER);
        Pose pusher = at(world, SpiderId.PUSHER);
        float y = zapper.y() + 0.2f;

        assertEquals(Optional.of(SpiderId.ZAPPER), SpiderPick.pick(world, 0f, CameraId.EAST,
                new SpiderPick.Ray(zapper.x() - 3f, y, zapper.z(), 1f, 0f, 0f)), "from the west");
        assertEquals(Optional.of(SpiderId.PUSHER), SpiderPick.pick(world, 0f, CameraId.EAST,
                new SpiderPick.Ray(pusher.x() + 3f, y, pusher.z(), -2f, 0f, 0f)), "from the east");
    }

    @Test
    void aDeadSpiderAndTheShownSpiderAreIgnored() {
        Simulation sim = Simulation.start(TestTowers.tower().level(0, "FFF", "FFF").object(0, 0, 0, ObjectType.FZE)
                .object(1, 0, 0, ObjectType.PSH).object(0, 1, 0, ObjectType.ZAP).build(), SimOptions.DEFAULT);
        Pose pusher = at(sim.world(), SpiderId.PUSHER);
        sim.step(List.of(new Command.Fire(SpiderId.ZAPPER)));
        for (int i = 0; i < 10; i++) {
            sim.step(List.of());
        }
        assertFalse(sim.world().spider(SpiderId.PUSHER).isAlive(), "destroyed by the fixed zapper");
        assertEquals(Optional.empty(), SpiderPick.pick(sim.world(), 0f, CameraId.NORTH,
                down(pusher.x(), pusher.z())), "a dead spider cannot be picked");

        WorldState world = world();
        Pose zapper = at(world, SpiderId.ZAPPER);
        assertEquals(Optional.empty(), SpiderPick.pick(world, 0f, SpiderId.ZAPPER,
                down(zapper.x(), zapper.z())), "the shown spider is not drawn");
    }

    @Test
    void theBoxFollowsTheInterpolatedWalk() {
        Simulation sim = Simulation.start(TestTowers.tower().level(0, "FFF").object(0, 0, 0, ObjectType.ZAP)
                .object(2, 0, 0, ObjectType.PSH).build(), SimOptions.DEFAULT);
        Pose start = at(sim.world(), SpiderId.ZAPPER);
        sim.step(List.of(new Command.TurnRight(SpiderId.ZAPPER)));
        for (int i = 0; i < 4; i++) {
            sim.step(List.of());
        }
        sim.step(List.of(new Command.Forward(SpiderId.ZAPPER)));
        for (int i = 0; i < 15; i++) {
            sim.step(List.of());
        }
        Pose walking = Motion.position(sim.world().spider(SpiderId.ZAPPER), sim.world().isFrozen(), 0.5f);

        assertEquals(0.5f, walking.x() - start.x(), 0.05f, "half-way to the east neighbour");
        assertEquals(Optional.of(SpiderId.ZAPPER),
                SpiderPick.pick(sim.world(), 0.5f, CameraId.NORTH, down(walking.x() + 0.2f, walking.z())));
        assertEquals(Optional.empty(),
                SpiderPick.pick(sim.world(), 0.5f, CameraId.NORTH, down(start.x() - 0.2f, start.z())),
                "the box has left the start cell's west half");
    }
}
