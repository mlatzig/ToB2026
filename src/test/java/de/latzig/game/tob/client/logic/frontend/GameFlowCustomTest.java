package de.latzig.game.tob.client.logic.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.latzig.game.tob.client.logic.Texts;
import de.latzig.game.tob.client.logic.editor.EditorModel;
import de.latzig.game.tob.client.logic.editor.EditorModelTest;
import de.latzig.game.tob.client.logic.editor.TowerLibraries;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Effect;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Hit;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Modal;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Screen;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Widget;
import de.latzig.game.tob.client.logic.ui.Drawing;
import de.latzig.game.tob.client.logic.ui.ScreenFade;
import de.latzig.game.tob.client.logic.ui.StartLayout;
import de.latzig.game.tob.client.logic.ui.UiKey;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.progress.Profile;
import de.latzig.game.tob.core.sim.Rules;

/**
 * Phase 6d spec decisions 2–7: own towers played from the Overview — the Library button, groups of 9 all open, the
 * campaign's start and result path without score, the solved marks per library, the guard for a tower without a task.
 */
class GameFlowCustomTest {

    private static final Campaign CAMPAIGN = Campaign.of(CampaignTest.towers());
    private static final Instant NOW = Instant.parse("2026-10-16T12:00:00Z");
    private static final Texts TEXTS = Texts.english();
    private static final StartLayout LAYOUT = new StartLayout(1280, 720);

    private static void settle(GameFlow flow) {
        for (int i = 0; i < 4; i++) {
            flow.update(ScreenFade.SECONDS);
        }
    }

    private static void keys(GameFlow flow, UiKey... keys) {
        for (UiKey key : keys) {
            flow.key(key);
            settle(flow);
        }
    }

    private static TowerLibraries libraries(Path dir) {
        return new TowerLibraries(dir.resolve("assets-towers"), dir.resolve("user"));
    }

    /** "mine": the playable towers mine-0 … mine-8 and mine-9 without a task; "zeta": one tower. */
    private static void ownLibraries(Path dir) throws IOException {
        List<TowerDefinition> mine = new ArrayList<>(CustomLibraryTest.playable("mine", 9));
        mine.add(CustomLibraryTest.noTask("mine-9"));
        libraries(dir).save("mine", mine);
        libraries(dir).save("zeta", CustomLibraryTest.playable("zeta", 1));
    }

    /** The menu with the designer's libraries, then Play → the profile Anna (created the first time) → Overview. */
    private static GameFlow overview(Path dir) {
        ProfileStore store = new ProfileStore(dir.resolve("user"));
        boolean known = store.exists("Anna");
        GameFlow flow = new GameFlow(TEXTS, store);
        flow.designer(libraries(dir));
        flow.loaded(CAMPAIGN);
        settle(flow);
        keys(flow, UiKey.ENTER, UiKey.ENTER);
        if (!known) {
            for (char c : "Anna".toCharArray()) {
                flow.typed(c);
            }
            keys(flow, UiKey.ENTER, UiKey.ENTER);
        }
        return flow;
    }

    private static Profile anna(Path dir) {
        return new ProfileStore(dir.resolve("user")).find("Anna").orElseThrow();
    }

    private static List<Effect> effects(GameFlow flow) {
        List<Effect> all = new ArrayList<>();
        for (Optional<Effect> effect = flow.poll(); effect.isPresent(); effect = flow.poll()) {
            all.add(effect.get());
        }
        return all;
    }

    private static Optional<Drawing.Label> label(StartScreens.Frame frame, String text) {
        return frame.drawing().labels().stream().filter(label -> label.text().equals(text)).findFirst();
    }

    private static List<Widget> clickable(StartScreens.Frame frame) {
        return frame.areas().stream().map(area -> area.hit().widget()).toList();
    }

