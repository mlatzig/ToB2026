package de.latzig.game.tob.client.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.FloatBuffer;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial.CullHint;
import com.jme3.scene.VertexBuffer.Type;

import de.latzig.game.tob.client.logic.MeshFrame;
import de.latzig.game.tob.client.logic.Pose;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.sim.SimObject;
import de.latzig.game.tob.core.sim.SimOptions;
import de.latzig.game.tob.core.sim.Simulation;
import de.latzig.game.tob.core.sim.SpiderId;
import de.latzig.game.tob.core.sim.TestTowers;
import de.latzig.game.tob.core.sim.WorldState;

class ObjectViewTest {

    @Test
    void showsOnlyTheSelectedGroupAtThePoseAndOneCarriedKlondikePerKey() {
        assumeTrue(GeneratedAssets.available(), "generated assets not available");
        AssetLibrary assets = new AssetLibrary(GeneratedAssets.assetManager(), GeneratedAssets.root());
        WorldState world = Simulation.start(TestTowers.tower().level(0, "FF").object(0, 0, 0, ObjectType.GRB)
                .build(), SimOptions.DEFAULT).world();
        ObjectView view = new ObjectView(assets, world.spider(SpiderId.GRABBER));

        view.update(MeshFrame.of(2, 16f), new Pose(4f, 0.25f, 11f), true);

        assertEquals(2, view.shownGroup());
        assertEquals(new Vector3f(4f, 0.25f, 11f), view.node().getLocalTranslation());
        assertEquals(CullHint.Always, view.node().getChild("group-1").getCullHint());
        assertEquals(CullHint.Dynamic, view.node().getChild("group-2").getCullHint());
        view.updateCarried(List.of(170f, 32f));
        assertEquals(2, ((Node) view.node().getChild("carried")).getQuantity());
        view.updateCarried(List.of(170f));
        assertEquals(CullHint.Always, ((Node) view.node().getChild("klondike-1")).getChild(0).getCullHint());
        view.update(MeshFrame.of(1, 0f), new Pose(0f, 0f, 0f), false);
        assertEquals(CullHint.Always, view.node().getCullHint());
    }

    /**
     * Fix round 1 review finding: {@link WorldView} sets {@code CastAndReceive} on the whole world node, but a
     * blended part must not cast an opaque shadow of its see-through shape. The converter's unlit beam effect
     * ({@code CVN} group 1 part 2: {@code UNLIT} and {@code GLASS}) takes part in no shadow at all; the reflector's
     * real glass ({@code REL} group 1 part 2: {@code LIT} and {@code GLASS}) still receives the world's shadows, it
     * just does not cast one of its own.
     * <p>
     * Fix round 2 re-review finding: {@link com.jme3.scene.Spatial#getShadowMode()} resolves an unset
     * ({@code Inherit}) local mode by walking up to the parent, but a node with no parent at all (as in round 1's
     * test) resolves straight to {@code Off} — the same value the UNLIT branch is supposed to produce, so a
     * regression of that branch back to {@code Inherit} would have gone undetected. Attaching both views under a
     * node set to {@code CastAndReceive}, exactly as {@link WorldView} does with the real scene (Task 10), makes
     * the resolved modes meaningful: only an explicit local override (the fix) can still read {@code Off} or
     * {@code Receive} under a {@code CastAndReceive} parent; an unset part (opaque, {@code Inherit}) must resolve
     * to the parent's {@code CastAndReceive}, which the test also checks (the converter's own opaque part, CVN.1.1).
     */
    @Test
    void unlitEffectPartsCastNoShadowAndGlassPartsOnlyReceive() {
        assumeTrue(GeneratedAssets.available(), "generated assets not available");
        AssetLibrary assets = new AssetLibrary(GeneratedAssets.assetManager(), GeneratedAssets.root());
        WorldState world = WorldState.load(TestTowers.tower().level(0, "FFF").object(0, 0, 0, ObjectType.CVN)
                .object(1, 0, 0, ObjectType.REL).build(), SimOptions.DEFAULT);
        SimObject converter = world.objects().stream().filter(o -> o.type() == ObjectType.CVN).findFirst()
                .orElseThrow();
        SimObject reflector = world.objects().stream().filter(o -> o.type() == ObjectType.REL).findFirst()
                .orElseThrow();

        ObjectView converterView = new ObjectView(assets, converter);
        ObjectView reflectorView = new ObjectView(assets, reflector);
        // Mirrors WorldView.node()'s own CastAndReceive (Task 10): only attached under it do resolved shadow
        // modes reflect the real scene instead of an unattached node's Inherit-resolves-to-Off default.
        Node worldNode = new Node("world");
        worldNode.setShadowMode(ShadowMode.CastAndReceive);
        worldNode.attachChild(converterView.node());
        worldNode.attachChild(reflectorView.node());

        assertEquals(ShadowMode.CastAndReceive, part(converterView, 1, 1).getShadowMode(),
                "an opaque part (CVN.1.1) inherits the world's CastAndReceive");
        assertEquals(ShadowMode.Off, part(converterView, 1, 2).getShadowMode(),
                "the unlit beam effect (CVN.1.2) takes part in no shadow even under a CastAndReceive parent");
        assertEquals(ShadowMode.Receive, part(reflectorView, 1, 2).getShadowMode(),
                "the glass (REL.1.2) receives but does not cast a shadow");
    }

    /**
     * Phase 4b-4 decision 4: the enemy zapper's needle (part 2, vertex colours pulsing between 0x404040 and white) is
     * drawn with its colours averaged with the white object colour, so even its darkest phase is 0x9F9F9F; its
     * material colour is white, so the tint counts once.
     */
    @Test
    void theEnemyZapperNeedleIsDrawnWithAveragedVertexColours() {
        assumeTrue(GeneratedAssets.available(), "generated assets not available");
        AssetLibrary assets = new AssetLibrary(GeneratedAssets.assetManager(), GeneratedAssets.root());
        WorldState world = WorldState.load(TestTowers.tower().level(0, "FF").object(1, 0, 0, ObjectType.FZN).build(),
                SimOptions.DEFAULT);
        SimObject zapper = world.objects().stream().filter(o -> o.type() == ObjectType.FZN).findFirst()
                .orElseThrow();
        ObjectView view = new ObjectView(assets, zapper);
        Geometry needle = (Geometry) part(view, 1, 2);
        float darkest = 1f;

        for (float key = 0f; key < 13 * 16f; key += 4f) {
            view.update(MeshFrame.of(1, key), new Pose(1f, 0f, 0f), true);
            FloatBuffer colours = needle.getMesh().getFloatBuffer(Type.Color);
            for (int i = 0; i < colours.limit(); i++) {
                if (i % 4 != 3) {
                    darkest = Math.min(darkest, colours.get(i));
                }
            }
        }

        assertEquals(0x9F / 255f, darkest, 1e-4f, "(0x40 + 0xFF) / 2 in the darkest phase");
        assertEquals(AssetLibrary.color(0xFFFFFF), needle.getMaterial().getParamValue("Diffuse"));
    }

    /** The geometry of part {@code partNumber} (1-based) of group {@code groupId} of {@code view}. */
    private static com.jme3.scene.Spatial part(ObjectView view, int groupId, int partNumber) {
        Node group = (Node) view.node().getChild("group-" + groupId);
        return group.getChild("group-" + groupId + "-part-" + partNumber);
    }
}
