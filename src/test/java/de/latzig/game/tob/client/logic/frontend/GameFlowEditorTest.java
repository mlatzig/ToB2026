package de.latzig.game.tob.client.logic.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.latzig.game.tob.client.logic.Texts;
import de.latzig.game.tob.client.logic.editor.EditorElement;
import de.latzig.game.tob.client.logic.editor.EditorModelTest;
import de.latzig.game.tob.client.logic.editor.EditorScreensTest;
import de.latzig.game.tob.client.logic.editor.TowerDesigner;
import de.latzig.game.tob.client.logic.editor.TowerDesigner.Command;
import de.latzig.game.tob.client.logic.editor.TowerLibraries;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Hit;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Modal;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Screen;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Widget;
import de.latzig.game.tob.client.logic.ui.ScreenFade;
import de.latzig.game.tob.client.logic.ui.StartLayout;
import de.latzig.game.tob.client.logic.ui.UiKey;
import de.latzig.game.tob.core.io.TowerLibraryWriter;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.TowerGrid;
import de.latzig.game.tob.core.model.TowerLibrary;
import de.latzig.game.tob.core.sim.Rules;

/** Phase 6b spec decisions 6 and 9: the main menu's Tower Designer, its questions and the test play's round trip. */
class GameFlowEditorTest {

    private static final Campaign CAMPAIGN = Campaign.of(CampaignTest.towers());
    private static final Instant NOW = Instant.parse("2026-10-14T12:00:00Z");

    private static void settle(GameFlow flow) {
        for (int i = 0; i < 4; i++) {
            flow.update(ScreenFade.SECONDS);
        }
    }

    /** The main menu with the designer's libraries: the shipped {@code original.json} holds one playable tower. */
    private static GameFlow menu(Path dir) throws IOException {
        Path shipped = Files.createDirectories(dir.resolve("assets-towers"));
        new TowerLibraryWriter().write(new TowerLibrary("original", "Original", "test",
                List.of(EditorModelTest.playable("original-000"))), shipped.resolve("original.json"));
        GameFlow flow = new GameFlow(Texts.english(), new ProfileStore(dir.resolve("user")));
        flow.designer(new TowerLibraries(shipped, dir.resolve("user")));
        flow.loaded(CAMPAIGN);
        settle(flow);
        return flow;
    }

    private static GameFlow editor(Path dir) throws IOException {
        GameFlow flow = menu(dir);
        flow.click(Hit.of(Widget.MENU_DESIGNER));
        settle(flow);
        return flow;
    }

    private static Hit cell(int x, int y) {
        return new Hit(Widget.EDITOR_CELL, y * TowerGrid.WIDTH + x);
    }

    private static Hit item(Command command) {
        return new Hit(Widget.EDITOR_ITEM, command.ordinal());
    }

    /** Opens {@code command}'s menu and clicks the entry. */
    private static void choose(GameFlow flow, Command command) {
        flow.click(new Hit(Widget.EDITOR_MENU, command.menu().ordinal()));
        flow.click(item(command));
    }

    @Test
    void theTowerDesignerEntryOpensTheEditorAndExitLeadsBack(@TempDir Path dir) throws IOException {
        GameFlow flow = menu(dir);
        assertTrue(flow.isEnabled(Widget.MENU_DESIGNER), "spec decision 9");
        flow.key(UiKey.DOWN);
        assertEquals(Widget.MENU_DESIGNER, flow.focus(), "Phase 5c-1: below Play and the greyed Tutorial");

        flow.key(UiKey.ENTER);
        settle(flow);
        assertEquals(Screen.EDITOR, flow.screen());
        assertEquals("(NOT SAVED)", flow.designer().model().libraryLabel());

        flow.key(UiKey.ESCAPE);
        settle(flow);
        assertEquals(Screen.MENU, flow.screen(), "nothing unsaved: no question");
        assertEquals(null, flow.designer());
    }

    @Test
    void theEditorsQuestionsCoverTheEditor(@TempDir Path dir) throws IOException {
        GameFlow flow = editor(dir);
        flow.click(cell(2, 2));
        flow.key(UiKey.ESCAPE);
        assertEquals(Modal.EDITOR_CONFIRM, flow.modal());

        EditorScreensTest.assertCovered(StartScreens.draw(flow, Texts.english(), new StartLayout(1680, 720), -1f,
                -1f));
    }

