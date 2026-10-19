package de.latzig.game.tob.client.logic.effects;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import de.latzig.game.tob.core.model.TowerDefinition;

/**
 * A tower's 3D moon (Phase 4b-3 spec §2 decisions 5-9 and 14), chosen from the tower seed within the original moon type
 * ({@code moon.type}): the look ({@link Look}: the bodies, an optional ring, the phase, the earthshine), the size class
 * ({@link SizeClass}: normal 6°, large 20°, giant 45°), the place in the sky and the slow turn. Normal moons keep
 * Phase 4a's place from the tower data ({@link MoonPlacement}); large and giant moons stand near one of the four
 * compass directions, at an elevation that keeps them inside the level spider view (vertical field 1.2 rad, 34.4°
 * above the horizon). The lit side faces the sun; how much is lit is the look's phase, so crescents stay crescents.
 * A pure function of the tower: the same tower always has the same moon. Directions use the scene's axes (x east,
 * y up, z south); a moon's own frame has x to the viewer's right, y up and z towards the viewer.
 *
 * @param azimuth    radians, clockwise from north (as {@link MoonPlacement})
 * @param elevation  radians above the horizon
 * @param ring       the ring of a ringed look (it stands still; only the bodies turn), or empty
 * @param phase      radians between the light and the viewer, seen from the moon (0 = full)
 * @param earthshine the ambient light on the dark side (0 = black)
 * @param axisRoll   radians: the turning axis leans this far from the moon's up towards its left
 * @param spinStart  radians: the bodies' turn at simulation time 0
 */
