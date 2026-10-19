package de.latzig.game.tob.client.logic.effects;

import java.util.EnumMap;
import java.util.Map;

import de.latzig.game.tob.client.logic.BaseGeometry;
import de.latzig.game.tob.client.logic.MeshArrays;
import de.latzig.game.tob.core.model.StaticQuadrant;

/**
 * Phase 4b-1 spec §2 decision 8: {@link BaseGeometry} keeps ToB 2009's STATIC atlas UVs (its tests pin them); this
 * splits its triangles into one list per {@link StaticQuadrant}, with the UVs in the quadrant's own 0..1, for the
 * quadrant textures the converter writes (repeated, so mipmaps and anisotropic filtering never reach a neighbouring
 * quadrant). A triangle belongs to the quadrant of its UV centroid; every quad of {@link BaseGeometry} lies inside
 * one quadrant.
 */
public final class StaticAtlas {

    /**
     * Detail map repeats per face: ToB 2005 samples its STATIC detail map at the atlas UV × 4
     * ({@link MaterialTuning#STATIC_DETAIL_TILES}: {@code DXE_STATIC_TEXTILES_MULTI}, {@code dxe.h:50};
     * {@code RenderBM} vertex shader {@code oT2 = v1 · c12}), and a face spans half the atlas: 2. Since Phase 4b-4 on
     * every face, the walkable tops as well as the sides and bottoms.
     */
    public static final float DETAIL_REPEAT = MaterialTuning.STATIC_DETAIL_TILES / 2f;

    private StaticAtlas() {
    }

    /** One triangle list per non-empty quadrant, in triangle order; positions and colours unchanged. */
    public static Map<StaticQuadrant, MeshArrays> split(MeshArrays atlas) {
        int triangles = atlas.triangles();
        float[] uvs = atlas.uvs();
        StaticQuadrant[] quadrants = new StaticQuadrant[triangles];
        int[] counts = new int[StaticQuadrant.values().length];
        for (int t = 0; t < triangles; t++) {
            float u = (uvs[t * 6] + uvs[t * 6 + 2] + uvs[t * 6 + 4]) / 3f;
            float v = (uvs[t * 6 + 1] + uvs[t * 6 + 3] + uvs[t * 6 + 5]) / 3f;
            quadrants[t] = StaticQuadrant.at(u, v);
            counts[quadrants[t].ordinal()]++;
        }
        Map<StaticQuadrant, MeshArrays> out = new EnumMap<>(StaticQuadrant.class);
        for (StaticQuadrant quadrant : StaticQuadrant.values()) {
            int count = counts[quadrant.ordinal()];
            if (count == 0) {
                continue;
            }
            float[] positions = new float[count * 9];
            float[] local = new float[count * 6];
            float[] colors = new float[count * 12];
            int n = 0;
            for (int t = 0; t < triangles; t++) {
                if (quadrants[t] != quadrant) {
                    continue;
                }
                System.arraycopy(atlas.positions(), t * 9, positions, n * 9, 9);
                System.arraycopy(atlas.colors(), t * 12, colors, n * 12, 12);
                for (int c = 0; c < 3; c++) {
                    local[n * 6 + c * 2] = quadrant.localU(uvs[t * 6 + c * 2]);
                    local[n * 6 + c * 2 + 1] = quadrant.localV(uvs[t * 6 + c * 2 + 1]);
                }
                n++;
            }
            out.put(quadrant, new MeshArrays(positions, local, colors));
        }
        return out;
    }
}
