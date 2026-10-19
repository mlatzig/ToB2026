package de.latzig.game.tob.client.logic.effects;

/**
 * The knobs of the Phase 4b-3 sky in one place (spec §2): the author tunes them at the acceptance, Phase 5 turns some
 * into options. Times are simulation frames (0.14 s each); the sky runs on simulation time, so the clouds and the moon
 * stand still while paused and keep pace with fast-forward. Strengths of 0 switch a part off.
 * <ul>
 * <li>Clouds (decision 3): ToB 2005's constants ({@code sky.h:42-47}).</li>
 * <li>Moon (decisions 5-12): size shares and angles, placement of large and giant moons, the phases, the earthshine,
 * the rotation period, the texture resolutions per size class (fixed by the Task 1 timing spike), the detail
 * switch and the time budget, the haze (acceptance change, Phase 6b-2).</li>
 * </ul>
 */
public final class SkyTuning {

    /** ToB 2005 {@code SKY_CLOUDS_COVER}: the cover of {@code clouds} towers. */
    public static final int CLOUD_COVER = 80;
    /** ToB 2005 {@code SKY_CLOUDS_HEAVYCOVER}: the cover of {@code heavyClouds} towers. */
    public static final int HEAVY_CLOUD_COVER = 0;
    /** ToB 2005 {@code SKY_CLOUDS_SHARPNESS}: the exponential curve's base. */
    public static final double CLOUD_SHARPNESS = 0.98;
    /** ToB 2005 {@code SKY_CLOUDS_HEIGHTFACTOR}: the cloud plane lies at this share of the sky plane's height. */
    public static final double CLOUD_HEIGHT = 0.6;
    /** ToB 2005 {@code SKY_CLOUDS_TEXFACTOR}: the cloud texture repeats this often across the sky plane. */
    public static final double CLOUD_TILES = 6.0;
    /** ToB 2005's vertex alpha of the cloud plane at its centre, of 255 ({@code sky.cpp:297-301}). */
    public static final int CLOUD_ALPHA = 192;
    /** ToB 2005 {@code SKY_CLOUDS_FRAME_SPEED}: ToB 2005 frames per unit of texture drift. */
    public static final double CLOUD_FRAME_SPEED = 8192.0;

    /** The share of large moons (decision 8: about 1 in 6). */
    public static final float LARGE_SHARE = 1f / 6f;
    /** The share of giant moons (decision 8: about 1 in 20; a pair is never giant). */
    public static final float GIANT_SHARE = 1f / 20f;
    /** The angular width of a normal moon (Phase 4a's 6°). */
    public static final float NORMAL_DEGREES = 6f;
    public static final float LARGE_DEGREES = 20f;
    public static final float GIANT_DEGREES = 45f;
    /**
     * The elevation of a large moon's centre: 10°..30° of sky, inside the spider view's upper half (34.4°, decision
     * 14), ± {@link #ELEVATION_JITTER_DEGREES}.
     */
    public static final float LARGE_ELEVATION_DEGREES = 17f;
    /**
     * The elevation of a giant moon's centre: its top stays just inside the spider view (−12.5°..32.5°), its lower
     * quarter sets behind the hills (decision 8), ± {@link #ELEVATION_JITTER_DEGREES}.
     */
    public static final float GIANT_ELEVATION_DEGREES = 10f;
    public static final float ELEVATION_JITTER_DEGREES = 2f;
    /** How far a large moon's azimuth may lie from its compass direction (decision 14). */
    public static final float LARGE_AZIMUTH_JITTER_DEGREES = 12f;
    /** How far a giant moon's azimuth may lie from its compass direction (decision 14). */
    public static final float GIANT_AZIMUTH_JITTER_DEGREES = 8f;

