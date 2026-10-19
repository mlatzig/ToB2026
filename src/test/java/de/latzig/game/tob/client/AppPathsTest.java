package de.latzig.game.tob.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Phase 7c decisions 1 and 5: the user data folder and the assets root, from injected values only - no test reads
 * the real environment or touches the user profile.
 */
class AppPathsTest {

    private static final String WINDOWS = "Windows 10";
    private static final String LINUX = "Linux";
    private static final Path WINDOWS_HOME = Path.of("C:\\Users\\anna");
    private static final Path LINUX_HOME = Path.of("/home/anna");
    private static final String DEV = AppConfig.DEFAULT_ASSETS;
    private static final String APP_PATH = "C:\\Games\\ToB2026\\ToB2026.exe";

    @Test
    void tobUserdirWinsOnEverySystem() {
        Map<String, String> env = Map.of("APPDATA", "C:\\Users\\anna\\AppData\\Roaming", "XDG_DATA_HOME", "/data");
        assertEquals(Path.of("D:/dev/tob-user"), AppPaths.userDir("D:/dev/tob-user", WINDOWS, env, WINDOWS_HOME));
        assertEquals(Path.of("/srv/tob"), AppPaths.userDir(" /srv/tob ", LINUX, env, LINUX_HOME));
    }

    @Test
    void windowsKeepsTheUserDataInAppData() {
        Map<String, String> env = Map.of("APPDATA", "C:\\Users\\anna\\AppData\\Roaming");
        assertEquals(Path.of("C:\\Users\\anna\\AppData\\Roaming", "ToB2026"),
                AppPaths.userDir(null, WINDOWS, env, WINDOWS_HOME));
        assertEquals(Path.of("C:\\Users\\anna\\AppData\\Roaming", "ToB2026"),
                AppPaths.userDir(" ", "Windows 11", env, WINDOWS_HOME), "a blank tob.userdir counts as unset");
    }

    @Test
    void windowsWithoutAppDataUsesRoamingBelowTheHome() {
        Path expected = WINDOWS_HOME.resolve("AppData").resolve("Roaming").resolve("ToB2026");
        assertEquals(expected, AppPaths.userDir(null, WINDOWS, Map.of(), WINDOWS_HOME));
        assertEquals(expected, AppPaths.userDir(null, WINDOWS, Map.of("APPDATA", " "), WINDOWS_HOME));
    }

    @Test
    void linuxHonoursXdgDataHome() {
        assertEquals(Path.of("/data/anna", "tob2026"),
                AppPaths.userDir(null, LINUX, Map.of("XDG_DATA_HOME", "/data/anna"), LINUX_HOME));
    }

    @Test
    void linuxWithoutAnAbsoluteXdgDataHomeUsesLocalShare() {
        Path expected = LINUX_HOME.resolve(".local").resolve("share").resolve("tob2026");
        assertEquals(expected, AppPaths.userDir(null, LINUX, Map.of(), LINUX_HOME));
        assertEquals(expected, AppPaths.userDir(null, LINUX, Map.of("XDG_DATA_HOME", ""), LINUX_HOME));
        assertEquals(expected, AppPaths.userDir(null, LINUX, Map.of("XDG_DATA_HOME", "data"), LINUX_HOME),
                "the XDG specification ignores a relative path");
    }

    @Test
    void otherSystemsFollowTheLinuxRule() {
        assertEquals(LINUX_HOME.resolve(".local").resolve("share").resolve("tob2026"),
                AppPaths.userDir(null, "Mac OS X", Map.of("APPDATA", "C:\\x"), LINUX_HOME));
        assertEquals(LINUX_HOME.resolve(".local").resolve("share").resolve("tob2026"),
                AppPaths.userDir(null, null, Map.of(), LINUX_HOME), "no os.name");
    }

    @Test
    void tobAssetsWinsEvenInAPackage() {
        Optional<Path> jar = Optional.of(Path.of("C:\\Games\\ToB2026\\app\\tob2026-1.0.0.jar"));
        assertEquals(Path.of("D:/assets"), AppPaths.assetsRoot(" D:/assets ", APP_PATH, jar, DEV));
        assertEquals(Path.of("D:/assets"), AppPaths.assetsRoot("D:/assets", null, Optional.empty(), DEV));
    }

    /** Phase 7d: the jar's path in the running system's own syntax - the tests also run on Linux (GitHub Actions). */
    @Test
    void aPackageFindsItsAssetsNextToTheGamesJar() {
        assertEquals(Path.of("Games", "ToB2026", "app", "assets"), AppPaths.assetsRoot(null, APP_PATH,
                Optional.of(Path.of("Games", "ToB2026", "app", "tob2026-1.0.0.jar")), DEV));
        assertEquals(Path.of("/opt/tob2026/lib/app", "assets"), AppPaths.assetsRoot("", "/opt/tob2026/bin/tob2026",
                Optional.of(Path.of("/opt/tob2026/lib/app/tob2026-1.0.0.jar")), DEV));
    }

    @Test
    void developmentUsesTheSourceAssets() {
        assertEquals(Path.of(DEV), AppPaths.assetsRoot(null, null, Optional.of(Path.of("x/tob2026.jar")), DEV),
                "not packaged");
        assertEquals(Path.of(DEV), AppPaths.assetsRoot(null, APP_PATH, Optional.of(Path.of("target/classes")), DEV),
                "the classes folder of a development start is no jar");
        assertEquals(Path.of(DEV), AppPaths.assetsRoot(null, APP_PATH, Optional.empty(), DEV), "location unknown");
        assertFalse(AppPaths.packaged(null));
        assertFalse(AppPaths.packaged(" "));
        assertTrue(AppPaths.packaged(APP_PATH));
    }

    @Test
    void theCodeLocationOfTheTestsIsTheClassesFolder() {
        Path location = AppPaths.codeLocation().orElseThrow();
        assertTrue(Files.isDirectory(location), location + " is the build's classes folder");
        assertEquals("classes", location.getFileName().toString());
    }
}