public record MoonDesign(Look look, SizeClass size, float azimuth, float elevation, List<Body> bodies,
        Optional<Ring> ring, float phase, float earthshine, float axisRoll, float spinStart) {

    /** The part of {@link AmbientRandom} that draws the moon. */
    public static final int RANDOM_PART = 300;
    /**
     * The turning axis leans this far towards the viewer (cos of its angle to the line of sight): a ring around it
     * shows as an ellipse 0.28 as high as wide, as in the brainstorm preview.
     */
    public static final float AXIS_TOWARDS_VIEWER = 0.28f;

    /** The size classes (decision 8) with their angular widths and full-detail texture widths. */
    public enum SizeClass {
        NORMAL(SkyTuning.NORMAL_DEGREES, SkyTuning.NORMAL_TEXTURE),
        LARGE(SkyTuning.LARGE_DEGREES, SkyTuning.LARGE_TEXTURE),
        GIANT(SkyTuning.GIANT_DEGREES, SkyTuning.GIANT_TEXTURE);

        private final float degrees;
        private final int texture;

        SizeClass(float degrees, int texture) {
            this.degrees = degrees;
            this.texture = texture;
        }

        /** The design's angular width in radians. */
        public float width() {
            return (float) Math.toRadians(degrees);
        }

        /** The texture width of a full-size body (height = width / 2); half detail is one level smaller. */
        public int textureWidth(boolean halfDetail) {
            return halfDetail ? texture / 2 : texture;
        }
    }

    /** The looks per original moon type (decision 6, brainstorm {@code moon-types.html}). */
    public enum Look {
        GREY(0), MARIA(0), ICE(0),
        THIN_CRESCENT(1), WIDE_CRESCENT(1),
        COMPANION(2), PAIR(2), RED_COMPANION(2),
        GAS_RINGED(3), GAS(3), CRATER_RINGED(3),
        WARM(4), DESERT(4),
        COLD_CRESCENT(5);

        private final int type;

        Look(int type) {
            this.type = type;
        }

        /** The original moon type (0-5) this look belongs to. */
        public int type() {
            return type;
        }

        /** The looks of an original moon type; types 6 and 7 never occur and look like type 0. */
        public static List<Look> ofType(int type) {
            int own = type > 5 ? 0 : type;
            List<Look> looks = new ArrayList<>();
            for (Look look : values()) {
                if (look.type == own) {
                    looks.add(look);
                }
            }
            return looks;
        }
    }

    /**
     * One sphere of the design.
     *
     * @param scale   its radius as a share of the design's half width
     * @param offsetX its centre right of the design's centre, in the design's half widths
     * @param offsetY its centre above the design's centre, in the design's half widths
     */
    public record Body(MoonTexture.Surface surface, float scale, float offsetX, float offsetY) {
    }

    /**
     * A flat ring around the first body, perpendicular to the turning axis.
     *
     * @param inner  inner radius in the first body's radii
     * @param outer  outer radius in the first body's radii
     * @param colour 0xRRGGBB
     */
    public record Ring(float inner, float outer, int colour) {

        /**
         * The ring's bands from the inner to the outer edge, {@code width × 1} RGBA8: the colour with the preview's
         * alpha 0.25 + 0.45 · (0.55 + 0.45 · sin(60 r) · sin(23 r + 1)) at r body radii, faded out at both edges.
         */
        public byte[] texture(int width) {
            byte[] pixels = new byte[width * 4];
            for (int x = 0; x < width; x++) {
                double t = (x + 0.5) / width;
                double r = inner + (outer - inner) * t;
                double band = 0.55 + 0.45 * Math.sin(r * 60) * Math.sin(r * 23 + 1);
                double edge = Math.min(1, Math.min(t, 1 - t) / 0.04);
                pixels[x * 4] = (byte) ((colour >> 16) & 0xFF);
                pixels[x * 4 + 1] = (byte) ((colour >> 8) & 0xFF);
                pixels[x * 4 + 2] = (byte) (colour & 0xFF);
                pixels[x * 4 + 3] = (byte) Math.round(255 * (0.25 + 0.45 * band) * edge);
            }
            return pixels;
        }
    }

    /** The tower's moon, or empty for heading 4 ("none") and the other headings never shown (5-7). */
    public static Optional<MoonDesign> of(TowerDefinition tower) {
        return of(tower, SkyOptions.DEFAULT);
    }

    /**
     * The tower's moon with the developer overrides of {@code options}, or empty without a moon. The size class is,
     * in order: the developer override ({@code options.moonSize()}, a seam for the headless tests), else the
     * tower's stored size (Phase 6b-2 Task 0, {@code tower.moon().size()}) if present, else drawn from the seed.
     * Author decision A (2026-10-15, final review I2): a <em>stored</em> size (set in the editor, not the developer
     * override) honours the tower's Moon direction and Moon Height exactly as a Normal moon does, placing the large
     * or giant moon at the tower's own {@link MoonPlacement} instead of the seed's quarter; without a stored size
     * (every original tower) the seed-chosen quarter stays exactly as before.
     */
    public static Optional<MoonDesign> of(TowerDefinition tower, SkyOptions options) {
        Optional<SizeClass> storedSize = tower.moon().size().map(s -> SizeClass.values()[s]);
        boolean developerOverride = options.moonSize().isPresent();
        Optional<SizeClass> size = developerOverride ? options.moonSize() : storedSize;
        boolean honourPlacement = !developerOverride && storedSize.isPresent();
        return MoonPlacement.of(tower).map(placement -> design(tower.moon().type(), tower.id().hashCode(),
                placement.azimuth(), placement.elevation(), options.moonLook(), size, honourPlacement));
    }

    /**
     * The moon of original type {@code type} for the tower seed {@code seed}; a normal moon stands at
     * ({@code azimuth}, {@code elevation}) (Phase 4a's place), large and giant moons where the seed puts them.
     */
    public static MoonDesign design(int type, long seed, float azimuth, float elevation) {
        return design(type, seed, azimuth, elevation, Optional.empty(), Optional.empty());
    }

    /**
     * As {@link #design(int, long, float, float)}, with a forced look and size class where given (the seed's draws
     * stay the same); a pair is never giant, even when forced. Large and giant moons stand where the seed puts them
     * (as today); see {@link #design(int, long, float, float, Optional, Optional, boolean)} for a moon that honours
     * {@code azimuth}/{@code elevation} instead.
     */
    public static MoonDesign design(int type, long seed, float azimuth, float elevation, Optional<Look> forcedLook,
            Optional<SizeClass> forcedSize) {
        return design(type, seed, azimuth, elevation, forcedLook, forcedSize, false);
    }

    /**
     * As {@link #design(int, long, float, float, Optional, Optional)}; when {@code honourPlacement} is {@code true},
     * a large or giant moon stands at ({@code azimuth}, {@code elevation}) like a Normal moon instead of the seed's
     * own quarter (author decision A, final review I2: a <em>stored</em> size honours the tower's Moon direction and
     * Moon Height). The look's pair-is-never-giant demotion still runs first, so a demoted Large still honours the
     * placement when {@code honourPlacement} is {@code true}.
     */
    public static MoonDesign design(int type, long seed, float azimuth, float elevation, Optional<Look> forcedLook,
            Optional<SizeClass> forcedSize, boolean honourPlacement) {
        Random random = AmbientRandom.of(seed, RANDOM_PART);
        float draw = random.nextFloat();
        List<Look> looks = Look.ofType(type);
        Look look = forcedLook.orElse(looks.get(random.nextInt(looks.size())));
        SizeClass size = forcedSize.orElse(draw < SkyTuning.GIANT_SHARE ? SizeClass.GIANT
                : draw < SkyTuning.GIANT_SHARE + SkyTuning.LARGE_SHARE ? SizeClass.LARGE : SizeClass.NORMAL);
        if (size == SizeClass.GIANT && look == Look.PAIR) {
            size = SizeClass.LARGE;
        }
        int quarter = random.nextInt(4);
        float azimuthJitter = random.nextFloat() * 2 - 1;
        float elevationJitter = random.nextFloat() * 2 - 1;
        float axisRoll = (random.nextFloat() * 2 - 1) * 0.35f;
        float spinStart = random.nextFloat() * (float) (2 * Math.PI);
        long bodySeed = random.nextLong();
        if (size != SizeClass.NORMAL && !honourPlacement) {
            boolean giant = size == SizeClass.GIANT;
            azimuth = (float) Math.toRadians(quarter * 90.0 + azimuthJitter
                    * (giant ? SkyTuning.GIANT_AZIMUTH_JITTER_DEGREES : SkyTuning.LARGE_AZIMUTH_JITTER_DEGREES));
            elevation = (float) Math.toRadians((giant ? SkyTuning.GIANT_ELEVATION_DEGREES
                    : SkyTuning.LARGE_ELEVATION_DEGREES) + elevationJitter * SkyTuning.ELEVATION_JITTER_DEGREES);
        }
        return new MoonDesign(look, size, azimuth, elevation, bodies(look, bodySeed), ring(look),
                (float) Math.toRadians(phaseDegrees(look)), look == Look.WIDE_CRESCENT ? 0f : SkyTuning.EARTHSHINE,
                axisRoll, spinStart);
    }

    private static float phaseDegrees(Look look) {
        return switch (look) {
            case THIN_CRESCENT, COLD_CRESCENT -> SkyTuning.CRESCENT_PHASE_DEGREES;
            case WIDE_CRESCENT -> SkyTuning.WIDE_CRESCENT_PHASE_DEGREES;
            default -> SkyTuning.FULL_PHASE_DEGREES;
        };
    }

    private static List<Body> bodies(Look look, long seed) {
        MoonTexture.Kind cratered = MoonTexture.Kind.CRATERED;
        return switch (look) {
            case GREY, THIN_CRESCENT, WIDE_CRESCENT -> List.of(body(cratered, 0xFFFFFF, 70, seed, 1f, 0f, 0f));
            case MARIA -> List.of(body(MoonTexture.Kind.MARIA, 0xFFFFFF, 55, seed, 1f, 0f, 0f));
            case ICE -> List.of(body(MoonTexture.Kind.ICE, 0xF2FAFF, 12, seed, 1f, 0f, 0f));
            case COMPANION -> List.of(body(cratered, 0xFFFFFF, 70, seed, 0.94f, -0.14f, -0.1f),
                    body(cratered, 0xFFFFFF, 30, seed + 1, 0.34f, 0.96f, 0.81f));
            case PAIR -> List.of(body(cratered, 0xFFFFFF, 50, seed, 0.55f, -0.55f, -0.35f),
                    body(MoonTexture.Kind.MARIA, 0xFFFFFF, 40, seed + 1, 0.5f, 0.6f, 0.45f));
            case RED_COMPANION -> List.of(body(cratered, 0xFFFFFF, 70, seed, 0.94f, -0.14f, -0.1f),
                    body(MoonTexture.Kind.DESERT, 0xFFB88C, 20, seed + 1, 0.34f, 0.96f, 0.81f));
            case GAS_RINGED -> List.of(body(MoonTexture.Kind.GAS, 0xFFFFFF, 0, seed, 0.74f, 0f, 0f));
            case GAS -> List.of(body(MoonTexture.Kind.GAS, 0xFFFFFF, 0, seed, 1f, 0f, 0f));
            case CRATER_RINGED -> List.of(body(cratered, 0xFFFFFF, 60, seed, 0.77f, 0f, 0f));
            case WARM -> List.of(body(cratered, 0xFFC78F, 70, seed, 1f, 0f, 0f));
            case DESERT -> List.of(body(MoonTexture.Kind.DESERT, 0xFFB380, 40, seed, 1f, 0f, 0f));
            case COLD_CRESCENT -> List.of(body(cratered, 0xB8CCFF, 70, seed, 1f, 0f, 0f));
        };
    }

    private static Body body(MoonTexture.Kind kind, int tint, int craters, long seed, float scale, float x, float y) {
        return new Body(new MoonTexture.Surface(kind, tint, craters, seed), scale, x, y);
    }

    private static Optional<Ring> ring(Look look) {
        return switch (look) {
            case GAS_RINGED -> Optional.of(new Ring(1.35f, 1.95f, 0xDCCDAF));
            case CRATER_RINGED -> Optional.of(new Ring(1.55f, 1.75f, 0xC8D2E6));
            default -> Optional.empty();
        };
    }

    /** The unit direction from the camera to the design's centre. */
    public float[] direction() {
        float ring = (float) Math.cos(elevation);
        return new float[] {(float) Math.sin(azimuth) * ring, (float) Math.sin(elevation),
                -(float) Math.cos(azimuth) * ring};
    }

    /**
     * The unit direction from the moon towards its light (decision 5): the lit side faces {@code towardsSun} (the unit
     * direction to the sun) projected onto the sky at the moon, and the light stands {@link #phase} from the viewer;
     * for a sun right behind or in front of the moon, the light comes from above.
     */
    public float[] light(float[] towardsSun) {
        float[] d = direction();
        float along = towardsSun[0] * d[0] + towardsSun[1] * d[1] + towardsSun[2] * d[2];
        float[] side = {towardsSun[0] - along * d[0], towardsSun[1] - along * d[1], towardsSun[2] - along * d[2]};
        float length = (float) Math.sqrt(side[0] * side[0] + side[1] * side[1] + side[2] * side[2]);
        if (length < 1e-5f) {
            float up = d[1];
            side = new float[] {-up * d[0], 1 - up * d[1], -up * d[2]};
            length = (float) Math.sqrt(side[0] * side[0] + side[1] * side[1] + side[2] * side[2]);
        }
        float c = (float) Math.cos(phase);
        float s = (float) Math.sin(phase);
        return new float[] {-c * d[0] + s * side[0] / length, -c * d[1] + s * side[1] / length,
                -c * d[2] + s * side[2] / length};
    }

    /** The share of the visible disc that is lit, (1 + cos phase) / 2. */
    public float litShare() {
        return (1f + (float) Math.cos(phase)) / 2f;
    }

    /** The turning axis in the moon's own frame (x right, y up, z towards the viewer), a unit vector. */
    public float[] axis() {
        float s = (float) Math.sqrt(1 - AXIS_TOWARDS_VIEWER * AXIS_TOWARDS_VIEWER);
        return new float[] {-(float) Math.sin(axisRoll) * s, (float) Math.cos(axisRoll) * s, AXIS_TOWARDS_VIEWER};
    }

    /** The bodies' turn about the axis at simulation time {@code now} (frames), in [0, 2π). */
    public float spin(float now) {
        double turn = spinStart + 2 * Math.PI * now / SkyTuning.MOON_TURN_FRAMES;
        return (float) (turn - 2 * Math.PI * Math.floor(turn / (2 * Math.PI)));
    }

    /** The texture width of {@code body}: the class's width, halved for small companions (scale below 0.5). */
    public int textureWidth(Body body, boolean halfDetail) {
        int width = size.textureWidth(halfDetail);
        return body.scale() < 0.5f ? width / 2 : width;
    }
}