    @Test
    void unsavedChangesAreAskedAboutBeforeLeaving(@TempDir Path dir) throws IOException {
        GameFlow flow = editor(dir);
        flow.click(cell(2, 2));

        flow.key(UiKey.ESCAPE);
        assertEquals(Modal.EDITOR_CONFIRM, flow.modal());
        assertEquals("UNSAVED CHANGES", flow.editorQuestion());
        assertFalse(flow.modalYes(), "NO preselected");
        flow.key(UiKey.ENTER);
        settle(flow);
        assertEquals(Screen.EDITOR, flow.screen());

        choose(flow, Command.EXIT);
        flow.click(Hit.of(Widget.YES));
        settle(flow);
        assertEquals(Screen.MENU, flow.screen());
    }

    @Test
    void aTestPlayRunsWithoutAProfileAndReturnsToTheEditorAsItWas(@TempDir Path dir) throws IOException {
        GameFlow flow = editor(dir);
        choose(flow, Command.OPEN);
        flow.key(UiKey.ENTER);
        TowerDesigner designer = flow.designer();
        designer.model().level(1);
        designer.model().cursor(6, 7);
        choose(flow, Command.FREEZE);
        choose(flow, Command.COPY);
        choose(flow, Command.NEW);
        choose(flow, Command.PASTE);
        assertTrue(designer.model().canUndo());

        choose(flow, Command.PLAY);
        settle(flow);
        assertEquals(Screen.GAME, flow.screen());
        TowerDesigner.Test test = flow.testPlay().orElseThrow();
        assertTrue(test.freezeEnemies());
        assertEquals(1, test.number());
        assertEquals(Optional.of(new GameFlow.StartTower(designer.model().tower(), false)), flow.poll());

        flow.attemptRestarted();
        flow.attemptEnded(Rules.EXIT_COMPLETED, 1, 500, NOW);
        assertEquals(Screen.EDITOR, flow.screen());
        assertEquals(Optional.empty(), flow.testPlay());
        assertEquals("Test play: Tower Completed", designer.model().status());
        assertEquals(java.util.OptionalInt.empty(), flow.interlude(), "Phase 5c-2 decision 6: no interlude");
        assertEquals(List.of(1, 6, 7), List.of(designer.model().level(), designer.model().x(), designer.model().y()));
        assertTrue(designer.model().canUndo(), "the history survives the test play");
        assertEquals(Optional.empty(), flow.profile());
        assertFalse(Files.exists(dir.resolve("user").resolve("savegames")), "no profile was written");

        // Phase 5c-2 plan decision 9: the test play did not advance the shared interlude counter either
        flow.developerSession(CAMPAIGN, CAMPAIGN.tower(0, 0));
        flow.attemptEnded(Rules.EXIT_COMPLETED, 3, 500, NOW);
        assertEquals(java.util.OptionalInt.of(1), flow.interlude(), "the test play did not advance the counter");
    }

    /**
     * Phase 5c-2 decision 3 at the flow level: the designer's test play is silent from the editor's fade-out to the
     * editor again (the editor and the game want no song, and neither does a test play).
     */
    @Test
    void aTestPlayIsSilentFromTheEditorBackToTheEditor(@TempDir Path dir) throws IOException {
        GameFlow flow = editor(dir);
        choose(flow, Command.OPEN);
        flow.key(UiKey.ENTER);
        assertEquals(Optional.empty(), MusicChoice.wanted(flow, true), "the editor");

        choose(flow, Command.PLAY);
        flow.update(ScreenFade.SECONDS / 2f);
        MusicChoice.Fade fade = MusicChoice.fade(flow, true);
        assertTrue(fade.fadingOut(), "EDITOR -> GAME fades out");
        assertEquals(Optional.empty(), fade.afterFade(), "no song after the fade");
        settle(flow);
        assertEquals(Screen.GAME, flow.screen());
        assertTrue(flow.testPlay().isPresent());
        assertEquals(Optional.empty(), MusicChoice.wanted(flow, true), "the test play");

        flow.attemptEnded(Rules.EXIT_SPIDERS_DESTROYED, 0, 500, NOW);
        assertEquals(Screen.EDITOR, flow.screen());
        assertEquals(Optional.empty(), MusicChoice.wanted(flow, true), "back in the editor");
        assertEquals(Optional.empty(), MusicChoice.fade(flow, true).afterFade());
    }

