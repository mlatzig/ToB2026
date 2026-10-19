package de.latzig.game.tob.client.logic.effects;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.stream.IntStream;

/**
 * The generated surface of a 3D moon (Phase 4b-3 spec §2 decision 10): an equirectangular albedo map and a
 * tangent-space normal map of the same size, computed from the moon's {@link Surface} on the unit sphere, so the maps
 * have no seam at the date line and no pinched poles: every texel takes the noise and the craters at its own direction
 * on the sphere, never at its texture coordinates. Row 0 is the south pole's row ({@code v} = 0, latitude −90°),
 * column 0 starts at longitude 0 ({@code u} = 0); the direction of texel ({@code x}, {@code y}) is {@link #direction}.
 * The rows are computed in parallel (the common fork-join pool); a texel is a pure function of the surface and its
 * direction, so the result does not depend on the order. Noise and craters follow the brainstorm previews
 * ({@code moon-types.html}): 3D value noise with smoothstep interpolation, fractal sums of halving amplitude, craters
 * as spherical caps.
 * <p>
 * The normal map is OpenGL style (material parameter {@code NormalType} 1): red along +u (east), green along +v
 * (north), blue out of the surface. The sphere mesh must carry the tangent (east, w = +1) to match.
 */
public final class MoonTexture {

    /** The look of a body (spec decision 6). */
    public enum Kind {
        /** A grey crater moon like the Earth's. */
        CRATERED,
        /** A crater moon with large dark plains ("maria"). */
        MARIA,
        /** A bright ice moon with fine dark cracks and few craters. */
        ICE,
        /** A banded gas planet: bands only, no craters, no storm spot (author 2026-10-11). */
        GAS,
        /** A desert moon with dark plains; the tint makes it reddish. */
        DESERT
    }

    /**
     * One body's surface.
     *
     * @param tint    0xRRGGBB multiplied onto the albedo
     * @param craters the number of craters (0 for none)
     */
    public record Surface(Kind kind, int tint, int craters, long seed) {
    }

    /** The two maps, RGBA8 each, {@code width × height × 4} bytes, rows as described in the class comment. */
    public record Maps(int width, int height, byte[] albedo, byte[] normal) {
    }

    /** The height scale of the normal map: slopes of the height field are multiplied by this. */
    static final double BUMP = 1.0;

    /** The crater outline noise's cells per crater radius: about a dozen bumps around a crater. */
    private static final double OUTLINE_CELLS = 1.9;

    /**
     * A crater (a spherical cap, made irregular by the acceptance fix): its centre ({@code x}, {@code y}, {@code z})
     * and mean angular {@code radius}; the oval's long axis {@code a} and its short axis {@code b} (unit tangents at
     * the centre) and {@code stretch} (1 = round); {@code depth} and {@code rim} (factors on the base depth and rim
     * height); {@code erosion} (0 = fresh, up to 0.9: flatter, with a lower, wider and softer rim); the seed of its
     * outline noise; the rim's width as a share of the radius; and {@code limit}, the largest angle from the centre
     * that the crater can reach (with its cosine).
     */
    record Crater(double x, double y, double z, double radius, double latitude, double ax, double ay, double az,
            double bx, double by, double bz, double stretch, double depth, double rim, double erosion, int noiseSeed,
            double rimWidth, double limit, double cosLimit) {
    }

    private MoonTexture() {
    }

    /** The maps of {@code surface} at {@code width × height} texels (width = 2 · height). */
    public static Maps generate(Surface surface, int width, int height) {
        return generate(surface, width, height, () -> false);
    }

