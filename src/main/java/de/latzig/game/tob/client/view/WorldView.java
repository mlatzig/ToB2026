package de.latzig.game.tob.client.view;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;
import com.jme3.scene.Node;

import de.latzig.game.tob.client.logic.CellColours;
import de.latzig.game.tob.client.logic.FloorArea;
import de.latzig.game.tob.client.logic.MeshFrame;
import de.latzig.game.tob.client.logic.Motion;
import de.latzig.game.tob.client.logic.MotionCarry;
import de.latzig.game.tob.client.logic.effects.ExplosionTrigger;
import de.latzig.game.tob.client.logic.effects.Lightning;
import de.latzig.game.tob.client.logic.effects.Meteors;
import de.latzig.game.tob.client.logic.game.AnimationState;
import de.latzig.game.tob.core.model.Effects;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.sim.CellPos;
import de.latzig.game.tob.core.sim.SimEvent;
import de.latzig.game.tob.core.sim.SimObject;
import de.latzig.game.tob.core.sim.SpiderId;
import de.latzig.game.tob.core.sim.Viewpoint;
import de.latzig.game.tob.core.sim.WorldState;

/**
 * The simulated world in the scene: base geometry, one {@link ObjectView} per object and the explosions. Rays are not
 * drawn (Phase 4a spec §2 decision 8: hits show only through their results). The views only read the state; the
 * events of each step go through the {@link FloorArea} filter first. An object is hidden once it is no longer alive
 * and while it owns the spider camera. Phase 4b-1: a destroyed object breaks apart where it stands, from the frame it
 * showed last ({@link ExplosionTrigger}: once per object), an eaten floor in its own colour; the explosions run on
 * the simulation time {@link #updateEffects} receives. Phase 4b-2: the tower's ambient effects, only where its flag
 * is set and seeded with the tower id: lightning ({@link LightningView}), meteors ({@link MeteorView}, striking the
 * terrain around the tower only) and the ground haze ({@link HazeView}), on the same simulation time. Phase 5e: the
 * base geometry, the objects, their debris and the haze take their materials from the scene's
 * {@link SceneMaterials} (the Remake or the Classic Look, chosen when the scene is built).
 */
public final class WorldView {

    /** An object's view and the frame it showed last while it was alive (the debris starts from it). */
    private static final class Entry {

        private final SimObject object;
        private final ObjectView view;
        private MeshFrame shown;

        Entry(SimObject object, ObjectView view) {
            this.object = object;
            this.view = view;
        }
    }

    private final Node node = new Node("world");
    private final WorldState world;
    private final CellColours colours;
    private final AnimationState animation;
    private final FloorArea area;
    private final BaseView base;
    private final ExplosionView explosions;
    private final ExplosionTrigger trigger = new ExplosionTrigger();
    private final List<Entry> objects = new ArrayList<>();
    private final Map<Integer, Entry> bySlot = new HashMap<>();
    private final LightningView lightning;
    private final MeteorView meteors;
    private final HazeView haze;
    /** Phase 5b: the Ambient effects option at the tower's start; off, the haze does not pulse. */
    private final boolean ambientEffects;

    /** @throws AssetLoadException if an asset of the tower's elements is missing or unreadable */
    public WorldView(AssetLibrary assets, WorldState world, CellColours colours) {
        this(assets, world, colours, true);
    }

    /**
     * @param ambientEffects the Ambient effects option (Phase 5b): off, the tower's lightning and meteors are left
     *                       out and its ground haze does not pulse
     * @throws AssetLoadException if an asset of the tower's elements is missing or unreadable
     */
    public WorldView(AssetLibrary assets, WorldState world, CellColours colours, boolean ambientEffects) {
        this(assets, world, colours, ambientEffects, SceneMaterials.remake(assets), Meteors.ground(world.tower()));
    }

    /**
     * Phase 5e: the world in {@code colours} on the scene's {@code materials} (the Remake or the Classic Look), the
     * meteors striking {@code meteorGround} (the terrain, or the Classic Look's flat ground).
     *
     * @throws AssetLoadException if an asset of the tower's elements is missing or unreadable
     */
    public WorldView(AssetLibrary assets, WorldState world, CellColours colours, boolean ambientEffects,
            SceneMaterials materials, Meteors.Ground meteorGround) {
        this.world = world;
        this.ambientEffects = ambientEffects;
        this.colours = colours;
        this.animation = new AnimationState(world);
        this.area = FloorArea.of(world.tower());
        for (SimObject object : world.objects()) {
            if (object.cell() != null) {
                assets.preload(object.type());
            }
        }
        this.base = new BaseView(world, colours, materials);
        node.attachChild(base.node());
        node.setShadowMode(ShadowMode.CastAndReceive);
        for (SimObject object : world.objects()) {
            if (object.cell() != null) {
                ObjectView view = new ObjectView(assets, materials, object);
                node.attachChild(view.node());
                Entry entry = new Entry(object, view);
                objects.add(entry);
                bySlot.put(object.slot(), entry);
            }
        }
        long seed = world.tower().id().hashCode();
        this.explosions = new ExplosionView(assets, seed, materials);
        node.attachChild(explosions.node());
        Effects effects = world.tower().effects();
        this.lightning = effects.lightning() && ambientEffects ? new LightningView(assets, explosions, seed) : null;
        if (lightning != null) {
            node.attachChild(lightning.node());
        }
        this.meteors = effects.meteor() && ambientEffects
                ? new MeteorView(assets, explosions, seed, meteorGround)
                : null;
        if (meteors != null) {
            node.attachChild(meteors.node());
        }
        this.haze = effects.bluePulse() ? new HazeView(assets, materials.haze()) : null;
        if (haze != null) {
            node.attachChild(haze.geometry());
        }
        update(0f, SpiderId.ZAPPER);
    }

