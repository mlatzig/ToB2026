package de.latzig.game.tob.client;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;

import de.latzig.game.tob.client.logic.Motion;
import de.latzig.game.tob.client.logic.Pose;
import de.latzig.game.tob.client.logic.ShadowRange;
import de.latzig.game.tob.client.logic.TowerColours;
import de.latzig.game.tob.client.logic.audio.SoundCue;
import de.latzig.game.tob.client.logic.audio.SoundCues;
import de.latzig.game.tob.client.logic.effects.AmbientTuning;
import de.latzig.game.tob.client.logic.frontend.ChaseCamera;
import de.latzig.game.tob.client.logic.frontend.DemoPlayer;
import de.latzig.game.tob.client.logic.frontend.DemoScripts;
import de.latzig.game.tob.client.logic.frontend.OrbitCamera;
import de.latzig.game.tob.client.logic.frontend.Settings;
import de.latzig.game.tob.client.logic.game.SimDriver;
import de.latzig.game.tob.client.view.AssetLibrary;
import de.latzig.game.tob.client.view.CameraController;
import de.latzig.game.tob.client.view.SceneLook;
import de.latzig.game.tob.client.view.ShadowSetup;
import de.latzig.game.tob.client.view.SkyView;
import de.latzig.game.tob.client.view.SoundView;
import de.latzig.game.tob.client.view.WorldView;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.sim.CameraId;
import de.latzig.game.tob.core.sim.ObjectStatus;
import de.latzig.game.tob.core.sim.SimOptions;
import de.latzig.game.tob.core.sim.Simulation;
import de.latzig.game.tob.core.sim.SpiderId;

/**
 * The live background of the start screens (Phase 5a spec decision 13): the Demo Sequence towers 12-17 in the
 * original's order ({@link DemoScripts#SEQUENCE}), each driven by its original script ({@link DemoPlayer}) with
 * the enemies moving and the sounds on, seen through ToB 2009's slow orbit ({@link OrbitCamera}). The demos run with
 * Backwards Allowed on, the original's default ({@code W0017C8} = 0, {@code S:1027}, {@code S:8628-8629}). The scene
 * is built like the game's ({@code GameState}): world, sky, terrain, the sun with the application's one shadow
 * renderer, the ambient light and the lightning's scene flash; no panel, no HUD, no input.
 * <p>
 * Small extra (author decision A, 2026-10-14): one demo tower per <em>menu visit</em>. At app start and at every
 * return to the menus (after a game, after the editor — each one a new visit, {@link #onEnable()}), the next demo
 * tower of the sequence is loaded ({@link #show()}), while the screen changes anyway. While that visit lasts, a
 * script end (or the attempt's end), plus the existing {@value #LINGER_SECONDS} s tail, no longer advances to the
 * next tower; instead {@link #restart()} resets the simulation and the demo player to the start of the <em>same</em>
 * tower. The scene node, the terrain, the sky and its generated textures (moon, clouds) stay untouched — no asset
 * reload, no sky regeneration. Only the small world view (the object sprites mirroring the simulation's objects) is
 * rebuilt, because it is bound by identity to the old simulation's objects; this is unavoidable but cheap (no asset
 * loading, no texture generation, the already loaded assets are reused).
 * <p>
 * Leaving the menus (detached or disabled while a game runs) releases everything the demo holds: its scene with its
 * lights, the sky (a moon texture generation still running is cancelled), the shadow renderer's viewport slot and
 * every sound. Returning to the menus (attached or enabled again) resumes the sequence with the next demo tower.
 * <p>
 * Robustness (a controller addition, not spec-mandated): a demo that cannot be shown (a missing asset) or that fails
 * while running (an exception in a frame) is logged, released and skipped — the sequence moves on and the next demo
 * is shown within the same {@link #show()} call (a start failure) or on the very next {@link #update(float)} call (a
 * failure mid-frame), instead of ending the application or retrying the broken demo forever. If every demo of the
 * sequence fails to start, {@link #show()} gives up after one full cycle, logs once and leaves the background empty
 * until the menus are entered again. These error paths are unrelated to {@link #restart()} and still advance the
 * sequence as before.
 * <p>
 * Phase 5c-2 (spec decisions 6-8): the interludes. Every frame the state asks its supplier (the app: the flow's
 * {@code interlude()}) whether the result window wants an interlude. While it does, the state shows that demo (0-11)
 * instead of the sequence's: same scene, sky, shadows and release, but the original's chase camera
 * ({@link ChaseCamera}: north, level, driven by the spider and the script's dolly; the view's axis in the band above
 * the result window) instead of the orbit; the script runs once — its end restarts nothing, the spider stands still,
 * the window's button ends the interlude. ToB 2026's simulation still ends a failure demo after its spider died
 * ({@code core.sim} has no switch for the exit check); its objects then stand still while the ambient effects keep
 * running. When the interlude is no longer wanted, the sequence's current tower follows (an interlude does not advance
 * the sequence). An interlude that cannot be shown is logged and released; the background stays empty until the
 * result window is left.
 * <p>
 * Phase 5c-2 decision 5: while the Music switch is on, the demo's sounds (menu and interludes) are muted — the cues are
 * computed, nothing is played and the loops are stopped; the switch is read every frame, and turning it on also stops
 * the one-shots still sounding. A frame in which the state has just been detached (Retry on the result window starts
 * the game) does nothing: no tower is built and the sequence is not advanced.
 */
