package de.latzig.game.tob.client.view;

import java.nio.FloatBuffer;

import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.util.BufferUtils;

import de.latzig.game.tob.client.logic.FaceFrames;
import de.latzig.game.tob.client.logic.KeyframeSampler;
import de.latzig.game.tob.core.model.MeshData;

/**
 * A jME triangle list for one {@code .tobmesh} (already in ToB 2026 coordinates: 1 field = 1.0, origin at the bottom
 * centre of the field, counter-clockwise front faces). {@link #setKey} blends positions and colours between the two
 * keys around the animation key, steps the UVs ({@link KeyframeSampler}) and recomputes the flat normals and
 * tangents ({@link FaceFrames}). A mesh with one key is built once.
 */
public final class KeyframeMesh {

    private final int[] keys;
    private final float[][] positions;
    private final float[][] uvs;
    private final float[][] colors;
    private final Mesh mesh = new Mesh();
    private final float[] position;
    private final float[] normal;
    private final float[] tangent;
    private final float[] color;
    private float currentKey = Float.NaN;

    public KeyframeMesh(MeshData data) {
        keys = data.keys();
        int count = data.keyCount();
        positions = new float[count][];
        uvs = new float[count][];
        colors = data.hasColors() ? new float[count][] : null;
        for (int i = 0; i < count; i++) {
            positions[i] = data.positions(i);
            uvs[i] = data.uvs(i);
            if (colors != null) {
                colors[i] = rgba(data.colors(i));
            }
        }
        int corners = data.triangleCount() * 3;
        position = new float[corners * 3];
        normal = new float[corners * 3];
        tangent = new float[corners * 4];
        color = colors == null ? null : new float[corners * 4];
        mesh.setBuffer(Type.Position, 3, BufferUtils.createFloatBuffer(corners * 3));
        mesh.setBuffer(Type.Normal, 3, BufferUtils.createFloatBuffer(corners * 3));
        mesh.setBuffer(Type.Tangent, 4, BufferUtils.createFloatBuffer(corners * 4));
        mesh.setBuffer(Type.TexCoord, 2, BufferUtils.createFloatBuffer(corners * 2));
        if (colors != null) {
            mesh.setBuffer(Type.Color, 4, BufferUtils.createFloatBuffer(corners * 4));
        }
        if (isAnimated()) {
            mesh.setStreamed();
        }
        setKey(keys[0]);
    }

    public Mesh mesh() {
        return mesh;
    }

    public boolean isAnimated() {
        return keys.length > 1;
    }

    public boolean hasColors() {
        return colors != null;
    }

    /** Shows animation key {@code key} (16 keys per simulation frame). */
    public void setKey(float key) {
        KeyframeSampler.Sample sample = KeyframeSampler.sample(keys, key);
        float wanted = keys[sample.from()] + sample.fraction() * (keys[sample.to()] - keys[sample.from()]);
        if (wanted == currentKey) {
            return;
        }
        currentKey = wanted;
        blend(positions[sample.from()], positions[sample.to()], sample.fraction(), position);
        float[] uv = uvs[sample.uvKey(keys, key)];
        FaceFrames.compute(position, uv, normal, tangent);
        upload(Type.Position, position);
        upload(Type.Normal, normal);
        upload(Type.Tangent, tangent);
        upload(Type.TexCoord, uv);
        if (colors != null) {
            blend(colors[sample.from()], colors[sample.to()], sample.fraction(), color);
            upload(Type.Color, color);
        }
        mesh.updateBound();
    }

    private static void blend(float[] a, float[] b, float f, float[] out) {
        for (int i = 0; i < out.length; i++) {
            out[i] = a[i] + (b[i] - a[i]) * f;
        }
    }

    private static float[] rgba(int[] argb) {
        float[] out = new float[argb.length * 4];
        for (int i = 0; i < argb.length; i++) {
            out[i * 4] = ((argb[i] >> 16) & 0xFF) / 255f;
            out[i * 4 + 1] = ((argb[i] >> 8) & 0xFF) / 255f;
            out[i * 4 + 2] = (argb[i] & 0xFF) / 255f;
            out[i * 4 + 3] = ((argb[i] >>> 24) & 0xFF) / 255f;
        }
        return out;
    }

    private void upload(Type type, float[] data) {
        VertexBuffer buffer = mesh.getBuffer(type);
        FloatBuffer floats = (FloatBuffer) buffer.getData();
        floats.clear();
        floats.put(data);
        floats.flip();
        buffer.updateData(floats);
    }
}
