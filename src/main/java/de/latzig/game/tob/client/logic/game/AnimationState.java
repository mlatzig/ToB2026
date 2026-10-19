package de.latzig.game.tob.client.logic.game;

import java.util.ArrayList;
import java.util.List;

import de.latzig.game.tob.client.logic.MeshFrame;
import de.latzig.game.tob.client.logic.Motion;
import de.latzig.game.tob.client.logic.MotionCarry;
import de.latzig.game.tob.client.logic.Pose;
import de.latzig.game.tob.core.model.ObjectFamily;
import de.latzig.game.tob.core.sim.Direction;
import de.latzig.game.tob.core.sim.ForcerLifts;
import de.latzig.game.tob.core.sim.MoveKind;
import de.latzig.game.tob.core.sim.SimEvent;
import de.latzig.game.tob.core.sim.SimObject;
import de.latzig.game.tob.core.sim.Walk;
import de.latzig.game.tob.core.sim.WorldState;
import de.latzig.game.tob.core.sim.element.EnemyBehaviour;
import de.latzig.game.tob.core.sim.element.KlondikeBehaviour;

/**
 * Animation frame selection (spec §4.2), following the ToB 2009 renderers with the state view as the clock. 16 keys
 * are one simulation frame (ToB 2009: 16 frames per unit; a walk of 32 frames plays keys 0..512). The selection reads
 * the current state and adds {@code alpha} of the next frame where that frame advances the animation (never for
 * objects other than spiders while the world is frozen). UV animation is stepped by the mesh sampler, not here.
 * <ul>
 * <li>Spiders: a walk plays 0 → 512 over its 32 frames, north/east forward and south/west backwards (N/S share group
 * 1, E/W group 2); standing plays 512 → 640 once from the end of the last walk and then holds (group 1).</li>
 * <li>Worm, lizard, watcher: a walk plays 0 → 512 forward; standing 0. The watcher alone turns its mesh
 * ({@link WatcherTurns}).</li>
 * <li>Hopper: a walk uses group 2–5 (N, E, S, W); its 16-frame wait plays group 1 0 → 256; standing group 1, 0.</li>
 * <li>Fixed enemies loop 0 → 192 every 12 frames; rotating enemies show {@link EnemyBehaviour#rotation} × 32 keys;
 * both follow the {@link FreezeClock}.</li>
 * <li>Converter loops 0 → 256 (16 frames) with an offset per slot; bonus flag loops 0 → 128, mine 0 → 96, the
 * exchanger sparkle ping-pongs 0 → 96.</li>
 * <li>Klondike: 0, while vanishing 0 → 170; forcers play 0 → 512 while a forcer motion of their direction runs;
 * freezer 0 → 2048 over its 128-frame timer; wiper 0 → 256 per sweep; time bomb group by speed (1 slow, 2 medium,
 * 3 fast and faster), key = 16 × fuse / (speed + 1).</li>
 * </ul>
 */
public final class AnimationState {

    public static final int KEYS_PER_FRAME = 16;
    public static final int WALK_KEYS = 512;
    public static final int STAND_KEYS = 128;
    public static final int CARRY_KEYS = 170;

    private final WorldState world;
    private final int[] standSince;
    private final boolean[] wasWalking;
    private final Direction[] lastDirection;
    private final List<Integer> collectTicks = new ArrayList<>();
    private final WatcherTurns turns = new WatcherTurns();
    private final FreezeClock enemyClock = new FreezeClock();
    private int tick;

    public AnimationState(WorldState world) {
        this.world = world;
        int slots = world.objects().size();
        standSince = new int[slots];
        wasWalking = new boolean[slots];
        lastDirection = new Direction[slots];
        for (SimObject object : world.objects()) {
            lastDirection[object.slot()] = object.moveDirection();
        }
    }

