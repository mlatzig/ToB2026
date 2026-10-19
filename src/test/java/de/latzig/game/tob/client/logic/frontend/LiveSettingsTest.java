package de.latzig.game.tob.client.logic.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.latzig.game.tob.client.logic.game.GameAction;
import de.latzig.game.tob.client.logic.game.KeyChord;

class LiveSettingsTest {

    @Test
    void withoutAFileTheDefaultsApply(@TempDir Path dir) {
        LiveSettings live = LiveSettings.load(new SettingsFile(dir.resolve("settings.json")));

        assertEquals(Settings.DEFAULTS, live.saved());
        assertEquals(Settings.DEFAULTS, live.current());
    }

    @Test
    void aPreviewIsCurrentUntilItIsRevertedOrSaved(@TempDir Path dir) throws IOException {
        SettingsFile file = new SettingsFile(dir.resolve("settings.json"));
        LiveSettings live = LiveSettings.load(file);
        Settings louder = Settings.DEFAULTS.withMasterVolume(180);

        live.preview(louder);
        assertEquals(louder, live.current());
        assertEquals(Settings.DEFAULTS, live.saved());
        live.revert();
        assertEquals(Settings.DEFAULTS, live.current());

        live.preview(louder.withKeys(Map.of(GameAction.FIRE, KeyChord.of(28))));
        live.save();
        assertEquals(live.current(), live.saved());
        assertEquals(live.saved(), LiveSettings.load(file).saved(), "a fresh load reads the saved options back");
    }

    @Test
    void savingKeepsTheLastProfile(@TempDir Path dir) throws IOException {
        SettingsFile file = new SettingsFile(dir.resolve("settings.json"));
        file.update(settings -> settings.withLastProfile("Anna"));
        LiveSettings live = LiveSettings.load(file);

        live.preview(Settings.DEFAULTS.withFullscreen(false));
        live.save();

        assertEquals(Optional.of("Anna"), file.read().lastProfile());
        assertEquals("window", file.read().options().get(Settings.OPTION_DISPLAY));
    }

    /**
     * Phase 5c-2 decision 4: the Music switch applies at once (previewed into {@code current}), is saved in
     * {@code settings.json} as a JSON boolean, and an older file without it reads as on.
     */
    @Test
    void theMusicSwitchIsPreviewedAtOnceAndSavedAsABoolean(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("settings.json");
        Files.writeString(path, "{\"format\":\"tob2026-settings\",\"version\":1,\"options\":{\"brightness\":80}}");
        SettingsFile file = new SettingsFile(path);
        LiveSettings live = LiveSettings.load(file);
        assertTrue(live.current().music(), "an older file without the switch: on");

        live.preview(live.current().withMusic(false));
        assertFalse(live.current().music(), "at once");
        assertTrue(live.saved().music(), "not saved yet");
        live.save();

        assertTrue(Files.readString(path).replaceAll("\\s+", " ").contains("\"music\" : false"),
                Files.readString(path));
        assertFalse(LiveSettings.load(file).saved().music(), "read back");
        assertEquals(80, LiveSettings.load(file).saved().brightness(), "the other options stay");
    }

    /**
     * Phase 5e decision 5: the Look is saved in {@code settings.json} as {@code "look"}, an older file without it
     * reads as Remake.
     */
    @Test
    void theLookIsSavedAndAnOlderFileReadsAsRemake(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("settings.json");
        Files.writeString(path, "{\"format\":\"tob2026-settings\",\"version\":1,\"options\":{\"brightness\":80}}");
        SettingsFile file = new SettingsFile(path);
        LiveSettings live = LiveSettings.load(file);
        assertFalse(live.current().classicLook(), "an older file without the Look: Remake");

        live.preview(live.current().withClassicLook(true));
        live.save();

        assertTrue(Files.readString(path).replaceAll("\s+", " ").contains("\"look\" : \"classic\""),
                Files.readString(path));
        assertTrue(LiveSettings.load(file).saved().classicLook(), "read back");
        assertEquals(80, LiveSettings.load(file).saved().brightness(), "the other options stay");
    }

    @Test
    void inMemorySettingsAreNeverWritten() throws IOException {
        LiveSettings live = LiveSettings.inMemory(Settings.DEFAULTS.withBrightness(60));

        live.preview(live.current().withBrightness(70));
        live.save();

        assertEquals(70, live.saved().brightness());
    }
}
