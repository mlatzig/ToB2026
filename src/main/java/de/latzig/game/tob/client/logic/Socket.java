package de.latzig.game.tob.client.logic;

import java.util.Optional;

import de.latzig.game.tob.core.model.BaseType;
import de.latzig.game.tob.core.model.TowerGrid;

/**
 * The playground socket under the extent of level 0 (its non-empty base cells); higher levels may overhang it, as in
 * the original, ToB 2005 and ToB 2009 (acceptance 2026-10-10). Phase 4b-1 spec §2 decision 6: the shape of ToB 2009's
 * {@code BasisElementsRenderer.createSocket} ({@code SOCKET_HEIGHT = 1.0f}): the top just below the floor bottoms of
 * level 0, the four sides sloping out at 45° to a bottom one unit lower and one unit wider on every side, which is the
 * terrain's flat zone ({@link de.latzig.game.tob.client.logic.effects.Terrain#FLAT_Y}). Phase 4a used ToB 2005's 8-unit
 * playground meshes. The top texture repeats once per field; every side vertex has the same UV ({@link #SIDE_UV}), as
 * both originals do (ToB 2009 one whole-number UV per side quad, ToB 2005 all side UVs within the first texel), so the
 * sides are a single colour lit per face. Colour as ToB 2009: grey mixed half with ground colour 2, lit by the scene's
 * sun.
 *
 * @param x0 west edge of the top (scene x)
 * @param z0 north edge of the top (scene z)
 * @param x1 east edge
 * @param z1 south edge
 */
public record Socket(float x0, float z0, float x1, float z1) {

    /** ToB 2009 {@code SOCKET_HEIGHT}: one unit (1/32 field). */
    public static final float HEIGHT = 1f / 32f;
    /** 45°: the bottom reaches as far beyond the top as it is lower. */
    public static final float BORDER = HEIGHT;
    /** Just below the floor bottoms of level 0 (y 0). */
    public static final float TOP = -0.002f;
    /** The bottom, on the terrain's flat zone ({@link de.latzig.game.tob.client.logic.effects.Terrain#FLAT_Y}). */
    public static final float BOTTOM = TOP - HEIGHT;
    /** The one UV (u and v) of every side vertex. */
    public static final float SIDE_UV = 0f;

    /** The socket under the non-empty cells of {@code grid}'s level 0, or empty if level 0 is empty. */
    public static Optional<Socket> of(TowerGrid grid) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int y = 0; y < TowerGrid.DEPTH; y++) {
            for (int x = 0; x < TowerGrid.WIDTH; x++) {
                if (grid.cell(x, y, 0).base() != BaseType.EMPTY) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (minX == Integer.MAX_VALUE) {
            return Optional.empty();
        }
        return Optional.of(new Socket(minX - 0.5f, minY - 0.5f, maxX + 0.5f, maxY + 0.5f));
    }

    /** ToB 2009: {@code Colors.interpolate(Colors.GRAY, groundColor2, 0.5f)}. */
    public static int colour(TowerColours colours) {
        return TowerColours.mix(0x808080, colours.ground2(), 0.5f);
    }

    /** The top and the four sloped sides (10 triangles, ToB 2009's five quads), in {@code colour}. */
    public MeshArrays arrays(int colour) {
        float b0 = x0 - BORDER;
        float c0 = z0 - BORDER;
        float b1 = x1 + BORDER;
        float c1 = z1 + BORDER;
        float w = x1 - x0;
        float d = z1 - z0;
        float[][] quads = {
                {x0, TOP, z0, x0, TOP, z1, x1, TOP, z1, x1, TOP, z0},
                {x0, TOP, z0, x1, TOP, z0, b1, BOTTOM, c0, b0, BOTTOM, c0},
                {x1, TOP, z0, x1, TOP, z1, b1, BOTTOM, c1, b1, BOTTOM, c0},
                {x1, TOP, z1, x0, TOP, z1, b0, BOTTOM, c1, b1, BOTTOM, c1},
                {x0, TOP, z1, x0, TOP, z0, b0, BOTTOM, c0, b0, BOTTOM, c1}};
        float s = SIDE_UV;
        float[] side = {s, s, s, s, s, s, s, s};
        float[][] uvQuads = {{0, 0, 0, d, w, d, w, 0}, side, side, side, side};
        float[] positions = new float[quads.length * 18];
        float[] uvs = new float[quads.length * 12];
        float[] colors = new float[quads.length * 24];
        int[] order = {0, 1, 2, 0, 2, 3};
        float r = ((colour >> 16) & 0xFF) / 255f;
        float g = ((colour >> 8) & 0xFF) / 255f;
        float bl = (colour & 0xFF) / 255f;
        for (int q = 0; q < quads.length; q++) {
            for (int c = 0; c < 6; c++) {
                int corner = order[c];
                System.arraycopy(quads[q], corner * 3, positions, q * 18 + c * 3, 3);
                System.arraycopy(uvQuads[q], corner * 2, uvs, q * 12 + c * 2, 2);
                int k = q * 24 + c * 4;
                colors[k] = r;
                colors[k + 1] = g;
                colors[k + 2] = bl;
                colors[k + 3] = 1f;
            }
        }
        return new MeshArrays(positions, uvs, colors);
    }
}
