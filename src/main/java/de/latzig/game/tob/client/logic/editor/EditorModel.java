package de.latzig.game.tob.client.logic.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

import de.latzig.game.tob.client.logic.Texts;
import de.latzig.game.tob.client.logic.ui.Drawing;
import de.latzig.game.tob.client.logic.ui.ScrollBox;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.model.TowerGrid;
import de.latzig.game.tob.core.sim.Rules;
import de.latzig.game.tob.core.sim.TowerCheck.Finding;
import de.latzig.game.tob.core.sim.TowerCheck.Severity;

/**
 * The Tower Designer's editing state (Phase 6b, engine-free): the open library's towers (a working copy; nothing is
 * written until File → Save), the selected tower with its {@link EditHistory}, the cursor (cell and level), the
 * palette's element, the clipboard, the rule pass of every tower ({@link EditorFindings}, recomputed after each
 * change) and the status line. The left button places the element (held: a painting stroke, one undo step), the right
 * button removes ({@link TowerEdits}); a read-only (shipped) library refuses every change. A test play (spec decision
 * 6) is refused while the tower has an error (the cursor jumps there) and asks first if it cannot be won.
 * <p>
 * Phase 6d decision 7: a tower gets its id once, when it is created ({@link TowerIds}: a new library's tower, the
 * appended row, Insert, and Paste and Delete, whose result is a new tower); editing, inserting or removing other
 * towers and saving never change it. A shipped library's towers get new ids when it is saved as an own library
 * ({@link #towersToSave}).
 */
public final class EditorModel {

    /** The test play gate (Phase 2a spec §6): go, ask "play anyway?", or refused. */
    public enum Play {
        GO, ASK, REFUSED
    }

    /** A status line message for a while: its text and colour. */
    public record Notice(String text, int colour) {
    }

    /** One row of the library list: its text, ToB 2009's colour, selected or not. */
    public record Row(String text, int colour, boolean selected) {
    }

    /** The library list's visible rows (ToB 2009's list shows 24). */
    public static final int LIST_ROWS = 24;
    /** How long a notice stays in the status line. */
    public static final double NOTICE_SECONDS = 3.0;
    /** ToB 2009's {@code Colors.GREEN}, {@code DARK_GREEN} and {@code DARK_RED} of the library list. */
    public static final int GREEN = Drawing.GREEN;
    public static final int DARK_GREEN = 0x008000;
    public static final int DARK_RED = 0x800000;

    private final Texts texts;
    private final TowerIds ids;
    private final List<TowerDefinition> towers = new ArrayList<>();
    private final List<EditorFindings> findings = new ArrayList<>();
    private final ScrollBox list = new ScrollBox(LIST_ROWS);
    private String library;
    private boolean readOnly;
    private boolean dirty;
    private int selected;
    private EditHistory history;
    private int x;
    private int y;
    private int level;
    private EditorElement tool = EditorElement.FLOOR;
    private boolean painting;
    private TowerDefinition clipboard;
    private Notice notice;
    private double noticeLeft;
    private boolean freezeEnemies;
    /**
     * Final review M2: whether the open library was opened with towers it could not read (unreadable, or beyond
     * {@value EditorTowers#MAX_TOWERS}); while true, {@code TowerDesigner}'s Save asks before overwriting the file
     * without them, since that would drop them for good.
     */
    private boolean droppedTowers;

    /** A new, unsaved library with one empty tower; new towers get random UUIDs. */
    public EditorModel(Texts texts) {
        this(texts, new TowerIds());
    }

    /** @param ids where new towers get their ids (Phase 6d decision 7; tests: a predictable source) */
    public EditorModel(Texts texts, TowerIds ids) {
        this.texts = texts;
        this.ids = Objects.requireNonNull(ids, "ids");
        newLibrary();
    }

    // ------------------------------------------------------------------ the library

    /** File → New: an unsaved library with one empty tower. */
    public void newLibrary() {
        replace(null, false, List.of(EditorTowers.blank(ids.next(), 0)));
    }

    /** A library was opened; unreadable towers are counted in a notice. */
    public void load(TowerLibraries.Opened opened) {
        List<TowerDefinition> loaded = opened.towers().isEmpty()
                ? List.of(EditorTowers.blank(ids.next(), 0)) : opened.towers();
        replace(opened.entry().name(), opened.entry().readOnly(), loaded);
        droppedTowers = opened.skipped() > 0;
        if (droppedTowers) {
            notice(texts.format("editor.notice.skipped", opened.skipped()), EditorFindings.YELLOW);
        }
    }

    /** The library was saved as the own library {@code name}: no longer read-only, nothing unsaved. */
    public void saved(String name) {
        library = name;
        readOnly = false;
        dirty = false;
        droppedTowers = false;
        notice(texts.get("editor.notice.saved"), Drawing.WHITE);
    }