    /** Decision 2: Original → the own libraries by name → Original; a library that cannot be read is skipped. */
    @Test
    void theLibraryButtonStepsFromTheOriginalThroughTheOwnLibrariesByNameAndBack(@TempDir Path dir)
            throws IOException {
        ownLibraries(dir);
        Files.writeString(libraries(dir).folder().resolve("bad.json"), "{\"format\":\"other\"}");
        GameFlow flow = overview(dir);
        assertEquals(Screen.OVERVIEW, flow.screen());
        assertEquals("Anna", flow.overview().caption());
        assertTrue(flow.isEnabled(Widget.LIBRARY));
        assertEquals(List.of(Widget.GRID, Widget.PREVIOUS_GROUP, Widget.NEXT_GROUP, Widget.LIBRARY, Widget.PLAY,
                Widget.BACK), GameFlow.OVERVIEW_FOCUS);

        flow.click(Hit.of(Widget.LIBRARY));

        assertEquals(Screen.OVERVIEW, flow.screen(), "no fade: the grid changes in place");
        assertEquals("Anna (mine)", flow.overview().caption(), "\"bad\" cannot be read: skipped");
        assertEquals(Widget.LIBRARY, flow.focus());
        assertEquals(Optional.of("mine"), anna(dir).customLibrary(), "remembered in the profile, saved");
        flow.click(Hit.of(Widget.LIBRARY));
        assertEquals("Anna (zeta)", flow.overview().caption());
        flow.click(Hit.of(Widget.LIBRARY));
        assertEquals("Anna", flow.overview().caption(), "back to the original towers");
        assertEquals(Optional.empty(), flow.overview().custom());
        assertEquals(Optional.empty(), anna(dir).customLibrary());
        flow.click(Hit.of(Widget.LIBRARY));
        assertEquals(Modal.NONE, flow.modal());

        GameFlow next = overview(dir);
        assertEquals("Anna (mine)", next.overview().caption(), "decision 5: the profile's last library again");
    }

    /** Decision 3: groups of 9, all open, the group buttons; the last group ends after the last tower. */
    @Test
    void ownTowersComeInGroupsOfNineAllOpen(@TempDir Path dir) throws IOException {
        ownLibraries(dir);
        GameFlow flow = overview(dir);
        flow.click(Hit.of(Widget.LIBRARY));
        assertTrue(flow.overview().cells().stream().allMatch(cell -> cell.state() == OverviewModel.State.OPEN));
        assertFalse(flow.isEnabled(Widget.PREVIOUS_GROUP));
        assertTrue(flow.isEnabled(Widget.NEXT_GROUP), "nothing solved: all open");

        flow.click(Hit.of(Widget.NEXT_GROUP));

        assertEquals(1, flow.overview().group());
        assertEquals(OverviewModel.State.LOCKED, flow.overview().cells().get(0).state(), "mine-9 has no task");
        assertEquals(8L, flow.overview().cells().stream().filter(cell -> cell.state() == OverviewModel.State.EMPTY)
                .count());
        assertEquals(0, anna(dir).group(), "the campaign's group stays");
        assertFalse(flow.isEnabled(Widget.NEXT_GROUP));
        assertEquals(Widget.GRID, flow.focus(), "the disabled button gives the focus back to the grid");
        flow.click(Hit.of(Widget.PREVIOUS_GROUP));
        assertEquals(0, flow.overview().group());
    }