public final class MenuBackgroundState extends BaseAppState {

    private static final Logger LOG = LoggerFactory.getLogger(MenuBackgroundState.class);

    /** How long a tower keeps running after its script has ended, before {@link #restart()}. */
    public static final double LINGER_SECONDS = 5.0;
    private static final ColorRGBA AMBIENT_COLOUR = new ColorRGBA(0.4f, 0.4f, 0.45f, 1f);

    private final AssetLibrary assets;
    private final List<TowerDefinition> demos;
    private final boolean fastForward;
    /** Phase 5c-2: the interlude the result window wants now, if any (the app: the flow's interlude()). */
    private final Supplier<OptionalInt> interludes;
    /** The interlude shown (or tried), -1 in the menu mode (the Demo Sequence). */
    private int interlude = -1;
    private ChaseCamera chase;
    /** Seconds since an interlude's simulation has ended (the ambient effects' clock goes on). */
    private double afterEnd;
    /** Phase 5c-2 decision 5: the Music switch is on, the demo's sounds are muted. */
    private boolean muted;
    private int sequence;
    private double orbitClock;
    private double lingered = -1.0;
    private Node scene;
    private Simulation simulation;
    private SimDriver driver;
    private DemoPlayer player;
    private WorldView world;
    private SkyView sky;
    /**
     * Phase 5e decision 5: the shown demo's look, taken at its start and kept by a restart; rebuilt in place
     * ({@link #lookChanged()}, author's acceptance tweak 2026-10-16) whenever the live option no longer matches it.
     */
    private SceneLook look;
    private SoundCues cues;
    private SoundView sound;
    private DirectionalLight sun;
    private ColorRGBA sunColour;
    private final ColorRGBA sunShown = new ColorRGBA();
    private AmbientLight ambient;
    private final ColorRGBA ambientShown = new ColorRGBA();
    /** The Ambient effects option at the demo tower's start (Phase 5b). */
    private boolean ambientEffects = true;
    private OrbitCamera orbit;
    private ShadowSetup shadows;
    private int played;
    /** How many times the current demo tower has restarted after its script ended (test seam, diagnostics). */
    private int restarted;
    private int width;
    private int height;
    /** Test seam (package-private): these demo numbers throw instead of starting, as a broken asset would. */
    private Set<Integer> failToStart = Set.of();
    /** Test seam (package-private): the next {@link #frame(float)} call throws once, as a broken frame would. */
    private boolean failNextFrame;

    /**
     * @param demos       the demo library in its order (index = demo number, 18 towers)
     * @param fastForward one simulation frame per render frame (headless tests), as the game's fast-forward
     */
    public MenuBackgroundState(AssetLibrary assets, List<TowerDefinition> demos, boolean fastForward) {
        this(assets, demos, fastForward, OptionalInt::empty);
    }

