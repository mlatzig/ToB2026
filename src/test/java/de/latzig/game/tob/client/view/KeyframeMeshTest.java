package de.latzig.game.tob.client.view;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.jme3.scene.VertexBuffer.Type;

import de.latzig.game.tob.core.model.MeshData;

class KeyframeMeshTest {

    private static float[] buffer(KeyframeMesh mesh, Type type) {
        FloatBuffer data = (FloatBuffer) mesh.mesh().getBuffer(type).getData();
        float[] values = new float[data.limit()];
        data.rewind();
        data.get(values);
        return values;
    }

    @Test
    void blendsPositionsAndColoursStepsUvsAndRecomputesNormals() {
        // one triangle, counter-clockwise seen from above; key 16 lifts it by one field
        float[] low = {0, 0, 0, 0, 0, 1, 1, 0, 0};
        float[] high = {0, 1, 0, 0, 1, 1, 1, 1, 0};
        MeshData data = new MeshData(1, new int[] {0, 16}, List.of(low, high),
                List.of(new float[6], new float[] {0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f}),
                List.of(new int[] {0xFF000000, 0xFF000000, 0xFF000000}, new int[] {-1, -1, -1}));
        KeyframeMesh mesh = new KeyframeMesh(data);
        assertTrue(mesh.isAnimated());
        assertTrue(mesh.hasColors());

        mesh.setKey(8f);

        assertArrayEquals(new float[] {0, 0.5f, 0, 0, 0.5f, 1, 1, 0.5f, 0}, buffer(mesh, Type.Position), 1e-6f);
        assertArrayEquals(new float[] {0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f}, buffer(mesh, Type.TexCoord), 1e-6f);
        assertArrayEquals(new float[] {0, 1, 0, 0, 1, 0, 0, 1, 0}, buffer(mesh, Type.Normal), 1e-6f);
        assertArrayEquals(new float[] {0.5f, 0.5f, 0.5f, 1f}, Arrays.copyOf(buffer(mesh, Type.Color), 4),
                1e-6f);

        // strictly before the first key, ToB 2005's DXE_nextFrameKey and DXE_previousFrameKey both collapse
        // to key 0 (dxe.cpp:2583-2600, 2612-2631: there is no earlier key to fall back to), so the UV comes
        // from key 0 (the "low" UVs, all zero), not key 1; the position also clamps to key 0 (fraction 0).
        mesh.setKey(-5f);
        assertArrayEquals(new float[6], buffer(mesh, Type.TexCoord), 1e-6f);
        assertFalse(new KeyframeMesh(new MeshData(1, new int[] {0}, List.of(low), List.of(new float[6]), null))
                .isAnimated());
    }
}