    /** Decisions 4 and 5: Briefing, game, result with interlude; no score, no attempt; solved for its library. */
    @Test
    void anOwnTowerIsPlayedWithoutScoreAndMarkedSolvedForItsLibrary(@TempDir Path dir) throws IOException {
        ownLibraries(dir);
        GameFlow flow = overview(dir);
        flow.click(Hit.of(Widget.LIBRARY));
        flow.click(new Hit(Widget.GRID, 0));
        effects(flow);

        keys(flow, UiKey.ENTER);
        assertEquals(Screen.BRIEFING, flow.screen());
        assertEquals("mine-0", flow.tower().id());
        assertFalse(flow.practice());
        flow.skyProgress(1f);
        keys(flow, UiKey.ENTER);
        assertEquals(Screen.GAME, flow.screen());
        List<Effect> started = effects(flow);
        assertTrue(started.contains(new GameFlow.StartTower(flow.tower(), false)), started.toString());
        assertEquals(OptionalInt.of(1), flow.customNumber("mine-0"), "the title bar's number in its library");
        assertEquals(Map.of(), anna(dir).towers(), "no attempt counted");

        flow.attemptEnded(Rules.EXIT_COMPLETED, 3, 500, NOW);
        settle(flow);

        assertEquals(Screen.RESULT, flow.screen());
        assertEquals("Tower Completed", flow.result().caption());
        assertEquals(List.of(new ResultText.Line("Own Tower", "no score")), flow.result().score());
        assertTrue(flow.interlude().isPresent(), "an interlude as for the originals");
        assertFalse(flow.retryAvailable());
        Profile saved = anna(dir);
        assertTrue(saved.customSolved("mine", "mine-0"));
        assertEquals(0L, saved.score(), "no score");
        assertEquals(Map.of(), saved.towers());

        keys(flow, UiKey.ENTER);
        assertEquals(Screen.OVERVIEW, flow.screen());
        assertEquals("Anna (mine)", flow.overview().caption());
        assertEquals(OverviewModel.State.SOLVED, flow.overview().cells().get(0).state(), "green");
        assertEquals(0, flow.overview().selected(), "the tower just played stays selected");
        assertEquals("Solved: 1 of 10", flow.overview().scoreText());
        assertEquals(OptionalInt.empty(), flow.customNumber("mine-0"), "fix wave: no own tower played any more");
        assertEquals(OptionalInt.empty(), overview(dir).customNumber("mine-0"), "only while one is played");
    }

    /** Decision 4: Retry after a failure as usual; the mark survives saving and a new session. */
    @Test
    void aFailedOwnTowerOffersRetryAndTheMarkSurvivesANewSession(@TempDir Path dir) throws IOException {
        ownLibraries(dir);
        GameFlow flow = overview(dir);
        flow.click(Hit.of(Widget.LIBRARY));
        keys(flow, UiKey.UP, UiKey.ENTER);
        flow.skyProgress(1f);
        keys(flow, UiKey.ENTER);
        flow.attemptRestarted();
        flow.attemptEnded(Rules.EXIT_TIMEOUT, 3, 500, NOW);
        settle(flow);
        assertEquals("Tower Not Completed", flow.result().caption());
        assertEquals(List.of(), flow.result().score());
        assertTrue(flow.retryAvailable());
        assertEquals(Map.of(), anna(dir).towers(), "neither the restart nor the failure count");

        flow.click(Hit.of(Widget.RETRY));
        settle(flow);
        assertEquals(Screen.GAME, flow.screen());
        flow.attemptEnded(Rules.EXIT_COMPLETED, 3, 500, NOW);
        settle(flow);

        GameFlow next = overview(dir);
        assertEquals("Anna (mine)", next.overview().caption());
        assertEquals(OverviewModel.State.SOLVED, next.overview().cells().get(0).state());
        assertEquals(1, next.overview().selected(), "the next open tower");
    }

