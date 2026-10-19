package de.latzig.game.tob.core.sim;

import java.util.ArrayList;
import java.util.List;

/**
 * Ray tracing of the fire routine {@code L00A796} ({@code S:8730-9022}): zap {@code L00A7AC}, push {@code L00A966}
 * and grab {@code L00AA3A}. A ray is traced and applied at once, inside the emitter's own step.
 * <ul>
 * <li>It starts at the emitter's current cell and steps one cell before the first test.</li>
 * <li>A cell outside the grid (the border) or with {@link CellFlags#BLOCKED} stops it. Floor, void and lift top stops
 * do not.</li>
 * <li>Zap and push rays test at most {@link #RANGE} cells in total, across reflections and conversions; the grab
 * ray has no limit.</li>
 * <li>A free cell lets it pass. So does the emitter itself (zap and push only: the grab has no own-slot test,
 * {@code S:8917-8936}), an object being destroyed and a vanishing klondike (spec deviation 1).</li>
 * <li>A cell held by a moving forcer lift ({@link Occupancy#FORCER_LIFT}) lets a zap pass and ends a push or grab
 * without effect (oracle §3.1, marked uncertain there).</li>
 * <li>Zap only: a lift rider blocks it and is immune ({@code S:8766-8771}); otherwise an occupant is reached only in
 * its current cell, so claimed and not yet released cells are transparent ({@code S:8772-8773}).</li>
 * <li>Then the occupant's flag word decides, in the original test order.</li>
 * </ul>
 */
public final class Ray {

    /** Zap and push rays share a budget of 16 tested cells ({@code move.w #$10,d4}, {@code S:8737}, {@code S:8854}). */
    public static final int RANGE = 16;

    /** The "own slot" after a reflection: nobody, so the emitter can be hit ({@code S:8815-8818}). */
    private static final int NOBODY = -1;

    private static final int GRAB_TARGETS = ObjectFlags.KLONDIKE | ObjectFlags.FORCER | ObjectFlags.TOOL;

    private Ray() {
    }

