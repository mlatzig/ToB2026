package de.latzig.game.tob.client.view;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;

import de.latzig.game.tob.client.logic.BaseGeometry;
import de.latzig.game.tob.client.logic.CellColours;
import de.latzig.game.tob.client.logic.MeshArrays;
import de.latzig.game.tob.client.logic.MotionCarry;
import de.latzig.game.tob.client.logic.effects.StaticAtlas;
import de.latzig.game.tob.client.logic.game.LiftHeights;
import de.latzig.game.tob.core.model.StaticQuadrant;
import de.latzig.game.tob.core.model.TowerGrid;
import de.latzig.game.tob.core.sim.Lift;
import de.latzig.game.tob.core.sim.SimEvent;
import de.latzig.game.tob.core.sim.WorldState;

/**
 * The tower's floors, boxes and lifts in the scene: one node per level (rebuilt locally when a
 * {@link SimEvent.FloorEaten} changes it) and one per lift shaft (rebuilt whenever its platform height changes: spider
 * rides, forcer motions). Phase 4b-1: each node holds one geometry per STATIC quadrant ({@link StaticAtlas}) on the
 * quadrant's own repeated texture ({@link AssetLibrary#staticMaterial}): the walkable tops (floor and box tops, lift
 * platforms) always the ToB 2009 slabs, the sides and bottoms the ToB 2005 set; every face with ToB 2005's detail
 * map on its second texture coordinates (the tops since Phase 4b-1, the others since Phase 4b-4).
 */
public final class BaseView {

    private record LiftGeometry(Lift lift, Node node, float[] ratio) {
    }

    private final Node node = new Node("base");
    private final SceneMaterials materials;
    private final WorldState world;
    private final CellColours colours;
    private final Node[] levels = new Node[TowerGrid.LEVELS];
    private final List<LiftGeometry> lifts = new ArrayList<>();

    /** @throws AssetLoadException if a STATIC quadrant texture is missing */
    public BaseView(AssetLibrary assets, WorldState world, CellColours colours) {
        this(world, colours, SceneMaterials.remake(assets));
    }

    /**
     * Phase 5e: the base geometry in {@code colours} on the scene's {@code materials} (the Remake or the Classic
     * Look).
     *
     * @throws AssetLoadException if a STATIC quadrant texture is missing
     */
    public BaseView(WorldState world, CellColours colours, SceneMaterials materials) {
        this.materials = materials;
        this.world = world;
        this.colours = colours;
        for (StaticQuadrant quadrant : StaticQuadrant.values()) {
            materials.base(quadrant);
        }
        for (int level = 0; level < TowerGrid.LEVELS; level++) {
            rebuild(level);
        }
        for (Lift lift : world.lifts()) {
            Node liftNode = new Node("lift-" + lift.lower());
            node.attachChild(liftNode);
            lifts.add(new LiftGeometry(lift, liftNode, new float[] {Float.NaN}));
        }
        update(0f);
    }

    public Node node() {
        return node;
    }

    /** Rebuilds the levels the step's floor losses changed. */
    public void onStep(List<SimEvent> events) {
        for (int level : BaseGeometry.dirtyLevels(events)) {
            rebuild(level);
        }
    }

    /** Moves the lift platforms to their interpolated heights (no carry). */
    public void update(float alpha) {
        update(alpha, MotionCarry.NONE);
    }

    /** Moves the lift platforms to their interpolated heights, the riders drawn with the driver's carry. */
    public void update(float alpha, MotionCarry carry) {
        for (LiftGeometry entry : lifts) {
            float ratio = LiftHeights.ratio(entry.lift(), world, alpha, carry);
            if (ratio != entry.ratio()[0]) {
                entry.ratio()[0] = ratio;
                fill(entry.node(), liftArrays(entry.lift(), ratio));
            }
        }
    }

    private MeshArrays liftArrays(Lift lift, float ratio) {
        boolean boxBelow = BaseGeometry.kind(world, lift.lower().below()) == BaseGeometry.Kind.BOX;
        return BaseGeometry.lift(lift, ratio, boxBelow, colours);
    }

    private void rebuild(int level) {
        if (levels[level] != null) {
            levels[level].removeFromParent();
            levels[level] = null;
        }
        MeshArrays arrays = BaseGeometry.level(world, level, colours);
        if (arrays.isEmpty()) {
            return;
        }
        Node levelNode = new Node("level-" + level);
        fill(levelNode, arrays);
        node.attachChild(levelNode);
        levels[level] = levelNode;
    }

    /** One geometry per quadrant of {@code arrays} below {@code parent}, reusing the geometries already there. */
    private void fill(Node parent, MeshArrays arrays) {
        Map<StaticQuadrant, MeshArrays> parts = StaticAtlas.split(arrays);
        for (StaticQuadrant quadrant : StaticQuadrant.values()) {
            String name = parent.getName() + "-" + quadrant.baseName();
            Geometry geometry = (Geometry) parent.getChild(name);
            MeshArrays part = parts.get(quadrant);
            if (part == null) {
                if (geometry != null) {
                    geometry.removeFromParent();
                }
                continue;
            }
            Mesh mesh = MeshBuilder.triangles(part, StaticAtlas.DETAIL_REPEAT);
            if (geometry == null) {
                geometry = new Geometry(name, mesh);
                geometry.setMaterial(materials.base(quadrant));
                parent.attachChild(geometry);
            } else {
                geometry.setMesh(mesh);
            }
        }
    }
}
