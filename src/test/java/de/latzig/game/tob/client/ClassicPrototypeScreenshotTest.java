package de.latzig.game.tob.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.jme3.app.SimpleApplication;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeContext;
import com.jme3.texture.Image;
import com.jme3.util.BufferUtils;

import de.latzig.game.tob.client.logic.ClassicColours;
import de.latzig.game.tob.client.logic.MeshFrame;
import de.latzig.game.tob.client.logic.Pose;
import de.latzig.game.tob.client.logic.SrgbColours;
import de.latzig.game.tob.client.logic.TowerColours;
import de.latzig.game.tob.client.view.AssetLibrary;
import de.latzig.game.tob.client.view.ClassicMaterials;
import de.latzig.game.tob.client.view.ObjectView;
import de.latzig.game.tob.client.view.SceneMaterials;
import de.latzig.game.tob.client.view.SkyView;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.sim.SimObject;
import de.latzig.game.tob.core.sim.SimOptions;
import de.latzig.game.tob.core.sim.TestTowers;
import de.latzig.game.tob.core.sim.TowerAssets;
import de.latzig.game.tob.core.sim.WorldState;

/**
 * Phase 5e Task 1, the prototype (spec §4): a zapper, an enemy zapper, a grabber and a prism, rendered offscreen (an
 * invisible GLFW surface) in the Remake look and the Classic Look (the static classic textures, coloured by the
 * converter in the palette of original tower 001 "The Sacrifice", the reference screenshot's tower; no edge lines
 * since the rework, author 2026-10-16), each object once from the front and once turned by 135°, on a flat ground in
 * that tower's floor colour (entry 16) under the game's sun and ambient light; saved as
 * {@code target/screenshots/phase5e-prototype/<shot>.png}. Task 2 adds the gallery of every object type in the
 * Classic Look. None may be blank.
 */
@Offscreen
class ClassicPrototypeScreenshotTest {

    static final Path SCREENSHOTS = Path.of("target", "screenshots", "phase5e-prototype");
    /**
     * One screenshot: its file name, the look (the Remake look or the Classic Look), whether it shows the gallery of
     * every object instead of the four, and the camera.
     */
    record Shot(String name, boolean classic, boolean gallery, Vector3f eye, Vector3f target) {
    }

    private static final Vector3f ROW_EYE = new Vector3f(1.5f, 1.6f, 4.4f);
    private static final Vector3f ROW_TARGET = new Vector3f(1.5f, 0.2f, -0.55f);
    private static final Vector3f CLOSE_EYE = new Vector3f(1f, 0.75f, 1.45f);
    private static final Vector3f CLOSE_TARGET = new Vector3f(1f, 0.22f, 0f);
    /** Every look from two cameras: all eight objects, and the front row's zapper, enemy zapper and grabber. */
    static final List<Shot> SHOTS = List.of(new Shot("remake-row", false, false, ROW_EYE, ROW_TARGET),
            new Shot("remake-close", false, false, CLOSE_EYE, CLOSE_TARGET),
            new Shot("classic-row", true, false, ROW_EYE, ROW_TARGET),
            new Shot("classic-close", true, false, CLOSE_EYE, CLOSE_TARGET),
            new Shot("classic-gallery", true, true, new Vector3f(2.5f, 4.6f, 5.2f), new Vector3f(2.5f, 0f, -2.6f)));
    private static final List<ObjectType> OBJECTS = List.of(ObjectType.ZAP, ObjectType.FZN, ObjectType.GRB,
            ObjectType.PNE);
    private static final int SETTLE_FRAMES = 3;

    private static final class Prototype extends SimpleApplication {

        private final Path assetsRoot;
        private final TowerDefinition palette;
        private final CountDownLatch done = new CountDownLatch(1);
        private final List<Path> files = new ArrayList<>();
        private final List<Integer> colours = new ArrayList<>();
        private volatile String failure;
        private AssetLibrary assets;
        private Node shown;
        private int shot = -1;
        private int frames;