    /**
     * @param interludes the interlude wanted now (Phase 5c-2: the demo number 0-11 behind the result window), read
     *                   every frame
     */
    public MenuBackgroundState(AssetLibrary assets, List<TowerDefinition> demos, boolean fastForward,
            Supplier<OptionalInt> interludes) {
        this.assets = assets;
        this.demos = List.copyOf(demos);
        this.fastForward = fastForward;
        this.interludes = interludes;
    }

    @Override
    protected void initialize(Application app) {
        shadows = ((TobApp) app).shadows();
    }

    @Override
    protected void cleanup(Application app) {
    }

    @Override
    protected void onEnable() {
        show();
    }

    /**
     * Leaving the menus: the next time they are shown, the next demo tower of the sequence follows (an interlude
     * does not advance the sequence).
     */
    @Override
    protected void onDisable() {
        if (scene != null) {
            stop();
            if (interlude < 0) {
                sequence = (sequence + 1) % DemoScripts.SEQUENCE.size();
            }
        }
    }

    /**
     * Starts the interlude the result window wants, else the current demo of the sequence; a demo of the sequence
     * that cannot be shown is logged and skipped in favour of the next one, tried right away. Gives up after one full
     * cycle of the sequence (every demo tried once) instead of looping forever if every demo fails, logging once and
     * leaving the background empty.
     */
    private void show() {
        OptionalInt wanted = interludes.get();
        if (wanted.isPresent()) {
            showInterlude(wanted.getAsInt());
            return;
        }
        interlude = -1;
        for (int tries = 0; tries < DemoScripts.SEQUENCE.size(); tries++) {
            try {
                start(DemoScripts.SEQUENCE.get(sequence));
                return;
            } catch (RuntimeException e) {
                LOG.error("Menu background: demo {} cannot be shown: {}", demo(), e.getMessage(), e);
                if (scene != null) {
                    // the shadow renderer is attached last, so a failed start never holds it
                    scene.removeFromParent();
                }
                release();
                sequence = (sequence + 1) % DemoScripts.SEQUENCE.size();
            }
        }
        LOG.error("Menu background: no demo of the sequence could be shown, staying empty");
    }

    /** Phase 5c-2: starts interlude {@code demo}; one that cannot be shown leaves the background empty. */
    private void showInterlude(int demo) {
        interlude = demo;
        try {
            start(demo);
        } catch (RuntimeException e) {
            LOG.error("Interlude: demo {} cannot be shown: {}", demo, e.getMessage(), e);
            if (scene != null) {
                scene.removeFromParent();
            }
            release();
        }
    }

    private void start(int demo) {
        if (failToStart.contains(demo)) {
            // test seam: simulates a broken demo (missing asset, bad data) without needing one
            throw new IllegalStateException("test seam: demo " + demo + " is set to fail to start");
        }
        Application app = getApplication();
        TowerDefinition tower = demos.get(demo);
        Settings options = ((TobApp) app).settings().current();
        // Phase 5e decision 5: the Look applies from the next demo tower on (a restart keeps this one's); the
        // author's acceptance tweak (2026-10-16) also rebuilds the current demo in place via stop()+show() when
        // lookChanged() fires, so this read always picks up the live (previewed) option

        look = SceneLook.of(assets, tower, options.classicLook());
        look.applyFog(assets);
        TowerColours colours = look.colours();
        simulation = Simulation.start(tower, new SimOptions(true));
        player = new DemoPlayer(DemoScripts.script(demo));
        ambientEffects = options.ambientEffects();
        world = look.world(assets, simulation.world(), ambientEffects);
        sky = look.sky(assets, tower, ((TobApp) app).config().sky().withHalfMoonDetail(options.halfMoonDetail()),
                Optional.empty());
        Node terrain = look.ground(assets, tower);
        cues = new SoundCues(simulation.world(), assets.soundBank(), tower.sounds(), options.soundDetail(),
                tower.id().hashCode());
        sound = new SoundView(app.getAssetManager(), app.getAudioRenderer(), app.getListener(), assets.soundBank());
        sound.preload();
        chase = new ChaseCamera();
        afterEnd = 0.0;
        driver = newDriver();
        scene = new Node("menu-background");
        scene.attachChild(terrain);
        scene.attachChild(world.node());
        sunColour = color(look.sun());
        sun = new DirectionalLight(SkyView.SUN_DIRECTION.clone(), sunColour.clone());
        ambient = new AmbientLight(AMBIENT_COLOUR.clone());
        scene.addLight(sun);
        scene.addLight(ambient);
        orbit = OrbitCamera.of(tower);
        lingered = -1.0;
        restarted = 0;
        app.getViewPort().setBackgroundColor(color(colours.skyHorizon()));
        ((TobApp) app).getRootNode().attachChild(scene);
        ((TobApp) app).getRootNode().attachChild(sky.node());
        shadows.setSun(sun, ShadowRange.distance(tower));
        shadows.attach(app.getViewPort());
        width = 0;
        height = 0;
        played++;
        LOG.info("{}: demo {} {}", interlude >= 0 ? "Interlude" : "Menu background", demo, tower.title());
    }

