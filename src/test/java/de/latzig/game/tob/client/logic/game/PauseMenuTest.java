package de.latzig.game.tob.client.logic.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.client.logic.game.ConfirmDialogLayout.Answer;
import de.latzig.game.tob.client.logic.game.PauseMenu.Choice;
import de.latzig.game.tob.client.logic.game.PauseMenu.Level;

class PauseMenuTest {

    private static final PanelLayout LAYOUT = PanelLayout.fullWidth(1280, 720);
    private static final ConfirmDialogLayout DIALOG = ConfirmDialogLayout.of(LAYOUT, false);

    private static Optional<Choice> clickTitle(PauseMenu menu) {
        PanelLayout.Rect title = LAYOUT.menuTitle();
        return menu.click(title.x() + 10f, title.y() + 10f, LAYOUT, DIALOG);
    }

    private static Optional<Choice> clickRow(PauseMenu menu, int row) {
        PanelLayout.Rect rect = LAYOUT.menuRow(row);
        return menu.click(rect.x() + 10f, rect.y() + 10f, LAYOUT, DIALOG);
    }

    private static Optional<Choice> clickAnswer(PauseMenu menu, Answer answer) {
        PanelLayout.Rect rect = DIALOG.button(answer);
        return menu.click(rect.x() + 10f, rect.y() + 10f, LAYOUT, DIALOG);
    }

    private static void hoverAnswer(PauseMenu menu, Answer answer) {
        PanelLayout.Rect rect = DIALOG.button(answer);
        menu.hover(rect.x() + 10f, rect.y() + 10f, LAYOUT, DIALOG);
    }

    @Test
    void theMouseAbandonsOrRestartsOnlyAfterYes() {
        for (Choice choice : PauseMenu.ENTRIES) {
            PauseMenu menu = new PauseMenu();
            assertEquals(Optional.empty(), clickTitle(menu));
            assertEquals(Level.EXIT, menu.level());
            assertEquals(List.of("ABANDON", "RESTART"), menu.rows());

            assertEquals(Optional.empty(), clickRow(menu, choice.ordinal()));
            assertEquals(Level.CONFIRM, menu.level());
            assertEquals(List.of(), menu.rows(), "round 2: the drop-down closes behind the dialog");
            PanelLayout.Rect question = DIALOG.messageGroup();
            assertEquals(Optional.empty(), menu.click(question.x() + 10f, question.y() + 10f, LAYOUT, DIALOG),
                    "the question itself does nothing");
            assertEquals(Optional.empty(), clickAnswer(menu, Answer.NO), "NO");
            assertEquals(Level.EXIT, menu.level(), "NO returns to the menu");

            clickRow(menu, choice.ordinal());
            assertEquals(Optional.of(choice), clickAnswer(menu, Answer.YES), "YES");
            assertEquals(Level.CLOSED, menu.level());
        }
    }

    @Test
    void theKeysAbandonOrRestartOnlyAfterYes() {
        for (Choice choice : PauseMenu.ENTRIES) {
            PauseMenu menu = new PauseMenu();
            menu.key(GameAction.MENU_CONFIRM);
            assertEquals(Level.EXIT, menu.level(), "Enter opens the menu");
            assertEquals(-1, menu.highlightedRow(), "item 7: nothing highlighted on open");
            assertEquals(Optional.empty(), menu.key(GameAction.MENU_CONFIRM));
            assertEquals(Level.EXIT, menu.level(), "item 7: Enter with nothing highlighted does nothing");
            // item 7: the first Down highlights the first entry, the first Up the last
            menu.key(choice == Choice.ABANDON ? GameAction.BACK : GameAction.FORWARD);
            assertEquals(choice.ordinal(), menu.highlightedRow());
            menu.key(GameAction.MENU_CONFIRM);
            assertEquals(Level.CONFIRM, menu.level());
            assertFalse(menu.yesHighlighted(), "NO is highlighted first");
            assertEquals(Optional.empty(), menu.key(GameAction.MENU_CONFIRM), "Enter on NO");
            assertEquals(Level.EXIT, menu.level());
            assertEquals(-1, menu.highlightedRow(), "item 7: back at the EXIT level, nothing highlighted");

            menu.key(choice == Choice.ABANDON ? GameAction.BACK : GameAction.FORWARD);
            menu.key(GameAction.MENU_CONFIRM);
            menu.key(GameAction.TURN_LEFT);
            assertTrue(menu.yesHighlighted());
            menu.key(GameAction.FORWARD);
            menu.key(GameAction.BACK);
            assertEquals(Optional.of(choice), menu.key(GameAction.MENU_CONFIRM), "Enter on YES");
            assertEquals(Level.CLOSED, menu.level());
        }
    }