    @Test
    void aTowerThatCannotBeWonIsAskedAboutAndAnErrorRefused(@TempDir Path dir) throws IOException {
        GameFlow flow = editor(dir);
        choose(flow, Command.PLAY);
        assertEquals(Modal.EDITOR_CONFIRM, flow.modal());
        assertEquals("PLAY ANYWAY", flow.editorQuestion());
        flow.key(UiKey.ESCAPE);
        assertEquals(Modal.NONE, flow.modal());

        flow.click(new Hit(Widget.EDITOR_PALETTE, EditorElement.PALETTE.indexOf(EditorElement.of(ObjectType.ZAP))));
        flow.click(cell(4, 4));
        choose(flow, Command.PLAY);
        assertEquals(Modal.MESSAGE, flow.modal());
        assertEquals("No floor for object: you can't play this tower!", flow.message());
    }

    @Test
    void aFailedTestStartReturnsToTheEditorWithTheError(@TempDir Path dir) throws IOException {
        GameFlow flow = editor(dir);
        choose(flow, Command.PLAY);
        flow.click(Hit.of(Widget.YES));
        settle(flow);
        assertEquals(Screen.GAME, flow.screen());

        flow.gameLeft("Missing or unreadable asset: x");

        assertEquals(Screen.EDITOR, flow.screen());
        assertEquals(Modal.MESSAGE, flow.modal());
        assertEquals("Missing or unreadable asset: x", flow.message());
    }

    @Test
    void theLibraryOfTheLastVisitOpensAgainAndTypingGoesToSaveAs(@TempDir Path dir) throws IOException {
        GameFlow flow = editor(dir);
        choose(flow, Command.SAVE_AS);
        for (char c : "mine".toCharArray()) {
            flow.typed(c);
        }
        flow.key(UiKey.ENTER);
        assertEquals(Optional.of("mine"), flow.designer().model().library());
        flow.key(UiKey.ESCAPE);
        settle(flow);

        flow.click(Hit.of(Widget.MENU_DESIGNER));
        settle(flow);
        assertEquals(Optional.of("mine"), flow.designer().model().library());
        flow.wheel(-1);
        assertEquals(0, flow.scrollBox().orElseThrow().first(), "the wheel scrolls the tower list");
    }

    /** Final review M3: reopening the last visit's library keeps/shows its failure instead of swallowing it. */
    @Test
    void theLibraryOfTheLastVisitKeepsItsFailureInsteadOfSwallowingIt(@TempDir Path dir) throws IOException {
        GameFlow flow = editor(dir);
        choose(flow, Command.SAVE_AS);
        for (char c : "mine".toCharArray()) {
            flow.typed(c);
        }
        flow.key(UiKey.ENTER);
        flow.key(UiKey.ESCAPE);
        settle(flow);
        assertEquals(Screen.MENU, flow.screen());

        // the file is corrupted (or deleted) since the last visit
        Files.writeString(dir.resolve("user").resolve("towers").resolve("mine.json"), "{\"format\":\"other\"}");

        flow.click(Hit.of(Widget.MENU_DESIGNER));
        settle(flow);

        assertEquals(Screen.EDITOR, flow.screen());
        assertEquals(Modal.MESSAGE, flow.modal(), "the reopen failure is shown, not swallowed");
        assertEquals(Optional.empty(), flow.designer().model().library(), "a fresh, unsaved library instead");
    }

    /** Phase 6d decision 8: the designer opens the library of the last session again (settings.json). */
    @Test
    void theDesignerOpensTheLastLibraryOfAnEarlierSessionAgain(@TempDir Path dir) throws IOException {
        GameFlow flow = editor(dir);
        choose(flow, Command.SAVE_AS);
        for (char c : "mine".toCharArray()) {
            flow.typed(c);
        }
        flow.key(UiKey.ENTER);
        assertEquals(Optional.of("mine"), new ProfileStore(dir.resolve("user")).settings().read().designerLibrary(),
                "remembered at once");

        GameFlow next = editor(dir);
        assertEquals(Optional.of("mine"), next.designer().model().library(), "a new session opens it again");
        assertEquals(Modal.NONE, next.modal());

        choose(next, Command.OPEN);
        next.click(new Hit(Widget.EDITOR_DIALOG_LIST, 0));
        next.click(new Hit(Widget.EDITOR_DIALOG, TowerDesigner.DIALOG_OK));
        assertEquals("original (READ-ONLY)", editor(dir).designer().model().libraryLabel(),
                "a shipped library is remembered too and opens read-only");
    }

