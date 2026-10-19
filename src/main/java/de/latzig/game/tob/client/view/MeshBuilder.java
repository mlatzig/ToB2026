package de.latzig.game.tob.client.view;

import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.util.BufferUtils;

import de.latzig.game.tob.client.logic.FaceFrames;
import de.latzig.game.tob.client.logic.MeshArrays;

/** Builds a jME triangle list with flat normals, tangents, UVs and vertex colours from {@link MeshArrays}. */
final class MeshBuilder {

    private MeshBuilder() {
    }

    static Mesh triangles(MeshArrays arrays) {
        int corners = arrays.triangles() * 3;
        float[] normals = new float[corners * 3];
        float[] tangents = new float[corners * 4];
        FaceFrames.compute(arrays.positions(), arrays.uvs(), normals, tangents);
        Mesh mesh = new Mesh();
        mesh.setBuffer(Type.Position, 3, BufferUtils.createFloatBuffer(arrays.positions()));
        mesh.setBuffer(Type.Normal, 3, BufferUtils.createFloatBuffer(normals));
        mesh.setBuffer(Type.Tangent, 4, BufferUtils.createFloatBuffer(tangents));
        mesh.setBuffer(Type.TexCoord, 2, BufferUtils.createFloatBuffer(arrays.uvs()));
        mesh.setBuffer(Type.Color, 4, BufferUtils.createFloatBuffer(arrays.colors()));
        mesh.updateBound();
        return mesh;
    }

    /**
     * As {@link #triangles(MeshArrays)}, plus a second set of texture coordinates at {@code detailRepeat} times the
     * first: the detail map of the walkable tops (Phase 4b-1, {@code Lighting.j3md} {@code LightMap} with
     * {@code SeparateTexCoord}).
     */
    static Mesh triangles(MeshArrays arrays, float detailRepeat) {
        Mesh mesh = triangles(arrays);
        float[] detail = arrays.uvs().clone();
        for (int i = 0; i < detail.length; i++) {
            detail[i] *= detailRepeat;
        }
        mesh.setBuffer(Type.TexCoord2, 2, BufferUtils.createFloatBuffer(detail));
        return mesh;
    }
}