    /** Called after every step with its events and the driver's tick. */
    public void onStep(List<SimEvent> events, int newTick) {
        this.tick = newTick;
        for (SimEvent event : events) {
            switch (event) {
                case SimEvent.TurnStarted turn ->
                        turns.start(turn.slot(), lastDirection[turn.slot()], turn.toFacing(), turn.frames());
                case SimEvent.Collected collected -> collectTicks.add(newTick);
                default -> {
                }
            }
        }
        for (SimObject object : world.objects()) {
            int slot = object.slot();
            boolean walking = isWalking(object);
            if (wasWalking[slot] && !walking) {
                standSince[slot] = newTick;
            }
            wasWalking[slot] = walking;
            lastDirection[slot] = object.moveDirection();
        }
        enemyClock.update(world.isFrozen(), world.frame());
    }

    /** The driver's tick of the last {@link #onStep}; 0 before the first step. */
    public int tick() {
        return tick;
    }

    public MeshFrame frame(SimObject object, float alpha) {
        return frame(object, alpha, MotionCarry.NONE);
    }

    /**
     * The frame with the driver's {@link MotionCarry}: a standing spider whose walk the look-ahead predicts plays that
     * walk's first keys instead of the stand keys (Task 16), as {@link Motion} draws it walking.
     */
    public MeshFrame frame(SimObject object, float alpha, MotionCarry carry) {
        boolean frozen = world.isFrozen();
        float advance = frozen && !object.isSpider() ? 0f : alpha;
        return switch (object.type().family()) {
            case ZAPPER, PUSHER, GRABBER -> spider(object, alpha, carry);
            case WORM, LIZARD -> MeshFrame.of(1, isWalking(object) ? walkKey(object.moveCounter(), advance) : 0f);
            case WATCHER -> new MeshFrame(1, isWalking(object) ? walkKey(object.moveCounter(), advance) : 0f,
                    turns.heading(object.slot(), object.moveDirection(), object.classTimer(), advance));
            case HOPPER -> hopper(object, advance);
            case ENEMY_ZAPPER, ENEMY_PUSHER -> MeshFrame.of(1,
                    loop(KEYS_PER_FRAME * (enemyClock.frame() + advance), 192f));
            case ROTATING_ZAPPER, ROTATING_PUSHER -> MeshFrame.of(1, rotationKey(enemyClock.frame(), advance));
            case CONVERTER -> MeshFrame.of(1,
                    loop(KEYS_PER_FRAME * (tick + alpha) + (object.slot() * 37 % 16) * KEYS_PER_FRAME, 256f));
            case BONUS_FLAG -> MeshFrame.of(1, loop(KEYS_PER_FRAME * (tick + alpha), 128f));
            case PROXIMITY_MINE -> MeshFrame.of(1, loop(KEYS_PER_FRAME * (tick + alpha), 96f));
            case EXCHANGER -> MeshFrame.of(1, pingPong(KEYS_PER_FRAME * (tick + alpha), 96f));
            case KLONDIKE -> MeshFrame.of(1, object.isVanishing() && object.destroyCounter() > 0
                    ? Math.min(CARRY_KEYS, KEYS_PER_FRAME
                            * (KlondikeBehaviour.VANISH_FRAMES - object.destroyCounter() + alpha))
                    : 0f);
            case FORCER_UP, FORCER_DOWN -> MeshFrame.of(1, forcerKey(object, advance));
            case FREEZER -> MeshFrame.of(1, object.classTimer() > 0
                    ? KEYS_PER_FRAME * (128 - object.classTimer() + alpha)
                    : 0f);
            case WIPER -> MeshFrame.of(1, object.classTimer() > 0
                    ? KEYS_PER_FRAME * ((63 - object.classTimer()) % 16 + advance)
                    : 0f);
            case TIME_BOMB -> timeBomb(world.tower().behaviour().timeBombSpeed(), object.classTimer(), advance);
            case PUSH_BLOCK, REFLECTOR, PRISM -> MeshFrame.of(1, 0f);
        };
    }