    /** Phase 6d decision 8: nothing remembered, or the remembered library's file is gone: a fresh library. */
    @Test
    void withoutARememberedLibraryOrWhenItsFileIsGoneTheDesignerStartsAFreshOne(@TempDir Path dir)
            throws IOException {
        assertEquals("(NOT SAVED)", editor(dir).designer().model().libraryLabel(), "nothing remembered");
        new ProfileStore(dir.resolve("user")).settings().update(settings -> settings.withDesignerLibrary("gone"));

        GameFlow flow = editor(dir);

        assertEquals("(NOT SAVED)", flow.designer().model().libraryLabel(), "as before 6d");
        assertEquals(Modal.NONE, flow.modal(), "no message for a library deleted since");
    }

    /** Fix wave (Task 2 Minor): a remembered library whose file exists but cannot be read shows 6b's message. */
    @Test
    void aRememberedLibraryThatCannotBeReadShowsTheMessageAndAFreshLibrary(@TempDir Path dir) throws IOException {
        new ProfileStore(dir.resolve("user")).settings().update(settings -> settings.withDesignerLibrary("mine"));
        Path towers = Files.createDirectories(dir.resolve("user").resolve("towers"));
        Files.writeString(towers.resolve("mine.json"), "{\"format\":\"other\"}");

        GameFlow flow = editor(dir);

        assertEquals(Screen.EDITOR, flow.screen());
        assertEquals(Modal.MESSAGE, flow.modal(), "the reopen failure is shown");
        assertEquals("frame.error", flow.messageCaption());
        assertEquals(Optional.empty(), flow.designer().model().library(), "a fresh, unsaved library instead");
    }

    /** Fix wave (Task 2 Minor): a library that could not be remembered (write failed) is tried again next time. */
    @Test
    void aLibraryThatCouldNotBeRememberedIsTriedAgainAtTheNextRequest(@TempDir Path dir) throws IOException {
        GameFlow flow = editor(dir);
        SettingsFile settings = new ProfileStore(dir.resolve("user")).settings();
        Path blocker = Files.createDirectories(settings.path()).resolve("blocker");
        Files.writeString(blocker, "settings.json is a folder: every write fails");
        choose(flow, Command.SAVE_AS);
        for (char c : "mine".toCharArray()) {
            flow.typed(c);
        }
        flow.key(UiKey.ENTER);
        assertEquals(Optional.of("mine"), flow.designer().model().library());
        Files.delete(blocker);
        Files.delete(settings.path());

        flow.click(cell(1, 1));

        assertEquals(Optional.of("mine"), settings.read().designerLibrary(), "written at the next chance");
    }

    /** Phase 6d decision 7: a shipped library saved as an own one gives its towers new ids (never "original-"). */
    @Test
    void aShippedLibrarySavedAsAnOwnOneGetsNewTowerIds(@TempDir Path dir) throws IOException {
        GameFlow flow = editor(dir);
        choose(flow, Command.OPEN);
        flow.click(new Hit(Widget.EDITOR_DIALOG_LIST, 0));
        flow.click(new Hit(Widget.EDITOR_DIALOG, TowerDesigner.DIALOG_OK));
        assertEquals("original (READ-ONLY)", flow.designer().model().libraryLabel());

        choose(flow, Command.SAVE_AS);
        for (char c : "mine".toCharArray()) {
            flow.typed(c);
        }
        flow.key(UiKey.ENTER);

        List<String> ids = new TowerLibraries(dir.resolve("assets-towers"), dir.resolve("user"))
                .open(new TowerLibraries.Entry("mine", false)).towers().stream().map(tower -> tower.id()).toList();
        assertEquals(1, ids.size());
        assertTrue(ids.get(0).matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"), ids.toString());
        assertEquals(ids, flow.designer().model().towers().stream().map(tower -> tower.id()).toList(),
                "the model keeps them");
    }
}
