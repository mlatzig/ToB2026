package de.latzig.game.tob.client.logic.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.latzig.game.tob.core.sim.Command;
import de.latzig.game.tob.core.sim.Recording;
import de.latzig.game.tob.core.sim.RecordingMeta;
import de.latzig.game.tob.core.sim.RecordingMeta.Outcome;
import de.latzig.game.tob.core.sim.SimOptions;
import de.latzig.game.tob.core.sim.SpiderId;

class RecordingStoreTest {

    private static final Instant T0 = Instant.parse("2026-10-09T18:30:05Z");

    private static Recording recording(String towerId, Outcome outcome, Instant at) {
        return new Recording(towerId, SimOptions.DEFAULT, 3,
                List.of(new Recording.Entry(1, new Command.Fire(SpiderId.ZAPPER))))
                .withMeta(new RecordingMeta(outcome, at, 2, "ab", "ToB 2026 test"));
    }

    private static List<String> names(RecordingStore store, String towerId) throws IOException {
        return store.files(towerId).stream().map(file -> file.getFileName().toString()).toList();
    }

    @Test
    void savesPerTowerWithTheLocalTimeAndTheOutcomeInTheName(@TempDir Path userDir) throws IOException {
        RecordingStore store = new RecordingStore(userDir, ZoneOffset.ofHours(2));

        Path first = store.save(recording("original-000", Outcome.COMPLETED, T0));
        Path second = store.save(recording("original-000", Outcome.COMPLETED, T0));
        Path third = store.save(recording("original-000", Outcome.CRASH, T0));

        assertEquals(userDir.resolve("recordings/original-000/20261009-203005-completed.json"), first);
        assertEquals("20261009-203005-completed-2.json", second.getFileName().toString());
        assertEquals("20261009-203005-crash-3.json", third.getFileName().toString(), "counted per second");
        assertEquals(List.of("20261009-203005-completed.json", "20261009-203005-completed-2.json",
                "20261009-203005-crash-3.json"), names(store, "original-000"), "oldest first");
        assertEquals(Optional.of(new RecordingStore.Stored(second, recording("original-000", Outcome.COMPLETED, T0))),
                store.newestCompleted("original-000"));
        assertThrows(IllegalArgumentException.class,
                () -> store.save(new Recording("original-000", SimOptions.DEFAULT, 0, List.of())));
    }

    @Test
    void onlyCompletedAndCrashRecordingsAreSavedAndNoneIsEverDeleted(@TempDir Path userDir) throws IOException {
        RecordingStore store = new RecordingStore(userDir, ZoneOffset.UTC);
        for (int i = 0; i < 25; i++) {
            store.save(recording("tutorial-001", Outcome.COMPLETED, T0.plusSeconds(i)));
        }
        store.save(recording("tutorial-001", Outcome.CRASH, T0.plusSeconds(100)));
        for (Outcome other : new Outcome[] {Outcome.FAILED, Outcome.TIMEUP, Outcome.ABANDONED, Outcome.RESTART}) {
            assertThrows(IllegalArgumentException.class,
                    () -> store.save(recording("tutorial-001", other, T0.plusSeconds(200))), other.fileName());
        }

        List<String> names = names(store, "tutorial-001");

        assertEquals(26, names.size(), "no keep rule: " + names);
        assertEquals("20261009-183005-completed.json", names.get(0));
        assertEquals("20261009-183145-crash.json", names.get(names.size() - 1));
    }

    @Test
    void theNewestReadableCompletedRecordingIsFoundAndUnreadableOnesAreSkipped(@TempDir Path userDir)
            throws IOException {
        RecordingStore store = new RecordingStore(userDir, ZoneOffset.UTC);
        Path older = store.save(recording("original-005", Outcome.COMPLETED, T0));
        // a failed recording of an earlier build (format v2 still reads every outcome; the store no longer saves it)
        Files.copy(older, store.directory("original-005").resolve("20261009-183010-failed.json"));
        Path newer = store.save(recording("original-005", Outcome.COMPLETED, T0.plusSeconds(10)));
        Files.writeString(newer, "{ not json", StandardCharsets.UTF_8);
        Files.writeString(store.directory("original-005").resolve("notes.txt"), "ignored", StandardCharsets.UTF_8);

        assertEquals(older, store.newestCompleted("original-005").orElseThrow().file());
        assertEquals(Optional.empty(), store.newestCompleted("original-006"), "no folder");
        Files.delete(older);
        assertEquals(Optional.empty(), store.newestCompleted("original-005"), "only an unreadable one is left");
        assertEquals(2, store.files("original-005").size(), "the failed and the unreadable file, not notes.txt");
    }
}
