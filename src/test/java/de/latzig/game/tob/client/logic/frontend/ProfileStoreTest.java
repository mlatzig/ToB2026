package de.latzig.game.tob.client.logic.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.latzig.game.tob.core.progress.Profile;

class ProfileStoreTest {

    @Test
    void fileNamesAreLowerCaseWithEverythingElseEscaped() {
        assertEquals("profile-mr%20gumby.json", ProfileStore.fileName("Mr Gumby"));
        assertEquals("profile-con.json", ProfileStore.fileName("CON"), "no reserved Windows name");
        assertEquals("profile-a%2Fb%3Ac%2A%3F%22.json", ProfileStore.fileName("a/b:c*?\""));
        assertEquals("profile-x_y-1%2E.json", ProfileStore.fileName("x_Y-1."));
    }

    @Test
    void profilesAreSavedListedByNameFoundIgnoringCaseAndDeleted(@TempDir Path userDir) throws IOException {
        ProfileStore store = new ProfileStore(userDir);
        assertEquals(List.of(), store.list(), "no folder yet");

        store.save(Profile.create("mario", "original"));
        store.save(Profile.create("Anna", "original").withGroup(2));
        store.save(Profile.create("Zed", "original"));

        assertEquals(List.of("Anna", "mario", "Zed"), store.list().stream().map(Profile::name).toList());
        assertTrue(store.exists("MARIO"), "names are unique ignoring case");
        assertEquals(Optional.of(2), store.find("anna").map(Profile::group));
        assertTrue(Files.isRegularFile(userDir.resolve("savegames/profile-anna.json")));

        store.delete("Mario");

        assertFalse(store.exists("mario"));
        assertEquals(List.of("Anna", "Zed"), store.list().stream().map(Profile::name).toList());
    }

    @Test
    void anUnreadableFileIsSkipped(@TempDir Path userDir) throws IOException {
        ProfileStore store = new ProfileStore(userDir);
        store.save(Profile.create("Anna", "original"));
        Files.writeString(store.directory().resolve("profile-broken.json"), "{", StandardCharsets.UTF_8);
        Files.writeString(store.directory().resolve("notes.txt"), "not a profile", StandardCharsets.UTF_8);

        assertEquals(List.of("Anna"), store.list().stream().map(Profile::name).toList());
    }

    @Test
    void theLastProfileIsRemembered(@TempDir Path userDir) throws IOException {
        ProfileStore store = new ProfileStore(userDir);
        assertEquals(Optional.empty(), store.lastProfile());

        store.rememberLast("Anna");

        assertEquals(Optional.of("Anna"), new ProfileStore(userDir).lastProfile());
        assertTrue(Files.isRegularFile(userDir.resolve("settings.json")));
    }

    /** Phase 5b decision 10: remembering the last profile is a read-modify-write that keeps the options. */
    @Test
    void rememberingTheLastProfileKeepsTheOptions(@TempDir Path userDir) throws IOException {
        ProfileStore store = new ProfileStore(userDir);
        LiveSettings live = LiveSettings.load(store.settings());
        live.preview(Settings.DEFAULTS.withMasterVolume(170).withBackwardsAllowed(false));
        live.save();

        store.rememberLast("Anna");

        assertEquals(Optional.of("Anna"), store.lastProfile());
        Settings read = LiveSettings.load(new ProfileStore(userDir).settings()).saved();
        assertEquals(170, read.masterVolume());
        assertFalse(read.backwardsAllowed());
    }

    /** Phase 5c-1 decision 2: five profiles fill the store; nothing is deleted to make room. */
    @Test
    void fiveProfilesFillTheStore(@TempDir Path userDir) throws IOException {
        ProfileStore store = new ProfileStore(userDir);
        for (String name : List.of("A", "B", "C", "D")) {
            store.save(Profile.create(name, "original"));
        }
        assertFalse(store.full(), "four profiles");

        store.save(Profile.create("E", "original"));

        assertTrue(store.full());
        assertEquals(5, ProfileStore.MAX_PROFILES);
        assertEquals(5, store.list().size());
    }
}
