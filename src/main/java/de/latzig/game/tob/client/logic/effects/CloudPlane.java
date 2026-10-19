package de.latzig.game.tob.client.logic.effects;

import de.latzig.game.tob.client.logic.MeshArrays;

/**
 * ToB 2005's cloud plane around the camera (Phase 4b-3 spec §2 decision 3, {@code sky.cpp:287-301}): the curved sky
 * plane y = (4A − r²/A) · 0.2 − 1800 ({@link SkyDome}) at {@link SkyTuning#CLOUD_HEIGHT} of its height, the texture
 * repeated {@link SkyTuning#CLOUD_TILES} times across the plane's width, white with the vertex alpha
 * (1 − 2 r / 8192) · {@link SkyTuning#CLOUD_ALPHA} / 255, which reaches 0 at r = 4096 units (128 fields, 9° above the
 * horizon), so the sky colour shows through. Here as a disc of {@value #RINGS} rings and {@value #SEGMENTS}
 * segments out to that radius, in fields relative to the camera (the view moves it with the camera, like the sky
 * dome).
 */
public final class CloudPlane {

    public static final int RINGS = 16;
    public static final int SEGMENTS = 32;
    /** Where the vertex alpha reaches 0: half of ToB 2005's {@code IDX_ZDEPTH} (units). */
    public static final double RADIUS = 4096;
    static final double UNITS_PER_FIELD = 32;

    private CloudPlane() {
    }

    /** The plane's height (units) at the horizontal distance {@code r} (units) from its centre. */
    public static double height(double r) {
        double a = SkyDome.ATMOSPHERE;
        return SkyTuning.CLOUD_HEIGHT * ((4 * a - r * r / a) * 0.2 - 1800);
    }

    /** The vertex alpha (0..1) at the horizontal distance {@code r} (units). */
    public static float alpha(double r) {
        return (float) (Math.max(0.0, 1 - 2 * r / 8192) * SkyTuning.CLOUD_ALPHA / 255.0);
    }

    /** The disc: positions in fields, the texture coordinates before the drift, white with the vertex alpha. */
    public static MeshArrays arrays() {
        int triangles = RINGS * SEGMENTS * 2;
        float[] positions = new float[triangles * 9];
        float[] uvs = new float[triangles * 6];
        float[] colors = new float[triangles * 12];
        int t = 0;
        for (int ring = 0; ring < RINGS; ring++) {
            for (int s = 0; s < SEGMENTS; s++) {
                float[][] corners = {corner(ring, s), corner(ring, s + 1), corner(ring + 1, s + 1),
                        corner(ring + 1, s)};
                for (int[] triangle : new int[][] {{0, 1, 2}, {0, 2, 3}}) {
                    for (int c = 0; c < 3; c++) {
                        float[] v = corners[triangle[c]];
                        System.arraycopy(v, 0, positions, t * 9 + c * 3, 3);
                        System.arraycopy(v, 3, uvs, t * 6 + c * 2, 2);
                        colors[t * 12 + c * 4] = 1f;
                        colors[t * 12 + c * 4 + 1] = 1f;
                        colors[t * 12 + c * 4 + 2] = 1f;
                        colors[t * 12 + c * 4 + 3] = v[5];
                    }
                    t++;
                }
            }
        }
        return new MeshArrays(positions, uvs, colors);
    }

    /** x, y, z (fields), u, v and alpha of ring {@code ring}'s corner {@code s}. */
    private static float[] corner(int ring, int s) {
        double r = RADIUS * ring / RINGS;
        double angle = 2 * Math.PI * s / SEGMENTS;
        double x = r * Math.sin(angle);
        double z = -r * Math.cos(angle);
        double width = 2 * SkyDome.PLANE_HALF;
        double u = SkyTuning.CLOUD_TILES * (x + SkyDome.PLANE_HALF) / width;
        double v = SkyTuning.CLOUD_TILES * (1 - (z + SkyDome.PLANE_HALF) / width);
        return new float[] {(float) (x / UNITS_PER_FIELD), (float) (height(r) / UNITS_PER_FIELD),
                (float) (z / UNITS_PER_FIELD), (float) u, (float) v, alpha(r)};
    }
}
