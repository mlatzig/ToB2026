package de.latzig.game.tob.client.logic.effects;

import java.util.Random;

import de.latzig.game.tob.core.sim.Rules;

/**
 * The flying triangles of an exploding mesh (Phase 4b-1 spec §2 decisions 1–4), ToB 2005's
 * {@code DXE_initMeshPolysForExplosion} and {@code DXE_explodeMesh} ({@code dxe.cpp:2435-2570}) as closed-form
 * functions of the time since the start, so the debris stands still while the simulation is paused and does not
 * depend on the frame rate. Time: ToB 2005 played 512 of its frames (128 per second) per explosion; here the
 * explosion lasts the destruction's {@value #DURATION} simulation frames, so one simulation frame is
 * {@value #TOB2005_FRAMES_PER_FRAME} ToB 2005 frames (f below). Units: ToB 2005 had 32 per field.
 * <ul>
 * <li>Start: every triangle is shrunk to 0.5 around its centroid and turned by a random −1..1 rad about x and then y;
 * its flight direction is its face normal turned by another random −1..1 rad about x and y, flipped up if it points
 * down.</li>
 * <li>Flight: the centroid moves by the direction times (0.04, 0.08, 0.04) units per ToB 2005 frame while gravity
 * takes 0.005 per frame off the direction's y: {@code (0.04 n.x f, 0.08 (n.y f − 0.0025 f²), 0.04 n.z f)}.</li>
 * <li>Spin about the centroid: 0.13 f rad about x, then 0.1 f about y.</li>
 * <li>Shrink: ToB 2005 multiplied by 0.98 per rendered frame, which depends on the frame rate (spec §2 decision 3,
 * a bug not copied); here 0.98 per 1/60 s of ToB 2005 time: {@code 0.98^(f · 60 / 128)}.</li>
 * <li>A triangle whose centroid is below {@code minHeight} (0 for objects, −1 field for floors) is gone for good:
 * its corners collapse to the centroid. Its height is a downward parabola, so "ever below" is "below at the start or
 * now".</li>
 * </ul>
 */
public final class ExplosionDebris {

    /** The explosion's length in simulation frames: the destruction time ({@code Rules.DESTROY_FRAMES}). */
    public static final float DURATION = Rules.DESTROY_FRAMES;
    /** ToB 2005's {@code TWR_EXPLOSION_MAXFRAMES} 512 over {@link #DURATION}. */
    public static final float TOB2005_FRAMES_PER_FRAME = 512f / DURATION;
    /** ToB 2005's minimum height of an object's debris (relative to the object's origin). */
    public static final float OBJECT_MIN_HEIGHT = 0f;
    /** ToB 2005's minimum height of a floor's debris: −32 units, one level down. */
    public static final float FLOOR_MIN_HEIGHT = -1f;

    private static final float UNITS = 32f;
    private static final double SHRINK_PER_TOB2005_FRAME = Math.pow(0.98, 60.0 / 128.0);

    private final int triangles;
    private final float minHeight;
    /** Per triangle: the start centroid (3), the flight direction (3) and the three corner offsets (9). */
    private final float[] data;

    private ExplosionDebris(int triangles, float minHeight) {
        this.triangles = triangles;
        this.minHeight = minHeight;
        this.data = new float[triangles * 15];
    }

    /**
     * @param positions the mesh's triangles at the key shown (9 floats per triangle, fields, the object's own frame)
     * @param minHeight {@link #OBJECT_MIN_HEIGHT} or {@link #FLOOR_MIN_HEIGHT}
     * @param random    four numbers per triangle, as ToB 2005's {@code rand()}
     */
    public static ExplosionDebris of(float[] positions, float minHeight, Random random) {
        int triangles = positions.length / 9;
        ExplosionDebris debris = new ExplosionDebris(triangles, minHeight);
        for (int t = 0; t < triangles; t++) {
            int p = t * 9;
            int d = t * 15;
            float cx = (positions[p] + positions[p + 3] + positions[p + 6]) / 3f;
            float cy = (positions[p + 1] + positions[p + 4] + positions[p + 7]) / 3f;
            float cz = (positions[p + 2] + positions[p + 5] + positions[p + 8]) / 3f;
            debris.data[d] = cx;
            debris.data[d + 1] = cy;
            debris.data[d + 2] = cz;
            float[] normal = faceNormal(positions, p);
            float normalX = angle(random);
            float normalY = angle(random);
            float polyX = angle(random);
            float polyY = angle(random);
            rotateY(rotateX(normal, normalX), normalY);
            normal[1] = Math.abs(normal[1]);
            System.arraycopy(normal, 0, debris.data, d + 3, 3);
            for (int k = 0; k < 3; k++) {
                float[] offset = {(positions[p + k * 3] - cx) * 0.5f, (positions[p + k * 3 + 1] - cy) * 0.5f,
                        (positions[p + k * 3 + 2] - cz) * 0.5f};
                rotateY(rotateX(offset, polyX), polyY);
                System.arraycopy(offset, 0, debris.data, d + 6 + k * 3, 3);
            }
        }
        return debris;
    }