    @Test
    void escGoesBackOneLevelAndOpensTheClosedMenu() {
        PauseMenu menu = new PauseMenu();
        menu.escape();
        assertEquals(Level.EXIT, menu.level(), "Esc while paused with the menu closed opens it");
        menu.key(GameAction.BACK);
        menu.key(GameAction.MENU_CONFIRM);
        assertEquals(Level.CONFIRM, menu.level());
        menu.escape();
        assertEquals(Level.EXIT, menu.level());
        menu.escape();
        assertEquals(Level.CLOSED, menu.level(), "the pause itself stays (the caller's business)");
    }

    @Test
    void otherClicksAndKeysDoNothing() {
        PauseMenu menu = new PauseMenu();
        assertEquals(Optional.empty(), clickRow(menu, 0), "rows are not there while closed");
        assertEquals(Level.CLOSED, menu.level());
        menu.key(GameAction.FIRE);
        menu.key(GameAction.BACK);
        assertEquals(Level.CLOSED, menu.level(), "only Enter opens it");

        menu.open();
        assertEquals(Optional.empty(), menu.click(1000f, 300f, LAYOUT, DIALOG), "outside the menu");
        assertEquals(Optional.empty(), clickRow(menu, 2), "no third row at the EXIT level");
        assertEquals(Level.EXIT, menu.level());
        menu.key(GameAction.TURN_RIGHT);
        assertEquals(-1, menu.highlightedRow(), "left/right do not highlight at the EXIT level");
        menu.key(GameAction.BACK);
        menu.key(GameAction.TURN_LEFT);
        assertEquals(0, menu.highlightedRow(), "left/right do not move the EXIT highlight");
        clickTitle(menu);
        assertEquals(Level.CLOSED, menu.level(), "a click on the title closes the open menu");
        assertFalse(menu.yesHighlighted());
        assertEquals(-1, menu.highlightedRow());
    }

    private static void hoverRow(PauseMenu menu, int row) {
        PanelLayout.Rect rect = LAYOUT.menuRow(row);
        menu.hover(rect.x() + 10f, rect.y() + 10f, LAYOUT, DIALOG);
    }

    /** Item 7 (author, 2026-10-09): the EXIT level opens with nothing highlighted, however it was opened. */
    @Test
    void theExitLevelOpensWithNothingHighlighted() {
        PauseMenu menu = new PauseMenu();
        assertEquals(-1, menu.highlightedRow(), "paused by P/Pause: closed, nothing highlighted");
        menu.escape();
        assertEquals(Level.EXIT, menu.level());
        assertEquals(-1, menu.highlightedRow(), "opened by Esc (or the title-bar X)");
        menu.key(GameAction.BACK);
        assertEquals(0, menu.highlightedRow());
        menu.escape();
        clickTitle(menu);
        assertEquals(Level.EXIT, menu.level());
        assertEquals(-1, menu.highlightedRow(), "opened by a click on the title: the old highlight is gone");
        menu.close();
        menu.open();
        assertEquals(-1, menu.highlightedRow(), "opened again");
        menu.key(GameAction.BACK);
        menu.key(GameAction.MENU_CONFIRM);
        menu.escape();
        assertEquals(Level.EXIT, menu.level());
        assertEquals(-1, menu.highlightedRow(), "back from ARE YOU SURE?: nothing highlighted either");
    }

