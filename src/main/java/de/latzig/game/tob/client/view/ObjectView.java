package de.latzig.game.tob.client.view;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial.CullHint;

import de.latzig.game.tob.client.logic.MeshFrame;
import de.latzig.game.tob.client.logic.Pose;
import de.latzig.game.tob.client.logic.game.AnimationState;
import de.latzig.game.tob.core.model.ElementDefinition;
import de.latzig.game.tob.core.model.Lighting;
import de.latzig.game.tob.core.model.MeshGroup;
import de.latzig.game.tob.core.model.MeshPart;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.RenderType;
import de.latzig.game.tob.core.sim.SimObject;

/**
 * One node per object (spec §3): every mesh group of its element as a child node with one geometry per part, of which
 * only the group of the current {@link MeshFrame} is shown. The grabber's group 3, the carried klondikes, is not a
 * walk group: it is drawn once per collected klondike at the ToB 2009 offsets.
 */
public final class ObjectView {

    /** The grabber's carried-klondike group in {@code elements.json}. */
    public static final int CARRY_GROUP = 3;

    private record Part(KeyframeMesh mesh, Geometry geometry) {
    }

    private final AssetLibrary assets;
    /** Phase 5e: the scene's materials (the Remake or the Classic Look). */
    private final SceneMaterials materials;
    private final ObjectType type;
    private final Node node;
    private final Map<Integer, Node> groups = new LinkedHashMap<>();
    private final Map<Integer, List<Part>> parts = new LinkedHashMap<>();
    private final MeshGroup carryGroup;
    private final List<List<Part>> carried = new ArrayList<>();
    private final Node carryNode = new Node("carried");
    private int shownGroup = -1;

    public ObjectView(AssetLibrary assets, SimObject object) {
        this(assets, SceneMaterials.remake(assets), object);
    }

    /** Phase 5e: the object with the scene's {@code materials} (the Remake or the Classic Look). */
    public ObjectView(AssetLibrary assets, SceneMaterials materials, SimObject object) {
        this.assets = assets;
        this.materials = materials;
        this.type = object.type();
        this.node = new Node("object-" + object.slot() + "-" + object.type().code());
        ElementDefinition element = assets.element(object.type());
        MeshGroup carry = null;
        for (MeshGroup group : element.groups()) {
            if (object.type() == ObjectType.GRB && group.id() == CARRY_GROUP) {
                carry = group;
                continue;
            }
            Node groupNode = new Node("group-" + group.id());
            groups.put(group.id(), groupNode);
            parts.put(group.id(), buildParts(group, groupNode));
            node.attachChild(groupNode);
        }
        this.carryGroup = carry;
        node.attachChild(carryNode);
    }

    public Node node() {
        return node;
    }

    /** Places, turns and animates the object; {@code visible} false hides it (destroyed, or the camera owner). */
    public void update(MeshFrame frame, Pose pose, boolean visible) {
        node.setCullHint(visible ? CullHint.Dynamic : CullHint.Always);
        if (!visible) {
            return;
        }
        node.setLocalTranslation(pose.x(), pose.y(), pose.z());
        node.setLocalRotation(new Quaternion().fromAngleAxis(-frame.heading(), Vector3f.UNIT_Y));
        int group = groups.containsKey(frame.group()) ? frame.group() : groups.keySet().iterator().next();
        if (group != shownGroup) {
            groups.forEach((id, groupNode) -> groupNode.setCullHint(id == group ? CullHint.Dynamic : CullHint.Always));
            shownGroup = group;
        }
        for (Part part : parts.get(group)) {
            part.mesh().setKey(frame.key());
        }
    }

    /** The grabber's carried klondikes: a copy of group 3 per key ({@link AnimationState#carriedKlondikeKeys}). */
    public void updateCarried(List<Float> keys) {
        if (carryGroup == null) {
            return;
        }
        while (carried.size() < keys.size()) {
            int index = carried.size();
            Node copy = new Node("klondike-" + index);
            Pose offset = AnimationState.carriedKlondikeOffset(index);
            copy.setLocalTranslation(offset.x(), offset.y(), offset.z());
            carried.add(buildParts(carryGroup, copy));
            carryNode.attachChild(copy);
        }
        for (int i = 0; i < carried.size(); i++) {
            boolean shown = i < keys.size();
            for (Part part : carried.get(i)) {
                part.geometry().setCullHint(shown ? CullHint.Dynamic : CullHint.Always);
                if (shown) {
                    part.mesh().setKey(keys.get(i));
                }
            }
        }
    }

    /** The id of the group shown, -1 before the first visible update (tests and debugging). */
    public int shownGroup() {
        return shownGroup;
    }

    private List<Part> buildParts(MeshGroup group, Node parent) {
        List<Part> list = new ArrayList<>();
        for (int i = 0; i < group.parts().size(); i++) {
            MeshPart part = group.parts().get(i);
            KeyframeMesh mesh = new KeyframeMesh(assets.objectMesh(part.mesh(), part.tint()));
            Geometry geometry = new Geometry(parent.getName() + "-part-" + (i + 1), mesh.mesh());
            geometry.setMaterial(materials.part(type, part, mesh.hasColors()));
            if (AssetLibrary.isTransparent(part)) {
                geometry.setQueueBucket(Bucket.Transparent);
            }
            geometry.setShadowMode(shadowMode(part));
            parent.attachChild(geometry);
            list.add(new Part(mesh, geometry));
        }
        return list;
    }

    /**
     * Fix round 1 (review finding): {@link WorldView} sets {@code CastAndReceive} on the whole world node (spec:
     * blocks, spiders and enemies cast shadows), but a blended part must not cast an opaque shadow of its
     * see-through shape. Unlit effect parts ({@link Lighting#UNLIT}: the converter beam, the exchanger sparkle)
     * take part in no shadow at all ({@link ShadowMode#Off}); glass parts ({@link RenderType#GLASS}, lit: the
     * prism, the reflector, the freezer) still receive the world's shadows, they just do not cast one of their own
     * ({@link ShadowMode#Receive}); every opaque part inherits the node's {@code CastAndReceive}.
     */
    private static ShadowMode shadowMode(MeshPart part) {
        if (part.lighting() == Lighting.UNLIT) {
            return ShadowMode.Off;
        }
        if (part.render() == RenderType.GLASS) {
            return ShadowMode.Receive;
        }
        return ShadowMode.Inherit;
    }
}
