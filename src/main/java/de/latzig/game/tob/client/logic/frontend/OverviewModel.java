package de.latzig.game.tob.client.logic.frontend;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import de.latzig.game.tob.client.logic.Texts;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.progress.Profile;
import de.latzig.game.tob.core.progress.Progression;
import de.latzig.game.tob.core.progress.TowerProgress;

/**
 * The Overview screen's state (Phase 5a spec decision 7 with the original's rules; author decision 2026-10-12: a
 * 3×3 grid instead of ToB 2009's list): the profile's current group as 9 letter cells A–I in rows A–C, D–F, G–I,
 * each locked, open or solved; one info line for the selected tower; the score; the group with the completed
 * percentage; previous/next group. Every cell can be selected (a locked one only to read its info line); only an
 * open or solved tower can be played, a solved one as practice. The arrow keys move in the grid without wrapping.
 * <p>
 * Phase 6d (spec decisions 2–6): the Overview shows the original towers or one of the player's own libraries
 * ({@link CustomLibrary}). An own library's towers come 9 to a group in the file's order, its group is the model's
 * own (the profile's campaign group stays), every tower is open (no unlocking) and none is practice; the profile's
 * marks for that library show the solved ones (green). A cell past the last tower is {@link State#EMPTY} and cannot
 * be selected; a tower without a task or with a check error is greyed ({@link State#LOCKED}) but Play stays enabled
 * and answers why it cannot start ({@link Refused}). The caption is ToB 2009's "NAME (LIBRARY)", the header "Solved:
 * n of m" (no score) and "Group nn (xx%)" of the library. The first open tower is preselected, its group first.
 */
public final class OverviewModel {

    /** Cells per grid row and column. */
    public static final int SIZE = 3;
    public static final int CELLS = Progression.TOWERS_PER_GROUP;

    /** A cell's colour state: locked (grey), open (white), solved (green); Phase 6d: empty (no tower). */
    public enum State {
        LOCKED, OPEN, SOLVED, EMPTY
    }

    /**
     * One grid cell: tower {@code index} 0–8 with its {@code letter} A–I at grid {@code row}/{@code column} 0–2.
     */
    public record Cell(int index, char letter, int row, int column, State state, boolean selected) {

        /** Open or solved (solved = practice). */
        public boolean playable() {
            return state == State.OPEN || state == State.SOLVED;
        }
    }

    /** What Enter/Play or a group button does. */
    public sealed interface Action {
    }

    /** Play {@code tower}; {@code practice} for a solved tower (the profile stays unchanged). */
    public record Play(TowerDefinition tower, boolean practice) implements Action {
    }

    /** The group was switched; {@code profile} remembers it and should be saved. */
    public record GroupChanged(Profile profile) implements Action {
    }

    /** Nothing happens (a locked tower, or a group change that is not allowed). */
    public record Nothing() implements Action {
    }

    /** Phase 6d decision 6: the own tower cannot be started; {@code message} says why (the original's words). */
    public record Refused(String message) implements Action {
    }

    private final Campaign campaign;
    /** Phase 6d: the own library shown, or null for the original towers. */
    private final CustomLibrary custom;
    private final Texts texts;
    private Profile profile;
    private int customGroup;
    private int selected;

    public OverviewModel(Campaign campaign, Profile profile, Texts texts) {
        this(campaign, profile, texts, Optional.empty());
    }

    /** @param custom the own library to show (Phase 6d), or empty for the original towers */
    public OverviewModel(Campaign campaign, Profile profile, Texts texts, Optional<CustomLibrary> custom) {
        this.campaign = campaign;
        this.profile = profile;
        this.texts = texts;
        this.custom = custom.orElse(null);
        if (this.custom != null) {
            customGroup = firstOpen() / CELLS;
        }
        preselectNext();
    }

    public Profile profile() {
        return profile;
    }

    /** The own library shown (Phase 6d); empty for the original towers. */
    public Optional<CustomLibrary> custom() {
        return Optional.ofNullable(custom);
    }

    public int group() {
        return custom != null ? customGroup : profile.group();
    }

    /** The selected tower's index 0–8 (row = index / 3, column = index % 3). */
    public int selected() {
        return selected;
    }

