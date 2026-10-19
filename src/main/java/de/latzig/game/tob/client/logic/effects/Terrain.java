package de.latzig.game.tob.client.logic.effects;

import java.util.Random;

import de.latzig.game.tob.client.logic.CameraPlacement;
import de.latzig.game.tob.client.logic.Socket;
import de.latzig.game.tob.client.logic.TowerColours;
import de.latzig.game.tob.core.model.RemakeVisuals;
import de.latzig.game.tob.core.model.TowerDefinition;

/**
 * The hilly ground to the horizon (Phase 4a spec §2 decision 9), generated as ToB 2005 does it
 * ({@code sky.cpp} {@code _SKY_createPlanes}, {@code sky.h}): a square of {@value #DIVISIONS} × {@value #DIVISIONS}
 * quads over the sky plane's extent (half-size √2 · A, A = 8192 / 1.8 units of 32 per cell, about 201 cells), every
 * vertex raised by {@code (x² + z²) / A · rand(0..99) · 0.001 · groundHeight} (here times {@link #RELIEF}); the edge
 * vertices to a quarter (and at least to the sky plane), those beyond 0.95 A to a half; the vertices within 1800
 * units of the centre (the central 3 × 3) flat at the socket bottom ({@link #FLAT_Y}), so nothing cuts into the
 * socket; and no vertex within 45° of the sun direction above the line to the sun. ToB 2005 uses
 * {@code rand()}; here a {@link Random} seeded with the tower id draws the same sequence of numbers 0–99, so a tower
 * always gets the same hills. The ground height is the ToB 2009 look setting (0–10), {@value #DEFAULT_GROUND_HEIGHT}
 * for towers without one.
 * <p>
 * Colours and textures (Phase 4b-4 spec decision 1, ToB 2005's {@code Ground} shader, {@code Ground-14.vsh/.psh},
 * {@code sky.cpp:680-708}): every vertex has a ground weight {@code 1 − y / h} and a mountain weight {@code y / h}
 * (y in ToB 2005 units, h = {@link MaterialTuning#MOUNTAIN_HEIGHT_UNITS}, both clamped to 0..1;
 * {@link #groundWeight}, {@link #mountainWeight}),
 * which the terrain material reads from the second texture coordinates to blend the ground texture (with its detail
 * texture) into the mountain texture. The vertex colour is ToB 2005's ground colour 1 plus 0–15 per channel at
 * random, mixed into ground colour 2 by the ground weight (the pixel shader's {@code lrp}); it is lit and fogged by
 * the scene. The ground texture repeats {@value #TEXTURE_TILES} times across the plane ({@code SKY_TEXTILES_GROUND}).
 */
public final class Terrain {

    public static final int DIVISIONS = 8;
    public static final int DEFAULT_GROUND_HEIGHT = 4;
    /**
     * The flat zone's height: the socket's bottom (Phase 4b-1 spec §2 decision 6: ToB 2009's one-unit socket; Phase
     * 4a had ToB 2005's playground bottom, −8 units).
     */
    public static final float FLAT_Y = Socket.BOTTOM;
    public static final float TEXTURE_TILES = 15f;
    /**
     * Phase 4a deviation from ToB 2005: the relief is scaled down. ToB 2005's formula with the ToB 2009 ground heights
     * of the original towers (1–8) raises mountains up to about 20° above the horizon, which hide the low moons; the
     * ToB 2009 reference screenshots show low hills of a few degrees. A taste item for the author's acceptance.
     */
    public static final double RELIEF = 0.35;

    static final double UNITS_PER_CELL = 32;
    static final double ATMOSPHERE = SkyDome.ATMOSPHERE;
    static final double HALF = SkyDome.PLANE_HALF;
    /** {@code SKY_PLAYGROUNDMAXSIZE}: the flat square's half-size in units. */
    static final double FLAT_HALF = 1800;
    /** The ToB 2005 sun direction (−0.5, −0.6, 0.1) in the scene's axes (z mirrored), normalised. */
    private static final double[] SUN = normalise(-0.5, -0.6, -0.1);

    private static final int N = DIVISIONS + 1;

    private final double[] units = new double[N * N * 3];
    private final float centreX;
    private final float centreZ;

    private Terrain(float centreX, float centreZ) {
        this.centreX = centreX;
        this.centreZ = centreZ;
    }

