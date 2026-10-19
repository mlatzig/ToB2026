package de.latzig.game.tob.client.logic.effects;

/**
 * The knobs of the ambient effects in one place (Phase 4b-2 spec §2 decisions 2 and 4): the author tunes them at the
 * acceptance, Phase 5 turns them into options. Times are simulation frames (0.14 s each); the effects run on
 * simulation time, so they stand still while paused and keep pace with fast-forward.
 * <ul>
 * <li>Lightning flash, mixed: a strong, brief scene light flash ({@link #SCENE_FLASH}: white added to the sun, so the
 * faces the sun lights, the objects and the floors turn white while the unlit sky stays dark, as in the original's
 * palette flash) and a weak white full-screen overlay ({@link #OVERLAY_FLASH}: ToB 2005's blend sprite, much
 * fainter). As in ToB 2005 ({@code twr.cpp:982-990}) the flash comes with the bolt: it jumps to full
 * {@link #FLASH_FRAMES} before the strike and fades linearly to nothing at the strike, when the ground debris
 * explodes (the original flashed in the bolt's frame). Both strengths 0: no flash.</li>
 * <li>Ground haze: a gentle brightness pulse of {@link #HAZE_PULSE} around 1 with the period
 * {@link #HAZE_PULSE_FRAMES}; 0 shows exactly ToB 2005's static haze.</li>
 * </ul>
 */
public final class AmbientTuning {

    /**
     * Scene flash: white light added to the sun at the strike, in units of full white (0 = no light flash). 2.0 turns
     * every face the sun lights white for a moment, like the original's flash palette ({@code W002C58}).
     */
    public static final float SCENE_FLASH = 2.0f;
    /** Overlay flash: the alpha of the white full-screen overlay at the strike (0 = none; ToB 2005 was near 1). */
    public static final float OVERLAY_FLASH = 0.15f;
    /**
     * The flash's length in simulation frames: 1.5 frames (0.21 s) are ToB 2005's 24-frame blend
     * ({@code TWR_FXL_FRAME_BLEND_LEN}) at 16 ToB 2005 frames per simulation frame; the original flashed one frame.
     */
    public static final float FLASH_FRAMES = 1.5f;
    /** The haze's brightness pulse: brightness 1 ± this amplitude (0 = ToB 2005's static haze). */
    public static final float HAZE_PULSE = 0.15f;
    /** The haze pulse's period in simulation frames: 8 frames (1.12 s) are the original blue pulse's period. */
    public static final float HAZE_PULSE_FRAMES = 8f;

    private AmbientTuning() {
    }

    /** The flash's two strengths at one moment: the sun's added white and the overlay's alpha. */
    public record Flash(float scene, float overlay) {

        public static final Flash NONE = new Flash(0f, 0f);

        /** The tuned strengths at the flash curve's value {@code curve} (0..1). */
        public static Flash at(float curve) {
            return at(curve, SCENE_FLASH, OVERLAY_FLASH);
        }

        /** The given strengths at the flash curve's value {@code curve} (0..1). */
        public static Flash at(float curve, float scene, float overlay) {
            return curve <= 0f ? NONE : new Flash(curve * scene, curve * overlay);
        }
    }

    /**
     * The sun's added white at the flash curve's value {@code curve} (0..1): {@code Flash.at(curve).scene()} without
     * allocating a {@link Flash} (the per-frame path).
     */
    public static float sceneFlash(float curve) {
        return curve <= 0f ? 0f : curve * SCENE_FLASH;
    }

    /** The overlay's alpha at the flash curve's value {@code curve} (0..1): {@code Flash.at(curve).overlay()}. */
    public static float overlayFlash(float curve) {
        return curve <= 0f ? 0f : curve * OVERLAY_FLASH;
    }

    /** {@link #sceneFlash(float)} at the Flash strength option's factor {@code strength} (Phase 5b, 0..1). */
    public static float sceneFlash(float curve, float strength) {
        return sceneFlash(curve) * strength;
    }

    /** {@link #overlayFlash(float)} at the Flash strength option's factor {@code strength} (Phase 5b, 0..1). */
    public static float overlayFlash(float curve, float strength) {
        return overlayFlash(curve) * strength;
    }

    /**
     * One colour channel of the sun or the ambient light (Phase 5b decision 6): its own value scaled by the Brightness
     * option's factor, plus the flash's added white (not scaled by brightness: the flash stays a full white flash).
     */
    public static float light(float own, float brightness, float flash) {
        return own * brightness + flash;
    }

    /**
     * The flash curve {@code until} simulation frames before a strike: ToB 2005's {@code countdown / 24}, full
     * {@link #FLASH_FRAMES} before the strike, falling linearly to 0 at the strike; 0 earlier and afterwards.
     */
    public static float flashCurve(float until) {
        return flashCurve(until, FLASH_FRAMES);
    }

    /** The flash curve for a flash of {@code frames} simulation frames. */
    public static float flashCurve(float until, float frames) {
        if (until <= 0f || until >= frames) {
            return 0f;
        }
        return until / frames;
    }

    /** The haze's brightness factor at simulation time {@code now} with the tuned pulse. */
    public static float hazeBrightness(float now) {
        return hazeBrightness(now, HAZE_PULSE, HAZE_PULSE_FRAMES);
    }

    /** The haze's brightness factor: {@code 1 + amplitude · sin(2π now / period)}; exactly 1 at amplitude 0. */
    public static float hazeBrightness(float now, float amplitude, float period) {
        if (amplitude == 0f) {
            return 1f;
        }
        return 1f + amplitude * (float) Math.sin(2.0 * Math.PI * now / period);
    }
}
