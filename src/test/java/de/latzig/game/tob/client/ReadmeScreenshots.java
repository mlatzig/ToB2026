package de.latzig.game.tob.client;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeContext;
import com.jme3.texture.Image;
import com.jme3.util.BufferUtils;

import de.latzig.game.tob.client.logic.CameraPlacement;
import de.latzig.game.tob.client.logic.Pose;
import de.latzig.game.tob.client.logic.editor.TowerDesigner;
import de.latzig.game.tob.client.logic.editor.TowerDesigner.Command;
import de.latzig.game.tob.client.logic.frontend.GameFlow;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Hit;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Screen;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Widget;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.sim.CameraId;
import de.latzig.game.tob.core.sim.TowerAssets;

/**
 * Phase 7d decision 4: the four README screenshots, rendered in jME's offscreen surface (an invisible GLFW window, no
 * visible window) at 1280 x 720 and written as PNG files: original tower 002 "The Sacrifice" from its east camera in
 * the Remake look ({@code game-remake.png}, the large picture) and in the Classic Look ({@code game-classic.png}),
 * the main menu over its demo tower ({@code menu.png}) and the Tower Designer with its 3D preview
 * ({@code designer.png}). A tool, not a test: the pictures are kept in {@code docs/images}, so it runs only when they
 * are to be made again (the README names the command):
 * <pre>
 * mvn test-compile exec:java -Dexec.mainClass=de.latzig.game.tob.client.ReadmeScreenshots
 *     -Dexec.classpathScope=test -Dexec.args=docs/images -Dtob.userdir=target/readme-userdir
 * </pre>
 * It needs a graphics card with OpenGL 3.2 and the generated assets, keeps its profiles and settings in
 * {@code target/readme-userdir} and fails if a picture comes out blank.
 */
public final class ReadmeScreenshots {

    /** The pictures, in the order they are taken. */
    static final List<String> NAMES = List.of("game-remake", "game-classic", "menu", "designer");
    static final int WIDTH = 1280;
    static final int HEIGHT = 720;
    /** The game pictures' tower: "The Sacrifice", the tower of the reference screenshot of the Classic Look. */
    private static final String TOWER = "original-001";
    /** Render frames before a capture: the game's views settle, the menu's demo tower turns into view. */
    private static final int GAME_FRAMES = 6;
    private static final int MENU_FRAMES = 90;
    private static final int DESIGNER_FRAMES = 6;
    /** The user folder of the two runs (profiles, settings), emptied first. */
    private static final String USER_DIR = "target/readme-userdir";

    private ReadmeScreenshots() {
    }

    /** Argument: the folder for the pictures (default {@code docs/images}). */
    public static void main(String[] args) throws Exception {
        Path folder = Path.of(args.length > 0 ? args[0] : "docs/images");
        Path assets = Path.of(System.getProperty(AppConfig.ASSETS_PROPERTY, AppConfig.DEFAULT_ASSETS));
        if (!Files.isRegularFile(assets.resolve("elements.json")) || !TowerAssets.available()) {
            throw new IllegalStateException("the generated assets are missing in " + assets);
        }
        Logger.getLogger("com.jme3").setLevel(Level.SEVERE);
        Files.createDirectories(folder);
        Path userDir = freshUserDir();
        TowerDefinition tower = TowerAssets.allTowers().stream().filter(t -> t.id().equals(TOWER)).findFirst()
                .orElseThrow();
        List<Integer> colours = new ArrayList<>();
        colours.addAll(run(new GameShots(folder, tower), new AppConfig(assets, Optional.of(TOWER), true, userDir)));
        colours.addAll(run(new FrontEndShots(folder), new AppConfig(assets, Optional.empty(), true, userDir)));
        for (int i = 0; i < NAMES.size(); i++) {
            if (colours.get(i) <= 100) {
                throw new IllegalStateException(NAMES.get(i) + " is blank: " + colours.get(i) + " colours");
            }
            System.out.println("[readme] " + folder.resolve(NAMES.get(i) + ".png") + ": " + colours.get(i)
                    + " colours");
        }
    }

