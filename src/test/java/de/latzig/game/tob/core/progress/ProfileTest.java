package de.latzig.game.tob.core.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ProfileTest {

    private static final Instant NOW = Instant.parse("2026-10-12T10:00:00Z");

    @Test
    void aNewProfileStartsInTheFirstGroupWithNothingDone() {
        Profile profile = Profile.create("Mario", "original");

        assertEquals("Mario", profile.name(), "mixed case is kept");
        assertEquals(0, profile.group());
        assertEquals(0L, profile.score());
        assertSame(TowerProgress.NONE, profile.progress("original-000"));
        assertEquals(0, profile.completedCount());
    }

    @Test
    void namesAreOneToSixteenPrintableAsciiCharacters() {
        assertTrue(Profile.isValidName("A"));
        assertTrue(Profile.isValidName("Mr Gumby ~{}!?"));
        assertTrue(Profile.isValidName("1234567890123456"));
        assertFalse(Profile.isValidName(""));
        assertFalse(Profile.isValidName("   "), "blank");
        assertFalse(Profile.isValidName(" Lead"), "no leading space");
        assertFalse(Profile.isValidName("12345678901234567"), "17 characters");
        assertFalse(Profile.isValidName("Müller"), "no umlaut (spec §5: ASCII only)");
        assertFalse(Profile.isValidName("Tab\there"));
        assertThrows(IllegalArgumentException.class, () -> Profile.create("", "original"));
    }

    @Test
    void startingCountsTheAttemptAndAFailureAddsTwoToTheCounter() {
        Profile profile = Profile.create("Anna", "original").started("original-004");

        Profile.Ended ended = profile.ended("original-004", Outcome.FAILED, 2, 300, NOW);

        TowerProgress progress = ended.profile().progress("original-004");
        assertEquals(1, progress.attempts());
        assertEquals(2, progress.counter());
        assertFalse(progress.completed());
        assertEquals(Optional.empty(), ended.score());
        assertEquals(0L, ended.profile().score());
    }

    @Test
    void aRestartAddsOneAndACompletionScoresWithTheCounter() {
        Profile profile = Profile.create("Anna", "original").started("original-000");
        profile = profile.ended("original-000", Outcome.RESTARTED, 3, 50, NOW).profile().started("original-000");

        Profile.Ended ended = profile.ended("original-000", Outcome.COMPLETED, 3, 812, NOW);

        assertEquals(Optional.of(new TowerScore(100, 96, 30)), ended.score(), "counter 1 after the restart");
        TowerProgress progress = ended.profile().progress("original-000");
        assertEquals(2, progress.attempts());
        assertTrue(progress.completed());
        assertEquals(226, progress.score());
        assertEquals(Optional.of(NOW), progress.firstCompleted());
        assertEquals(OptionalInt.of(812), progress.bestFrames());
        assertEquals(226L, ended.profile().score());
        assertEquals(1, ended.profile().completedCount());
        assertTrue(ended.profile().isPractice("original-000"));
    }

    @Test
    void practiceOnACompletedTowerLeavesTheProfileUnchanged() {
        Profile done = Profile.create("Anna", "original").started("original-000")
                .ended("original-000", Outcome.COMPLETED, 3, 812, NOW).profile();

        Profile started = done.started("original-000");
        Profile.Ended failed = started.ended("original-000", Outcome.FAILED, 0, 10, NOW);
        Profile.Ended again = started.ended("original-000", Outcome.COMPLETED, 3, 400, NOW);

        assertEquals(done, started);
        assertEquals(done, failed.profile());
        assertEquals(done, again.profile(), "no better frames, no score in practice");
        assertEquals(Optional.empty(), again.score());
    }

    @Test
    void theGroupIsKeptAndChecked() {
        Profile profile = Profile.create("Anna", "original").withGroup(3);

        assertEquals(3, profile.group());
        assertThrows(IllegalArgumentException.class, () -> profile.withGroup(13));
    }

    /** Phase 6d decision 5: own towers are marked solved per library, without score; the campaign is untouched. */
    @Test
    void ownTowersAreMarkedSolvedPerLibraryWithoutScoreAndTheLastLibraryIsKept() {
        Profile profile = Profile.create("Anna", "original").started("original-000")
                .ended("original-000", Outcome.COMPLETED, 3, 500, NOW).profile();
        assertEquals(Optional.empty(), profile.customLibrary(), "a new profile shows the original towers");

        Profile marked = profile.withCustomLibrary(Optional.of("mine")).withCustomSolved("mine", "id-b")
                .withCustomSolved("mine", "id-a").withCustomSolved("mine", "id-b").withCustomSolved("zeta", "id-b");

        assertTrue(marked.customSolved("mine", "id-a"));
        assertTrue(marked.customSolved("zeta", "id-b"));
        assertFalse(marked.customSolved("zeta", "id-a"), "per library");
        assertFalse(marked.customSolved("other", "id-a"));
        assertEquals(Map.of("mine", Set.of("id-a", "id-b"), "zeta", Set.of("id-b")), marked.customSolved());
        assertEquals(Optional.of("mine"), marked.customLibrary());
        assertEquals(profile.score(), marked.score(), "no score");
        assertEquals(profile.towers(), marked.towers());
        assertEquals(1, marked.completedCount(), "the campaign's count");
        Profile after = marked.withGroup(2).started("original-001")
                .ended("original-001", Outcome.COMPLETED, 3, 500, NOW).profile();
        assertEquals(marked.customSolved(), after.customSolved(), "the campaign's changes keep the marks");
        assertEquals(Optional.of("mine"), after.customLibrary());
        assertEquals(Optional.empty(), after.withCustomLibrary(Optional.empty()).customLibrary(), "back to Original");
    }
}