    /** The phase angle (light to viewer, seen from the moon) of full-looking moons: about three quarters lit. */
    public static final float FULL_PHASE_DEGREES = 55f;
    /** The phase angle of the thin crescents (types 1 A and 5). */
    public static final float CRESCENT_PHASE_DEGREES = 110f;
    /** The phase angle of the wider crescent (type 1 B). */
    public static final float WIDE_CRESCENT_PHASE_DEGREES = 93f;
    /**
     * The earthshine on the moon's dark side (ambient light in linear colour, 0 = black; with jME's gamma correction
     * 0.02 shows as a faint grey); type 1 B has none.
     */
    public static final float EARTHSHINE = 0.02f;
    /** The moon's own sun light (linear colour, white × this). */
    public static final float MOON_LIGHT = 1.5f;
    /**
     * Moon haze (acceptance change, Phase 6b-2): how far the moon is blended towards the sky colour behind it high in
     * the sky, from {@link #MOON_HAZE_FADE_DEGREES} of elevation up (0 = pure moon colours, 1 = only sky).
     */
    public static final float MOON_HAZE_HIGH = 0.25f;
    /** Moon haze: the blend towards the sky at and below the horizon. */
    public static final float MOON_HAZE_HORIZON = 0.6f;
    /** Moon haze: the elevation over which the blend falls linearly from the horizon's to the high sky's. */
    public static final float MOON_HAZE_FADE_DEGREES = 30f;
    /** One turn of the moon in simulation frames: 1286 frames ≈ 3 minutes (decision 9). */
    public static final float MOON_TURN_FRAMES = 1286f;

    /** The moon texture widths (height = width / 2) at full detail, by size class (Task 1 timing spike). */
    public static final int NORMAL_TEXTURE = 1024;
    public static final int LARGE_TEXTURE = 2048;
    public static final int GIANT_TEXTURE = 4096;
    /** "Moon texture detail: half" (decision 12): one level smaller in each direction; the displayed size stays. */
    public static final boolean MOON_HALF_DETAIL = false;
    /** The detail texture against blur on large and giant moons: how much it darkens at most (0 = none). */
    public static final float DETAIL_STRENGTH = 0.15f;
    /** The detail texture's repeats around the moon (half as many from pole to pole). */
    public static final int DETAIL_TILES = 32;
    /**
     * Gas planet bands (acceptance fix): how far the bands reach from a mid colour towards the dark brown and the
     * light cream band colours (1 = the full range, 0 = one flat colour). Before the fix the range was about a third.
     */
    public static final double GAS_BAND_CONTRAST = 1.0;
    /** Gas planet: large-scale waviness of the bands (amplitude in sine-of-latitude units; 0 = straight bands). */
    public static final double GAS_BAND_WAVE = 0.07;
    /**
     * Gas planet: turbulence of the band edges, from noise squeezed in latitude and stretched along the circumference,
     * so the edges are ragged and swirled and differ around the planet; this is what makes the turn visible
     * (0 = smooth edges).
     */
    public static final double GAS_TURBULENCE = 0.06;
    /**
     * Gas planet: lighter and darker streaks drawn out along the circumference, varying with the longitude (share of
     * brightness up or down; 0 = none).
     */
    public static final double GAS_STREAKS = 0.22;

    /**
     * Craters (acceptance fix): how far a crater's outline wanders from its circle, as a share of its radius (noise
     * around the rim, about a dozen bumps per circumference; rarely up to twice this; 0 = round).
     */
    public static final double CRATER_IRREGULARITY = 0.09;
    /** Craters: the share of slightly elongated (oval) craters. */
    public static final double CRATER_OVAL_SHARE = 0.3;
    /** Craters: the longest axis of an oval crater against its circle (its area stays; 1 = round). */
    public static final double CRATER_MAX_STRETCH = 1.45;
    /** Craters: the share of eroded craters: flatter, with a lower, wider and softer rim. */
    public static final double CRATER_ERODED_SHARE = 0.3;
    /** Craters: each crater's depth is 1 ± this times the base depth (0 = all alike). */
    public static final double CRATER_DEPTH_VARIATION = 0.5;
    /** Craters: each crater's rim height and brightness is 1 ± this times the base rim (0 = all alike). */
    public static final double CRATER_RIM_VARIATION = 0.6;

    /**
     * The time from the tower start within which the moon's textures must be ready (decision 11), in seconds: the
     * READY phase lasts until the first move, which a player hardly makes within a second. The resolutions above are
     * chosen so that 3 × the time measured on the author's PC stays within it.
     */
    public static final double MOON_BUDGET_SECONDS = 1.0;

    private SkyTuning() {
    }
}
