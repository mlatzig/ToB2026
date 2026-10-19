package de.latzig.game.tob.client.view;

import com.jme3.math.ColorRGBA;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.util.BufferUtils;

import de.latzig.game.tob.client.logic.Socket;
import de.latzig.game.tob.client.logic.TowerColours;
import de.latzig.game.tob.client.logic.effects.Terrain;
import de.latzig.game.tob.core.model.TowerDefinition;

/**
 * The ground around the tower (Phase 4a spec §2 decisions 3 and 9): the hilly {@link Terrain} with ToB 2005's ground,
 * ground detail and mountain textures ({@link AssetLibrary#terrainMaterial}, Phase 4b-4 decision 1; the mesh's second
 * texture coordinates carry the ground and mountain weights), and ToB 2009's one-unit playground {@link Socket}
 * under the tower with the playground texture. Both are lit by the sun and receive shadows. The ToB 2005 horizon fog
 * comes with the library's materials, shared by every world material ({@link AssetLibrary#setFogColour}).
 */
public final class TerrainView {

    private final Node node = new Node("terrain");
    private final Geometry ground;
    private final Geometry socket;

    /** @throws AssetLoadException if the ground or playground texture is missing */
    public TerrainView(AssetLibrary assets, TowerDefinition tower, TowerColours colours) {
        Terrain.TerrainMesh data = Terrain.of(tower).mesh(colours, tower.id().hashCode());
        Mesh mesh = new Mesh();
        mesh.setBuffer(Type.Position, 3, BufferUtils.createFloatBuffer(data.positions()));
        mesh.setBuffer(Type.Normal, 3, BufferUtils.createFloatBuffer(data.normals()));
        mesh.setBuffer(Type.TexCoord, 2, BufferUtils.createFloatBuffer(data.uvs()));
        mesh.setBuffer(Type.Color, 4, BufferUtils.createFloatBuffer(data.colors()));
        mesh.setBuffer(Type.TexCoord2, 2, BufferUtils.createFloatBuffer(data.weights()));
        mesh.setBuffer(Type.Index, 3, BufferUtils.createIntBuffer(data.indices()));
        mesh.updateBound();
        ground = new Geometry("terrain", mesh);
        ground.setMaterial(assets.terrainMaterial());
        node.attachChild(ground);

        socket = Socket.of(tower.grid()).map(s -> {
            Geometry geometry = new Geometry("socket", MeshBuilder.triangles(s.arrays(Socket.colour(colours))));
            geometry.setMaterial(assets.litBaseMaterial("playground"));
            node.attachChild(geometry);
            return geometry;
        }).orElse(null);
        node.setShadowMode(ShadowMode.Receive);
    }

    public Node node() {
        return node;
    }

    /** The terrain geometry (tests). */
    Geometry ground() {
        return ground;
    }

    /** The socket geometry, or null for a tower without any cell (tests). */
    Geometry socket() {
        return socket;
    }

    /** The fog colour of the terrain (tests). */
    ColorRGBA fogColour() {
        return (ColorRGBA) ground.getMaterial().getParam("FogColor").getValue();
    }
}
