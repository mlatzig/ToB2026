package de.latzig.game.tob.client.logic;

import java.util.ArrayList;
import java.util.List;

import de.latzig.game.tob.core.model.MeshData;

/**
 * ToB 2005's and ToB 2009's vertex colour rule (Phase 4b-4 spec decision 4): every vertex colour is averaged with the
 * object colour, {@code (vertex + object) / 2} per channel, truncated as ToB 2005 does ({@code dxe.cpp:1124-1126}
 * FX 2, {@code 1243-1244} FX 3, {@code 1495-1496} FX 4; ToB 2009 {@code CRendererSimpleMesh.cpp:426-435}). The alpha
 * stays: ToB 2005's glass shader ({@code RenderABS}) never reads the vertex alpha, and the simple alpha path adds
 * the object's 255 modulo 256, i.e. leaves it (all but one step) as it is. Computed at load time; the converted
 * meshes are unchanged.
 */
public final class VertexColours {

    private VertexColours() {
    }

    /** One ARGB vertex colour averaged with {@code objectColour} (0xRRGGBB); the alpha stays. */
    public static int average(int argb, int objectColour) {
        int r = (((argb >> 16) & 0xFF) + ((objectColour >> 16) & 0xFF)) / 2;
        int g = (((argb >> 8) & 0xFF) + ((objectColour >> 8) & 0xFF)) / 2;
        int b = ((argb & 0xFF) + (objectColour & 0xFF)) / 2;
        return (argb & 0xFF000000) | r << 16 | g << 8 | b;
    }

    /** {@code data} with every key's vertex colours averaged with {@code objectColour}; without colours unchanged. */
    public static MeshData average(MeshData data, int objectColour) {
        if (!data.hasColors()) {
            return data;
        }
        List<float[]> positions = new ArrayList<>(data.keyCount());
        List<float[]> uvs = new ArrayList<>(data.keyCount());
        List<int[]> colors = new ArrayList<>(data.keyCount());
        for (int k = 0; k < data.keyCount(); k++) {
            positions.add(data.positions(k));
            uvs.add(data.uvs(k));
            int[] averaged = data.colors(k).clone();
            for (int i = 0; i < averaged.length; i++) {
                averaged[i] = average(averaged[i], objectColour);
            }
            colors.add(averaged);
        }
        return new MeshData(data.triangleCount(), data.keys(), positions, uvs, colors);
    }
}