    /**
     * The maps of {@code surface}; {@code cancelled} is asked once per row.
     *
     * @throws CancellationException if {@code cancelled} answered true
     */
    public static Maps generate(Surface surface, int width, int height, BooleanSupplier cancelled) {
        if (width != 2 * height || height < 2) {
            throw new IllegalArgumentException("equirectangular maps need width = 2 · height: " + width + "x" + height);
        }
        List<Crater> craters = craters(surface);
        float[] heights = new float[width * height];
        byte[] albedo = new byte[width * height * 4];
        IntStream.range(0, height).parallel().forEach(y -> {
            if (cancelled.getAsBoolean()) {
                return;
            }
            albedoRow(surface, craters, width, height, y, heights, albedo);
        });
        if (cancelled.getAsBoolean()) {
            throw new CancellationException("moon texture cancelled");
        }
        byte[] normal = new byte[width * height * 4];
        IntStream.range(0, height).parallel().forEach(y -> {
            if (cancelled.getAsBoolean()) {
                return;
            }
            normalRow(width, height, y, heights, normal);
        });
        if (cancelled.getAsBoolean()) {
            throw new CancellationException("moon texture cancelled");
        }
        return new Maps(width, height, albedo, normal);
    }

    /** The unit direction of texel ({@code x}, {@code y}): x = cos φ sin λ, y = sin φ (north), z = cos φ cos λ. */
    public static double[] direction(int x, int y, int width, int height) {
        double longitude = 2 * Math.PI * (x + 0.5) / width;
        double latitude = Math.PI * (y + 0.5) / height - Math.PI / 2;
        double ring = Math.cos(latitude);
        return new double[] {ring * Math.sin(longitude), Math.sin(latitude), ring * Math.cos(longitude)};
    }

