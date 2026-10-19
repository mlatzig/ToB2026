package de.latzig.game.tob.client.logic.effects;

import de.latzig.game.tob.client.logic.TowerColours;

/**
 * The moon's haze (acceptance change, Phase 6b-2): the moon is no longer drawn in its pure colours in front of the
 * sky but blended towards what the sky dome shows behind it ({@link SkyDome#look}: the sky gradient of palette entries
 * 8 and 7 by elevation, times the sky texture), weakly high in the sky ({@link SkyTuning#MOON_HAZE_HIGH}) and more
 * near the horizon ({@link SkyTuning#MOON_HAZE_HORIZON}), per vertex, so a giant moon is hazed more at its foot than
 * at its top. The view draws this as a sphere over each body in the sky colours with the strength as alpha; the
 * moon's own light and phase stay underneath.
 */
public final class MoonHaze {

    /** Per vertex: u, v of the sky texture; r, g, b of the sky colour and the haze strength as alpha. */
    public record Vertices(float[] uvs, float[] colors) {
    }

    private MoonHaze() {
    }

    /**
     * The blend towards the sky at {@code elevation} radians: {@link SkyTuning#MOON_HAZE_HORIZON} at and below the
     * horizon, falling linearly to {@link SkyTuning#MOON_HAZE_HIGH} at {@link SkyTuning#MOON_HAZE_FADE_DEGREES}.
     */
    public static float strength(double elevation) {
        double t = Math.clamp(Math.toDegrees(elevation) / SkyTuning.MOON_HAZE_FADE_DEGREES, 0.0, 1.0);
        return (float) (SkyTuning.MOON_HAZE_HORIZON + (SkyTuning.MOON_HAZE_HIGH - SkyTuning.MOON_HAZE_HORIZON) * t);
    }

    /**
     * The haze of the vertices at {@code positions} (x, y, z each, relative to the camera; only the direction
     * counts): the dome's texture spot and colour in each vertex's direction and the strength at its elevation.
     */
    public static Vertices vertices(float[] positions, TowerColours colours) {
        int count = positions.length / 3;
        float[] uvs = new float[count * 2];
        float[] colors = new float[count * 4];
        for (int v = 0; v < count; v++) {
            float x = positions[v * 3];
            float y = positions[v * 3 + 1];
            float z = positions[v * 3 + 2];
            float length = (float) Math.sqrt(x * x + y * y + z * z);
            float[] look = SkyDome.look(colours, x / length, y / length, z / length);
            uvs[v * 2] = look[0];
            uvs[v * 2 + 1] = look[1];
            colors[v * 4] = look[2];
            colors[v * 4 + 1] = look[3];
            colors[v * 4 + 2] = look[4];
            colors[v * 4 + 3] = strength(Math.asin(Math.clamp(y / length, -1f, 1f)));
        }
        return new Vertices(uvs, colors);
    }

    /**
     * The ring in the haze of the moon's centre at {@code elevation} radians (one colour for the whole ring: its
     * texture is a single colour with banded alpha).
     */
    public static MoonDesign.Ring ring(MoonDesign.Ring ring, TowerColours colours, double elevation) {
        int sky = TowerColours.mix(colours.skyTop(), colours.skyHorizon(), SkyDome.horizonMix(elevation));
        return new MoonDesign.Ring(ring.inner(), ring.outer(), TowerColours.mix(ring.colour(), sky,
                strength(elevation)));
    }
}
