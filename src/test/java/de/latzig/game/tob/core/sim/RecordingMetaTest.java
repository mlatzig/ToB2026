package de.latzig.game.tob.core.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.sim.RecordingMeta.Outcome;

class RecordingMetaTest {

    @Test
    void outcomesFollowTheExitCodesAndHaveLowerCaseFileNames() {
        assertEquals(Outcome.COMPLETED, Outcome.ofExit(Rules.EXIT_COMPLETED));
        assertEquals(Outcome.TIMEUP, Outcome.ofExit(Rules.EXIT_TIMEOUT));
        assertEquals(Outcome.FAILED, Outcome.ofExit(Rules.EXIT_SPIDERS_DESTROYED));
        assertEquals(Outcome.FAILED, Outcome.ofExit(Rules.EXIT_GRABBER_DESTROYED));
        assertEquals(Outcome.ABANDONED, Outcome.ofExit(Rules.EXIT_ABANDONED));
        assertEquals(Outcome.RESTART, Outcome.ofExit(Rules.EXIT_RESTART));
        assertEquals(Outcome.ABANDONED, Outcome.ofExit(0), "left before the end");
        assertEquals("timeup", Outcome.TIMEUP.fileName());
        assertEquals(Outcome.CRASH, Outcome.fromFileName("crash"));
        assertThrows(IllegalArgumentException.class, () -> Outcome.fromFileName("won"));
    }

    @Test
    void metadataIsOptionalAndKeptByWithMeta() {
        Recording plain = new Recording("t", SimOptions.DEFAULT, 2, List.of());
        RecordingMeta meta = new RecordingMeta(Outcome.COMPLETED, Instant.parse("2026-10-09T12:00:00Z"), 120,
                "ab12", "ToB 2026 test");

        Recording withMeta = plain.withMeta(meta);

        assertEquals(Optional.empty(), plain.meta());
        assertEquals(Optional.of(meta), withMeta.meta());
        assertEquals(new Recording("t", SimOptions.DEFAULT, 2, List.of(), Optional.of(meta)), withMeta);
        assertThrows(IllegalArgumentException.class,
                () -> new RecordingMeta(Outcome.CRASH, Instant.EPOCH, -1, "ab12", "g"));
    }
}