    /**
     * Traces the ray of {@code emitter} in {@code direction} and applies its effect.
     *
     * @return true if the ray affected the occupant it ended at (destroyed, pushed, grabbed or reversed it): a hit
     *         for the 8-frame fire throttle; false for a miss (1 frame)
     */
    static boolean fire(SimContext ctx, SimObject emitter, Direction direction, RayType type) {
        WorldState world = ctx.world();
        List<RayStep> path = new ArrayList<>();
        List<SimEvent> passes = new ArrayList<>();
        CellPos cell = emitter.cell();
        Direction moving = direction;
        RayType current = type;
        int own = emitter.slot();
        int budget = RANGE;
        SimObject target = null;
        RayOutcome end = null;
        while (target == null) {
            if (current != RayType.GRAB && --budget < 0) {
                break;
            }
            cell = cell.step(moving);
            if (!cell.inGrid()) {
                break;
            }
            path.add(new RayStep(cell, moving, current));
            if (world.isBlocked(cell)) {
                break;
            }
            int holder = world.occupancy().holder(cell);
            if (holder == Occupancy.FREE) {
                continue;
            }
            if (holder == Occupancy.FORCER_LIFT) {
                // the pseudo record of a moving forcer lift (S:9043): its +14 is not this cell, so a zap passes
                // (S:8772-8773); its flag word is 0, so a push or grab ray ends here without effect
                if (current == RayType.ZAP) {
                    continue;
                }
                break;
            }
            SimObject occupant = world.object(holder);
            if ((current != RayType.GRAB && holder == own) || occupant.status() == ObjectStatus.DESTROYING
                    || occupant.isVanishing()) {
                continue;
            }
            int flags = occupant.objectFlags();
            switch (current) {
                case ZAP -> {
                    if (occupant.isRiding()) {
                        target = occupant;
                        end = RayOutcome.BLOCKED;
                    } else if (!cell.equals(occupant.cell())) {
                        continue;
                    } else if ((flags & ObjectFlags.ZAPPABLE) != 0) {
                        target = occupant;
                        end = RayOutcome.DESTROYED;
                    } else if ((flags & ObjectFlags.REFLECTS) != 0) {
                        moving = moving.opposite();
                        own = NOBODY;
                        passes.add(new SimEvent.RayHit(ctx.frame(), emitter.slot(), holder, RayOutcome.REFLECTED));
                    } else if ((flags & ObjectFlags.PRISM) != 0) {
                        Direction bent = deflect(occupant.facing(), moving);
                        if (bent == null) {
                            target = occupant;
                            end = RayOutcome.ABSORBED;
                        } else {
                            moving = bent;
                            own = holder;
                            passes.add(new SimEvent.RayHit(ctx.frame(), emitter.slot(), holder, RayOutcome.DEFLECTED));
                        }
                    } else if ((flags & ObjectFlags.CONVERTER) != 0) {
                        if (parallel(occupant.facing(), moving)) {
                            current = RayType.PUSH;
                            passes.add(new SimEvent.RayHit(ctx.frame(), emitter.slot(), holder, RayOutcome.CONVERTED));
                        } else {
                            target = occupant;
                            end = RayOutcome.ABSORBED;
                        }
                    } else if ((flags & ObjectFlags.ZAP_REVERSES) != 0) {
                        target = occupant;
                        end = RayOutcome.REVERSED;
                    } else {
                        target = occupant;
                        end = RayOutcome.ABSORBED;
                    }
                }
                case PUSH -> {
                    if ((flags & ObjectFlags.PUSHABLE) != 0) {
                        target = occupant;
                        end = Push.canStart(world, occupant, moving) ? RayOutcome.PUSHED : RayOutcome.ABSORBED;
                    } else if ((flags & ObjectFlags.CONVERTER) != 0 && parallel(occupant.facing(), moving)) {
                        current = RayType.ZAP;
                        passes.add(new SimEvent.RayHit(ctx.frame(), emitter.slot(), holder, RayOutcome.CONVERTED));
                    } else {
                        target = occupant;
                        end = RayOutcome.ABSORBED;
                    }
                }
                case GRAB -> {
                    target = occupant;
                    end = (flags & GRAB_TARGETS) != 0 ? RayOutcome.GRABBED : RayOutcome.ABSORBED;
                }
            }
        }
        ctx.emit(new SimEvent.RayFired(ctx.frame(), emitter.slot(), type, path));
        passes.forEach(ctx::emit);
        if (target == null) {
            return false;
        }
        boolean hit = switch (end) {
            case DESTROYED -> {
                Rules.zap(world, target, ctx.out());
                yield true;
            }
            case PUSHED -> {
                Push.start(world, target, moving, ctx.out());
                ctx.behaviour(target).onPush(ctx, target, emitter);
                yield true;
            }
            case GRABBED -> ctx.behaviour(target).onGrab(ctx, target, emitter);
            case REVERSED -> ctx.behaviour(target).onZap(ctx, target, emitter);
            default -> false;
        };
        RayOutcome outcome = hit || end == RayOutcome.BLOCKED ? end : RayOutcome.ABSORBED;
        ctx.emit(new SimEvent.RayHit(ctx.frame(), emitter.slot(), target.slot(), outcome));
        return hit;
    }

    /**
     * The prism rule ({@code S:8819-8834}, oracle §3.3) with p = the prism's facing: a ray moving p leaves moving
     * p − 1, a ray moving p + 1 leaves moving p + 2; every other ray is absorbed (null).
     */
    static Direction deflect(Direction prism, Direction moving) {
        if (moving == prism) {
            return prism.left();
        }
        if (moving == prism.right()) {
            return moving.right();
        }
        return null;
    }

    /** A converter passes a ray moving along its axis ({@code (facing XOR dir) & 1 == 0}, {@code S:8835-8839}). */
    static boolean parallel(Direction axis, Direction moving) {
        return ((axis.ordinal() ^ moving.ordinal()) & 1) == 0;
    }
}
