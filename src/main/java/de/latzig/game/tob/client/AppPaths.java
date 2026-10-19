package de.latzig.game.tob.client;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Phase 7c decisions 1 and 5: where the game finds its assets and keeps its user data. Pure functions of the values
 * they are given (the system properties, the OS name, the environment, the home folder, the location of the game's
 * code), so the tests never read or write the real user profile; {@link AppConfig#fromSystemProperties} passes the
 * real values.
 */
public final class AppPaths {

    /** Set by the {@code jpackage} launcher to the launcher's path; present only when started from a package. */
    public static final String APP_PATH_PROPERTY = "jpackage.app-path";
    /** The user data folder's name below {@code %APPDATA%} on Windows. */
    public static final String WINDOWS_FOLDER = "ToB2026";
    /** The user data folder's name below the XDG data folder on Linux (and every other system). */
    public static final String XDG_FOLDER = "tob2026";
    /** The assets folder's name next to the game's jars in a package. */
    public static final String PACKAGE_ASSETS = "assets";

    private AppPaths() {
    }

    /**
     * The user data folder (decision 1): {@code property} ({@code -Dtob.userdir}) if set; else on Windows
     * {@code %APPDATA%\ToB2026} ({@code <home>\AppData\Roaming\ToB2026} without {@code APPDATA}); else
     * {@code $XDG_DATA_HOME/tob2026}, or {@code <home>/.local/share/tob2026} if {@code XDG_DATA_HOME} is unset,
     * blank or relative (the XDG base directory specification ignores relative paths).
     *
     * @param property the value of {@code tob.userdir}, null if unset
     * @param osName   the value of {@code os.name}
     * @param env      the environment ({@code System.getenv()})
     * @param home     the user's home folder ({@code user.home})
     */
    public static Path userDir(String property, String osName, Map<String, String> env, Path home) {
        if (property != null && !property.isBlank()) {
            return Path.of(property.strip());
        }
        if (osName != null && osName.startsWith("Windows")) {
            String appData = env.get("APPDATA");
            Path base = appData == null || appData.isBlank() ? home.resolve("AppData").resolve("Roaming")
                    : Path.of(appData.strip());
            return base.resolve(WINDOWS_FOLDER);
        }
        String xdg = env.get("XDG_DATA_HOME");
        Path base = xdg == null || !xdg.strip().startsWith("/") ? home.resolve(".local").resolve("share")
                : Path.of(xdg.strip());
        return base.resolve(XDG_FOLDER);
    }

    /**
     * The assets root (decision 5): {@code property} ({@code -Dtob.assets}) if set; else, when started from a package
     * ({@code appPath} set) and the game's code is a jar, the {@value #PACKAGE_ASSETS} folder next to that jar; else
     * {@code developmentDefault} (relative to the working directory).
     *
     * @param property           the value of {@code tob.assets}, null if unset
     * @param appPath            the value of {@value #APP_PATH_PROPERTY}, null if unset
     * @param codeLocation       the jar or folder holding the game's classes, empty if unknown
     * @param developmentDefault the assets root during development
     */
    public static Path assetsRoot(String property, String appPath, Optional<Path> codeLocation,
            String developmentDefault) {
        if (property != null && !property.isBlank()) {
            return Path.of(property.strip());
        }
        if (packaged(appPath) && codeLocation.isPresent()) {
            Path code = codeLocation.get();
            Path parent = code.getParent();
            if (parent != null && code.getFileName().toString().endsWith(".jar")) {
                return parent.resolve(PACKAGE_ASSETS);
            }
        }
        return Path.of(developmentDefault);
    }

    /** Whether the game was started from a package: {@code appPath} ({@value #APP_PATH_PROPERTY}) is set. */
    public static boolean packaged(String appPath) {
        return appPath != null && !appPath.isBlank();
    }

    /** The jar or folder this class was loaded from; empty if the JVM cannot tell. */
    static Optional<Path> codeLocation() {
        try {
            var source = AppPaths.class.getProtectionDomain().getCodeSource();
            return source == null ? Optional.empty() : Optional.of(Path.of(source.getLocation().toURI()));
        } catch (URISyntaxException | RuntimeException e) {
            return Optional.empty();
        }
    }
}
