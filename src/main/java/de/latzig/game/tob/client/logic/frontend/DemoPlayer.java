package de.latzig.game.tob.client.logic.frontend;

import java.util.List;
import java.util.Optional;

import de.latzig.game.tob.client.logic.game.CommandSource;
import de.latzig.game.tob.core.sim.Command;
import de.latzig.game.tob.core.sim.ObjectStatus;
import de.latzig.game.tob.core.sim.RayOutcome;
import de.latzig.game.tob.core.sim.SimEvent;
import de.latzig.game.tob.core.sim.SimObject;
import de.latzig.game.tob.core.sim.Simulation;
import de.latzig.game.tob.core.sim.SpiderController;
import de.latzig.game.tob.core.sim.SpiderId;

/**
 * Plays a {@link DemoScript} as the original's demo frame routine {@code L014F42} does ({@code S:20404-20439}): only
 * while the demo's spider stands (its movement counter +10 is 0) is the next script byte read; 1-5 become the spider's
 * forward, back, left, right and fire commands, 6 waits {@value DemoScript#WAIT_FRAMES} frames, 0 ends the script (it
 * is read again and again, nothing more happens). The spider is the tower's first present spider, the decoder's point
 * of view ({@code S:17683-17695}).
 * <p>
 * The original clears the turn/fire and move throttles before every demo frame ({@code S:20416-20417}), so a script
 * is never throttled. {@code core.sim} keeps its throttles, so the player mirrors the turn/fire throttle
 * ({@link SpiderController#TURN_THROTTLE}, {@link SpiderController#FIRE_HIT_THROTTLE},
 * {@link SpiderController#FIRE_MISS_THROTTLE}, counted down at the start of every frame as in the simulation) and
 * holds a turn or fire until it would be accepted; a move's throttle always runs out during the 32-frame walk, or after
 * the one frame of a refused move. The frames are counted by the player itself, because a tower's READY phase
 * (frame counter 0) lasts until the first command; the original's demos have no READY phase.
 * <p>
 * Phase 5c-2: {@link #lastByte()} is the byte read last ({@code W001696}), whose bits 6-7 drive the interludes'
 * camera dolly ({@link ChaseCamera}); 0 before the first byte and after the script's end.
 */
public final class DemoPlayer implements CommandSource {

    private final DemoScript script;
    private final byte[] bytes;
    private int step;
    private int frames;
    private int waitUntil;
    private int turnFire;
    private boolean fired;
    private boolean finished;
    private byte lastByte;
    private SpiderId spider;

    public DemoPlayer(DemoScript script) {
        this.script = script;
        this.bytes = script.bytes();
    }

    @Override
    public Optional<Command> next(Simulation simulation) {
        frames++;
        if (turnFire > 0) {
            turnFire--;
        }
        if (spider == null) {
            spider = firstPresent(simulation);
        }
        if (finished || spider == null || frames < waitUntil) {
            return Optional.empty();
        }
        SimObject object = simulation.world().spider(spider);
        if (object.moveCounter() != 0) {
            return Optional.empty();
        }
        if (step >= bytes.length || bytes[step] == 0) {
            finished = true;
            lastByte = 0;
            return Optional.empty();
        }
        int code = bytes[step] & 7;
        boolean turnOrFire = code == DemoScript.TURN_LEFT || code == DemoScript.TURN_RIGHT || code == DemoScript.FIRE;
        if (turnOrFire && turnFire > 0) {
            return Optional.empty();
        }
        lastByte = bytes[step];
        step++;
        return switch (code) {
            case DemoScript.FORWARD -> Optional.of(new Command.Forward(spider));
            case DemoScript.BACK -> Optional.of(new Command.Back(spider));
            case DemoScript.TURN_LEFT -> {
                turnFire = SpiderController.TURN_THROTTLE;
                yield Optional.of(new Command.TurnLeft(spider));
            }
            case DemoScript.TURN_RIGHT -> {
                turnFire = SpiderController.TURN_THROTTLE;
                yield Optional.of(new Command.TurnRight(spider));
            }
            case DemoScript.FIRE -> {
                fired = true;
                yield Optional.of(new Command.Fire(spider));
            }
            case DemoScript.WAIT -> {
                waitUntil = frames + DemoScript.WAIT_FRAMES;
                yield Optional.empty();
            }
            default -> Optional.empty();
        };
    }

    /**
     * Task 16: the move the next {@link #next} will send, without any side effect: Forward or Back if the spider
     * stands, the script is not waiting and its next byte is 1 or 2; empty otherwise (turns and fire are not needed
     * by the look-ahead).
     */
    @Override
    public Optional<Command> peek(Simulation simulation) {
        SpiderId id = spider != null ? spider : firstPresent(simulation);
        if (finished || id == null || frames + 1 < waitUntil || step >= bytes.length) {
            return Optional.empty();
        }
        if (simulation.world().spider(id).moveCounter() != 0) {
            return Optional.empty();
        }
        return switch (bytes[step] & 7) {
            case DemoScript.FORWARD -> Optional.of(new Command.Forward(id));
            case DemoScript.BACK -> Optional.of(new Command.Back(id));
            default -> Optional.empty();
        };
    }

    /**
     * The events of the step just run: after a fire, the throttle is {@link SpiderController#FIRE_HIT_THROTTLE} if the
     * spider's ray affected its target, else {@link SpiderController#FIRE_MISS_THROTTLE} (as {@code Ray.fire}).
     */
    public void afterStep(List<SimEvent> events) {
        if (!fired) {
            return;
        }
        fired = false;
        boolean hit = false;
        for (SimEvent event : events) {
            if (event instanceof SimEvent.RayHit ray && ray.slot() == spider.slot()) {
                RayOutcome outcome = ray.outcome();
                hit = outcome == RayOutcome.DESTROYED || outcome == RayOutcome.PUSHED || outcome == RayOutcome.GRABBED
                        || outcome == RayOutcome.REVERSED;
            }
        }
        turnFire = hit ? SpiderController.FIRE_HIT_THROTTLE : SpiderController.FIRE_MISS_THROTTLE;
    }

    /** Whether the script has reached its 0 byte. */
    public boolean isFinished() {
        return finished;
    }

    /** Script bytes read so far. */
    public int step() {
        return step;
    }

    /**
     * The script byte read last ({@code W001696}; Phase 5c-2: its bits 6-7 drive the interludes' dolly), 0 before
     * the first byte and once the script has ended.
     */
    public byte lastByte() {
        return lastByte;
    }

    public DemoScript script() {
        return script;
    }

    /** The spider the script drives, or null before the first frame or without a spider. */
    public SpiderId spider() {
        return spider;
    }

    private static SpiderId firstPresent(Simulation simulation) {
        for (SpiderId id : SpiderId.values()) {
            if (simulation.world().spider(id).status() != ObjectStatus.ABSENT) {
                return id;
            }
        }
        return null;
    }
}