    /**
     * The driver of the current simulation and demo player: every step feeds the demo player, the chase camera's
     * dolly (the script byte read last), the world view and the sounds (none while muted, decision 5).
     */
    private SimDriver newDriver() {
        return new SimDriver(simulation, player, (events, tick) -> {
            player.afterStep(events);
            chase.step(player.lastByte());
            world.onStep(events, tick);
            List<SoundCue.OneShot> step = cues.onStep(events);
            if (!muted) {
                sound.play(step);
            }
        });
    }

    private void stop() {
        if (scene == null) {
            return;
        }
        scene.removeFromParent();
        shadows.detach(getApplication().getViewPort());
        release();
    }

    /** Removes the sky, cancels its generator, stops every sound and drops the demo's references. */
    private void release() {
        if (sky != null) {
            sky.node().removeFromParent();
            sky.dispose();
        }
        if (sound != null) {
            sound.stopAll();
        }
        scene = null;
        sky = null;
        sound = null;
        cues = null;
        world = null;
        driver = null;
        player = null;
        simulation = null;
        sun = null;
        ambient = null;
        look = null;
    }

    @Override
    public void update(float tpf) {
        if (!getStateManager().hasState(this)) {
            // detached this frame (e.g. Retry on the result window starts the game): jME still updates the state
            // once from its snapshot; build no tower and leave the sequence alone, onDisable follows
            return;
        }
        if (interludes.get().orElse(-1) != interlude) {
            // Phase 5c-2: the result window came or went (the screen is black between the fades)
            stop();
            show();
        }
        if (scene != null && look != null && lookChanged()) {
            // author's acceptance tweak (2026-10-16): Apply in Options shows the new Look at once, no game start
            // needed; the same rebuild undoes a Back/Esc that reverted the preview, because this compares the
            // background's built look against the live option every frame, not just once after a change
            stop();
            show();
        }
        if (scene == null) {
            return;
        }
        try {
            frame(tpf);
        } catch (RuntimeException e) {
            LOG.error("{}: demo {} failed: {}", interlude >= 0 ? "Interlude" : "Menu background", demo(),
                    e.getMessage(), e);
            stop();
            if (interlude >= 0) {
                // an interlude stays empty until the result window is left
                return;
            }
            sequence = (sequence + 1) % DemoScripts.SEQUENCE.size();
            // release the broken demo and move on to the next one of the sequence, at the latest this same frame
            show();
        }
    }