    /**
     * Save succeeded: {@code written} ({@link #towersToSave()}'s list) becomes the model's towers, ids included (a
     * shipped library's towers got new ids), and the library is the own library {@code name}.
     */
    public void saved(String name, List<TowerDefinition> written) {
        if (readOnly) {
            towers.clear();
            towers.addAll(written);
            history = new EditHistory(tower());
        }
        saved(name);
    }

    private void replace(String name, boolean shipped, List<TowerDefinition> loaded) {
        library = name;
        readOnly = shipped;
        dirty = false;
        droppedTowers = false;
        towers.clear();
        findings.clear();
        for (TowerDefinition tower : loaded) {
            towers.add(tower);
            findings.add(EditorFindings.of(tower));
        }
        selected = -1;
        select(0);
        list.home();
    }

    /** The open library's name; empty while it was never saved. */
    public Optional<String> library() {
        return Optional.ofNullable(library);
    }

    public boolean readOnly() {
        return readOnly;
    }

    /** Whether the library has changes not saved yet. */
    public boolean dirty() {
        return dirty;
    }

    /** Final review M2: whether the library was opened with towers it could not read (dropped on an unasked Save). */
    public boolean hadDroppedTowers() {
        return droppedTowers;
    }

    public List<TowerDefinition> towers() {
        return List.copyOf(towers);
    }

    /**
     * The towers as Save writes them (Phase 6d decision 7). A shipped (read-only) library's towers are about to become
     * own towers (Save As): each gets a new id, so no own tower ever carries a shipped tower's id (and joins the
     * campaign); the model adopts them only once the write succeeded ({@link #saved(String, List)}). An own library's
     * towers keep theirs. Nothing in the model changes here.
     */
    public List<TowerDefinition> towersToSave() {
        return readOnly ? towers.stream().map(tower -> EditorTowers.withId(tower, ids.next())).toList() : towers();
    }

    /** The library label above the list: its name or "(NOT SAVED)", and "(READ-ONLY)" for a shipped one. */
    public String libraryLabel() {
        String name = library == null ? texts.get("editor.not_saved") : library;
        return readOnly ? texts.format("editor.read_only", name) : name;
    }

    // ------------------------------------------------------------------ the towers

    public int selected() {
        return selected;
    }

    /** The selected tower as edited. */
    public TowerDefinition tower() {
        return towers.get(selected);
    }

    /** The selected tower's 1-based number (the title strip and the test play's title bar). */
    public int number() {
        return selected + 1;
    }

    public EditorFindings findings() {
        return findings.get(selected);
    }

    /**
     * Selects tower {@code index}; the empty row after the last tower appends a new tower (not in a read-only library,
     * at most {@value EditorTowers#MAX_TOWERS}). Another tower starts a new undo history (spec decision 7).
     */
    public void select(int index) {
        if (index == towers.size() && canInsert()) {
            towers.add(EditorTowers.blank(ids.next(), index));
            findings.add(EditorFindings.of(towers.get(index)));
            dirty = true;
        }
        if (index < 0 || index >= towers.size() || index == selected) {
            return;
        }
        endStroke();
        selected = index;
        history = new EditHistory(tower());
        list.size(rowCount());
        list.show(selected);
    }