    /** The profile's name; with an own library ToB 2009's "NAME (LIBRARY)". */
    public String caption() {
        return custom == null ? profile.name() : texts.format("overview.caption.custom", profile.name(), custom.name());
    }

    /** The profile's score; with an own library "Solved: n of m" (no score, decision 5). */
    public String scoreText() {
        return custom == null ? texts.format("overview.score", profile.score())
                : texts.format("overview.custom.solved", custom.solvedCount(profile), custom.size());
    }

    /**
     * "Group nn (xx%)" in two digits as the original ({@code S:7450-7453}), but displayed 1-based (author decision
     * 2026-10-15: "Group 01" … "Group 13"); {@link #group()} stays 0-based. With an own library: of its towers.
     */
    public String groupText() {
        int percent = custom == null ? Progression.completedPercent(campaign.completedCount(profile))
                : custom.percent(custom.solvedCount(profile));
        return texts.format("overview.group", group() + 1, percent);
    }

    /** Every tower solved: the percentage turns green (ToB 2009). */
    public boolean completedAll() {
        return custom == null ? campaign.allSolved(profile) : custom.solvedCount(profile) == custom.size();
    }

    /** The 9 cells A–I in index order. */
    public List<Cell> cells() {
        if (custom != null) {
            return customCells();
        }
        int done = campaign.doneMask(profile, group());
        int open = Progression.accessibleMask(done);
        List<Cell> cells = new ArrayList<>(CELLS);
        for (int i = 0; i < CELLS; i++) {
            int bit = 1 << i;
            State state = (done & bit) != 0 ? State.SOLVED : (open & bit) != 0 ? State.OPEN : State.LOCKED;
            cells.add(new Cell(i, Progression.letter(i), i / SIZE, i % SIZE, state, i == selected));
        }
        return cells;
    }

    /** An own library's group: empty past the last tower, greyed if refused, else solved or open. */
    private List<Cell> customCells() {
        List<Cell> cells = new ArrayList<>(CELLS);
        for (int i = 0; i < CELLS; i++) {
            int position = customGroup * CELLS + i;
            State state;
            if (position >= custom.size()) {
                state = State.EMPTY;
            } else if (!custom.startable(position)) {
                state = State.LOCKED;
            } else {
                state = profile.customSolved(custom.name(), custom.towers().get(position).id()) ? State.SOLVED
                        : State.OPEN;
            }
            cells.add(new Cell(i, Progression.letter(i), i / SIZE, i % SIZE, state, i == selected));
        }
        return cells;
    }

    public Cell selectedCell() {
        return cells().get(selected);
    }

    public TowerDefinition selectedTower() {
        return custom == null ? campaign.tower(group(), selected) : custom.tower(customGroup, selected).orElseThrow();
    }

    /**
     * The selected tower's info line "nnn) Title - Author [attempts]": the 1-based tower number in three digits
     * (ToB 2009), the full title, the author ("Anonymous" if empty), the attempts only once there are any. Wrapping
     * or shortening is the layout's job. An own tower: its number in the library, "(NO TITLE)" for an empty title,
     * no attempts.
     */
    public String infoText() {
        TowerDefinition tower = selectedTower();
        String author = tower.author().isBlank() ? texts.get("briefing.author.none") : tower.author();
        if (custom != null) {
            String title = tower.title().isBlank() ? texts.get("editor.no_title") : tower.title();
            String number = String.format(Locale.ROOT, "%03d", customGroup * CELLS + selected + 1);
            return texts.format("overview.tower", number, title, author);
        }
        String number = String.format(Locale.ROOT, "%03d", campaign.number(group(), selected));
        TowerProgress progress = profile.progress(tower.id());
        return progress.attempts() > 0
                ? texts.format("overview.tower.attempts", number, tower.title(), author, progress.attempts())
                : texts.format("overview.tower", number, tower.title(), author);
    }

    /**
     * The selected tower can be played (Play enabled). Every own tower can be tried: a refused one says why
     * ({@link #activate}).
     */
    public boolean playable() {
        return custom != null || campaign.isPlayable(profile, group(), selected);
    }

    /** The selected tower is solved: playing it is practice (Play reads "Practice"); never for an own tower. */
    public boolean practice() {
        return custom == null && profile.isPractice(selectedTower().id());
    }