    /** The terrain of {@code tower}, centred on the grid centre {@link CameraPlacement#GRID_CENTRE}. */
    public static Terrain of(TowerDefinition tower) {
        int height = tower.remake().map(RemakeVisuals::groundHeight).orElse(DEFAULT_GROUND_HEIGHT);
        return generate(height, tower.id().hashCode(), CameraPlacement.GRID_CENTRE, CameraPlacement.GRID_CENTRE);
    }

    static Terrain generate(int groundHeight, long seed, float centreX, float centreZ) {
        Terrain terrain = new Terrain(centreX, centreZ);
        Random random = new Random(seed);
        double delta = 2 * HALF / DIVISIONS;
        for (int j = 0; j < N; j++) {
            for (int i = 0; i < N; i++) {
                double x = -HALF + i * delta;
                double z = -HALF + j * delta;
                double h = (x * x + z * z) / ATMOSPHERE;
                double sky = (4 * ATMOSPHERE - h) * 0.2 - 1800;
                double y = h * random.nextInt(100) * 0.001 * groundHeight * RELIEF;
                boolean edge = i == 0 || i == DIVISIONS || j == 0 || j == DIVISIONS;
                if (edge) {
                    y = Math.max(y / 4, sky);
                }
                if (Math.sqrt(x * x + z * z) > ATMOSPHERE * 0.95) {
                    y *= 0.5;
                }
                if (Math.abs(x) < FLAT_HALF && Math.abs(z) < FLAT_HALF) {
                    y = FLAT_Y * UNITS_PER_CELL;
                }
                double length = Math.sqrt(x * x + y * y + z * z);
                double towards = -(x * SUN[0] + y * SUN[1] + z * SUN[2]) / length;
                double angle = Math.acos(Math.max(-1, Math.min(1, towards)));
                if (angle < Math.PI / 4) {
                    double sunHeight = -SUN[1] * length;
                    if (y > sunHeight) {
                        y = sunHeight * 0.9;
                    }
                }
                int v = (j * N + i) * 3;
                terrain.units[v] = x;
                terrain.units[v + 1] = y;
                terrain.units[v + 2] = z;
            }
        }
        return terrain;
    }

    /** Vertex (i, j) in scene coordinates: i along x (east), j along z (south). */
    public float[] position(int i, int j) {
        int v = (j * N + i) * 3;
        return new float[] {centreX + (float) (units[v] / UNITS_PER_CELL), (float) (units[v + 1] / UNITS_PER_CELL),
                centreZ + (float) (units[v + 2] / UNITS_PER_CELL)};
    }

    /**
     * The ground's height (fields) at scene point ({@code x}, {@code z}), on the mesh's own triangles ({@link #mesh}:
     * every quad split along its (i, j)–(i + 1, j + 1) diagonal); beyond the plane, its edge.
     */
    public float heightAt(float x, float z) {
        double delta = 2 * HALF / DIVISIONS;
        double s = ((x - centreX) * UNITS_PER_CELL + HALF) / delta;
        double t = ((z - centreZ) * UNITS_PER_CELL + HALF) / delta;
        int i = (int) Math.clamp(Math.floor(s), 0, DIVISIONS - 1);
        int j = (int) Math.clamp(Math.floor(t), 0, DIVISIONS - 1);
        double fs = Math.clamp(s - i, 0.0, 1.0);
        double ft = Math.clamp(t - j, 0.0, 1.0);
        double h00 = height(i, j);
        double h11 = height(i + 1, j + 1);
        double h;
        if (ft >= fs) {
            double h01 = height(i, j + 1);
            h = h00 + fs * (h11 - h01) + ft * (h01 - h00);
        } else {
            double h10 = height(i + 1, j);
            h = h00 + fs * (h10 - h00) + ft * (h11 - h10);
        }
        return (float) (h / UNITS_PER_CELL);
    }

    /**
     * ToB 2005's ground texture weight at height {@code units} (ToB 2005 units): {@code Ground-14.vsh} constant c13,
     * {@code 1 − y / h} with h = {@link MaterialTuning#MOUNTAIN_HEIGHT_UNITS}, clamped to 0..1 as Direct3D clamps the
     * colour output.
     */
    public static float groundWeight(double units) {
        return (float) Math.clamp(1.0 - units / MaterialTuning.MOUNTAIN_HEIGHT_UNITS, 0.0, 1.0);
    }

