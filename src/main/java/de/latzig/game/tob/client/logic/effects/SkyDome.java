package de.latzig.game.tob.client.logic.effects;

import de.latzig.game.tob.client.logic.MeshArrays;
import de.latzig.game.tob.client.logic.TowerColours;

/**
 * The sky dome around the camera (Phase 4a spec §2 decision 4): a hemisphere plus a skirt below the horizon, coloured
 * and mapped as ToB 2005 draws its curved sky plane ({@code sky.cpp} {@code _SKY_createPlanes}, {@code sky.h}). The
 * 2005 plane is the paraboloid y = (4A − r²/A) · 0.2 − 1800 over the square of half-size √2 · A
 * ({@code SKY_ATMOSPHERE_RADIUS} A = 8192 / 1.8); its colour runs from sky colour 1 at the centre to sky colour 2
 * where 2 r² / A² reaches 1 (about 23° above the horizon) and stays sky colour 2 below; the sky texture is projected
 * from above once over the whole plane. Here every dome corner takes the colour and texture coordinate of the plane
 * point seen in its direction: zenith {@link TowerColours#skyTop()}, horizon and skirt
 * {@link TowerColours#skyHorizon()}.
 */
public final class SkyDome {

    public static final int SEGMENTS = 24;
    public static final int RINGS = 16;
    public static final float SKIRT_DEGREES = 30f;

    /** ToB 2005 {@code SKY_ATMOSPHERE_RADIUS} = {@code IDX_ZDEPTH} / 1.8 (2005 units, 32 per cell). */
    static final double ATMOSPHERE = 8192 / 1.8;
    /** Half the width of the 2005 sky plane: {@code g_fPlaneSize} / 2 = √2 · A. */
    static final double PLANE_HALF = Math.sqrt(2) * ATMOSPHERE;

    private SkyDome() {
    }

    public static MeshArrays arrays(TowerColours colours, float radius) {
        int bands = RINGS + 1;
        float[] positions = new float[SEGMENTS * bands * 2 * 9];
        float[] uvs = new float[SEGMENTS * bands * 2 * 6];
        float[] colors = new float[SEGMENTS * bands * 2 * 12];
        int triangle = 0;
        for (int band = 0; band < bands; band++) {
            float low = elevation(band);
            float high = elevation(band + 1);
            for (int s = 0; s < SEGMENTS; s++) {
                float a0 = (float) (2 * Math.PI * s / SEGMENTS);
                float a1 = (float) (2 * Math.PI * (s + 1) / SEGMENTS);
                float[][] corners = {corner(a0, low, radius, colours), corner(a1, low, radius, colours),
                        corner(a1, high, radius, colours), corner(a0, high, radius, colours)};
                int[][] triangles = {{0, 1, 2}, {0, 2, 3}};
                for (int[] corner : triangles) {
                    for (int c = 0; c < 3; c++) {
                        float[] v = corners[corner[c]];
                        System.arraycopy(v, 0, positions, triangle * 9 + c * 3, 3);
                        System.arraycopy(v, 3, uvs, triangle * 6 + c * 2, 2);
                        System.arraycopy(v, 5, colors, triangle * 12 + c * 4, 4);
                    }
                    triangle++;
                }
            }
        }
        return new MeshArrays(positions, uvs, colors);
    }

    /**
     * The horizontal distance (2005 units) of the point of the 2005 sky plane seen at {@code elevation} radians from
     * the plane's centre below it: the positive root of 0.2 r² / A + r · tan(e) − (0.8 A − 1800) = 0; the horizon
     * value for elevations at or below 0.
     */
    static double planeDistance(double elevation) {
        double e = Math.max(elevation, 0.0);
        if (e >= Math.PI / 2 - 1e-6) {
            return 0.0;
        }
        double a = 0.2 / ATMOSPHERE;
        double c = 0.8 * ATMOSPHERE - 1800;
        double b = Math.tan(e);
        return 2 * c / (b + Math.sqrt(b * b + 4 * a * c));
    }

    /** ToB 2005's sky colour mix at {@code elevation} radians: 0 = sky colour 1 (zenith), 1 = sky colour 2. */
    public static float horizonMix(double elevation) {
        double r = planeDistance(elevation);
        return (float) Math.min(1.0, 2 * r * r / (ATMOSPHERE * ATMOSPHERE));
    }

    /** Elevation in radians of ring {@code band}: band 0 is the skirt's lower edge, band 1 the horizon. */
    private static float elevation(int band) {
        if (band == 0) {
            return (float) Math.toRadians(-SKIRT_DEGREES);
        }
        return (float) (Math.PI / 2 * (band - 1) / RINGS);
    }

    /** x, y, z, u, v, r, g, b, a. */
    private static float[] corner(float azimuth, float elevation, float radius, TowerColours colours) {
        float ring = (float) Math.cos(elevation) * radius;
        float x = (float) Math.sin(azimuth);
        float z = -(float) Math.cos(azimuth);
        float[] look = lookAt(colours, elevation, x, z);
        return new float[] {x * ring, (float) Math.sin(elevation) * radius, z * ring, look[0], look[1], look[2],
                look[3], look[4], 1f};
    }

    /**
     * What the dome shows in the unit direction (x, y, z) from the camera (moon haze, Phase 6b-2): u, v of the sky
     * texture and r, g, b of the sky colour (0..1, as the dome's vertex colours).
     */
    public static float[] look(TowerColours colours, float x, float y, float z) {
        float horizontal = (float) Math.sqrt(x * x + z * z);
        float elevation = (float) Math.asin(Math.clamp(y, -1f, 1f));
        return horizontal < 1e-6f ? lookAt(colours, elevation, 0f, 0f)
                : lookAt(colours, elevation, x / horizontal, z / horizontal);
    }

    /** u, v, r, g, b at {@code elevation} radians in the horizontal unit direction (x, z). */
    private static float[] lookAt(TowerColours colours, float elevation, float x, float z) {
        int colour = TowerColours.mix(colours.skyTop(), colours.skyHorizon(), horizonMix(elevation));
        double plane = planeDistance(elevation) / (2 * PLANE_HALF);
        return new float[] {(float) (0.5 + x * plane), (float) (0.5 - z * plane), ((colour >> 16) & 0xFF) / 255f,
                ((colour >> 8) & 0xFF) / 255f, (colour & 0xFF) / 255f};
    }
}
