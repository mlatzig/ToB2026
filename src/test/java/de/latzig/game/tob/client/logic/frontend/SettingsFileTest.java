package de.latzig.game.tob.client.logic.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.latzig.game.tob.core.io.SettingsJson;

class SettingsFileTest {

    @Test
    void noFileYetReadsAsEmpty(@TempDir Path dir) {
        assertEquals(SettingsJson.Settings.EMPTY, new SettingsFile(dir.resolve("settings.json")).read());
    }

    /** Decision 10: an unreadable file is logged and read as empty (the defaults), never an error. */
    @Test
    void anUnreadableFileReadsAsEmpty(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("settings.json");
        for (String broken : List.of("{", "[]", "{\"format\":\"tob2026-settings\",\"version\":9}", "")) {
            Files.writeString(path, broken, StandardCharsets.UTF_8);

            assertEquals(SettingsJson.Settings.EMPTY, new SettingsFile(path).read(), broken);
        }
    }

    @Test
    void anUpdateChangesOnlyItsPartAndReplacesAnUnreadableFile(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("settings.json");
        SettingsFile file = new SettingsFile(path);
        file.update(settings -> settings.withOptions(Map.of("brightness", "80")));
        file.update(settings -> settings.withLastProfile("Anna"));

        assertEquals(new SettingsJson.Settings(Optional.of("Anna"), Map.of("brightness", "80")), file.read());

        Files.writeString(path, "{ broken", StandardCharsets.UTF_8);
        file.update(settings -> settings.withLastProfile("Bob"));
        assertEquals(new SettingsJson.Settings(Optional.of("Bob"), Map.of()), file.read(), "replaced");
    }

    /** Phase 5c-1 decision 3: a solved tutorial tower is kept by the options' and the last profile's updates. */
    @Test
    void theSolvedTutorialTowersSurviveTheOtherUpdates(@TempDir Path dir) throws IOException {
        SettingsFile file = new SettingsFile(dir.resolve("settings.json"));
        file.update(settings -> settings.withSolvedTutorial("tutorial-007"));
        file.update(settings -> settings.withOptions(Map.of("brightness", "80")));
        file.update(settings -> settings.withLastProfile("Anna"));

        assertEquals(new SettingsJson.Settings(Optional.of("Anna"), Map.of("brightness", "80"),
                Set.of("tutorial-007")), file.read());
    }

    @Test
    void anUpdateIsAtomicAndLeavesNoTemporaryFile(@TempDir Path dir) throws IOException {
        SettingsFile file = new SettingsFile(dir.resolve("settings.json"));
        file.update(settings -> settings.withLastProfile("Anna"));
        file.update(settings -> settings.withOptions(Map.of("display", "window")));

        try (Stream<Path> files = Files.list(dir)) {
            assertEquals(List.of("settings.json"), files.map(f -> f.getFileName().toString()).toList());
        }
    }
}
