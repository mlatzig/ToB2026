package de.latzig.game.tob.core.sim.element;

import de.latzig.game.tob.core.sim.Direction;
import de.latzig.game.tob.core.sim.SimContext;
import de.latzig.game.tob.core.sim.SimObject;

/**
 * The enemy emitters (classes 1–4, {@code S:9376-9462}). Each fires in its own handler, in list order, so its ray
 * sees all movement of the frame and the decisions of the objects before it. None fires while the world is frozen.
 * The rotating enemies follow the global frame counter {@code W001678}, so all of them are synchronised and the
 * phase keeps running during a freeze; their facing (+0C) is not changed, as in the original (the handler only
 * stores the animation frame in +1E).
 */
public enum EnemyBehaviour implements ElementBehaviour {
    /** Class 1, fixed zapper: a zap every frame in its facing ({@code L00B198}, {@code S:9376-9397}). */
    FIXED_ZAPPER {
        @Override
        public void act(SimContext ctx, SimObject self) {
            if (!ctx.world().isFrozen()) {
                ctx.fireZap(self, self.facing());
            }
        }
    },
    /** Class 2, fixed pusher: a push every frame in its facing ({@code L00B1E4}, {@code S:9398-9419}). */
    FIXED_PUSHER {
        @Override
        public void act(SimContext ctx, SimObject self) {
            if (!ctx.world().isFrozen()) {
                ctx.firePush(self, self.facing());
            }
        }
    },
    /**
     * Class 3, rotating zapper ({@code L00B230}, {@code S:9420-9441}): it zaps whenever its animation frame
     * {@link #rotation(int)} is a multiple of 8, i.e. in the 9 still frames of each quarter, towards frame / 8.
     */
    ROTATING_ZAPPER {
        @Override
        public void act(SimContext ctx, SimObject self) {
            if (ctx.world().isFrozen()) {
                return;
            }
            int animation = rotation(ctx.frame());
            if (animation % STILL == 0) {
                ctx.fireZap(self, DIRECTIONS[animation / STILL]);
            }
        }
    },
    /**
     * Class 4, rotating pusher ({@code L00B280}, {@code S:9442-9462}): one push per 16 frames, when
     * {@code frame & 15 == 0}, towards {@code (frame >> 4) & 3}. The first north push comes at frame 64.
     */
    ROTATING_PUSHER {
        @Override
        public void act(SimContext ctx, SimObject self) {
            if (ctx.world().isFrozen()) {
                return;
            }
            int frame = ctx.frame();
            if ((frame & 15) == 0) {
                ctx.firePush(self, DIRECTIONS[(frame >> 4) & 3]);
            }
        }
    };

    /** Animation frames per quarter turn; a multiple of it is a still frame facing N, E, S or W. */
    public static final int STILL = 8;

    private static final Direction[] DIRECTIONS = Direction.values();

    /** Table {@code L00AF3C} ({@code S:9229-9236}): the animation frame of the rotating enemies per frame mod 64. */
    private static final int[] ROTATION = {
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
            0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F,
            0x10, 0x10, 0x10, 0x10, 0x10, 0x10, 0x10, 0x10, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17,
            0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F};

    /** The animation frame 0..31 of the rotating enemies in {@code frame} (clockwise, 8 per quarter turn). */
    public static int rotation(int frame) {
        return ROTATION[frame & 63];
    }
}
