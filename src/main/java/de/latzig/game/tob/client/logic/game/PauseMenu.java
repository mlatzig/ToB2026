package de.latzig.game.tob.client.logic.game;

import java.util.List;
import java.util.Optional;

/**
 * The pause menu (Phase 3b-2 spec §4.3): while paused, the title bar carries the menu title {@code EXIT}; opening it
 * shows {@code ABANDON} and {@code RESTART}; choosing one asks {@code ARE YOU SURE?} with {@code YES} and {@code NO}
 * (the original's pause box {@code L0091AA} with its "Are You Sure?" box {@code L00FFDE}, {@code S:7289-7316},
 * {@code S:14744}). Operated by the mouse ({@link #click}, {@link #hover}) or by the arrow
 * keys and Enter ({@link #key}); Esc goes back one level ({@link #escape}). Only a YES returns a choice.
 * <p>
 * Acceptance round 2 (item 2, 2026-10-09): ARE YOU SURE? is a modal dialog window ({@link ConfirmDialogLayout}) in
 * the upper part of the screen, captioned with the chosen action, with YES and NO side by side. While it is open the
 * drop-down closes (the title bar shows only the menu title, as a desktop menu closes when its item opens a dialog);
 * clicks outside the dialog's buttons do nothing; NO or Esc reopen the EXIT level, YES closes everything. A question
 * asked straight from the running game ({@link #ask}: F9, F10, the window's close button) goes back to the game on NO
 * or Esc instead (acceptance 2026-10-13).
 * <p>
 * Highlight (item 7, author's decision 2026-10-09): the EXIT level opens with nothing highlighted, however it is
 * reached (Esc, the title-bar X, Enter, a click on the title, back from ARE YOU SURE?). The highlight appears when the
 * cursor moves over an entry (it follows the cursor; off the entries the last highlight stays) or with an arrow key
 * (the first Down highlights the first entry, the first Up the last; further presses move it); Enter with nothing
 * highlighted does nothing. ARE YOU SURE? opens with NO highlighted.
 */
public final class PauseMenu {

    /** CLOSED: only the menu title shows; EXIT: Abandon/Restart; CONFIRM: the "Are You Sure?" dialog. */
    public enum Level {
        CLOSED, EXIT, CONFIRM
    }

    /**
     * What the player can confirm: the EXIT level's {@link #ENTRIES}, and QUIT, asked only when the window is closed
     * during a game (final review I1; a YES ends the attempt as abandoned and quits).
     */
    public enum Choice {
        ABANDON, RESTART, QUIT
    }

    /** The EXIT level's entries, top to bottom. */
    public static final List<Choice> ENTRIES = List.of(Choice.ABANDON, Choice.RESTART);

    public static final String TITLE = "EXIT";
    public static final String QUESTION = "ARE YOU SURE?";

    private Level level = Level.CLOSED;
    private Choice choice = Choice.ABANDON;
    /** The highlighted entry of the EXIT level, or −1. */
    private int highlighted = -1;
    private boolean yes;
    /** The open question was asked straight from the running game ({@link #ask}): NO and Esc resume it. */
    private boolean fromGame;
    /** NO or Esc answered a question asked from the game; {@link #takeResume} reports it once. */
    private boolean resume;

    public Level level() {
        return level;
    }

    /** The entry being confirmed (or last confirmed). */
    public Choice choice() {
        return choice;
    }

    /** At the CONFIRM level: whether YES (rather than NO) is highlighted in the dialog. */
    public boolean yesHighlighted() {
        return yes;
    }

    /** Opens the EXIT level with nothing highlighted (Esc while playing, the X, a click on the title, Enter). */
    public void open() {
        level = Level.EXIT;
        highlighted = -1;
    }

    /** Back to the bare menu title (unpausing, or a choice was made). */
    public void close() {
        level = Level.CLOSED;
    }

    /** Esc: back one level; at CLOSED it opens the menu. */
    public void escape() {
        switch (level) {
            case CLOSED -> open();
            case EXIT -> close();
            case CONFIRM -> back();
        }
    }