    /** Keys of the klondikes the grabber carries (group 3), one per collected klondike, in collection order. */
    public List<Float> carriedKlondikeKeys(float alpha) {
        int count = Math.min(world.counters().klondikesCollected(), collectTicks.size());
        List<Float> keys = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            keys.add(Math.min(CARRY_KEYS, KEYS_PER_FRAME * (tick - collectTicks.get(i) + alpha)));
        }
        return keys;
    }

    /**
     * Offset of carried klondike {@code index} from the grabber's origin in fields: ToB 2009
     * {@code (4·(i % 2), 2·(i / 8), 2·((i / 2) % 4))} units ({@code ObjectElementGRBRenderer}), z mirrored.
     */
    public static Pose carriedKlondikeOffset(int index) {
        return new Pose(4f * (index % 2) / 32f, 2f * (index / 8) / 32f, -2f * ((index / 2) % 4) / 32f);
    }

    private MeshFrame spider(SimObject object, float alpha, MotionCarry carry) {
        if (isWalking(object)) {
            return spiderWalk(object.moveDirection(), object.moveCounter(), alpha);
        }
        Direction predicted = object.isMoving() ? null : carry.walk(object.slot());
        return predicted != null ? spiderWalk(predicted, Walk.FRAMES, alpha)
                : spiderStand(tick - standSince[object.slot()], alpha);
    }

    static boolean isWalking(SimObject object) {
        return object.moveKind() == MoveKind.WALK && object.isMoving();
    }

    public static MeshFrame spiderWalk(Direction direction, int counter, float alpha) {
        float keys = walkKey(counter, alpha);
        int group = direction == Direction.NORTH || direction == Direction.SOUTH ? 1 : 2;
        boolean forward = direction == Direction.NORTH || direction == Direction.EAST;
        return MeshFrame.of(group, forward ? keys : WALK_KEYS - keys);
    }

    static MeshFrame spiderStand(int ticksStanding, float alpha) {
        return MeshFrame.of(1, WALK_KEYS + Math.min(STAND_KEYS, KEYS_PER_FRAME * (ticksStanding + alpha)));
    }

    static float walkKey(int counter, float advance) {
        return Math.min(WALK_KEYS, KEYS_PER_FRAME * (32 - counter + advance));
    }

    /** The rotating enemies: {@link EnemyBehaviour#rotation} (0..31) × 32 keys, towards the next frame's value. */
    static float rotationKey(int frame, float advance) {
        int from = EnemyBehaviour.rotation(frame);
        int to = EnemyBehaviour.rotation(frame + 1);
        if (to < from) {
            to += 32;
        }
        return 32f * (from + (to - from) * advance);
    }

    static MeshFrame timeBomb(int speed, int fuse, float advance) {
        return MeshFrame.of(Math.min(speed, 2) + 1, KEYS_PER_FRAME * ((float) fuse / (speed + 1) + advance));
    }

    private MeshFrame hopper(SimObject object, float advance) {
        if (isWalking(object)) {
            return MeshFrame.of(2 + object.moveDirection().ordinal(), walkKey(object.moveCounter(), advance));
        }
        if (object.moveKind() == MoveKind.IDLE && object.isMoving()) {
            return MeshFrame.of(1, KEYS_PER_FRAME * (16 - object.moveCounter() + advance));
        }
        return MeshFrame.of(1, 0f);
    }

    private float forcerKey(SimObject object, float advance) {
        ForcerLifts forcer = world.forcerLifts();
        boolean up = object.type().family() == ObjectFamily.FORCER_UP;
        if (!forcer.isActive() || forcer.isUp() != up) {
            return 0f;
        }
        return KEYS_PER_FRAME * (ForcerLifts.FRAMES - forcer.timer() + advance);
    }

    private static float loop(float keys, float period) {
        float value = keys % period;
        return value < 0 ? value + period : value;
    }

    private static float pingPong(float keys, float top) {
        float value = loop(keys, 2 * top);
        return value <= top ? value : 2 * top - value;
    }
}
