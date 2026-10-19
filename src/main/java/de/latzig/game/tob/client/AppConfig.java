package de.latzig.game.tob.client;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.latzig.game.tob.client.logic.effects.SkyOptions;

/**
 * Start configuration of the game: the assets root (the generated, svn-ignored {@code src/main/resources/assets}
 * during development, the {@code assets} folder next to the game's jars in a package; Phase 7c, {@link AppPaths}),
 * an optional tower id to start directly instead of the start screens (a developer start), the fast-forward switch
 * of the headless smoke test (one simulation frame per render frame, independent of the wall clock), the user data
 * folder (Phase 3c: the recordings; tests always pass a temporary folder; Phase 7c: the system's user folder unless
 * {@value #USERDIR_PROPERTY} is set), whether the developer recording is on at start (Phase 3c rework: off unless
 * {@value #RECORD_PROPERTY} is {@code true}; F12 toggles it in a game; Phase 7c: always off in a package), the sky's
 * options (Phase 4b-3, {@link SkyOptions}; Phase 5b: always the defaults here, the moon detail is the player's option
 * and the look and size overrides are a test seam), the hidden volume override (Phase 5b, {@value #VOLUME_PROPERTY})
 * and an extra tower library for developer starts (Phase 6a, {@value #TOWER_LIBRARY_PROPERTY}).
 */
