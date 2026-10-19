package de.latzig.game.tob.client.logic;

import java.util.Optional;

import de.latzig.game.tob.core.sim.CellPos;
import de.latzig.game.tob.core.sim.Command;
import de.latzig.game.tob.core.sim.Direction;
import de.latzig.game.tob.core.sim.ProgramState;
import de.latzig.game.tob.core.sim.ProgramStep;
import de.latzig.game.tob.core.sim.SimObject;
import de.latzig.game.tob.core.sim.Simulation;
import de.latzig.game.tob.core.sim.SpiderId;
import de.latzig.game.tob.core.sim.SpiderProgram;
import de.latzig.game.tob.core.sim.WorldState;

/**
 * Task 16 (walk stutter): a spider walking on over several cells has no stand frame in the simulation – the frame after
 * a walk's last one starts the next walk and advances it at once – but in that interval its state stands (counter 0),
 * so {@link Motion} drew it standing for 140 ms and then snapped 16 units. The look-ahead predicts, for every standing
 * spider, the walk the next frame will most likely start: from the command source's side-effect-free
 * {@link de.latzig.game.tob.client.logic.game.CommandSource#peek} (held key, queued press, demo script) or else from
 * its running program's next step, if the target passes the test of {@code Walk.start} (walkable and free) and, for
 * Back, of a backwards walk (not on a lift, Backwards Allowed). The move throttle is not visible outside
 * {@code core.sim}; between two walks it is always run out. A wrong guess is harmless: {@link MotionCarry} glides it
 * back over the next frame.
 */
final class WalkLookAhead {

    private WalkLookAhead() {
    }

    /** The predicted walk direction per spider slot (null = none) for the next frame. */
    static Direction[] predict(Simulation simulation, Optional<Command> peeked) {
        WorldState world = simulation.world();
        Direction[] walks = new Direction[WorldState.SPIDER_SLOTS];
        for (SpiderId id : SpiderId.values()) {
            SimObject spider = world.spider(id);
            if (!spider.isAlive() || spider.isMoving() || spider.cell() == null) {
                continue;
            }
            Optional<Command.SpiderCommand> command = peeked.filter(Command.SpiderCommand.class::isInstance)
                    .map(Command.SpiderCommand.class::cast).filter(c -> c.spider() == id);
            SpiderProgram program = simulation.program(id);
            if (command.isPresent()) {
                if (program.state() != ProgramState.WRITING) {
                    walks[id.slot()] = switch (command.get()) {
                        case Command.Forward forward -> walk(world, spider, false);
                        case Command.Back back -> walk(world, spider, true);
                        default -> null;
                    };
                }
                // any other command for this spider may change what its program does: no guess
            } else {
                walks[id.slot()] = programWalk(world, spider, program);
            }
        }
        return walks;
    }

    /** The walk the running program's next step starts, if any. */
    private static Direction programWalk(WorldState world, SimObject spider, SpiderProgram program) {
        int next = program.cursor() + 1;
        if (program.state() != ProgramState.RUNNING || next < 0 || next >= SpiderProgram.SLOTS) {
            return null;
        }
        ProgramStep step = program.slot(next);
        return step == ProgramStep.FORWARD ? walk(world, spider, false)
                : step == ProgramStep.BACK ? walk(world, spider, true) : null;
    }

    /** The direction of a walk forward or backwards from standing, or null if {@code Walk.start} would refuse it. */
    private static Direction walk(WorldState world, SimObject spider, boolean back) {
        if (back && (world.isLift(spider.cell()) || !world.options().backwardsAllowed())) {
            return null;
        }
        Direction direction = back ? spider.facing().opposite() : spider.facing();
        CellPos target = spider.cell().step(direction);
        return world.isWalkable(target) && world.occupancy().isFree(target) ? direction : null;
    }
}