        Prototype(Path assetsRoot, TowerDefinition palette) {
            super((com.jme3.app.state.AppState[]) null);
            this.assetsRoot = assetsRoot;
            this.palette = palette;
        }

        @Override
        public void simpleInitApp() {
            assets = new AssetLibrary(assetManager, assetsRoot);
            viewPort.setBackgroundColor(colour(TowerColours.rgb(palette.palette().colours().get(7))));
            cam.setFrustumPerspective(30f, (float) cam.getWidth() / cam.getHeight(), 0.05f, 100f);
            rootNode.addLight(new DirectionalLight(SkyView.SUN_DIRECTION.clone(), ColorRGBA.White.clone()));
            rootNode.addLight(new AmbientLight(new ColorRGBA(0.4f, 0.4f, 0.45f, 1f)));
            Geometry ground = new Geometry("ground", new Quad(30f, 30f));
            Material groundMaterial = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
            groundMaterial.setBoolean("UseMaterialColors", true);
            float[] ground16 = SrgbColours.rgb(ClassicColours.of(palette).ground(), true);
            ColorRGBA groundColour = new ColorRGBA(ground16[0], ground16[1], ground16[2], 1f);
            groundMaterial.setColor("Diffuse", groundColour);
            groundMaterial.setColor("Ambient", groundColour);
            ground.setMaterial(groundMaterial);
            ground.setLocalRotation(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X));
            ground.setLocalTranslation(-12f, 0f, 15f);
            rootNode.attachChild(ground);
        }

        /**
         * The objects in a row from the front and, one field behind, a second row turned by 135°; or (Task 2) the
         * gallery: every object type, six per row, turned a little towards the camera.
         */
        private Node scene(SceneMaterials materials, boolean gallery) {
            Node node = new Node("objects");
            if (gallery) {
                TestTowers.Builder builder = TestTowers.tower().level(0, "FFFFFF", "FFFFFF", "FFFFFF", "FFFFFF",
                        "FFFFFF", "FFFFFF");
                ObjectType[] types = ObjectType.values();
                for (int i = 0; i < types.length; i++) {
                    builder.object(i % 6, i / 6, 0, types[i]);
                }
                for (SimObject object : WorldState.load(builder.build(), SimOptions.DEFAULT).objects()) {
                    if (object.cell() == null) {
                        continue;
                    }
                    int index = object.type().ordinal();
                    ObjectView view = new ObjectView(assets, materials, object);
                    view.update(MeshFrame.of(1, 0f), new Pose(index % 6, 0f, -(index / 6)), true);
                    view.node().rotate(0f, 0.6f, 0f);
                    node.attachChild(view.node());
                }
                return node;
            }
            for (int row = 0; row < 2; row++) {
                TestTowers.Builder builder = TestTowers.tower().level(0, "FFFF");
                for (int i = 0; i < OBJECTS.size(); i++) {
                    builder.object(i, 0, 0, OBJECTS.get(i));
                }
                WorldState world = WorldState.load(builder.build(), SimOptions.DEFAULT);
                for (SimObject object : world.objects()) {
                    if (object.cell() == null) {
                        continue;
                    }
                    ObjectView view = new ObjectView(assets, materials, object);
                    view.update(MeshFrame.of(1, 0f), new Pose(0f, 0f, 0f), true);
                    Node holder = new Node("holder");
                    holder.attachChild(view.node());
                    holder.setLocalTranslation(OBJECTS.indexOf(object.type()), 0f, -1.1f * row);
                    holder.setLocalRotation(new Quaternion().fromAngleAxis(0.6f + 2.36f * row, Vector3f.UNIT_Y));
                    node.attachChild(holder);
                }
            }
            return node;
        }