    /**
     * A tiling grey detail texture ({@code size × size}, RGBA8) against blur on large moons: 1 − {@code strength} ·
     * noise, so it only darkens a little (it multiplies the albedo as jME's light map).
     */
    public static byte[] detail(int size, long seed, float strength) {
        byte[] pixels = new byte[size * size * 4];
        int base = (int) seed;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double angleX = 2 * Math.PI * x / size;
                double angleY = 2 * Math.PI * y / size;
                // a torus in 4D folded into 3D noise: the tile wraps in both directions
                double n = fbm(8 * Math.cos(angleX), 8 * Math.sin(angleX) + 8 * Math.cos(angleY),
                        8 * Math.sin(angleY), base, 4);
                int v = clamp(255 * (1 - strength * n));
                int i = (y * size + x) * 4;
                pixels[i] = (byte) v;
                pixels[i + 1] = (byte) v;
                pixels[i + 2] = (byte) v;
                pixels[i + 3] = (byte) 255;
            }
        }
        return pixels;
    }

    /**
     * The craters of {@code surface}. The places and sizes come from one random sequence (unchanged by the
     * acceptance fix), the shapes from a second one; every crater draws the same number of values from each, so the
     * places stay the same whatever the tuning values.
     */
    static List<Crater> craters(Surface surface) {
        Random random = new Random(surface.seed() * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L);
        Random shape = new Random(surface.seed() * 0x2545F4914F6CDD1DL + 0x1D8E4E27C47D124FL);
        List<Crater> craters = new ArrayList<>();
        for (int i = 0; i < surface.craters(); i++) {
            double u = random.nextDouble() * 2 - 1;
            double a = random.nextDouble() * 2 * Math.PI;
            double s = Math.sqrt(1 - u * u);
            double r = random.nextDouble();
            double x = s * Math.cos(a);
            double z = s * Math.sin(a);
            double radius = 0.03 + r * r * r * 0.22;
            boolean oval = shape.nextDouble() < SkyTuning.CRATER_OVAL_SHARE;
            double ovalDraw = shape.nextDouble();
            double stretch = oval ? 1 + (SkyTuning.CRATER_MAX_STRETCH - 1) * (0.4 + 0.6 * ovalDraw) : 1;
            double turn = shape.nextDouble() * 2 * Math.PI;
            boolean eroded = shape.nextDouble() < SkyTuning.CRATER_ERODED_SHARE;
            double erosionDraw = shape.nextDouble();
            double erosion = eroded ? 0.4 + 0.5 * erosionDraw : 0;
            double depth = 1 + SkyTuning.CRATER_DEPTH_VARIATION * (2 * shape.nextDouble() - 1);
            double rim = 1 + SkyTuning.CRATER_RIM_VARIATION * (2 * shape.nextDouble() - 1);
            int noiseSeed = shape.nextInt();
            // the tangent plane at the centre (t1, t2), turned by the seed for the oval's long axis a; b = c x a
            double[] up = Math.abs(u) < 0.9 ? new double[] {0, 1, 0} : new double[] {1, 0, 0};
            double t1x = up[1] * z - up[2] * u;
            double t1y = up[2] * x - up[0] * z;
            double t1z = up[0] * u - up[1] * x;
            double length = Math.sqrt(t1x * t1x + t1y * t1y + t1z * t1z);
            t1x /= length;
            t1y /= length;
            t1z /= length;
            double t2x = u * t1z - z * t1y;
            double t2y = z * t1x - x * t1z;
            double t2z = x * t1y - u * t1x;
            double cos = Math.cos(turn);
            double sin = Math.sin(turn);
            double ax = cos * t1x + sin * t2x;
            double ay = cos * t1y + sin * t2y;
            double az = cos * t1z + sin * t2z;
            double bx = u * az - z * ay;
            double by = z * ax - x * az;
            double bz = x * ay - u * ax;
            double rimWidth = 0.25 * (1 + erosion);
            double limit = Math.min(Math.PI, radius * stretch * (1 + 2 * SkyTuning.CRATER_IRREGULARITY)
                    * (1 + rimWidth));
            craters.add(new Crater(x, u, z, radius, Math.asin(u), ax, ay, az, bx, by, bz, stretch, depth, rim,
                    erosion, noiseSeed, rimWidth, limit, Math.cos(limit)));
        }
        return craters;
    }

    /**
     * How far the unit direction ({@code nx}, {@code ny}, {@code nz}) lies from {@code crater}'s centre, as a share
     * of the crater's radius in that direction: below 1 inside the bowl, 1 to 1 + rim width on the rim, infinite
     * beyond the crater's reach. The radius in a direction is the oval's (long axis {@code a}, the circle's area)
     * times the outline noise, 1 ± about {@link SkyTuning#CRATER_IRREGULARITY} (at most twice that).
     */
    static double craterQ(Crater crater, double nx, double ny, double nz) {
        double dot = nx * crater.x() + ny * crater.y() + nz * crater.z();
        if (dot < crater.cosLimit()) {
            return Double.POSITIVE_INFINITY;
        }
        double angle = Math.acos(Math.min(1, dot));
        double radius = crater.radius();
        double stretch = crater.stretch();
        if (stretch != 1) {
            double a = nx * crater.ax() + ny * crater.ay() + nz * crater.az();
            double b = nx * crater.bx() + ny * crater.by() + nz * crater.bz();
            double square = a * a + b * b;
            if (square > 1e-18) {
                radius /= Math.sqrt((a * a / (stretch * stretch) + b * b * stretch * stretch) / square);
            }
        }
        if (SkyTuning.CRATER_IRREGULARITY > 0) {
            double f = OUTLINE_CELLS / crater.radius();
            double n = fbm(nx * f, ny * f, nz * f, crater.noiseSeed(), 2);
            radius *= 1 + SkyTuning.CRATER_IRREGULARITY * Math.max(-2, Math.min(2, 4 * (n - 0.5)));
        }
        return angle / radius;
    }

    private static void albedoRow(Surface surface, List<Crater> all, int width, int height, int y, float[] heights,
            byte[] albedo) {
        double latitude = Math.PI * (y + 0.5) / height - Math.PI / 2;
        List<Crater> near = new ArrayList<>();
        for (Crater crater : all) {
            if (Math.abs(crater.latitude() - latitude) < crater.limit()) {
                near.add(crater);
            }
        }
        int seed = (int) surface.seed();
        int tint = surface.tint();
        double tr = ((tint >> 16) & 0xFF) / 255.0;
        double tg = ((tint >> 8) & 0xFF) / 255.0;
        double tb = (tint & 0xFF) / 255.0;
        double ring = Math.cos(latitude);
        double ny = Math.sin(latitude);
        for (int x = 0; x < width; x++) {
            double longitude = 2 * Math.PI * (x + 0.5) / width;
            double nx = ring * Math.sin(longitude);
            double nz = ring * Math.cos(longitude);
            double r;
            double g;
            double b;
            double h;
            if (surface.kind() == Kind.GAS) {
                // bands along the latitude, waved on a large scale and ragged at their edges by noise squeezed in
                // latitude, plus streaks drawn out along the circumference: both vary with the longitude
                double wave = fbm(nx * 2.5, ny * 2.5, nz * 2.5, seed, 3) - 0.5;
                double turbulence = fbm(nx * 5, ny * 16, nz * 5, seed + 7, 3) - 0.5;
                double q = ny + 2 * SkyTuning.GAS_BAND_WAVE * wave + 2 * SkyTuning.GAS_TURBULENCE * turbulence;
                double band = 0.62 * (0.5 + 0.5 * Math.sin(q * 18)) + 0.38 * (0.5 + 0.5 * Math.sin(q * 7.3 + 1.7));
                double t = Math.max(0, Math.min(1, 0.5 + 1.6 * SkyTuning.GAS_BAND_CONTRAST * (band - 0.5)));
                double streak = 1 + 2 * SkyTuning.GAS_STREAKS * (fbm(nx * 3, ny * 28, nz * 3, seed + 13, 2) - 0.5);
                r = (0.58 + 0.40 * t) * streak;
                g = (0.40 + 0.52 * t) * streak;
                b = (0.27 + 0.53 * t) * streak;
                h = 0;
            } else {
                double base;
                double relief;
                if (surface.kind() == Kind.ICE) {
                    relief = fbm(nx * 4, ny * 4, nz * 4, seed, 4);
                    base = 0.85 + 0.1 * (relief - 0.5);
                    double crack = Math.abs(fbm(nx * 5, ny * 5, nz * 5, seed + 9, 3) - 0.5);
                    base *= 1 - 0.25 * (1 - smoothstep(0.006, 0.018, crack));
                } else {
                    relief = fbm(nx * 3, ny * 3, nz * 3, seed, 5);
                    base = 0.55 + 0.35 * (relief - 0.5);
                    if (surface.kind() == Kind.MARIA) {
                        double m = fbm(nx * 1.6, ny * 1.6, nz * 1.6, seed + 5, 3);
                        base *= 1 - 0.38 * smoothstep(0.53, 0.57, m);
                    } else if (surface.kind() == Kind.DESERT) {
                        double m = fbm(nx * 2, ny * 2, nz * 2, seed + 3, 3);
                        base *= 1 - 0.3 * smoothstep(0.54, 0.58, m);
                    }
                }
                h = 0.01 * (relief - 0.5);
                double rim = 0;
                for (Crater crater : near) {
                    double q = craterQ(crater, nx, ny, nz);
                    if (q >= 1 + crater.rimWidth()) {
                        continue;
                    }
                    double erosion = crater.erosion();
                    if (q < 1) {
                        // an eroded bowl is shallower, with a softer edge
                        double bowl = 1 - q * q;
                        double depth = crater.depth() * (1 - 0.6 * erosion);
                        base *= 1 - depth * (0.18 - 0.12 * q);
                        h -= 0.2 * crater.radius() * depth * ((1 - erosion) * bowl + erosion * bowl * bowl);
                    } else {
                        // the rim: lower, wider and softer when eroded
                        double t = (q - 1) / crater.rimWidth();
                        double raised = crater.rim() * (1 - 0.7 * erosion);
                        rim += (1 - t) * 0.15 * raised;
                        h += 0.05 * crater.radius() * raised * Math.sin(Math.PI * t);
                    }
                }
                base = Math.min(1, base + rim * 0.25);
                r = base;
                g = base;
                b = base;
            }
            int i = (y * width + x) * 4;
            heights[y * width + x] = (float) h;
            albedo[i] = (byte) clamp(255 * r * tr);
            albedo[i + 1] = (byte) clamp(255 * g * tg);
            albedo[i + 2] = (byte) clamp(255 * b * tb);
            albedo[i + 3] = (byte) 255;
        }
    }

    private static void normalRow(int width, int height, int y, float[] heights, byte[] normal) {
        double latitude = Math.PI * (y + 0.5) / height - Math.PI / 2;
        double east = Math.max(Math.cos(latitude), 1e-6) * 2 * Math.PI / width;
        double north = Math.PI / height;
        int below = Math.max(y - 1, 0);
        int above = Math.min(y + 1, height - 1);
        for (int x = 0; x < width; x++) {
            int left = (x + width - 1) % width;
            int right = (x + 1) % width;
            double dx = (heights[y * width + right] - heights[y * width + left]) / (2 * east);
            double dy = (heights[above * width + x] - heights[below * width + x]) / ((above - below) * north);
            double nx = -BUMP * dx;
            double ny = -BUMP * dy;
            double length = Math.sqrt(nx * nx + ny * ny + 1);
            int i = (y * width + x) * 4;
            normal[i] = (byte) clamp(255 * (0.5 + 0.5 * nx / length));
            normal[i + 1] = (byte) clamp(255 * (0.5 + 0.5 * ny / length));
            normal[i + 2] = (byte) clamp(255 * (0.5 + 0.5 / length));
            normal[i + 3] = (byte) 255;
        }
    }

    private static int clamp(double value) {
        return (int) Math.max(0, Math.min(255, Math.round(value)));
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double t = Math.max(0, Math.min(1, (value - edge0) / (edge1 - edge0)));
        return t * t * (3 - 2 * t);
    }

    /** Fractal value noise: {@code octaves} octaves of halving amplitude and doubling frequency, in [0, 1). */
    static double fbm(double x, double y, double z, int seed, int octaves) {
        double amplitude = 0.5;
        double frequency = 1;
        double total = 0;
        double norm = 0;
        for (int i = 0; i < octaves; i++) {
            total += amplitude * noise(x * frequency, y * frequency, z * frequency, seed + i * 17);
            norm += amplitude;
            amplitude *= 0.5;
            frequency *= 2;
        }
        return total / norm;
    }

    /** 3D value noise in [0, 1): lattice values from {@link #hash}, smoothstep-interpolated. */
    static double noise(double x, double y, double z, int seed) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        int zi = (int) Math.floor(z);
        double xf = x - xi;
        double yf = y - yi;
        double zf = z - zi;
        double u = xf * xf * (3 - 2 * xf);
        double v = yf * yf * (3 - 2 * yf);
        double w = zf * zf * (3 - 2 * zf);
        double x00 = lerp(hash(xi, yi, zi, seed), hash(xi + 1, yi, zi, seed), u);
        double x10 = lerp(hash(xi, yi + 1, zi, seed), hash(xi + 1, yi + 1, zi, seed), u);
        double x01 = lerp(hash(xi, yi, zi + 1, seed), hash(xi + 1, yi, zi + 1, seed), u);
        double x11 = lerp(hash(xi, yi + 1, zi + 1, seed), hash(xi + 1, yi + 1, zi + 1, seed), u);
        return lerp(lerp(x00, x10, v), lerp(x01, x11, v), w);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** A lattice point's value in [0, 1). */
    static double hash(int x, int y, int z, int seed) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177 + seed * 144665;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h >>> 8) / 16777216.0;
    }
}
