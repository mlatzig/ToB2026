package de.latzig.game.tob.client.logic.frontend;

import de.latzig.game.tob.client.logic.CameraPlacement;
import de.latzig.game.tob.client.logic.Pose;

/**
 * The interludes' camera (Phase 5c-2 spec decision 8; report 17 §5, {@code S:20442-20467}): the original's chase
 * camera, following the demo's spider, with a fixed, level view north. The original takes the spider's world position
 * (x, height, z; 512 units per cell, from the grid's centre, z northwards) and the dolly h ({@code W001698},
 * {@value #START_DOLLY} at the start) and sets {@code camX = x·205/256}, {@code camY = y·320/256 + h},
 * {@code camZ = z − 8h}. In ToB 2026's scene (one cell = 1.0, the original's centre at
 * {@link CameraPlacement#GRID_CENTRE}, z southwards; as {@link CameraPlacement}) that is: x pulled 20 % towards the
 * centre, y = 320/256 of the spider's height + h/512, z = the spider's z + 8h/512, looking along −z.
 * <p>
 * The scripted dolly: every demo frame {@code h += 2 · (W001696 >> 6)} with the script byte read last, sign-extended
 * (bits 6-7 = 01: +2, 11: −2, 00: none); after the script's terminating 0 it stops; h is never clamped. Between
 * frames the eye moves on with the last frame's rate (as the spider's drawn position does).
 * <p>
 * The view's axis lies at {@link StartScreens#INTERLUDE_AXIS} from the top of the screen ({@link #frustum}), in the
 * band between the logo and the result window, as the original's 3D viewport sat above its result box.
 */
public final class ChaseCamera {

    /** The dolly at a demo's start ({@code W001698 = $120}, {@code S:20400}). */
    public static final int START_DOLLY = 0x120;
    /** The dolly's change per frame for bits 6-7 = 01 (and −2 for 11). */
    public static final int DOLLY_STEP = 2;
    /** {@code x · 205/256}: the eye's x pulled 20 % towards the grid's centre. */
    public static final float PULL = 205f / 256f;
    /** {@code y · 320/256}: the eye rises faster than the spider. */
    public static final float RISE = 320f / 256f;
    /** {@code z − 8h}: the eye's distance behind the spider in dolly units. */
    public static final int BACK = 8;
    /** The fixed view direction: north (−z), level. */
    public static final Pose DIRECTION = new Pose(0f, 0f, -1f);

    private int dolly = START_DOLLY;
    private int rate;

    /** One demo frame with the script byte read last ({@code W001696}): {@code h += 2 · (byte >> 6)}. */
    public void step(byte lastByte) {
        rate = DOLLY_STEP * (lastByte >> 6);
        dolly += rate;
    }

    /** The dolly h in the original's units. */
    public int dolly() {
        return dolly;
    }

    /** The eye for the spider drawn at {@code spider}, {@code alpha} of the way into the next frame. */
    public Pose eye(Pose spider, float alpha) {
        float h = (dolly + rate * alpha) / CameraPlacement.UNITS_PER_CELL;
        float centre = CameraPlacement.GRID_CENTRE;
        return new Pose(centre + (spider.x() - centre) * PULL, spider.y() * RISE + h, spider.z() + BACK * h);
    }

    /**
     * The view's frustum at {@code near} with the vertical field of view {@code fovYDegrees} and the aspect ratio
     * {@code aspect}, its axis at {@link StartScreens#INTERLUDE_AXIS} from the top: {@code {left, right, top,
     * bottom}} as jME's {@code Camera.setFrustum} takes them.
     */
    public static float[] frustum(float fovYDegrees, float aspect, float near) {
        float half = near * (float) Math.tan(Math.toRadians(fovYDegrees / 2.0));
        float width = half * aspect;
        float top = 2f * half * StartScreens.INTERLUDE_AXIS;
        return new float[] {-width, width, top, top - 2f * half};
    }
}