        @Override
        public void simpleUpdate(float tpf) {
            if (done.getCount() == 0) {
                return;
            }
            if (shot < 0 || frames > SETTLE_FRAMES) {
                Shot previous = shot < 0 ? null : SHOTS.get(shot);
                shot++;
                frames = 0;
                if (shot == SHOTS.size()) {
                    shown.removeFromParent();
                    done.countDown();
                    return;
                }
                Shot next = SHOTS.get(shot);
                if (previous == null || previous.classic() != next.classic() || previous.gallery() != next.gallery()) {
                    if (shown != null) {
                        shown.removeFromParent();
                    }
                    SceneMaterials materials = next.classic() ? new ClassicMaterials(assets)
                            : SceneMaterials.remake(assets);
                    shown = scene(materials, next.gallery());
                    rootNode.attachChild(shown);
                }
                cam.setLocation(next.eye());
                cam.lookAt(next.target(), Vector3f.UNIT_Y);
            }
            frames++;
        }

        @Override
        public void simpleRender(com.jme3.renderer.RenderManager rm) {
            if (shot >= 0 && shot < SHOTS.size() && frames == SETTLE_FRAMES) {
                capture(SHOTS.get(shot).name());
            }
        }

        private void capture(String name) {
            int width = cam.getWidth();
            int height = cam.getHeight();
            ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
            renderer.readFrameBufferWithFormat(null, buffer, Image.Format.RGBA8);
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Set<Integer> seen = new HashSet<>();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int i = ((height - 1 - y) * width + x) * 4;
                    int rgb = (buffer.get(i) & 0xFF) << 16 | (buffer.get(i + 1) & 0xFF) << 8 | buffer.get(i + 2) & 0xFF;
                    image.setRGB(x, y, rgb);
                    seen.add(rgb);
                }
            }
            Path file = SCREENSHOTS.resolve(name + ".png");
            try {
                Files.createDirectories(SCREENSHOTS);
                ImageIO.write(image, "png", file.toFile());
            } catch (IOException e) {
                failure = e.toString();
                throw new UncheckedIOException(e);
            }
            files.add(file);
            colours.add(seen.size());
        }

        private static ColorRGBA colour(int rgb) {
            return new ColorRGBA(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, 1f);
        }
    }

    @Test
    void theObjectsAreShotInTheRemakeLookAndTheClassicLook() throws Exception {
        Path assets = Path.of(System.getProperty(AppConfig.ASSETS_PROPERTY, AppConfig.DEFAULT_ASSETS));
        assumeTrue(Files.isRegularFile(assets.resolve("elements.json")) && TowerAssets.available(),
                "generated assets not available");
        Logger.getLogger("com.jme3").setLevel(Level.SEVERE);
        TowerDefinition sacrifice = TowerAssets.allTowers().stream()
                .filter(tower -> tower.id().equals("original-001")).findFirst().orElseThrow();
        Prototype app = new Prototype(assets, sacrifice);
        AppSettings settings = new AppSettings(true);
        settings.setResolution(1280, 720);
        settings.setFrameRate(Integer.MAX_VALUE);
        settings.setVSync(false);
        settings.setSamples(4);
        settings.setAudioRenderer(null);
        app.setSettings(settings);
        app.setShowSettings(false);
        long start = System.nanoTime();

        app.start(JmeContext.Type.OffscreenSurface);
        boolean finished = app.done.await(60, TimeUnit.SECONDS);
        app.stop(true);
        System.out.println("ClassicPrototypeScreenshotTest: " + (System.nanoTime() - start) / 1_000_000
                + " ms; colours " + app.colours + "; " + app.files);

        assertTrue(finished, "every shot within 60 s");
        assertNull(app.failure);
        assertEquals(SHOTS.stream().map(shot -> SCREENSHOTS.resolve(shot.name() + ".png")).toList(), app.files);
        for (int i = 0; i < app.files.size(); i++) {
            assertTrue(app.colours.get(i) > 50, SHOTS.get(i).name() + ": not blank, " + app.colours.get(i)
                    + " colours");
        }
    }
}