    public boolean canGoBack() {
        return custom == null ? Progression.canGoBack(group()) : customGroup > 0;
    }

    /** The original's rule (7 of 9 solved), or with an own library any group but the last (all open). */
    public boolean canGoForward() {
        return custom == null ? Progression.canGoForward(group(), campaign.doneMask(profile, group()))
                : customGroup < custom.groups() - 1;
    }

    /** Moves the selection one row up; false at the top row (no wrap). */
    public boolean up() {
        return move(-1, 0);
    }

    public boolean down() {
        return move(1, 0);
    }

    public boolean left() {
        return move(0, -1);
    }

    public boolean right() {
        return move(0, 1);
    }

    private boolean move(int rows, int columns) {
        return selectAt(selected / SIZE + rows, selected % SIZE + columns);
    }

    /** Selects cell {@code index} 0–8 (locked ones too); false if outside the grid or past an own library's end. */
    public boolean select(int index) {
        if (index < 0 || index >= CELLS || custom != null && customGroup * CELLS + index >= custom.size()) {
            return false;
        }
        selected = index;
        return true;
    }

    /** Selects the cell at grid {@code row}/{@code column} 0–2 (a click); false if outside the grid. */
    public boolean selectAt(int row, int column) {
        if (row < 0 || row >= SIZE || column < 0 || column >= SIZE) {
            return false;
        }
        return select(row * SIZE + column);
    }

    /**
     * Enter, Play or a double click: plays the selected tower if it is playable; an own tower that cannot be started
     * is {@link Refused} (decision 6).
     */
    public Action activate() {
        if (custom != null) {
            Optional<String> refusal = custom.refusal(customGroup * CELLS + selected, texts);
            return refusal.isPresent() ? new Refused(refusal.get()) : new Play(selectedTower(), false);
        }
        if (!playable()) {
            return new Nothing();
        }
        TowerDefinition tower = selectedTower();
        return new Play(tower, profile.isPractice(tower.id()));
    }

    /** "Go Back to Last Group" if allowed; the new group's next tower is preselected. */
    public Action back() {
        return canGoBack() ? changeGroup(-1) : new Nothing();
    }

    /** "Go Forward to Next Group" if allowed (7 of 9 solved); the new group's next tower is preselected. */
    public Action forward() {
        return canGoForward() ? changeGroup(1) : new Nothing();
    }

    private Action changeGroup(int step) {
        if (custom != null) {
            customGroup += step;
        } else {
            profile = profile.withGroup(group() + step);
        }
        preselectNext();
        return new GroupChanged(profile);
    }

    /**
     * The next tower: the first accessible one (A–I order), else the first solved one, else A. With an own library
     * the same: the group's first open tower, else its first solved one, else A.
     */
    public void preselectNext() {
        if (custom != null) {
            List<Cell> cells = customCells();
            for (State wanted : List.of(State.OPEN, State.SOLVED)) {
                for (Cell cell : cells) {
                    if (cell.state() == wanted) {
                        selected = cell.index();
                        return;
                    }
                }
            }
            selected = 0;
            return;
        }
        int next = campaign.nextIndex(profile, group());
        if (next >= 0) {
            selected = next;
            return;
        }
        int done = campaign.doneMask(profile, group());
        selected = done == 0 ? 0 : Integer.numberOfTrailingZeros(done);
    }

    /** The position of the first open tower (startable, not solved), else of the first startable one, else 0. */
    private int firstOpen() {
        for (int position = 0; position < custom.size(); position++) {
            if (custom.startable(position)
                    && !profile.customSolved(custom.name(), custom.towers().get(position).id())) {
                return position;
            }
        }
        for (int position = 0; position < custom.size(); position++) {
            if (custom.startable(position)) {
                return position;
            }
        }
        return 0;
    }

    /**
     * Preselects {@code towerId} if it is in the shown group (Retry, back from the Briefing); with an own library
     * wherever it is (its group is shown).
     */
    public void preselect(String towerId) {
        if (custom != null) {
            int position = custom.position(towerId);
            if (position >= 0) {
                customGroup = position / CELLS;
                selected = position % CELLS;
            }
            return;
        }
        campaign.locate(towerId).filter(location -> location.group() == group())
                .ifPresent(location -> select(location.index()));
    }
}