    private void frame(float tpf) {
        if (failNextFrame) {
            // test seam: simulates a broken frame (a programming error) without needing one
            failNextFrame = false;
            throw new IllegalStateException("test seam: demo " + demo() + " is set to fail mid-frame");
        }
        double seconds = fastForward ? SimDriver.FRAME_SECONDS : tpf;
        // Phase 5b decision 6: live while the Options window previews brightness and flash strength; Phase 5c-2
        // decision 5: the Music switch mutes the demo's sounds at once
        Settings options = ((TobApp) getApplication()).settings().current();
        boolean music = options.music();
        if (music && !muted) {
            // the switch was turned on: also stop the one-shots still sounding (the loops stop below as well)
            sound.stopAll();
        }
        muted = music;
        Camera cam = getApplication().getCamera();
        if (cam.getWidth() > 0 && cam.getHeight() > 0 && (cam.getWidth() != width || cam.getHeight() != height)) {
            width = cam.getWidth();
            height = cam.getHeight();
            if (interlude >= 0) {
                float[] f = ChaseCamera.frustum(CameraController.FIELD_OF_VIEW_DEGREES, (float) width / height,
                        CameraController.NEAR);
                cam.setFrustum(CameraController.NEAR, CameraController.FAR, f[0], f[1], f[2], f[3]);
            } else {
                cam.setFrustumPerspective(CameraController.FIELD_OF_VIEW_DEGREES, (float) width / height,
                        CameraController.NEAR, CameraController.FAR);
            }
        }
        driver.update(seconds);
        float alpha = driver.alpha();
        if (interlude >= 0) {
            // decision 7: the script runs once, nothing restarts; decision 8: the chase camera
            if (simulation.isFinished()) {
                afterEnd += seconds;
            }
            SpiderId spider = player.spider() != null ? player.spider() : firstSpider();
            if (spider != null) {
                Pose drawn = Motion.position(simulation.world().spider(spider), simulation.world().isFrozen(), alpha,
                        driver.carry());
                Pose eye = chase.eye(drawn, alpha);
                cam.setLocation(new Vector3f(eye.x(), eye.y(), eye.z()));
                cam.lookAtDirection(new Vector3f(ChaseCamera.DIRECTION.x(), ChaseCamera.DIRECTION.y(),
                        ChaseCamera.DIRECTION.z()), Vector3f.UNIT_Y);
            }
        } else {
            if (lingered < 0.0 && (player.isFinished() || simulation.isFinished())) {
                lingered = 0.0;
            } else if (lingered >= 0.0) {
                lingered += seconds;
                if (lingered >= LINGER_SECONDS) {
                    restart();
                    return;
                }
            }
            orbitClock += seconds;
            Pose eye = orbit.eye(orbitClock);
            Pose target = orbit.target();
            cam.setLocation(new Vector3f(eye.x(), eye.y(), eye.z()));
            cam.lookAt(new Vector3f(target.x(), target.y(), target.z()), Vector3f.UNIT_Y);
        }
        world.update(alpha, CameraId.NORTH, driver.carry());
        float now = driver.ticks() + alpha + (float) (afterEnd / SimDriver.FRAME_SECONDS);
        world.updateEffects(now, cam.getLeft(), cam.getUp());
        List<SoundCue.OneShot> weather = cues.onAmbient(world.newStrikes(), world.newImpacts(), now);
        if (!muted) {
            sound.play(weather);
        }
        float bright = options.brightnessFactor();
        float flash = AmbientTuning.sceneFlash(world.flash(now), options.flashFactor());
        sun.setColor(sunShown.set(AmbientTuning.light(sunColour.r, bright, flash),
                AmbientTuning.light(sunColour.g, bright, flash), AmbientTuning.light(sunColour.b, bright, flash),
                sunColour.a));
        ambient.setColor(ambientShown.set(AmbientTuning.light(AMBIENT_COLOUR.r, bright, flash),
                AmbientTuning.light(AMBIENT_COLOUR.g, bright, flash),
                AmbientTuning.light(AMBIENT_COLOUR.b, bright, flash), AMBIENT_COLOUR.a));
        sky.update(cam.getLocation(), now, ambientEffects);
        sound.backgroundVolume(options.backgroundScale());
        List<SoundCue.Loop> loops = cues.loops(alpha, false);
        sound.update(muted ? List.of() : loops, false, cam);
    }

