package de.latzig.game.tob.client.view;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.List;

import com.jme3.material.Material;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.util.BufferUtils;

import de.latzig.game.tob.client.logic.effects.ExplosionSprites;
import de.latzig.game.tob.core.model.FxAtlas;
import de.latzig.game.tob.core.model.FxSequence;
import de.latzig.game.tob.core.model.FxSprite;

/**
 * Up to {@code capacity} white, camera-facing FX atlas sprites as one quad mesh in the transparent bucket (Phase
 * 4b-1's explosion sprites, Phase 4b-2's meteor trails): {@link #update} rewrites the quads every frame, unused quads
 * collapse to a point. A sprite's frame beyond its sequence shows the sequence's last frame.
 */
final class SpriteBatch {

    private final FxAtlas atlas;
    private final int capacity;
    private final Geometry geometry;
    private final float[] positions;
    private final float[] uvs;

    SpriteBatch(String name, FxAtlas atlas, Material material, int capacity) {
        this.atlas = atlas;
        this.capacity = capacity;
        int corners = capacity * 6;
        positions = new float[corners * 3];
        uvs = new float[corners * 2];
        Mesh mesh = new Mesh();
        mesh.setBuffer(Type.Position, 3, BufferUtils.createFloatBuffer(corners * 3));
        mesh.setBuffer(Type.TexCoord, 2, BufferUtils.createFloatBuffer(corners * 2));
        float[] white = new float[corners * 4];
        Arrays.fill(white, 1f);
        mesh.setBuffer(Type.Color, 4, BufferUtils.createFloatBuffer(white));
        mesh.setStreamed();
        geometry = new Geometry(name, mesh);
        geometry.setMaterial(material);
        geometry.setQueueBucket(Bucket.Transparent);
    }

    Geometry geometry() {
        return geometry;
    }

    /** Writes the quads of {@code sprites} facing the camera ({@code left}, {@code up}); returns how many. */
    int update(List<ExplosionSprites.Sprite> sprites, Vector3f left, Vector3f up) {
        Arrays.fill(positions, 0f);
        Arrays.fill(uvs, 0f);
        int count = Math.min(capacity, sprites.size());
        for (int s = 0; s < count; s++) {
            ExplosionSprites.Sprite sprite = sprites.get(s);
            FxSequence sequence = atlas.sequences().get(sprite.sequence());
            FxSprite cell = sequence.frame(Math.min(sprite.frame(), sequence.frames() - 1));
            float u0 = (float) cell.x() / atlas.imageWidth();
            float u1 = (float) (cell.x() + cell.width()) / atlas.imageWidth();
            float top = (float) cell.y() / atlas.imageHeight();
            float bottom = (float) (cell.y() + cell.height()) / atlas.imageHeight();
            float h = sprite.halfSize();
            float rx = -left.x * h;
            float ry = -left.y * h;
            float rz = -left.z * h;
            float ux = up.x * h;
            float uy = up.y * h;
            float uz = up.z * h;
            // The four quad corners as local primitives (no array/object allocated); the six writeCorner calls
            // below lay them out as two triangles (0,1,2) and (0,2,3) straight into the reused positions/uvs
            // buffers.
            float x0 = sprite.x() - rx - ux;
            float y0 = sprite.y() - ry - uy;
            float z0 = sprite.z() - rz - uz;
            float x1 = sprite.x() + rx - ux;
            float y1 = sprite.y() + ry - uy;
            float z1 = sprite.z() + rz - uz;
            float x2 = sprite.x() + rx + ux;
            float y2 = sprite.y() + ry + uy;
            float z2 = sprite.z() + rz + uz;
            float x3 = sprite.x() - rx + ux;
            float y3 = sprite.y() - ry + uy;
            float z3 = sprite.z() - rz + uz;
            int p = s * 18;
            int t = s * 12;
            writeCorner(positions, p, uvs, t, x0, y0, z0, u0, bottom);
            writeCorner(positions, p + 3, uvs, t + 2, x1, y1, z1, u1, bottom);
            writeCorner(positions, p + 6, uvs, t + 4, x2, y2, z2, u1, top);
            writeCorner(positions, p + 9, uvs, t + 6, x0, y0, z0, u0, bottom);
            writeCorner(positions, p + 12, uvs, t + 8, x2, y2, z2, u1, top);
            writeCorner(positions, p + 15, uvs, t + 10, x3, y3, z3, u0, top);
        }
        Mesh mesh = geometry.getMesh();
        upload(mesh, Type.Position, positions);
        upload(mesh, Type.TexCoord, uvs);
        mesh.updateBound();
        geometry.updateModelBound();
        return count;
    }

    /** Writes one corner's position ({@code x}, {@code y}, {@code z}) and UV ({@code u}, {@code v}) in place. */
    private static void writeCorner(float[] positions, int p, float[] uvs, int t, float x, float y, float z,
            float u, float v) {
        positions[p] = x;
        positions[p + 1] = y;
        positions[p + 2] = z;
        uvs[t] = u;
        uvs[t + 1] = v;
    }

    static void upload(Mesh mesh, Type type, float[] data) {
        VertexBuffer buffer = mesh.getBuffer(type);
        FloatBuffer floats = (FloatBuffer) buffer.getData();
        floats.clear();
        floats.put(data);
        floats.flip();
        buffer.updateData(floats);
    }
}