    public Node node() {
        return node;
    }

    public AnimationState animation() {
        return animation;
    }

    /**
     * The step's events: animations, floors, and the explosions they start at simulation time {@code tick}. Called
     * exactly once per simulation step, after it.
     */
    public void onStep(List<SimEvent> events, int tick) {
        List<SimEvent> filtered = area.filter(events);
        animation.onStep(filtered, tick);
        base.onStep(filtered);
        for (ExplosionTrigger.Blast blast : trigger.onStep(filtered)) {
            switch (blast) {
                case ExplosionTrigger.ObjectBlast object -> {
                    Entry entry = bySlot.get(object.slot());
                    if (entry != null && entry.shown != null) {
                        explosions.explode(entry.object.type(), entry.shown,
                                Motion.position(entry.object, world.isFrozen(), 0f), tick);
                    }
                }
                case ExplosionTrigger.FloorBlast floor -> {
                    CellPos cell = floor.cell();
                    explosions.explodeFloor(cell, colours.top(cell.x(), cell.y(), cell.level()), colours.linear(),
                            tick);
                }
            }
        }
    }

    /** Positions and animates everything at {@code alpha} of the next frame (no carry). */
    public void update(float alpha, Viewpoint selected) {
        update(alpha, selected, MotionCarry.NONE);
    }

    /**
     * Positions and animates everything at {@code alpha} of the next frame with the driver's {@link MotionCarry};
     * the selected spider is hidden.
     */
    public void update(float alpha, Viewpoint selected, MotionCarry carry) {
        boolean frozen = world.isFrozen();
        base.update(alpha, carry);
        for (Entry entry : objects) {
            SimObject object = entry.object;
            boolean cameraOwner = selected instanceof SpiderId spider && spider.slot() == object.slot();
            boolean visible = object.isAlive() && !cameraOwner;
            MeshFrame frame = animation.frame(object, alpha, carry);
            if (object.isAlive()) {
                entry.shown = frame;
            }
            entry.view.update(frame, Motion.position(object, frozen, alpha, carry), visible);
            if (visible && object.type() == ObjectType.GRB) {
                entry.view.updateCarried(animation.carriedKlondikeKeys(alpha));
            }
        }
    }

    /**
     * Plays the ambient effects and the explosions at simulation time {@code now} (driver steps + alpha, in frames),
     * the sprites and bolts facing the camera.
     */
    public void updateEffects(float now, Vector3f cameraLeft, Vector3f cameraUp) {
        if (lightning != null) {
            lightning.update(now, cameraLeft);
        }
        if (meteors != null) {
            meteors.update(now, cameraLeft, cameraUp);
        }
        if (haze != null) {
            // Phase 5b: without the ambient effects the haze keeps ToB 2005's static brightness (time 0: no pulse)
            haze.update(ambientEffects ? now : 0f);
        }
        explosions.update(now, cameraLeft, cameraUp);
    }

    /** The lightning's flash curve at simulation time {@code now} (0..1; 0 without the lightning flag). */
    public float flash(float now) {
        return lightning == null ? 0f : lightning.flash(now);
    }

    /** The lightning strikes of the last {@link #updateEffects} (Phase 4c: their sounds); none without the flag. */
    public List<Lightning.Strike> newStrikes() {
        return lightning == null ? List.of() : lightning.newStrikes();
    }

    /** The meteor impacts of the last {@link #updateEffects} (Phase 4c: their sounds); none without the flag. */
    public List<Meteors.Impact> newImpacts() {
        return meteors == null ? List.of() : meteors.newImpacts();
    }

    /** The lightning strikes so far, -1 without the lightning flag (tests). */
    public int lightningStrikes() {
        return lightning == null ? -1 : lightning.strikes();
    }

    /** The meteor impacts so far, -1 without the meteor flag (tests). */
    public int meteorImpacts() {
        return meteors == null ? -1 : meteors.impacts();
    }

    /** Whether the ground haze is shown (tests). */
    public boolean hasHaze() {
        return haze != null;
    }

    /** The explosions running (tests). */
    public int activeExplosions() {
        return explosions.active();
    }
}
