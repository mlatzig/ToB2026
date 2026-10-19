package de.latzig.game.tob.client.logic.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.client.logic.Texts;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.progress.TowerScore;
import de.latzig.game.tob.core.sim.Rules;
import de.latzig.game.tob.core.sim.TestTowers;

class FrontEndTextsTest {

    private static final Texts TEXTS = Texts.english();

    @Test
    void theBundleIsAsciiAndHasTheToB2009Words() throws IOException {
        try (InputStream in = Texts.class.getResourceAsStream("/i18n/frontend.properties")) {
            for (byte b : in.readAllBytes()) {
                assertTrue(b >= 0, "ASCII only: the same bytes in UTF-8 and ISO-8859-1");
            }
        }
        assertEquals("Play", TEXTS.get("menu.play"));
        assertEquals("Hall of Fame", TEXTS.get("menu.halloffame"));
        assertEquals("< New Profile >", TEXTS.get("play.new"));
        assertEquals("Back", TEXTS.get("frame.back"));
        assertEquals("Score: 000452", TEXTS.format("overview.score", 452L));
        for (String line : TEXTS.get("menu.ticker").split("\n\t")) {
            assertTrue(line.length() <= 48, "a ticker line fits the menu's ticker group: " + line);
        }
        assertTrue(TEXTS.get("menu.ticker").startsWith("Based on \"Tower of Babel\" by Pete Cooke (1989),"),
                "spec decision 4");
    }

    @Test
    void theBriefingNamesTheSpidersAndTheObjectiveInToB2009sWords() {
        TowerDefinition tower = TestTowers.tower().level(0, "FFFF").object(0, 0, 0, ObjectType.ZAP)
                .object(1, 0, 0, ObjectType.GRB).object(2, 0, 0, ObjectType.PSH).conditions(120, true, 2, 3).build();

        BriefingText briefing = BriefingText.of(tower, TEXTS);

        assertEquals("Test", briefing.title());
        assertEquals("Anonymous", briefing.author(), "the original's fallback for an empty author");
        assertEquals("Zapper, Pusher and Grabber", briefing.spiders());
        assertEquals(List.of("Collect 2 Klondikes", "and Destroy 3 objects", "within a Timelimit of 120 seconds"),
                briefing.objective());
    }

    @Test
    void oneSpiderOneKlondikeAndNoTaskAreWordedLikeTheOriginal() {
        TowerDefinition one = TestTowers.tower().level(0, "FF").object(0, 0, 0, ObjectType.PSH)
                .conditions(60, false, 1, 0).build();
        TowerDefinition none = TestTowers.tower().level(0, "FF").build();

        assertEquals("Pusher", BriefingText.of(one, TEXTS).spiders());
        assertEquals(List.of("Collect 1 Klondike"), BriefingText.of(one, TEXTS).objective());
        assertEquals("No Spiders Present!", BriefingText.of(none, TEXTS).spiders());
        assertEquals(List.of("There is no task for this tower!"), BriefingText.of(none, TEXTS).objective());
    }

    @Test
    void aCompletionShowsTheScoreBoxAndAFailureItsReason() {
        ResultText done = ResultText.of(Rules.EXIT_COMPLETED, Optional.of(new TowerScore(100, 92, 20)), 452L, false,
                TEXTS);
        ResultText late = ResultText.of(Rules.EXIT_TIMEOUT, Optional.empty(), 452L, false, TEXTS);

        assertEquals("Tower Completed", done.caption());
        assertEquals("Congratulations", done.reason());
        assertEquals(List.of(new ResultText.Line("Tower Score", "100"), new ResultText.Line("Retry Bonus", "92"),
                new ResultText.Line("Survivor Bonus", "20"), new ResultText.Line("Total Score:", "000452")),
                done.score());
        assertTrue(done.completed());
        assertEquals("Tower Not Completed", late.caption());
        assertEquals("Out Of Time", late.reason());
        assertEquals(List.of(), late.score());
        assertEquals("All Spiders Destroyed",
                ResultText.of(Rules.EXIT_SPIDERS_DESTROYED, Optional.empty(), 0L, false, TEXTS).reason());
        assertEquals("Grabber Destroyed",
                ResultText.of(Rules.EXIT_GRABBER_DESTROYED, Optional.empty(), 0L, false, TEXTS).reason());
        assertEquals("Attempt Abandoned",
                ResultText.of(Rules.EXIT_ABANDONED, Optional.empty(), 0L, false, TEXTS).reason());
    }

    /** Task 6 Minor 1: a restart or an unknown exit code never shows a wrong reason; it fails loudly. */
    @Test
    void aRestartOrAnUnknownExitCodeHasNoResultText() {
        for (int exit : new int[] {Rules.EXIT_RESTART, 0, 99}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> ResultText.of(exit, Optional.empty(), 0L, false, TEXTS));
            assertTrue(e.getMessage().contains(Integer.toString(exit)), e.getMessage());
        }
    }

    @Test
    void practiceSaysTheProfileIsUnchangedAndTheLastTowerIsCelebrated() {
        ResultText practice = ResultText.of(Rules.EXIT_COMPLETED, Optional.empty(), 452L, true, TEXTS);

        assertEquals(List.of(new ResultText.Line("Practice", "profile unchanged")), practice.score());
        assertFalse(ResultText.allSolved(26000L, TEXTS).isEmpty());
        assertEquals("026000", ResultText.allSolved(26000L, TEXTS).get(ResultText.allSolved(26000L, TEXTS).size() - 1));
    }
}