    /**
     * Decision 5: an own tower never touches the campaign — no score, no attempts, no unlocking, no tutorial mark,
     * even when its id is a campaign tower's — and the music and the interludes follow the originals.
     */
    @Test
    void anOwnTowerNeverTouchesTheCampaignOrTheTutorialAndKeepsTheOriginalsMusic(@TempDir Path dir)
            throws IOException {
        List<TowerDefinition> clash = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            clash.add(EditorModelTest.playable(String.format("original-%03d", i)));
        }
        libraries(dir).save("clash", clash);
        GameFlow flow = overview(dir);
        flow.click(Hit.of(Widget.LIBRARY));
        assertEquals("Anna (clash)", flow.overview().caption());
        List<Integer> interludes = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            flow.click(new Hit(Widget.GRID, i));
            keys(flow, UiKey.ENTER);
            assertEquals(Optional.of(MusicChoice.Song.DREAMLAND), MusicChoice.wanted(flow, true), "Briefing");
            flow.skyProgress(1f);
            keys(flow, UiKey.ENTER);
            assertEquals(Optional.empty(), MusicChoice.wanted(flow, true), "in the game");
            flow.attemptEnded(Rules.EXIT_COMPLETED, 3, 500, NOW);
            settle(flow);
            assertEquals(Optional.of(MusicChoice.Song.IMPROVI), MusicChoice.wanted(flow, true));
            interludes.add(flow.interlude().orElseThrow());
            assertEquals(List.of(), flow.allSolvedLines());
            keys(flow, UiKey.ENTER);
            assertEquals(Screen.OVERVIEW, flow.screen());
        }
        assertEquals(List.of(1, 2, 3, 4, 5, 0, 1), interludes, "the original's shared counter, demos 0-5");

        Profile saved = anna(dir);
        assertEquals(0L, saved.score());
        assertEquals(Map.of(), saved.towers(), "the campaign's progress untouched");
        assertEquals(0, saved.group());
        assertTrue(saved.customSolved("clash", "original-006"));
        assertEquals(Set.of(), new ProfileStore(dir.resolve("user")).settings().read().solvedTutorials());
        flow.click(Hit.of(Widget.LIBRARY));
        assertEquals("Anna", flow.overview().caption());
        assertEquals("Score: 000000", flow.overview().scoreText());
        assertFalse(flow.isEnabled(Widget.NEXT_GROUP), "7 own towers solved unlock no campaign group");
        assertTrue(flow.overview().cells().stream().noneMatch(cell -> cell.state() == OverviewModel.State.SOLVED));

        flow.click(Hit.of(Widget.LIBRARY));
        flow.click(new Hit(Widget.GRID, 0));
        keys(flow, UiKey.ENTER);
        flow.skyProgress(1f);
        keys(flow, UiKey.ENTER);
        flow.attemptEnded(Rules.EXIT_TIMEOUT, 0, 500, NOW);
        settle(flow);
        assertEquals(Optional.of(MusicChoice.Song.FADEOUT), MusicChoice.wanted(flow, true), "a failure's song");
        assertEquals(OptionalInt.of(8), flow.interlude(), "a failure's demos 6-11");
    }

    /**
     * Decision 6, amended (author, acceptance 2026-10-17): a tower without a task cannot be started; the Overview
     * shows the designer's own words for it ({@code EditorFindings.messageKey}), not the original's hint.
     */
    @Test
    void aTowerWithoutATaskCannotBeStartedAndShowsTheOriginalsHint(@TempDir Path dir) throws IOException {
        ownLibraries(dir);
        GameFlow flow = overview(dir);
        flow.click(Hit.of(Widget.LIBRARY));
        flow.click(Hit.of(Widget.NEXT_GROUP));
        flow.click(new Hit(Widget.GRID, 0));
        assertTrue(flow.isEnabled(Widget.PLAY), "Play stays enabled: it tells why");
        effects(flow);

        keys(flow, UiKey.ENTER);

        assertEquals(Screen.OVERVIEW, flow.screen());
        assertEquals(Modal.MESSAGE, flow.modal());
        assertEquals("No Task!", flow.message(), "author's acceptance amendment 2026-10-17: the designer's words");
        assertEquals("frame.notice", flow.messageCaption(), "fix wave: a hint, not an error");
        assertTrue(label(StartScreens.draw(flow, TEXTS, LAYOUT, -1f, -1f), "Note").isPresent(), "the box's caption");
        assertEquals(List.of(), effects(flow), "nothing started");
        keys(flow, UiKey.ENTER);
        assertEquals(Modal.NONE, flow.modal());
        flow.doubleClick(new Hit(Widget.GRID, 0));
        assertEquals(Modal.MESSAGE, flow.modal(), "a double click is refused the same way");
    }

    /**
     * Decisions 5 and 7: a mark stays on its tower when the designer inserts a tower before it. The inserted blank
     * tower is itself greyed: it has the default goal but no spider (author's acceptance finding, 2026-10-17).
     */
    @Test
    void aMarkStaysOnItsTowerWhenATowerIsInsertedBeforeIt(@TempDir Path dir) throws IOException {
        ownLibraries(dir);
        ProfileStore store = new ProfileStore(dir.resolve("user"));
        store.save(Profile.create("Anna", "original").withCustomLibrary(Optional.of("mine"))
                .withCustomSolved("mine", "mine-1"));
        store.rememberLast("Anna");
        EditorModel designer = new EditorModel(TEXTS);
        designer.load(libraries(dir).open(new TowerLibraries.Entry("mine", false)));
        designer.select(1);
        designer.insert();
        libraries(dir).save("mine", designer.towersToSave());

        GameFlow flow = overview(dir);

        List<OverviewModel.Cell> cells = flow.overview().cells();
        assertEquals(OverviewModel.State.LOCKED, cells.get(1).state(), "the inserted blank tower has no spider");
        assertEquals(OverviewModel.State.SOLVED, cells.get(2).state(), "mine-1, one place further on");
        assertEquals("Solved: 1 of 11", flow.overview().scoreText());
    }

    /** Without own libraries the button says so; without the designer's libraries it is greyed. */
    @Test
    void withoutOwnLibrariesTheButtonSaysSoAndWithoutTheDesignerItIsGreyed(@TempDir Path dir) {
        GameFlow flow = overview(dir);
        flow.click(Hit.of(Widget.LIBRARY));
        assertEquals(Modal.MESSAGE, flow.modal());
        assertEquals("No own tower libraries yet", flow.message());
        assertEquals("frame.notice", flow.messageCaption());
        assertEquals("Anna", flow.overview().caption());

        GameFlow plain = new GameFlow(TEXTS, new ProfileStore(dir.resolve("user")));
        plain.loaded(CAMPAIGN);
        settle(plain);
        keys(plain, UiKey.ENTER, UiKey.ENTER);
        assertEquals(Screen.OVERVIEW, plain.screen());
        assertFalse(plain.isEnabled(Widget.LIBRARY));
        Set<Widget> visited = new HashSet<>();
        for (int i = 0; i < 2 * GameFlow.OVERVIEW_FOCUS.size(); i++) {
            keys(plain, UiKey.TAB);
            visited.add(plain.focus());
        }
        assertTrue(visited.contains(Widget.PLAY), visited.toString());
        assertFalse(visited.contains(Widget.LIBRARY), "fix wave: Tab skips the greyed button");
    }

    /** Fix wave (Task 5 Minor): the only own libraries were deleted while one is shown: back to the originals. */
    @Test
    void deletingTheShownLibraryLeadsBackToTheOriginalTowersAtTheNextPress(@TempDir Path dir) throws IOException {
        ownLibraries(dir);
        GameFlow flow = overview(dir);
        flow.click(Hit.of(Widget.LIBRARY));
        assertEquals("Anna (mine)", flow.overview().caption());
        Files.delete(libraries(dir).folder().resolve("mine.json"));
        Files.delete(libraries(dir).folder().resolve("zeta.json"));

        flow.click(Hit.of(Widget.LIBRARY));

        assertEquals(Modal.MESSAGE, flow.modal());
        assertEquals("No own tower libraries yet", flow.message());
        assertEquals("Anna", flow.overview().caption(), "not the deleted library any more");
        assertEquals(Optional.empty(), anna(dir).customLibrary(), "forgotten in the profile");
    }

    /** Fix wave (Task 5 Minor): own libraries exist but none can be read - the press says so. */
    @Test
    void whenNoOwnLibraryCanBeReadThePressSaysSo(@TempDir Path dir) throws IOException {
        Files.createDirectories(libraries(dir).folder());
        Files.writeString(libraries(dir).folder().resolve("bad.json"), "{\"format\":\"other\"}");
        GameFlow flow = overview(dir);

        flow.click(Hit.of(Widget.LIBRARY));

        assertEquals(Modal.MESSAGE, flow.modal());
        assertEquals("No own tower library can be read", flow.message());
        assertEquals("frame.notice", flow.messageCaption());
        assertEquals("Anna", flow.overview().caption());
    }

    /** Fix wave (Task 5 Minor): a remembered library whose file is gone shows the original towers. */
    @Test
    void aRememberedLibraryWhoseFileIsGoneShowsTheOriginalTowers(@TempDir Path dir) throws IOException {
        ownLibraries(dir);
        overview(dir).click(Hit.of(Widget.LIBRARY));
        assertEquals(Optional.of("mine"), anna(dir).customLibrary());
        Files.delete(libraries(dir).folder().resolve("mine.json"));

        GameFlow flow = overview(dir);

        assertEquals(Screen.OVERVIEW, flow.screen());
        assertEquals("Anna", flow.overview().caption());
        assertEquals(Modal.NONE, flow.modal());
    }

    /** Fix wave (Task 5 Minor): the Library button is reached and pressed with the keyboard. */
    @Test
    void theLibraryButtonIsReachedWithTabAndPressedWithEnter(@TempDir Path dir) throws IOException {
        ownLibraries(dir);
        GameFlow flow = overview(dir);
        assertEquals(Widget.GRID, flow.focus());
        for (int i = 0; i < GameFlow.OVERVIEW_FOCUS.size() && flow.focus() != Widget.LIBRARY; i++) {
            keys(flow, UiKey.TAB);
        }
        assertEquals(Widget.LIBRARY, flow.focus());

        keys(flow, UiKey.ENTER);

        assertEquals("Anna (mine)", flow.overview().caption());
        assertEquals(Widget.LIBRARY, flow.focus());
    }

    /** Plan decision 1: Library in the bar; the own library's caption and header; no letter past the last tower. */
    @Test
    void theOverviewOfAnOwnLibraryShowsItsCaptionHeaderAndTheLibraryButton(@TempDir Path dir) throws IOException {
        ownLibraries(dir);
        GameFlow flow = overview(dir);
        flow.click(Hit.of(Widget.LIBRARY));

        StartScreens.Frame frame = StartScreens.draw(flow, TEXTS, LAYOUT, -1f, -1f);

        assertTrue(label(frame, "Anna (mine)").isPresent(), "the caption");
        assertEquals(Drawing.GREY, label(frame, "Solved: 0 of 10").orElseThrow().colour());
        assertTrue(label(frame, "Group 01 (  0%)").isPresent());
        assertTrue(label(frame, "<Library>").isPresent(), "the focused Library button");
        assertTrue(label(frame, "Play").isPresent());
        assertTrue(clickable(frame).containsAll(List.of(Widget.LIBRARY, Widget.PLAY, Widget.BACK,
                Widget.NEXT_GROUP)));

        flow.click(Hit.of(Widget.NEXT_GROUP));
        StartScreens.Frame last = StartScreens.draw(flow, TEXTS, LAYOUT, -1f, -1f);
        assertEquals(Drawing.GREY, label(last, "A").orElseThrow().colour(), "mine-9 without a task: greyed");
        assertTrue(label(last, "B").isEmpty(), "no letter past the last tower");
        assertEquals(1L, clickable(last).stream().filter(widget -> widget == Widget.GRID).count(), "one cell");
    }
}