    /**
     * An empty user folder in the build folder, {@value #USER_DIR}: never the players' folder, the development
     * folder or the system's temporary folder (in the user profile).
     */
    private static Path freshUserDir() throws IOException {
        Path userDir = Path.of(USER_DIR);
        if (Files.exists(userDir)) {
            try (Stream<Path> paths = Files.walk(userDir)) {
                for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) {
                    Files.delete(path);
                }
            }
        }
        return Files.createDirectories(userDir);
    }

    /** Starts the game offscreen with {@code shots}, waits for them and returns their colour counts. */
    private static List<Integer> run(Shots shots, AppConfig config) throws InterruptedException {
        TobApp app = new TobApp(config, shots);
        AppSettings settings = new AppSettings(true);
        settings.setResolution(WIDTH, HEIGHT);
        settings.setFrameRate(Integer.MAX_VALUE);
        settings.setVSync(false);
        settings.setSamples(4);
        settings.setAudioRenderer(null);
        app.setSettings(settings);
        app.setShowSettings(false);
        app.start(JmeContext.Type.OffscreenSurface);
        boolean finished = shots.done.await(60, TimeUnit.SECONDS);
        app.stop(true);
        if (!finished || shots.failure != null || !app.errors().isEmpty()) {
            throw new IllegalStateException("the pictures failed: finished " + finished + ", " + shots.failure + ", "
                    + app.errors());
        }
        return shots.colours;
    }

    /** Captures the frame buffer as a picture; counts its colours. */
    private abstract static class Shots extends BaseAppState {

        final CountDownLatch done = new CountDownLatch(1);
        final List<Integer> colours = new ArrayList<>();
        final Path folder;
        volatile String failure;

        Shots(Path folder) {
            this.folder = folder;
        }

        @Override
        protected void initialize(Application app) {
        }

        @Override
        protected void cleanup(Application app) {
        }

        @Override
        protected void onEnable() {
        }

        @Override
        protected void onDisable() {
        }

        void capture(String name) {
            int width = getApplication().getCamera().getWidth();
            int height = getApplication().getCamera().getHeight();
            ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
            getApplication().getRenderer().readFrameBufferWithFormat(null, buffer, Image.Format.RGBA8);
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
            try {
                ImageIO.write(image, "png", folder.resolve(name + ".png").toFile());
            } catch (IOException e) {
                failure = e.toString();
                done.countDown();
                return;
            }
            colours.add(seen.size());
        }
    }

    /** The tower from its east camera, first in the Remake look, then in the Classic Look. */
    private static final class GameShots extends Shots {

        private final TowerDefinition tower;
        private int shot;
        private int frames;
        private boolean switching;

        GameShots(Path folder, TowerDefinition tower) {
            super(folder);
            this.tower = tower;
        }

        private boolean classic() {
            return shot == 1;
        }

        @Override
        public void update(float tpf) {
            TobApp app = (TobApp) getApplication();
            GameState game = getState(GameState.class);
            if (done.getCount() == 0 || game == null || game.driver() == null) {
                return;
            }
            if (game.look().classic() != classic()) {
                if (!switching) {
                    // the switched Look applies when the tower starts again (Phase 5e decision 5)
                    app.settings().preview(app.settings().current().withClassicLook(classic()));
                    app.startTower(tower);
                    switching = true;
                }
                return;
            }
            switching = false;
            if (game.sky().moonSize() != null && !game.moonReady()) {
                return;
            }
            frames++;
        }

        /** After the game's camera update: the view of the tower's east camera. */
        @Override
        public void render(RenderManager renderManager) {
            GameState game = getState(GameState.class);
            if (done.getCount() == 0 || game == null || game.driver() == null) {
                return;
            }
            Pose eye = CameraPlacement.of(game.tower()).eye(game.driver().simulation().camera(CameraId.EAST));
            float heading = CameraPlacement.heading(CameraId.EAST);
            Camera cam = getApplication().getCamera();
            cam.setLocation(new Vector3f(eye.x(), eye.y(), eye.z()));
            cam.lookAtDirection(new Vector3f(FastMath.sin(heading), 0f, -FastMath.cos(heading)), Vector3f.UNIT_Y);
        }

        @Override
        public void postRender() {
            if (done.getCount() == 0 || frames != GAME_FRAMES) {
                return;
            }
            capture(NAMES.get(shot));
            frames = 0;
            if (++shot == 2) {
                done.countDown();
            }
        }
    }

    /** The main menu over its demo tower, then the Tower Designer with the first original tower open. */
    private static final class FrontEndShots extends Shots {

        private int shot;
        private int frames = -1;

        FrontEndShots(Path folder) {
            super(folder);
        }

        @Override
        public void update(float tpf) {
            GameFlow flow = ((TobApp) getApplication()).flow();
            if (flow == null || done.getCount() == 0 || flow.fadeAlpha() > 0f) {
                return;
            }
            if (frames >= 0) {
                frames++;
                return;
            }
            if (shot == 0 && flow.screen() == Screen.MENU) {
                frames = 0;
            } else if (shot == 1 && flow.screen() == Screen.MENU) {
                flow.click(Hit.of(Widget.MENU_DESIGNER));
            } else if (shot == 1 && flow.screen() == Screen.EDITOR) {
                TowerDesigner designer = flow.designer();
                designer.run(Command.OPEN);
                flow.click(new Hit(Widget.EDITOR_DIALOG, TowerDesigner.DIALOG_OK));
                frames = 0;
            }
        }

        @Override
        public void postRender() {
            int wanted = shot == 0 ? MENU_FRAMES : DESIGNER_FRAMES;
            if (done.getCount() == 0 || frames < wanted) {
                return;
            }
            capture(NAMES.get(2 + shot));
            frames = -1;
            if (++shot == 2) {
                done.countDown();
            }
        }
    }
}
