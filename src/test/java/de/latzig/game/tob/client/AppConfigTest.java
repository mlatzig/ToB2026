package de.latzig.game.tob.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.client.logic.effects.SkyOptions;

class AppConfigTest {

    @Test
    void defaultsToTheGeneratedAssetsAndTheStartScreens() {
        AppConfig config = AppConfig.fromSystemProperties();

        assertEquals(Path.of(System.getProperty(AppConfig.ASSETS_PROPERTY, AppConfig.DEFAULT_ASSETS)),
                config.assetsRoot());
        assertEquals(Optional.ofNullable(System.getProperty(AppConfig.TOWER_PROPERTY)).filter(s -> !s.isBlank()),
                config.startTower());
        assertFalse(config.fastForward());
        assertEquals(config.assetsRoot().resolve("towers"), config.towersDirectory());
        assertEquals(AppConfig.userDir(System.getProperty(AppConfig.USERDIR_PROPERTY)), config.userDir());
        assertEquals(Boolean.getBoolean(AppConfig.RECORD_PROPERTY), config.record());
        assertFalse(new AppConfig(Path.of("a"), Optional.empty(), false, Path.of("u")).record(),
                "recording is off unless enabled");
    }

    @Test
    void tobRecordTrueEnablesTheDeveloperRecording() {
        String previous = System.getProperty(AppConfig.RECORD_PROPERTY);
        try {
            System.setProperty(AppConfig.RECORD_PROPERTY, "true");
            assertTrue(AppConfig.fromSystemProperties().record());
            System.setProperty(AppConfig.RECORD_PROPERTY, "false");
            assertFalse(AppConfig.fromSystemProperties().record());
        } finally {
            if (previous == null) {
                System.clearProperty(AppConfig.RECORD_PROPERTY);
            } else {
                System.setProperty(AppConfig.RECORD_PROPERTY, previous);
            }
        }
    }

    /** Phase 5b decision 7: the moon properties are gone; the moon detail is an option. */
    @Test
    void theMoonPropertiesAreNoLongerRead() {
        String previous = System.getProperty("tob.moonHalfDetail");
        try {
            System.setProperty("tob.moonHalfDetail", "true");
            System.setProperty("tob.moonSize", "giant");
            assertEquals(SkyOptions.DEFAULT, AppConfig.fromSystemProperties().sky());
        } finally {
            System.clearProperty("tob.moonSize");
            if (previous == null) {
                System.clearProperty("tob.moonHalfDetail");
            } else {
                System.setProperty("tob.moonHalfDetail", previous);
            }
        }
    }

    /** Phase 5b decision 3: {@code -Dtob.volume} stays a hidden developer override of Master. */
    @Test
    void tobVolumeIsAnOptionalGain() {
        assertEquals(Optional.of(4f), AppConfig.volume("4"));
        assertEquals(Optional.of(0.5f), AppConfig.volume(" 0.5 "));
        assertEquals(Optional.empty(), AppConfig.volume(null));
        assertEquals(Optional.empty(), AppConfig.volume(""));
        assertEquals(Optional.empty(), AppConfig.volume("loud"), "unreadable: no override");
        assertEquals(Optional.empty(), new AppConfig(Path.of("a"), Optional.empty(), false, Path.of("u")).volume());
    }

    /** M4 (final review): a NaN, infinite or negative override is rejected; the Master option applies instead. */
    @Test
    void tobVolumeRejectsNaNInfiniteAndNegativeValues() {
        assertEquals(Optional.empty(), AppConfig.volume("NaN"));
        assertEquals(Optional.empty(), AppConfig.volume("Infinity"));
        assertEquals(Optional.empty(), AppConfig.volume("-Infinity"));
        assertEquals(Optional.empty(), AppConfig.volume("-1"));
    }

    /** Phase 6a: {@code -Dtob.towerLibrary} names an extra library for developer starts; unset, there is none. */
    @Test
    void tobTowerLibraryIsAnOptionalExtraLibrary() {
        assertEquals(Optional.of(Path.of("src/test/resources/towers/large.json")),
                AppConfig.towerLibrary(" src/test/resources/towers/large.json "));
        assertEquals(Optional.empty(), AppConfig.towerLibrary(null));
        assertEquals(Optional.empty(), AppConfig.towerLibrary(" "));
        assertEquals(Optional.empty(), new AppConfig(Path.of("a"), Optional.empty(), false, Path.of("u"))
                .towerLibrary(), "the short constructors have none");
        String previous = System.getProperty(AppConfig.TOWER_LIBRARY_PROPERTY);
        try {
            System.setProperty(AppConfig.TOWER_LIBRARY_PROPERTY, "x/large.json");
            assertEquals(Optional.of(Path.of("x/large.json")), AppConfig.fromSystemProperties().towerLibrary());
        } finally {
            if (previous == null) {
                System.clearProperty(AppConfig.TOWER_LIBRARY_PROPERTY);
            } else {
                System.setProperty(AppConfig.TOWER_LIBRARY_PROPERTY, previous);
            }
        }
    }

    /** Phase 7c decision 1: {@code -Dtob.userdir} wins, else the system's user folder (only computed, never used). */
    @Test
    void theUserDirIsTobUserdirElseTheSystemsUserFolder() {
        assertEquals(Path.of("D:/portable/tob"), AppConfig.userDir("D:/portable/tob"));
        assertEquals(AppPaths.userDir(null, System.getProperty("os.name"), System.getenv(),
                Path.of(System.getProperty("user.home"))), AppConfig.userDir(" "));
    }

    /** Phase 7c: {@code -Dtob.record=true} is ignored in a package ({@code jpackage.app-path} set). */
    @Test
    void tobRecordIsIgnoredInAPackage() {
        assertTrue(AppConfig.record("true", null));
        assertFalse(AppConfig.record("true", "/opt/tob2026/bin/tob2026"));
        assertFalse(AppConfig.record(null, null));
        assertFalse(AppConfig.record("yes", null));
    }
}