    /** The list's rows: every tower ({@code nnn: title}) in ToB 2009's colours, then the empty row for a new one. */
    public List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < towers.size(); i++) {
            boolean chosen = i == selected;
            int colour = switch (findings.get(i).state()) {
                case VALID -> chosen ? GREEN : DARK_GREEN;
                case WARNING -> chosen ? Drawing.WHITE : Drawing.GREY;
                case ERROR -> chosen ? EditorFindings.RED : DARK_RED;
            };
            rows.add(new Row(texts.format("editor.list.row", i + 1, towers.get(i).title()), colour, chosen));
        }
        if (canInsert()) {
            rows.add(new Row(texts.format("editor.list.row", towers.size() + 1, ""), Drawing.GREY, false));
        }
        return rows;
    }

    /** The list's scroll position (the mouse wheel). */
    public ScrollBox list() {
        list.size(rowCount());
        return list;
    }

    /** The towers and the empty row for a new one. */
    private int rowCount() {
        return towers.size() + (canInsert() ? 1 : 0);
    }

    /** The title strip: {@code nnn: Title (Author)}, "(NO TITLE)" for a tower without one. */
    public String title() {
        TowerDefinition tower = tower();
        String title = tower.title().isBlank() ? texts.get("editor.no_title") : tower.title();
        String author = tower.author().isBlank() ? "" : " (" + tower.author() + ")";
        return texts.format("editor.title", number(), title, author);
    }

    // ------------------------------------------------------------------ the cursor and the palette

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    /** The level shown in the grid, 0 = bottom. */
    public int level() {
        return level;
    }

    public void level(int newLevel) {
        level = Math.clamp(newLevel, 0, TowerGrid.LEVELS - 1);
    }

    /** Moves the cursor to a cell (the mouse over the grid, ToB 2009's {@code SelectPositionAction}). */
    public void cursor(int cellX, int cellY) {
        x = Math.clamp(cellX, 0, TowerGrid.WIDTH - 1);
        y = Math.clamp(cellY, 0, TowerGrid.DEPTH - 1);
    }

    /** Moves the cursor by a step (the arrow keys; north is up). */
    public void moveCursor(int dx, int dy) {
        cursor(x + dx, y + dy);
    }

    public EditorElement tool() {
        return tool;
    }

    public void tool(EditorElement element) {
        tool = element;
    }

    // ------------------------------------------------------------------ editing

    /** A button went down over cell ({@code cellX}, {@code cellY}): left places (a stroke begins), right removes. */
    public void press(int cellX, int cellY, boolean right) {
        cursor(cellX, cellY);
        if (right) {
            removeAtCursor();
            return;
        }
        if (refused()) {
            return;
        }
        painting = true;
        history.beginStroke();
        place();
    }

    /** The mouse moved to a cell with the left button held: the stroke paints it (ToB 2009). */
    public void drag(int cellX, int cellY) {
        cursor(cellX, cellY);
        if (painting) {
            place();
        }
    }

    /** The left button went up: the stroke is one undo step. */
    public void release() {
        endStroke();
    }

    private void endStroke() {
        if (painting) {
            painting = false;
            history.endStroke();
        }
    }

    /** Places the palette's element at the cursor (Space or Enter): one step. */
    public void placeAtCursor() {
        if (!refused()) {
            place();
        }
    }

    /** Removes at the cursor (Delete, Backspace or the right button): one step. */
    public void removeAtCursor() {
        if (!refused()) {
            edit(TowerEdits.remove(tower().grid(), x, y, level));
        }
    }

    private void place() {
        edit(TowerEdits.place(tower().grid(), tool, x, y, level));
    }

    private void edit(TowerEdits.Result result) {
        result.notice().ifPresent(key -> notice(texts.get(key), EditorFindings.RED));
        change(EditorTowers.withGrid(tower(), result.grid()));
    }

    /** A Toolbox function applied to the selected tower's grid: one step. */
    public void apply(UnaryOperator<TowerGrid> function) {
        edit(tower -> EditorTowers.withGrid(tower, function.apply(tower.grid())));
    }

    /**
     * Any change of the selected tower as one undo step (Paste, Delete, the Toolbox; 6b-2's Settings and Effects
     * dialogs on OK); refused in a read-only library.
     */
    public void edit(UnaryOperator<TowerDefinition> function) {
        if (!refused()) {
            change(function.apply(tower()));
        }
    }

    private boolean refused() {
        if (readOnly) {
            notice(texts.get("editor.notice.read_only"), EditorFindings.RED);
        }
        return readOnly;
    }

    private void change(TowerDefinition next) {
        if (history.apply(next)) {
            set(next);
        }
    }

    private void set(TowerDefinition tower) {
        towers.set(selected, tower);
        findings.set(selected, EditorFindings.of(tower));
        dirty = true;
    }

    public boolean canUndo() {
        return !readOnly && history.canUndo();
    }

    public boolean canRedo() {
        return !readOnly && history.canRedo();
    }

    /**
     * Edit → Undo (Ctrl+Z). Final review M4: ends a running painting stroke first (also while the button stays
     * down), so the undo does not silently split the stroke into two: a further drag after this would otherwise
     * start a fresh stroke of its own and clear the redo stack cell by cell.
     */
    public void undo() {
        endStroke();
        if (canUndo()) {
            set(history.undo());
        }
    }

    /** Edit → Redo (Ctrl+Y); ends a running painting stroke first (final review M4, see {@link #undo()}). */
    public void redo() {
        endStroke();
        if (canRedo()) {
            set(history.redo());
        }
    }

    // ------------------------------------------------------------------ Edit: the clipboard and the list

    /** Edit → Copy: the selected tower to the clipboard (also from a read-only library: the template path). */
    public void copy() {
        clipboard = tower();
        notice(texts.get("editor.notice.copied"), Drawing.WHITE);
    }

    public boolean canPaste() {
        return clipboard != null && !readOnly;
    }

    /**
     * Edit → Paste: the clipboard's tower replaces the selected one's content (its place stays); one step. The copy is
     * a new tower with a new id (Phase 6d decision 7).
     */
    public void paste() {
        if (canPaste()) {
            TowerDefinition copied = clipboard;
            edit(tower -> new TowerDefinition(ids.next(), tower.group(), tower.index(), copied.title(),
                    copied.author(), copied.conditions(), copied.behaviour(), copied.effects(), copied.moon(),
                    copied.palette(), copied.sounds(), copied.lock(), copied.grid(), copied.remake()));
        }
    }

    /**
     * Edit → Delete (ToB 2009: empties the tower): the selected tower becomes a new, empty one with a new id (Phase 6d
     * decision 7); one step.
     */
    public void delete() {
        // the id is drawn inside the edit, which a read-only library refuses: a refused Delete draws none
        edit(tower -> EditorTowers.blank(ids.next(), selected));
    }

    public boolean canInsert() {
        return !readOnly && towers.size() < EditorTowers.MAX_TOWERS;
    }

    /** Edit → Insert: a new, empty tower before the selected one, selected (ToB 2009). */
    public void insert() {
        if (canInsert()) {
            int at = selected;
            towers.add(at, EditorTowers.blank(ids.next(), at));
            findings.add(at, EditorFindings.of(towers.get(at)));
            dirty = true;
            selected = -1;
            select(at);
        }
    }

    public boolean canRemove() {
        return !readOnly && towers.size() > 1;
    }

    /** Edit → Remove (after the question): the selected tower leaves the library; the next one is selected. */
    public void remove() {
        if (canRemove()) {
            int at = selected;
            towers.remove(at);
            findings.remove(at);
            dirty = true;
            selected = -1;
            select(Math.min(at, towers.size() - 1));
        }
    }

    // ------------------------------------------------------------------ the status line

    /** A message in the status line for {@value #NOTICE_SECONDS} s instead of the rule pass's. */
    public void notice(String text, int colour) {
        notice = new Notice(text, colour);
        noticeLeft = NOTICE_SECONDS;
    }

    /** The frame time: a notice runs out. */
    public void update(double seconds) {
        noticeLeft -= seconds;
    }

    private Optional<Notice> notice() {
        return noticeLeft > 0.0 ? Optional.ofNullable(notice) : Optional.empty();
    }

    /** The status line's message: a notice, else the rule pass's finding at the cursor or the first one, else OK. */
    public String status() {
        return notice().map(Notice::text).orElseGet(() -> texts.get(EditorFindings.messageKey(shown())));
    }

    /** The status line's colour (ToB 2009: grey OK, yellow a warning, red an error). */
    public int statusColour() {
        return notice().map(Notice::colour).orElseGet(() -> EditorFindings.colour(shown()));
    }

    private Optional<Finding> shown() {
        return findings().shown(x, y, level);
    }

    /** The cursor's coordinates as ToB 2009 shows them ({@code X: 1,Y: 10}, 1-based). */
    public String coordinates() {
        return texts.format("editor.coordinates", x + 1, y + 1);
    }

    // ------------------------------------------------------------------ test play (spec decision 6)

    /** Options → Freeze Enemies (the original designer's "Enemy Movement" off): for the test plays only. */
    public boolean freezeEnemies() {
        return freezeEnemies;
    }

    public void toggleFreezeEnemies() {
        freezeEnemies = !freezeEnemies;
    }

    /**
     * Options → Play Tower: refused while the tower has an error (named in a notice, the cursor on its cell), asked
     * first if it cannot be won, else go.
     */
    public Play play() {
        Optional<Finding> error = findings().first(Severity.ERROR);
        if (error.isPresent()) {
            error.get().location().ifPresent(cell -> {
                level(cell.level());
                cursor(cell.x(), cell.y());
            });
            notice(refusal(error.get()), EditorFindings.RED);
            return Play.REFUSED;
        }
        return findings().first(Severity.UNPLAYABLE).isPresent() ? Play.ASK : Play.GO;
    }

    /** "No floor for object: you can't play this tower!" */
    public String refusal(Finding error) {
        return texts.format("editor.play.refused", texts.get(EditorFindings.messageKey(Optional.of(error))));
    }

    /** The test play ended (completed, failed, timed out or abandoned): its result in a notice. */
    public void testEnded(int exitCode) {
        String reason = switch (exitCode) {
            case Rules.EXIT_COMPLETED -> "result.completed";
            case Rules.EXIT_TIMEOUT -> "result.timeout";
            case Rules.EXIT_SPIDERS_DESTROYED -> "result.spiders";
            case Rules.EXIT_GRABBER_DESTROYED -> "result.grabber";
            default -> "result.abandoned";
        };
        notice(texts.format("editor.notice.tested", texts.get(reason)), Drawing.WHITE);
    }
}