    public int triangles() {
        return triangles;
    }

    /** The shrink factor after {@code frames} simulation frames (on top of the start's 0.5). */
    public static float scale(float frames) {
        return (float) Math.pow(SHRINK_PER_TOB2005_FRAME, Math.max(0f, frames) * TOB2005_FRAMES_PER_FRAME);
    }

    /** Whether triangle {@code triangle} is still flying {@code frames} simulation frames after the start. */
    public boolean visible(int triangle, float frames) {
        int d = triangle * 15;
        return data[d + 1] >= minHeight && centroidY(d, tob2005(frames)) >= minHeight;
    }

    /** Every corner {@code frames} simulation frames after the start, 9 floats per triangle, into {@code out}. */
    public void positionsAt(float frames, float[] out) {
        float f = tob2005(frames);
        float shrink = scale(frames);
        float spinX = 0.13f * f;
        float spinY = 0.1f * f;
        for (int t = 0; t < triangles; t++) {
            int d = t * 15;
            float cx = data[d] + 0.04f * data[d + 3] * f / UNITS;
            float cy = centroidY(d, f);
            float cz = data[d + 2] + 0.04f * data[d + 5] * f / UNITS;
            boolean shown = data[d + 1] >= minHeight && cy >= minHeight;
            for (int k = 0; k < 3; k++) {
                int o = t * 9 + k * 3;
                if (!shown) {
                    out[o] = cx;
                    out[o + 1] = cy;
                    out[o + 2] = cz;
                    continue;
                }
                float[] offset = {data[d + 6 + k * 3], data[d + 7 + k * 3], data[d + 8 + k * 3]};
                rotateY(rotateX(offset, spinX), spinY);
                out[o] = cx + offset[0] * shrink;
                out[o + 1] = cy + offset[1] * shrink;
                out[o + 2] = cz + offset[2] * shrink;
            }
        }
    }

    private float centroidY(int d, float f) {
        return data[d + 1] + 0.08f * (data[d + 4] * f - 0.0025f * f * f) / UNITS;
    }

    private static float tob2005(float frames) {
        return Math.max(0f, frames) * TOB2005_FRAMES_PER_FRAME;
    }

    /** ToB 2005's {@code (rand() % 100) / 50 − 1}: −1..0.98 rad. */
    private static float angle(Random random) {
        return random.nextInt(100) / 50f - 1f;
    }

    private static float[] faceNormal(float[] p, int i) {
        float e1x = p[i + 3] - p[i];
        float e1y = p[i + 4] - p[i + 1];
        float e1z = p[i + 5] - p[i + 2];
        float e2x = p[i + 6] - p[i];
        float e2y = p[i + 7] - p[i + 1];
        float e2z = p[i + 8] - p[i + 2];
        float nx = e1y * e2z - e1z * e2y;
        float ny = e1z * e2x - e1x * e2z;
        float nz = e1x * e2y - e1y * e2x;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        return length < 1e-12f ? new float[] {0f, 1f, 0f} : new float[] {nx / length, ny / length, nz / length};
    }

    private static float[] rotateX(float[] v, float angle) {
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        float y = v[1] * cos - v[2] * sin;
        float z = v[1] * sin + v[2] * cos;
        v[1] = y;
        v[2] = z;
        return v;
    }

    private static float[] rotateY(float[] v, float angle) {
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        float x = v[0] * cos + v[2] * sin;
        float z = -v[0] * sin + v[2] * cos;
        v[0] = x;
        v[2] = z;
        return v;
    }
}