    /** ToB 2005's mountain texture weight at height {@code units}: {@code y / h}, clamped to 0..1. */
    public static float mountainWeight(double units) {
        return (float) Math.clamp(units / MaterialTuning.MOUNTAIN_HEIGHT_UNITS, 0.0, 1.0);
    }

    /**
     * The indexed mesh: smooth vertex normals, the ground texture's UVs, the vertex colours of {@code colours} and
     * the ground and mountain weights.
     */
    public TerrainMesh mesh(TowerColours colours, long seed) {
        Random random = new Random(seed);
        float[] positions = new float[N * N * 3];
        float[] normals = new float[N * N * 3];
        float[] uvs = new float[N * N * 2];
        float[] colors = new float[N * N * 4];
        float[] weights = new float[N * N * 2];
        for (int j = 0; j < N; j++) {
            for (int i = 0; i < N; i++) {
                int v = j * N + i;
                System.arraycopy(position(i, j), 0, positions, v * 3, 3);
                double dx = height(Math.min(i + 1, DIVISIONS), j) - height(Math.max(i - 1, 0), j);
                double dz = height(i, Math.min(j + 1, DIVISIONS)) - height(i, Math.max(j - 1, 0));
                double spanX = (Math.min(i + 1, DIVISIONS) - Math.max(i - 1, 0)) * 2 * HALF / DIVISIONS;
                double spanZ = (Math.min(j + 1, DIVISIONS) - Math.max(j - 1, 0)) * 2 * HALF / DIVISIONS;
                double[] n = normalise(-dx / spanX, 1, -dz / spanZ);
                normals[v * 3] = (float) n[0];
                normals[v * 3 + 1] = (float) n[1];
                normals[v * 3 + 2] = (float) n[2];
                uvs[v * 2] = TEXTURE_TILES * i / DIVISIONS;
                uvs[v * 2 + 1] = TEXTURE_TILES * (1f - (float) j / DIVISIONS);
                float ground = groundWeight(height(i, j));
                weights[v * 2] = ground;
                weights[v * 2 + 1] = mountainWeight(height(i, j));
                int colour = colours.ground();
                int mountains = colours.ground2();
                colors[v * 4] = mix(channel((colour >> 16 & 0xFF) + random.nextInt(16)), mountains >> 16, ground);
                colors[v * 4 + 1] = mix(channel((colour >> 8 & 0xFF) + random.nextInt(16)), mountains >> 8, ground);
                colors[v * 4 + 2] = mix(channel((colour & 0xFF) + random.nextInt(16)), mountains, ground);
                colors[v * 4 + 3] = 1f;
            }
        }
        int[] indices = new int[DIVISIONS * DIVISIONS * 6];
        int k = 0;
        for (int j = 0; j < DIVISIONS; j++) {
            for (int i = 0; i < DIVISIONS; i++) {
                int a = j * N + i;
                int[] quad = {a, a + N, a + N + 1, a, a + N + 1, a + 1};
                System.arraycopy(quad, 0, indices, k, 6);
                k += 6;
            }
        }
        return new TerrainMesh(positions, normals, uvs, colors, indices, weights);
    }

    /**
     * ToB 2005's {@code lrp}: {@code weight · vertex + (1 − weight) · mountain}, the mountain channel in the low byte
     * of {@code mountain}.
     */
    private static float mix(float vertex, int mountain, float weight) {
        return weight * vertex + (1f - weight) * ((mountain & 0xFF) / 255f);
    }

    /** The height of vertex (i, j) in ToB 2005 units. */
    double height(int i, int j) {
        return units[(j * N + i) * 3 + 1];
    }

    private static float channel(int value) {
        return Math.min(255, value) / 255f;
    }

    private static double[] normalise(double x, double y, double z) {
        double length = Math.sqrt(x * x + y * y + z * z);
        return new double[] {x / length, y / length, z / length};
    }

    /**
     * An indexed triangle mesh (counter-clockwise front faces seen from above); {@code weights} holds the ground and
     * the mountain weight of every vertex.
     */
    public record TerrainMesh(float[] positions, float[] normals, float[] uvs, float[] colors, int[] indices,
            float[] weights) {
    }
}