public record AppConfig(Path assetsRoot, Optional<String> startTower, boolean fastForward, Path userDir,
        boolean record, SkyOptions sky, Optional<Float> volume, Optional<Path> towerLibrary) {

    private static final Logger LOG = LoggerFactory.getLogger(AppConfig.class);

    /**
     * System property for the assets root; default: the {@code assets} folder next to the jars in a package, else
     * {@value #DEFAULT_ASSETS} (Phase 7c decision 5).
     */
    public static final String ASSETS_PROPERTY = "tob.assets";
    /** System property with a tower id (e.g. {@code tutorial-000}) to start without the start screens. */
    public static final String TOWER_PROPERTY = "tob.tower";
    /**
     * System property for the user data folder: {@code mvn exec:java} sets it to the development folder of the POM,
     * tests to a temporary folder; default the system's user folder (Phase 7c decision 1, {@link AppPaths#userDir}).
     */
    public static final String USERDIR_PROPERTY = "tob.userdir";
    /**
     * System property that switches the developer recording on at start (Phase 3c rework, author's decision of
     * 2026-10-09): {@code -Dtob.record=true}. Recordings are a development tool for regression tests; Phase 7c keeps
     * this property and F12 out of the packages: a packaged start ignores both ({@link #record(String, String)},
     * {@code RecordingSwitch.locked()}).
     */
    public static final String RECORD_PROPERTY = "tob.record";
    /**
     * Hidden developer override of the Master volume (Phase 5b decision 3), e.g. {@code -Dtob.volume=4}: the
     * listener's gain, 0 to {@code Settings.VOLUME_OVERRIDE_MAX}; not shown in the Options window.
     */
    public static final String VOLUME_PROPERTY = "tob.volume";
    /**
     * Developer property (Phase 6a): an extra tower library file whose towers {@value #TOWER_PROPERTY} can start, e.g.
     * {@code -Dtob.towerLibrary=src/test/resources/towers/large.json -Dtob.tower=large-000}. Its towers join the
     * catalogue only, never the campaign; the release ships no such library.
     */
    public static final String TOWER_LIBRARY_PROPERTY = "tob.towerLibrary";
    /** The assets root during development without {@value #ASSETS_PROPERTY}, relative to the working directory. */
    public static final String DEFAULT_ASSETS = "src/main/resources/assets";

    public AppConfig {
        Objects.requireNonNull(assetsRoot, "assetsRoot");
        Objects.requireNonNull(startTower, "startTower");
        Objects.requireNonNull(userDir, "userDir");
        Objects.requireNonNull(sky, "sky");
        Objects.requireNonNull(volume, "volume");
        Objects.requireNonNull(towerLibrary, "towerLibrary");
    }

    /** A configuration without an extra tower library. */
    public AppConfig(Path assetsRoot, Optional<String> startTower, boolean fastForward, Path userDir, boolean record,
            SkyOptions sky, Optional<Float> volume) {
        this(assetsRoot, startTower, fastForward, userDir, record, sky, volume, Optional.empty());
    }

    /** A configuration without a volume override. */
    public AppConfig(Path assetsRoot, Optional<String> startTower, boolean fastForward, Path userDir, boolean record,
            SkyOptions sky) {
        this(assetsRoot, startTower, fastForward, userDir, record, sky, Optional.empty());
    }

    /** A configuration with the developer recording off. */
    public AppConfig(Path assetsRoot, Optional<String> startTower, boolean fastForward, Path userDir) {
        this(assetsRoot, startTower, fastForward, userDir, false);
    }

    /** A configuration with the default sky options. */
    public AppConfig(Path assetsRoot, Optional<String> startTower, boolean fastForward, Path userDir,
            boolean record) {
        this(assetsRoot, startTower, fastForward, userDir, record, SkyOptions.DEFAULT);
    }

    public static AppConfig fromSystemProperties() {
        String tower = System.getProperty(TOWER_PROPERTY, "");
        String appPath = System.getProperty(AppPaths.APP_PATH_PROPERTY);
        Path assets = AppPaths.assetsRoot(System.getProperty(ASSETS_PROPERTY), appPath, AppPaths.codeLocation(),
                DEFAULT_ASSETS);
        return new AppConfig(assets, tower.isBlank() ? Optional.empty() : Optional.of(tower), false,
                userDir(System.getProperty(USERDIR_PROPERTY)), record(System.getProperty(RECORD_PROPERTY), appPath),
                SkyOptions.DEFAULT, volume(System.getProperty(VOLUME_PROPERTY)),
                towerLibrary(System.getProperty(TOWER_LIBRARY_PROPERTY)));
    }

    /**
     * Whether the developer recording is on at start: {@code property} ({@value #RECORD_PROPERTY}) is {@code true}
     * and the game was not started from a package ({@code appPath}, {@link AppPaths#APP_PATH_PROPERTY}, unset).
     */
    public static boolean record(String property, String appPath) {
        return Boolean.parseBoolean(property) && !AppPaths.packaged(appPath);
    }

    /** The extra tower library {@code property} ({@value #TOWER_LIBRARY_PROPERTY}); empty if unset or blank. */
    public static Optional<Path> towerLibrary(String property) {
        return property == null || property.isBlank() ? Optional.empty() : Optional.of(Path.of(property.strip()));
    }

    /**
     * The volume override {@code property} ({@value #VOLUME_PROPERTY}); empty if unset, blank or unreadable. M4
     * (final review): a NaN, infinite or negative value is rejected (logged, the Master option applies instead) —
     * {@code Math.clamp(NaN, 0, max)} would otherwise return NaN and the listener's gain with it.
     */
    public static Optional<Float> volume(String property) {
        if (property == null || property.isBlank()) {
            return Optional.empty();
        }
        try {
            float value = Float.parseFloat(property.trim());
            if (!Float.isFinite(value) || value < 0f) {
                LOG.warn("{}={} is not a usable volume (not finite or negative); the Master option applies",
                        VOLUME_PROPERTY, property.trim());
                return Optional.empty();
            }
            return Optional.of(value);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * The user data folder: {@code property} ({@value #USERDIR_PROPERTY}) if set, else the system's user folder of
     * this machine ({@link AppPaths#userDir}).
     */
    public static Path userDir(String property) {
        return AppPaths.userDir(property, System.getProperty("os.name"), System.getenv(),
                Path.of(System.getProperty("user.home")));
    }

    /** The converted tower libraries ({@code original.json}, {@code tutorial.json}, {@code demo.json}). */
    public Path towersDirectory() {
        return assetsRoot.resolve("towers");
    }
}
