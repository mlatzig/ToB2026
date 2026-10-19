package de.latzig.game.tob.client.logic.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.client.logic.Texts;
import de.latzig.game.tob.client.logic.editor.EditorModelTest;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.progress.Outcome;
import de.latzig.game.tob.core.progress.Profile;

class OverviewModelTest {

    private static final Campaign CAMPAIGN = Campaign.of(CampaignTest.towers());
    private static final Texts TEXTS = Texts.english();

    /** M12 (Task 7 Minor 1): the tower number is formatted with Locale.ROOT, whatever the default locale's digits. */
    @Test
    void theTowerNumberUsesAsciiDigitsInEveryLocale() {
        Locale before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("th-TH-u-nu-thai"));
            OverviewModel overview = new OverviewModel(CAMPAIGN, Profile.create("Anna", "original"), TEXTS);

            assertTrue(overview.infoText().startsWith("001) "), overview.infoText());
        } finally {
            Locale.setDefault(before);
        }
    }

    @Test
    void aNewProfileSeesTheTopRowOpenAndTheRestLocked() {
        OverviewModel overview = new OverviewModel(CAMPAIGN, Profile.create("Anna", "original"), TEXTS);

        assertEquals("Anna", overview.caption());
        assertEquals("Score: 000000", overview.scoreText());
        assertEquals("Group 01 (  0%)", overview.groupText(), "displayed 1-based (author 2026-10-15), group() is 0");
        List<OverviewModel.Cell> cells = overview.cells();
        assertEquals(OverviewModel.CELLS, cells.size());
        assertEquals(new OverviewModel.Cell(0, 'A', 0, 0, OverviewModel.State.OPEN, true), cells.get(0));
        assertEquals(new OverviewModel.Cell(5, 'F', 1, 2, OverviewModel.State.LOCKED, false), cells.get(5));
        assertEquals(new OverviewModel.Cell(7, 'H', 2, 1, OverviewModel.State.LOCKED, false), cells.get(7));
        assertFalse(overview.canGoBack());
        assertFalse(overview.canGoForward());
        assertEquals(0, overview.selected(), "the next tower is preselected");
        assertEquals("001) Tower 1 - Pete Cooke", overview.infoText());
        assertTrue(overview.playable());
        assertFalse(overview.practice());
    }

    @Test
    void theArrowKeysMoveInTheGridWithoutWrapping() {
        OverviewModel overview = new OverviewModel(CAMPAIGN, Profile.create("Anna", "original"), TEXTS);

        assertFalse(overview.up());
        assertFalse(overview.left());
        assertEquals(0, overview.selected());
        assertTrue(overview.right());
        assertTrue(overview.right());
        assertFalse(overview.right(), "no wrap to D");
        assertEquals(2, overview.selected());
        assertTrue(overview.down());
        assertTrue(overview.down());
        assertFalse(overview.down());
        assertEquals(8, overview.selected(), "locked cells can be selected to read their title");
        assertEquals(new OverviewModel.Cell(8, 'I', 2, 2, OverviewModel.State.LOCKED, true),
                overview.selectedCell());
        assertFalse(overview.playable());
        assertEquals(new OverviewModel.Nothing(), overview.activate(), "a locked tower cannot be played");
        assertTrue(overview.left());
        assertTrue(overview.up());
        assertEquals(4, overview.selected());
    }

    @Test
    void aClickSelectsAnyCellOfTheGrid() {
        OverviewModel overview = new OverviewModel(CAMPAIGN, Profile.create("Anna", "original"), TEXTS);

        assertTrue(overview.select(5));
        assertEquals(5, overview.selected());
        assertEquals("006) Tower 6 - Pete Cooke", overview.infoText());
        assertTrue(overview.selectAt(2, 0));
        assertEquals(6, overview.selected());
        assertFalse(overview.select(9));
        assertFalse(overview.select(-1));
        assertFalse(overview.selectAt(3, 0));
        assertFalse(overview.selectAt(0, -1));
        assertEquals(6, overview.selected());
    }

    @Test
    void playingAnOpenTowerIsNoPracticeButASolvedOneIs() {
        Profile profile = CampaignTest.complete(Profile.create("Anna", "original"), "original-000");
        profile = profile.started("original-001").ended("original-001", Outcome.FAILED, 0, 9, Instant.EPOCH)
                .profile();
        OverviewModel overview = new OverviewModel(CAMPAIGN, profile, TEXTS);

        assertEquals(1, overview.selected(), "B, the first accessible tower");
        assertEquals("002) Tower 2 - Pete Cooke [1]", overview.infoText(), "attempts as ToB 2009's [n]");
        assertEquals(OverviewModel.State.SOLVED, overview.cells().get(0).state());
        assertEquals(OverviewModel.State.LOCKED, overview.cells().get(2).state(), "C closed again");
        assertEquals(String.format("Score: %06d", profile.score()), overview.scoreText());
        assertEquals(new OverviewModel.Play(CAMPAIGN.tower(0, 1), false), overview.activate());
        assertTrue(overview.select(0));
        assertTrue(overview.playable());
        assertTrue(overview.practice());
        assertEquals(new OverviewModel.Play(CAMPAIGN.tower(0, 0), true), overview.activate(), "practice");
    }

    @Test
    void sevenOfNineOpenTheNextGroupAndTheGroupButtonsSwitchIt() {
        Profile profile = CampaignTest.complete(Profile.create("Anna", "original"), "original-000",
                "original-001", "original-002", "original-003", "original-004", "original-005", "original-007");
        OverviewModel overview = new OverviewModel(CAMPAIGN, profile, TEXTS);
        assertEquals("Group 01 (  6%)", overview.groupText(), "7 of 117 = 5.98 rounds up");
        assertEquals(6, overview.selected(), "G is still open");
        assertFalse(overview.canGoBack());
        assertTrue(overview.canGoForward());
        assertEquals(new OverviewModel.Nothing(), overview.back(), "no group before 01");

        OverviewModel.Action action = overview.forward();

        assertInstanceOf(OverviewModel.GroupChanged.class, action);
        assertEquals(1, ((OverviewModel.GroupChanged) action).profile().group());
        assertEquals(1, overview.group());
        assertEquals(1, overview.profile().group());
        assertEquals("Group 02 (  6%)", overview.groupText(), "group() 1 is displayed 1-based");
        assertEquals(0, overview.selected(), "the new group's first tower");
        assertEquals("010) Tower 10 - Pete Cooke", overview.infoText());
        assertTrue(overview.canGoBack());
        assertFalse(overview.canGoForward());
        assertEquals(new OverviewModel.Nothing(), overview.forward(), "group 02 has nothing completed");
        assertInstanceOf(OverviewModel.GroupChanged.class, overview.back());
        assertEquals(0, overview.group());
        assertEquals(6, overview.selected());
    }

    @Test
    void aGroupWithNothingOpenPreselectsItsFirstSolvedTower() {
        Profile profile = CampaignTest.complete(Profile.create("Anna", "original"), "original-001",
                "original-000", "original-002", "original-003", "original-004", "original-005", "original-006",
                "original-007", "original-008");
        OverviewModel overview = new OverviewModel(CAMPAIGN, profile, TEXTS);

        assertEquals(0, overview.selected());
        assertTrue(overview.practice());
        assertTrue(overview.cells().stream().allMatch(cell -> cell.state() == OverviewModel.State.SOLVED));
    }

    @Test
    void anEmptyAuthorIsShownAsAnonymous() {
        List<TowerDefinition> towers = CampaignTest.towers();
        TowerDefinition a = towers.get(0);
        towers.set(0, new TowerDefinition(a.id(), a.group(), a.index(), a.title(), "", a.conditions(),
                a.behaviour(), a.effects(), a.moon(), a.palette(), a.sounds(), a.lock(), a.grid(), a.remake()));
        OverviewModel overview = new OverviewModel(Campaign.of(towers), Profile.create("Anna", "original"), TEXTS);

        assertEquals("001) Tower 1 - Anonymous", overview.infoText());
    }

    @Test
    void theTowerOfTheLastAttemptCanBePreselected() {
        Profile profile = CampaignTest.complete(Profile.create("Anna", "original"), "original-000");
        OverviewModel overview = new OverviewModel(CAMPAIGN, profile, TEXTS);

        overview.preselect("original-003");
        assertEquals(3, overview.selected());
        overview.preselect("original-010");
        assertEquals(3, overview.selected(), "another group is ignored");
        overview.preselectNext();
        assertEquals(1, overview.selected());
    }

    /** Phase 6d decisions 3 and 5: an own library - all open, groups of 9, the library's caption and header. */
    @Test
    void anOwnLibraryShowsAllItsTowersOpenInGroupsOfNine() {
        Profile profile = Profile.create("Anna", "original").withCustomSolved("mine", "t-0");
        OverviewModel overview = new OverviewModel(CAMPAIGN, profile, TEXTS,
                Optional.of(new CustomLibrary("mine", CustomLibraryTest.playable("t", 10))));

        assertEquals("Anna (mine)", overview.caption(), "ToB 2009's NAME (LIBRARY)");
        assertEquals("Solved: 1 of 10", overview.scoreText(), "no score for own towers");
        assertEquals("Group 01 ( 10%)", overview.groupText());
        assertFalse(overview.completedAll());
        assertEquals(OverviewModel.State.SOLVED, overview.cells().get(0).state());
        for (int i = 1; i < OverviewModel.CELLS; i++) {
            assertEquals(OverviewModel.State.OPEN, overview.cells().get(i).state(), "all open");
        }
        assertEquals(1, overview.selected(), "the first open tower is preselected");
        assertEquals("002) (NO TITLE) - Anonymous", overview.infoText());
        assertTrue(overview.playable());
        assertFalse(overview.practice(), "own towers are never practice");
        assertFalse(overview.canGoBack());
        assertTrue(overview.canGoForward(), "all open: no 7 of 9 needed");

        assertInstanceOf(OverviewModel.GroupChanged.class, overview.forward());
        assertEquals(1, overview.group());
        assertEquals("Group 02 ( 10%)", overview.groupText());
        List<OverviewModel.Cell> cells = overview.cells();
        assertEquals(new OverviewModel.Cell(0, 'A', 0, 0, OverviewModel.State.OPEN, true), cells.get(0));
        assertEquals(new OverviewModel.Cell(1, 'B', 0, 1, OverviewModel.State.EMPTY, false), cells.get(1));
        assertFalse(cells.get(1).playable());
        assertFalse(overview.select(1), "past the last tower: nothing to select");
        assertFalse(overview.right());
        assertEquals("010) (NO TITLE) - Anonymous", overview.infoText());
        assertFalse(overview.canGoForward());
        assertEquals(0, overview.profile().group(), "the profile's campaign group is not touched");
    }

    /** Phase 6d decisions 5 and 6: an own tower is played (never practice); one without a task is refused. */
    @Test
    void anOwnTowerIsPlayedOrRefusedAndTheLastOneCanBePreselected() {
        List<TowerDefinition> towers = new ArrayList<>(CustomLibraryTest.playable("t", 10));
        towers.set(4, CustomLibraryTest.noTask("t-4"));
        Profile profile = Profile.create("Anna", "original");
        for (int i = 0; i < 4; i++) {
            profile = profile.withCustomSolved("mine", "t-" + i);
        }
        OverviewModel overview = new OverviewModel(CAMPAIGN, profile, TEXTS,
                Optional.of(new CustomLibrary("mine", towers)));

        assertEquals(5, overview.selected(), "the first open tower; the one without a task is skipped");
        assertEquals(OverviewModel.State.LOCKED, overview.cells().get(4).state(), "greyed");
        assertEquals(new OverviewModel.Play(towers.get(5), false), overview.activate());
        overview.select(4);
        assertTrue(overview.playable(), "Play stays enabled: it tells why");
        assertEquals(new OverviewModel.Refused("No Task!"), overview.activate(),
                "author's acceptance amendment 2026-10-17: the designer's own words");
        overview.select(0);
        assertEquals(new OverviewModel.Play(towers.get(0), false), overview.activate(), "a solved one is replayed");

        overview.preselect("t-9");
        assertEquals(1, overview.group());
        assertEquals(0, overview.selected());
        overview.preselect("unknown");
        assertEquals(1, overview.group(), "an unknown tower changes nothing");
        overview.back();
        assertEquals(0, overview.group());
        assertEquals(5, overview.selected());
    }

    /**
     * Fix wave (Task 4 Minor): without an open tower the campaign's rule holds for an own library too - the first
     * solved (playable) tower, else A; a library of refused towers only opens on its first group with A, refused.
     */
    @Test
    void anOwnLibraryWithoutAnOpenTowerPreselectsTheFirstSolvedOneElseA() {
        TowerDefinition solved = EditorModelTest.playable("t-1");
        Profile profile = Profile.create("Anna", "original").withCustomSolved("mine", "t-1");
        OverviewModel overview = new OverviewModel(CAMPAIGN, profile, TEXTS,
                Optional.of(new CustomLibrary("mine", List.of(CustomLibraryTest.noTask("t-0"), solved))));

        assertEquals(0, overview.group());
        assertEquals(1, overview.selected(), "the solved, playable tower");
        assertEquals(new OverviewModel.Play(solved, false), overview.activate());

        List<TowerDefinition> refused = new ArrayList<>(CustomLibraryTest.playable("r", 9));
        refused.replaceAll(tower -> CustomLibraryTest.noTask(tower.id()));
        refused.add(solved);
        OverviewModel later = new OverviewModel(CAMPAIGN, profile, TEXTS,
                Optional.of(new CustomLibrary("mine", refused)));
        assertEquals(1, later.group(), "the group of the first startable tower");
        assertEquals(0, later.selected());

        OverviewModel none = new OverviewModel(CAMPAIGN, profile, TEXTS, Optional.of(new CustomLibrary("none",
                List.of(CustomLibraryTest.noTask("n-0"), CustomLibraryTest.noTask("n-1")))));
        assertEquals(0, none.group());
        assertEquals(0, none.selected());
        assertInstanceOf(OverviewModel.Refused.class, none.activate());
    }
}