    /** Item 7: further arrow presses move the highlight (and wrap round, as before). */
    @Test
    void theArrowsHighlightFirstThenMove() {
        PauseMenu down = new PauseMenu();
        down.open();
        down.key(GameAction.BACK);
        assertEquals(0, down.highlightedRow(), "first Down: the first entry");
        down.key(GameAction.BACK);
        assertEquals(1, down.highlightedRow());
        down.key(GameAction.BACK);
        assertEquals(0, down.highlightedRow());

        PauseMenu up = new PauseMenu();
        up.open();
        up.key(GameAction.FORWARD);
        assertEquals(1, up.highlightedRow(), "first Up: the last entry");
        up.key(GameAction.FORWARD);
        assertEquals(0, up.highlightedRow());
    }

    /**
     * Item 7: the highlight follows the cursor over the entries (and over YES/NO); off the entries the last
     * highlight stays, so a resting mouse does not undo the arrow keys. ARE YOU SURE? opens with NO highlighted.
     */
    @Test
    void theHighlightFollowsTheCursorAndTheConfirmStepPreselectsNo() {
        PauseMenu menu = new PauseMenu();
        hoverRow(menu, 0);
        assertEquals(-1, menu.highlightedRow(), "closed: nothing to highlight");
        menu.open();
        hoverRow(menu, 1);
        assertEquals(1, menu.highlightedRow());
        hoverRow(menu, 0);
        assertEquals(0, menu.highlightedRow());
        menu.hover(1000f, 300f, LAYOUT, DIALOG);
        assertEquals(0, menu.highlightedRow(), "off the entries: the highlight stays");
        hoverRow(menu, 2);
        assertEquals(0, menu.highlightedRow(), "no third row at the EXIT level");

        assertEquals(Optional.empty(), menu.key(GameAction.MENU_CONFIRM));
        assertEquals(Level.CONFIRM, menu.level());
        assertEquals(Choice.ABANDON, menu.choice());
        assertFalse(menu.yesHighlighted(), "NO preselected");
        assertEquals(-1, menu.highlightedRow(), "no menu row highlighted behind the dialog");
        hoverAnswer(menu, Answer.YES);
        assertTrue(menu.yesHighlighted(), "hover YES");
        PanelLayout.Rect question = DIALOG.messageGroup();
        menu.hover(question.x() + 10f, question.y() + 10f, LAYOUT, DIALOG);
        assertTrue(menu.yesHighlighted(), "the question is no entry");
        hoverRow(menu, 1);
        assertTrue(menu.yesHighlighted(), "the menu rows behind the dialog are no entries");
        hoverAnswer(menu, Answer.NO);
        assertFalse(menu.yesHighlighted(), "hover NO");
    }

    /** Round 2, item 2: while ARE YOU SURE? is open, clicks outside the dialog do nothing (modal). */
    @Test
    void theConfirmDialogIsModal() {
        PauseMenu menu = new PauseMenu();
        menu.open();
        menu.key(GameAction.FORWARD);
        menu.key(GameAction.MENU_CONFIRM);
        assertEquals(Level.CONFIRM, menu.level());
        assertEquals(Choice.RESTART, menu.choice());

        PanelLayout.Rect window = DIALOG.window();
        PanelLayout.Rect question = DIALOG.messageGroup();
        PanelLayout.Rect caption = DIALOG.caption();
        for (float[] point : new float[][] {{10f, 10f}, {window.x() - 5f, window.y() + 5f},
                {LAYOUT.menuTitle().x() + 10f, LAYOUT.menuTitle().y() + 10f},
                {LAYOUT.menuRow(0).x() + 10f, LAYOUT.menuRow(0).y() + 10f},
                {LAYOUT.menuRow(1).x() + 10f, LAYOUT.menuRow(1).y() + 10f},
                {question.x() + 10f, question.y() + 10f}, {caption.x() + 10f, caption.y() + 10f}}) {
            assertEquals(Optional.empty(), menu.click(point[0], point[1], LAYOUT, DIALOG));
            assertEquals(Level.CONFIRM, menu.level(), "still asking after a click at " + point[0] + "," + point[1]);
            assertEquals(Choice.RESTART, menu.choice());
        }
        assertFalse(menu.yesHighlighted());
    }