    /**
     * A key while paused: {@link GameAction#FORWARD}/{@link GameAction#BACK} (Up/Down) move the highlight (at the
     * EXIT level the first Down highlights the first entry, the first Up the last),
     * {@link GameAction#TURN_LEFT}/{@link GameAction#TURN_RIGHT} (Left/Right) too in the dialog, and
     * {@link GameAction#MENU_CONFIRM} (Enter) opens the menu, asks for the highlighted entry, or answers. Other keys
     * do nothing.
     *
     * @return the confirmed choice after YES, else empty
     */
    public Optional<Choice> key(GameAction action) {
        boolean move = action == GameAction.FORWARD || action == GameAction.BACK;
        boolean side = action == GameAction.TURN_LEFT || action == GameAction.TURN_RIGHT;
        switch (level) {
            case CLOSED -> {
                if (action == GameAction.MENU_CONFIRM) {
                    open();
                }
            }
            case EXIT -> {
                int entries = ENTRIES.size();
                if (move && highlighted < 0) {
                    highlighted = action == GameAction.BACK ? 0 : entries - 1;
                } else if (move) {
                    highlighted = (highlighted + (action == GameAction.BACK ? 1 : entries - 1)) % entries;
                } else if (action == GameAction.MENU_CONFIRM && highlighted >= 0) {
                    confirm(ENTRIES.get(highlighted));
                }
            }
            case CONFIRM -> {
                if (move || side) {
                    yes = !yes;
                } else if (action == GameAction.MENU_CONFIRM) {
                    return answer(yes);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * A left click at a screen point while paused: the title opens or closes the menu, an entry asks, YES and NO in
     * the dialog answer; anything else does nothing. While the dialog is open nothing but YES and NO reacts (modal).
     *
     * @return the confirmed choice after YES, else empty
     */
    public Optional<Choice> click(float x, float y, PanelLayout layout, ConfirmDialogLayout dialog) {
        if (level == Level.CONFIRM) {
            Optional<ConfirmDialogLayout.Answer> answer = dialog.answerAt(x, y);
            return answer.isPresent() ? answer(answer.get() == ConfirmDialogLayout.Answer.YES) : Optional.empty();
        }
        if (layout.menuTitle().contains(x, y)) {
            if (level == Level.CLOSED) {
                open();
            } else {
                close();
            }
            return Optional.empty();
        }
        int row = -1;
        for (int i = 0; i < rows().size(); i++) {
            if (layout.menuRow(i).contains(x, y)) {
                row = i;
            }
        }
        if (level == Level.EXIT && row >= 0) {
            confirm(ENTRIES.get(row));
        }
        return Optional.empty();
    }

    /**
     * The cursor moved to a screen point while paused (item 7): over an entry (ABANDON, RESTART; YES, NO in the
     * dialog) it highlights that entry; elsewhere the highlight stays.
     */
    public void hover(float x, float y, PanelLayout layout, ConfirmDialogLayout dialog) {
        if (level == Level.CONFIRM) {
            dialog.answerAt(x, y).ifPresent(answer -> yes = answer == ConfirmDialogLayout.Answer.YES);
            return;
        }
        for (int row = 0; row < rows().size(); row++) {
            if (layout.menuRow(row).contains(x, y)) {
                highlighted = row;
            }
        }
    }

    /** The drop-down rows below the title: the two entries at the EXIT level, else none. */
    public List<String> rows() {
        return level == Level.EXIT ? ENTRIES.stream().map(Choice::name).toList() : List.of();
    }

    /** The highlighted drop-down row, or −1. */
    public int highlightedRow() {
        return level == Level.EXIT ? highlighted : -1;
    }

    /**
     * Asks ARE YOU SURE? about {@code asked} with NO highlighted, from the paused game: an entry of the EXIT level, or
     * F9 (RESTART), F10 (ABANDON) and the window's close button (QUIT, final review I1) while already paused; NO and
     * Esc then return to the EXIT level.
     */
    public void confirm(Choice asked) {
        choice = asked;
        yes = false;
        fromGame = false;
        level = Level.CONFIRM;
    }

    /**
     * Asks ARE YOU SURE? about {@code asked} with NO highlighted, straight from the running game: F9 (RESTART), F10
     * (ABANDON) or the window's close button (QUIT). NO and Esc close the menu and {@link #takeResume} tells the game
     * to resume (acceptance 2026-10-13: the player never opened the menu, so NO goes back to where they were).
     */
    public void ask(Choice asked) {
        confirm(asked);
        fromGame = true;
    }

    /** Whether NO or Esc just answered a question from {@link #ask}, so the game resumes; reported once. */
    public boolean takeResume() {
        boolean taken = resume;
        resume = false;
        return taken;
    }

    private Optional<Choice> answer(boolean confirmed) {
        if (confirmed) {
            close();
            return Optional.of(choice);
        }
        back();
        return Optional.empty();
    }

    /** NO or Esc in the dialog: back to the EXIT level, or out of the menu if the question came from the game. */
    private void back() {
        if (fromGame) {
            fromGame = false;
            close();
            resume = true;
        } else {
            open();
        }
    }
}
