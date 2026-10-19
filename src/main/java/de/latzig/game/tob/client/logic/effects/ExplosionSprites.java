package de.latzig.game.tob.client.logic.effects;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The 12 FX atlas sprites of an exploding element (Phase 4b-1 spec §2 decision 1), ToB 2005's
 * {@code BPE_updateExplosion} ({@code bpe.cpp:640-720}) as closed-form functions of the time since the start, in
 * simulation frames ({@link ExplosionDebris#TOB2005_FRAMES_PER_FRAME} ToB 2005 frames each; f below). The sprite frame
 * is {@code s = ⌊f / 5⌋} ({@code twr.cpp:2335}). Units: 32 per field; sizes are half sizes ({@code dxe.cpp:1329-1350}).
 * <ul>
 * <li>9 sparks: ToB 2005 half size 1.0–1.3, −0.008 per frame; they faded out within about 8–10 simulation frames of
 * the 32-frame explosion. Author decision (2026-10-10, first round): in play the sparks were barely noticeable next
 * to the fireball (half size 7) and the smoke (half size 5–9) — 5–7× bigger — so both the start size and the fade
 * time were scaled up. Author decision (2026-10-10, after a live check): enlarging the sparks did not help — they
 * were hidden by the explosion cloud itself while it was large, only becoming visible once the cloud had nearly
 * vanished — so the size is reverted to ToB 2005's own {@code SPARK_SIZE_FACTOR} = 1.0, while the fade time stays
 * stretched ×{@code SPARK_LIFETIME_FACTOR} = 1.75 (within the author's original 1.5–2× request), by shrinking the
 * same factor more slowly, keeping the shrink linear: ToB 2005's own size 1.0–1.3, fading out after about
 * 13.7–17.8 simulation frames instead of 8–10 — comfortably inside the 32-frame explosion
 * ({@link ExplosionDebris#DURATION}), still a pure closed-form function of time (the two factors are independent:
 * the fade-time formula below cancels {@code SPARK_SIZE_FACTOR} out of the lifetime, so reverting the size alone
 * does not also revert the lifetime). Atlas sequence {@value #SPARKS}, frame s / 2 (held on the sequence's last
 * frame once play outlasts it); they fly along a direction (0, 0..0.79, 1..2.5) turned by a random yaw, 0.05 per
 * frame, while gravity takes 0.015 per frame off its y (unchanged); shown while the size is positive and the
 * explosion has not ended.</li>
 * <li>1 fireball: half size 7, does not move; sequence {@value #FIREBALL}, frame s; shown while s &lt; 32.</li>
 * <li>2 smoke puffs: half size 5–9, −0.001 per frame; sequence {@value #SMOKE}, frame s; they drift along their
 * direction without gravity; shown while s &lt; 24.</li>
 * </ul>
 * The random numbers are drawn in ToB 2005's order (direction y, direction z, yaw, size, per sprite).
 */
public final class ExplosionSprites {

    public static final String SPARKS = "sparks";
    public static final String FIREBALL = "fireball";
    public static final String SMOKE = "smoke";
    /** ToB 2005 {@code BPE_SPRITE_ID_EXPL_NUM} and {@code BPE_SPRITE_ID_EXPL_BALL}. */
    public static final int COUNT = 12;
    public static final int SPARK_COUNT = 9;

    /**
     * Author decision (2026-10-10, second round): reverted to ToB 2005's own value (1×) after a live check — the
     * first round's ×2.5 did not make the sparks more noticeable, since they were hidden by the explosion cloud
     * itself while it was large, not by their own size; see the class javadoc. Kept as a named constant (rather than
     * folded away) so a future size change stays a one-number decision, independent of {@link #SPARK_LIFETIME_FACTOR}.
     */
    private static final float SPARK_SIZE_FACTOR = 1.0f;
    /**
     * Author decision (2026-10-10, replacing ToB 2005's implicit 1×): the time a spark takes to shrink to nothing is
     * stretched ×1.75 (within the author's requested 1.5–2× range), by slowing {@link #SPARK_FADE_PER_TOB2005_FRAME}
     * accordingly — independent of {@link #SPARK_SIZE_FACTOR} so each decision is a single named number.
     */
    private static final float SPARK_LIFETIME_FACTOR = 1.75f;
    /**
     * ToB 2005's spark shrink, 0.008 per ToB 2005 frame, scaled so that a spark's lifetime (start size over fade
     * rate) grows by exactly {@link #SPARK_LIFETIME_FACTOR}, independently of {@link #SPARK_SIZE_FACTOR} (it cancels
     * out of the lifetime): {@code 0.008 * SPARK_SIZE_FACTOR / SPARK_LIFETIME_FACTOR}.
     */
    private static final float SPARK_FADE_PER_TOB2005_FRAME = 0.008f * SPARK_SIZE_FACTOR / SPARK_LIFETIME_FACTOR;

    private static final float UNITS = 32f;

    /** One sprite to draw: FX atlas sequence and frame, centre and half size in fields (scene axes). */
    public record Sprite(String sequence, int frame, float x, float y, float z, float halfSize) {
    }

    private final float x;
    private final float y;
    private final float z;
    /** Per sprite: direction x, y, z and start size. */
    private final float[] start = new float[COUNT * 4];

    private ExplosionSprites(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /** The sprites of an explosion whose sprites start at ({@code x}, {@code y}, {@code z}) in fields. */
    public static ExplosionSprites start(float x, float y, float z, Random random) {
        ExplosionSprites sprites = new ExplosionSprites(x, y, z);
        for (int i = 0; i < COUNT; i++) {
            float dirY = random.nextInt(80) / 100f;
            float dirZ = 1f + random.nextInt(4) / 2f;
            float yaw = random.nextInt(31415) / 5000f;
            float size = i < SPARK_COUNT ? 1f + random.nextInt(4) / 10f : 5f + random.nextInt(5);
            sprites.start[i * 4] = (float) Math.sin(yaw) * dirZ;
            sprites.start[i * 4 + 1] = dirY;
            sprites.start[i * 4 + 2] = (float) Math.cos(yaw) * dirZ;
            sprites.start[i * 4 + 3] = size;
        }
        return sprites;
    }

    /** The sprites shown {@code frames} simulation frames after the start. */
    public List<Sprite> at(float frames) {
        float f = Math.max(0f, frames) * ExplosionDebris.TOB2005_FRAMES_PER_FRAME;
        int s = (int) (f / 5f);
        List<Sprite> out = new ArrayList<>(COUNT);
        for (int i = 0; i < COUNT; i++) {
            float dx = start[i * 4];
            float dy = start[i * 4 + 1];
            float dz = start[i * 4 + 2];
            float size = start[i * 4 + 3];
            if (i < SPARK_COUNT) {
                float half = size * SPARK_SIZE_FACTOR - SPARK_FADE_PER_TOB2005_FRAME * f;
                if (frames < ExplosionDebris.DURATION && half > 0f) {
                    out.add(new Sprite(SPARKS, s / 2, x + 0.05f * dx * f / UNITS,
                            y + 0.05f * (dy * f - 0.0075f * f * f) / UNITS, z + 0.05f * dz * f / UNITS, half / UNITS));
                }
            } else if (i == SPARK_COUNT) {
                if (s < 32) {
                    out.add(new Sprite(FIREBALL, s, x, y, z, 7f / UNITS));
                }
            } else if (s < 24) {
                out.add(new Sprite(SMOKE, s, x + 0.05f * dx * f / UNITS, y + 0.05f * dy * f / UNITS,
                        z + 0.05f * dz * f / UNITS, (size - 0.001f * f) / UNITS));
            }
        }
        return out;
    }
}