    /**
     * Phase 5a decision 1: F9 and F10 open ARE YOU SURE? directly, NO highlighted, as if RESTART or ABANDON had been
     * chosen in the EXIT level; NO returns to the EXIT level, YES returns the choice.
     */
    @Test
    void confirmAsksDirectlyWithNoHighlighted() {
        for (Choice choice : Choice.values()) {
            PauseMenu menu = new PauseMenu();

            menu.confirm(choice);

            assertEquals(Level.CONFIRM, menu.level());
            assertEquals(choice, menu.choice());
            assertFalse(menu.yesHighlighted(), "NO preselected");
            assertEquals(Optional.empty(), menu.key(GameAction.MENU_CONFIRM), "Enter on NO");
            assertEquals(Level.EXIT, menu.level(), "NO returns to the EXIT level");

            menu.confirm(choice);
            menu.key(GameAction.TURN_LEFT);
            assertEquals(Optional.of(choice), menu.key(GameAction.MENU_CONFIRM), "Enter on YES");
            assertEquals(Level.CLOSED, menu.level());
        }
    }

    /**
     * Acceptance 2026-10-13: a question asked straight from the running game (F9, F10, the window's close button)
     * closes the menu on NO or Esc and reports the resume once; YES still returns the choice.
     */
    @Test
    void aQuestionFromTheGameResumesOnNoOrEsc() {
        for (Choice choice : Choice.values()) {
            PauseMenu menu = new PauseMenu();

            menu.ask(choice);

            assertEquals(Level.CONFIRM, menu.level());
            assertEquals(choice, menu.choice());
            assertFalse(menu.yesHighlighted(), "NO preselected");
            assertFalse(menu.takeResume(), "nothing answered yet");
            assertEquals(Optional.empty(), menu.key(GameAction.MENU_CONFIRM), "Enter on NO");
            assertEquals(Level.CLOSED, menu.level(), "NO leaves the menu");
            assertTrue(menu.takeResume(), "NO resumes the game");
            assertFalse(menu.takeResume(), "reported once");

            menu.ask(choice);
            menu.escape();
            assertEquals(Level.CLOSED, menu.level(), "Esc leaves the menu");
            assertTrue(menu.takeResume(), "Esc resumes the game");

            menu.ask(choice);
            menu.key(GameAction.TURN_LEFT);
            assertEquals(Optional.of(choice), menu.key(GameAction.MENU_CONFIRM), "Enter on YES");
            assertFalse(menu.takeResume(), "YES does not resume");

            menu.ask(choice);
            menu.escape();
            menu.takeResume();
            menu.open();
            menu.key(GameAction.BACK);
            menu.key(GameAction.MENU_CONFIRM);
            menu.escape();
            assertEquals(Level.EXIT, menu.level(), "a question from the EXIT level still returns there");
            assertFalse(menu.takeResume());
        }
    }

    /** Round 2, item 2: Left/Right (and Up/Down) move between YES and NO; Esc is NO. */
    @Test
    void theArrowsMoveBetweenYesAndNoAndEscIsNo() {
        PauseMenu menu = new PauseMenu();
        menu.open();
        menu.key(GameAction.BACK);
        menu.key(GameAction.MENU_CONFIRM);
        menu.key(GameAction.TURN_LEFT);
        assertTrue(menu.yesHighlighted(), "Left: YES");
        menu.key(GameAction.TURN_RIGHT);
        assertFalse(menu.yesHighlighted(), "Right: NO");
        menu.key(GameAction.FORWARD);
        assertTrue(menu.yesHighlighted(), "Up toggles too");
        menu.key(GameAction.BACK);
        assertFalse(menu.yesHighlighted(), "Down toggles too");
        menu.key(GameAction.TURN_LEFT);
        menu.escape();
        assertEquals(Level.EXIT, menu.level(), "Esc answers NO even with YES highlighted");
    }
}