    /** The demo's spider before the demo player has seen it: the first present one, as the decoder chooses. */
    private SpiderId firstSpider() {
        for (SpiderId id : SpiderId.values()) {
            if (simulation.world().spider(id).status() != ObjectStatus.ABSENT) {
                return id;
            }
        }
        return null;
    }

    /**
     * Small extra (author decision A, 2026-10-14): a script end (plus the {@value #LINGER_SECONDS} s tail) restarts
     * the <em>same</em> demo tower instead of advancing to the next one — {@link #sequence} is untouched. The
     * simulation and the demo player go back to the start; the scene node, the terrain, the sky and its generated
     * textures stay as they are (no reload, no sky regeneration). Rebuilding the world view is unavoidable (its
     * object sprites are bound by identity to the old simulation's objects) but cheap: no asset loading, no texture
     * generation, only the already loaded assets and the unchanged tower colours are used again.
     */
    private void restart() {
        TobApp app = (TobApp) getApplication();
        TowerDefinition tower = demos.get(demo());
        Node oldWorldNode = world.node();
        simulation = Simulation.start(tower, new SimOptions(true));
        player = new DemoPlayer(DemoScripts.script(demo()));
        Settings options = app.settings().current();
        ambientEffects = options.ambientEffects();
        world = look.world(assets, simulation.world(), ambientEffects);
        cues = new SoundCues(simulation.world(), assets.soundBank(), tower.sounds(), options.soundDetail(),
                tower.id().hashCode());
        chase = new ChaseCamera();
        driver = newDriver();
        scene.detachChild(oldWorldNode);
        scene.attachChild(world.node());
        lingered = -1.0;
        restarted++;
        LOG.info("Menu background: demo {} restarted", demo());
    }

    /**
     * Whether the shown background's built Look no longer matches the live (previewed) option — Apply just changed
     * it, or Back/Esc just reverted a preview that had already rebuilt the background (author's acceptance tweak,
     * 2026-10-16). Checked every frame while the menus are shown, so a Back/Esc is caught the same way as an Apply.
     */
    private boolean lookChanged() {
        Settings options = ((TobApp) getApplication()).settings().current();
        return options.classicLook() != look.classic();
    }

    /** The demo number shown now: the interlude's, else the sequence's (tests). */
    public int demo() {
        return interlude >= 0 ? interlude : DemoScripts.SEQUENCE.get(sequence);
    }

    /** The interlude shown (Phase 5c-2), -1 while the Demo Sequence is shown (tests, diagnostics). */
    public int interlude() {
        return interlude;
    }

    /** The current demo's sounds, or null while hidden (tests: decision 5's muting). */
    SoundView sound() {
        return sound;
    }

    /** How many demo towers have been started so far (tests). */
    public int played() {
        return played;
    }

    /** The current demo's player, or null while hidden (tests). */
    public DemoPlayer player() {
        return scene == null ? null : player;
    }

    /** The current demo's simulation, or null while hidden (tests). */
    public Simulation simulation() {
        return simulation;
    }

    /**
     * How many times the current demo tower has restarted after its script ended (small extra, author decision A,
     * 2026-10-14; tests, diagnostics). Reset to 0 only by a new tower load ({@link #show()}), never by a restart.
     */
    public int restarted() {
        return restarted;
    }

    /** The shown demo's look, kept across a script-end restart (tests, Phase 5e). */
    SceneLook look() {
        return look;
    }

    /** The current sky, kept (not regenerated) across a script-end restart; null while hidden (tests). */
    SkyView sky() {
        return sky;
    }

    /** Test seam: these demo numbers fail to start from now on, as a broken asset would (error-path tests). */
    void failDemosToStart(Set<Integer> demoNumbers) {
        this.failToStart = Set.copyOf(demoNumbers);
    }

    /** Test seam: the next frame of the currently shown demo throws, as a broken frame would (error-path tests). */
    void failNextFrame() {
        this.failNextFrame = true;
    }

    private static ColorRGBA color(int rgb) {
        return new ColorRGBA(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, 1f);
    }
}
