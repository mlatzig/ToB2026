package de.latzig.game.tob.client.logic.editor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.latzig.game.tob.client.logic.Texts;
import de.latzig.game.tob.client.logic.frontend.GameFlow;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Hit;
import de.latzig.game.tob.client.logic.ui.UiKey;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.model.TowerGrid;

/**
 * The Tower Designer screen's logic (Phase 6b, ToB 2009's {@code EditorWindow} and its actions, engine-free): the menu
 * bar File / Edit / Options / Toolbox with ToB 2009's entries plus Undo, Redo and Freeze Enemies; the library and
 * Toolbox dialogs; the questions before something is lost (unsaved changes, Remove, Clear, a tower that cannot be won)
 * and the mouse and keyboard over the screen. It edits through its {@link EditorModel} and asks the
 * {@link GameFlow} for what only the flow can do ({@link Request}): leave to the main menu, start a test play, show a
 * question or a message. Phase 6b-2: Options → Settings and Effects (spec decision 8; a click on the title strip
 * opens Settings) open their dialogs ({@link TowerDialog}); a colour button of Effects opens the colour dialog over
 * it. While a dialog is open the 3D preview shows the tower as the dialog would leave it ({@link #shown()}); OK
 * applies it as one undo step, Cancel drops it. A read-only library greys both. The 3D preview (spec decision 3,
 * {@link PreviewCamera}): left-drag turns and tilts it, a right click resets the view, a double click toggles the
 * small area and the full screen; on the full screen Esc returns to the small area, Ctrl+Z and Ctrl+Y still undo
 * and redo, other keys do nothing.
 * <p>
 * Mouse (ToB 2009): over the grid the cursor follows the mouse; the left button places the palette's element and
 * paints while held, the right button removes; a click selects a palette entry, a level of the strip or a tower of
 * the list (the empty row after the last adds one). A menu opens with a click on its title and follows the mouse
 * along the bar; a click elsewhere closes it. Keyboard (new in ToB 2026; ToB 2009 had none): the arrows move the
 * cursor, Space or Enter place, Delete or Backspace remove, Page Up and Page Down change the level, Ctrl+Z and Ctrl+Y
 * undo and redo, Esc closes a menu or dialog, else it is File → Exit. In an open menu Up/Down/Left/Right move and
 * Enter chooses.
 */
public final class TowerDesigner {

    private static final Logger LOG = LoggerFactory.getLogger(TowerDesigner.class);

    /** The menu bar (ToB 2009). */
    public enum Menu {
        FILE, EDIT, OPTIONS, TOOLBOX;

        public String textKey() {
            return "editor.menu." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** The menu entries in ToB 2009's order; a separator line before {@code separated} ones. */
    public enum Command {
        NEW(Menu.FILE, false), OPEN(Menu.FILE, false), SAVE_AS(Menu.FILE, false), SAVE(Menu.FILE, false),
        EXIT(Menu.FILE, true),
        UNDO(Menu.EDIT, false), REDO(Menu.EDIT, false), COPY(Menu.EDIT, true), PASTE(Menu.EDIT, false),
        INSERT(Menu.EDIT, true), REMOVE(Menu.EDIT, false), DELETE(Menu.EDIT, true),
        SETTINGS(Menu.OPTIONS, false), EFFECTS(Menu.OPTIONS, false), PLAY(Menu.OPTIONS, true),
        FREEZE(Menu.OPTIONS, false),
        SCROLL(Menu.TOOLBOX, false), ROTATE(Menu.TOOLBOX, false), COPY_LEVEL(Menu.TOOLBOX, true),
        SWAP_LEVEL(Menu.TOOLBOX, false), CLEAR_LEVEL(Menu.TOOLBOX, true), CLEAR_ALL(Menu.TOOLBOX, false);

        private final Menu menu;
        private final boolean separated;

        Command(Menu menu, boolean separated) {
            this.menu = menu;
            this.separated = separated;
        }

        public Menu menu() {
            return menu;
        }

        /** Whether a separator line stands before this entry. */
        public boolean separated() {
            return separated;
        }

        public String textKey() {
            return "editor.command." + name().toLowerCase(Locale.ROOT);
        }

        /** The entries of {@code menu} in order. */
        public static List<Command> of(Menu menu) {
            List<Command> commands = new ArrayList<>();
            for (Command command : values()) {
                if (command.menu == menu) {
                    commands.add(command);
                }
            }
            return commands;
        }
    }

    /** A question waiting for YES or NO; its caption. */
    public enum Ask {
        DISCARD_NEW("editor.ask.discard"), DISCARD_OPEN("editor.ask.discard"), DISCARD_EXIT("editor.ask.discard"),
        REMOVE("editor.ask.remove"), CLEAR_LEVEL("editor.ask.clear_level"), CLEAR_ALL("editor.ask.clear_all"),
        PLAY("editor.ask.play"), OVERWRITE("editor.ask.overwrite"),
        /** Final review M2: Save, over a library opened with towers it could not read (dropped for good on save). */
        SAVE_DROPPED("editor.ask.save_dropped");

        private final String titleKey;

        Ask(String titleKey) {
            this.titleKey = titleKey;
        }

        public String titleKey() {
            return titleKey;
        }
    }

    /** What the flow has to do. */
    public sealed interface Request {
    }

    /** Nothing. */
    public record None() implements Request {
    }

    /** Leave the designer to the main menu. */
    public record Exit() implements Request {
    }

    /** Test-play {@code tower} (its number for the title bar; the enemies frozen or not). */
    public record Test(TowerDefinition tower, int number, boolean freezeEnemies) implements Request {
    }

    /** Ask ARE YOU SURE? under {@code title}; {@link #answer} gets the reply. */
    public record Confirm(String title) implements Request {
    }

    /** Show {@code text} with OK. */
    public record Message(String text) implements Request {
    }

    public static final Request NONE = new None();
    /** {@code EDITOR_DIALOG} rows: OK, Cancel, a choice ({@code DIALOG_CHOICE} + index), the switch. */
    public static final int DIALOG_OK = 0;
    public static final int DIALOG_CANCEL = 1;
    public static final int DIALOG_CHOICE = 10;
    public static final int DIALOG_SWITCH = 20;
    /** {@code EDITOR_DIALOG} row of the Effects dialog's "Randomize Colors". */
    public static final int DIALOG_RANDOMIZE = 30;

    private final TowerLibraries libraries;
    private final Texts texts;
    private final EditorModel model;
    private Menu menu;
    private Command hovered;
    private LibraryDialog library;
    private ToolboxDialog toolbox;
    private Ask pending;
    private TowerLibraries.Entry pendingEntry;
    private String pendingName;
    /** Phase 6b-2: the open Settings or Effects dialog, the colour dialog over it and the field it edits. */
    private TowerDialog dialog;
    private ColourDialog colour;
    private int colourField;
    private final Random random;
    /** Phase 6b-2: the 3D preview's view, and the designer's clock for its double click. */
    private final PreviewCamera preview = new PreviewCamera();
    private double clock;

    public TowerDesigner(TowerLibraries libraries, Texts texts) {
        this(libraries, texts, new Random());
    }

    /** @param random the Effects dialog's "Randomize Colors" (tests: a seeded one) */
    public TowerDesigner(TowerLibraries libraries, Texts texts, Random random) {
        this(libraries, texts, random, new TowerIds());
    }

    /**
     * @param random the Effects dialog's "Randomize Colors" (tests: a seeded one)
     * @param ids    where new towers get their ids (Phase 6d decision 7; tests: a predictable source)
     */
    public TowerDesigner(TowerLibraries libraries, Texts texts, Random random, TowerIds ids) {
        this.libraries = Objects.requireNonNull(libraries, "libraries");
        this.texts = texts;
        this.model = new EditorModel(texts, ids);
        this.random = Objects.requireNonNull(random, "random");
    }

    public EditorModel model() {
        return model;
    }

    // ------------------------------------------------------------------ state for the screen

    /** The open menu, if any. */
    public Optional<Menu> menu() {
        return Optional.ofNullable(menu);
    }

    /** The entry under the mouse (or chosen by the keys) in the open menu. */
    public Optional<Command> hovered() {
        return Optional.ofNullable(hovered);
    }

    public Optional<LibraryDialog> libraryDialog() {
        return Optional.ofNullable(library);
    }

    public Optional<ToolboxDialog> toolboxDialog() {
        return Optional.ofNullable(toolbox);
    }

    /** The open Settings or Effects dialog, if any. */
    public Optional<TowerDialog> towerDialog() {
        return Optional.ofNullable(dialog);
    }

    /** The colour dialog over the Effects dialog, if any. */
    public Optional<ColourDialog> colourDialog() {
        return Optional.ofNullable(colour);
    }

    /** The 3D preview's view: turned, tilted, small or full screen. */
    public PreviewCamera preview() {
        return preview;
    }

    /** The tower the 3D preview shows: the selected one, as the open Settings or Effects dialog would leave it. */
    public TowerDefinition shown() {
        return dialog == null ? model.tower() : dialog.apply(model.tower());
    }

    /** Whether {@code command} can be chosen now (greyed otherwise). */
    public boolean enabled(Command command) {
        return switch (command) {
            case SAVE -> !model.readOnly() && model.library().isPresent();
            case UNDO -> model.canUndo();
            case REDO -> model.canRedo();
            case PASTE -> model.canPaste();
            case INSERT -> model.canInsert();
            case REMOVE -> model.canRemove();
            case DELETE, SCROLL, ROTATE, COPY_LEVEL, SWAP_LEVEL, CLEAR_LEVEL, CLEAR_ALL, SETTINGS, EFFECTS ->
                !model.readOnly();
            default -> true;
        };
    }

    /** The entry's text; Freeze Enemies with its check mark. */
    public String label(Command command) {
        String text = texts.get(command.textKey());
        if (command == Command.FREEZE) {
            return texts.format(model.freezeEnemies() ? "editor.check.on" : "editor.check.off", text);
        }
        return text;
    }

    /** The open question's caption. */
    public Optional<Ask> pending() {
        return Optional.ofNullable(pending);
    }

    // ------------------------------------------------------------------ input

    /** The frame time (the status line's notices, the preview's double click). */
    public void update(double seconds) {
        clock += seconds;
        model.update(seconds);
    }

    /** Phase 6b-2: the mouse is at ({@code x}, {@code y}) (GUI pixels): it drags the preview while that is held. */
    public void mouse(float x, float y) {
        preview.mouse(x, y);
    }

    /**
     * A mouse button went down over {@code hit} (nothing: empty). Final review M7: the right button only acts on
     * the grid (removing); on a menu entry, a dialog's OK or the palette/list/level strip it does nothing, unlike
     * the left button (ToB 2009: the right button only ever removed from the grid).
     */
    public Request press(Optional<Hit> hit, boolean right) {
        if (right) {
            if (library == null && toolbox == null && dialog == null && menu == null && hit.isPresent()) {
                int row = hit.get().row();
                if (hit.get().widget() == GameFlow.Widget.EDITOR_CELL) {
                    model.press(row % TowerGrid.WIDTH, row / TowerGrid.WIDTH, true);
                } else if (hit.get().widget() == GameFlow.Widget.EDITOR_PREVIEW) {
                    preview.reset();
                }
            }
            return NONE;
        }
        if (library != null) {
            return hit.map(this::libraryClick).orElse(NONE);
        }
        if (toolbox != null) {
            hit.ifPresent(this::toolboxClick);
            return NONE;
        }
        if (dialog != null) {
            hit.ifPresent(this::dialogClick);
            return NONE;
        }
        if (menu != null) {
            return menuClick(hit);
        }
        if (hit.isEmpty()) {
            return NONE;
        }
        int row = hit.get().row();
        switch (hit.get().widget()) {
            case EDITOR_MENU -> openMenu(Menu.values()[row]);
            case EDITOR_CELL -> model.press(row % TowerGrid.WIDTH, row / TowerGrid.WIDTH, false);
            case EDITOR_PALETTE -> model.tool(EditorElement.PALETTE.get(row));
            case EDITOR_LEVEL -> model.level(row);
            case EDITOR_LIST -> model.select(row);
            case EDITOR_PREVIEW -> preview.press(clock);
            case EDITOR_TITLE -> {
                return enabled(Command.SETTINGS) ? run(Command.SETTINGS) : NONE;
            }
            default -> {
            }
        }
        return NONE;
    }

    /** The mouse moved over {@code hit}, the left button {@code held} or not. */
    public void pointer(Optional<Hit> hit, boolean held) {
        if (library != null || toolbox != null || dialog != null || hit.isEmpty()) {
            return;
        }
        int row = hit.get().row();
        switch (hit.get().widget()) {
            case EDITOR_MENU -> {
                if (menu != null && menu.ordinal() != row) {
                    openMenu(Menu.values()[row]);
                }
            }
            case EDITOR_ITEM -> {
                if (menu != null) {
                    hovered = Command.values()[row];
                }
            }
            case EDITOR_CELL -> {
                if (menu == null) {
                    if (held) {
                        model.drag(row % TowerGrid.WIDTH, row / TowerGrid.WIDTH);
                    } else {
                        model.cursor(row % TowerGrid.WIDTH, row / TowerGrid.WIDTH);
                    }
                }
            }
            default -> {
            }
        }
    }

    /** A mouse button went up: a painting stroke ends, the preview is let go. */
    public void release() {
        model.release();
        preview.release();
    }

    public Request key(UiKey key) {
        if (preview.maximized()) {
            switch (key) {
                case ESCAPE -> preview.restore();
                case UNDO -> model.undo();
                case REDO -> model.redo();
                default -> {
                }
            }
            return NONE;
        }
        if (library != null) {
            return libraryAction(library.key(key));
        }
        if (toolbox != null) {
            toolboxAction(toolbox.key(key));
            return NONE;
        }
        if (colour != null) {
            colourAction(colour.form().key(key));
            return NONE;
        }
        if (dialog != null) {
            dialogAction(dialog.form().key(key));
            return NONE;
        }
        if (menu != null) {
            return menuKey(key);
        }
        switch (key) {
            case UP -> model.moveCursor(0, -1);
            case DOWN -> model.moveCursor(0, 1);
            case LEFT -> model.moveCursor(-1, 0);
            case RIGHT -> model.moveCursor(1, 0);
            case SPACE, ENTER -> model.placeAtCursor();
            case DELETE, BACKSPACE -> model.removeAtCursor();
            case PAGE_UP -> model.level(model.level() + 1);
            case PAGE_DOWN -> model.level(model.level() - 1);
            case UNDO -> model.undo();
            case REDO -> model.redo();
            case ESCAPE -> {
                return run(Command.EXIT);
            }
            default -> {
            }
        }
        return NONE;
    }

    /** A typed character: Save As's name field, or a text or number box of the open dialog. */
    public void typed(char c) {
        if (library != null) {
            library.type(c);
        } else if (colour != null) {
            colour.form().type(c);
            colourChanged();
        } else if (dialog != null) {
            dialog.form().type(c);
            dialog.refresh();
        }
    }

    /** The reply to the open question. */
    public Request answer(boolean yes) {
        Ask asked = pending;
        pending = null;
        if (!yes || asked == null) {
            return NONE;
        }
        switch (asked) {
            case DISCARD_NEW -> model.newLibrary();
            case DISCARD_OPEN -> {
                return open(pendingEntry);
            }
            case DISCARD_EXIT -> {
                return new Exit();
            }
            case REMOVE -> model.remove();
            case CLEAR_LEVEL -> model.apply(grid -> EditorToolbox.clear(grid, OptionalInt.of(model.level())));
            case CLEAR_ALL -> model.apply(grid -> EditorToolbox.clear(grid, OptionalInt.empty()));
            case PLAY -> {
                return test();
            }
            case OVERWRITE, SAVE_DROPPED -> {
                return save(pendingName);
            }
        }
        return NONE;
    }

    // ------------------------------------------------------------------ the menus

    private void openMenu(Menu opened) {
        menu = opened;
        hovered = null;
    }

    private void closeMenu() {
        menu = null;
        hovered = null;
    }

    private Request menuClick(Optional<Hit> hit) {
        if (hit.isPresent() && hit.get().widget() == GameFlow.Widget.EDITOR_MENU) {
            Menu clicked = Menu.values()[hit.get().row()];
            if (clicked == menu) {
                closeMenu();
            } else {
                openMenu(clicked);
            }
            return NONE;
        }
        if (hit.isPresent() && hit.get().widget() == GameFlow.Widget.EDITOR_ITEM) {
            Command command = Command.values()[hit.get().row()];
            if (!enabled(command)) {
                return NONE;
            }
            closeMenu();
            return run(command);
        }
        closeMenu();
        return NONE;
    }

    private Request menuKey(UiKey key) {
        List<Command> entries = Command.of(menu);
        switch (key) {
            case LEFT, RIGHT -> openMenu(Menu.values()[Math.floorMod(menu.ordinal() + (key == UiKey.LEFT ? -1 : 1),
                    Menu.values().length)]);
            case UP, DOWN -> {
                int at = hovered == null ? (key == UiKey.UP ? entries.size() : -1) : entries.indexOf(hovered);
                for (int i = 1; i <= entries.size(); i++) {
                    Command next = entries.get(Math.floorMod(at + (key == UiKey.UP ? -i : i), entries.size()));
                    if (enabled(next)) {
                        hovered = next;
                        break;
                    }
                }
            }
            case ENTER, SPACE -> {
                if (hovered != null && enabled(hovered)) {
                    Command chosen = hovered;
                    closeMenu();
                    return run(chosen);
                }
            }
            case ESCAPE -> closeMenu();
            default -> {
            }
        }
        return NONE;
    }

    /** Runs a menu entry (also what the tests choose). */
    public Request run(Command command) {
        if (!enabled(command)) {
            return NONE;
        }
        switch (command) {
            case NEW -> {
                return discardFirst(Ask.DISCARD_NEW, () -> model.newLibrary());
            }
            case OPEN -> {
                return listLibraries(LibraryDialog.Mode.OPEN);
            }
            case SAVE_AS -> {
                return listLibraries(LibraryDialog.Mode.SAVE_AS);
            }
            case SAVE -> {
                String name = model.library().orElseThrow();
                // Final review M2: a library opened with towers it could not read (unreadable, or beyond 117) must
                // not silently lose them for good; Save asks first, Save As is unaffected (a fresh file, not this
                // one's overwrite).
                if (model.hadDroppedTowers()) {
                    pendingName = name;
                    return ask(Ask.SAVE_DROPPED);
                }
                return save(name);
            }
            case EXIT -> {
                return model.dirty() ? ask(Ask.DISCARD_EXIT) : new Exit();
            }
            case UNDO -> model.undo();
            case REDO -> model.redo();
            case COPY -> model.copy();
            case PASTE -> model.paste();
            case INSERT -> model.insert();
            case REMOVE -> {
                return ask(Ask.REMOVE);
            }
            case DELETE -> model.delete();
            case PLAY -> {
                return switch (model.play()) {
                    case GO -> test();
                    case ASK -> ask(Ask.PLAY);
                    case REFUSED -> new Message(model.status());
                };
            }
            case FREEZE -> model.toggleFreezeEnemies();
            case SETTINGS -> dialog = new SettingsDialog(model.tower(), texts);
            case EFFECTS -> dialog = new EffectsDialog(model.tower(), texts);
            case SCROLL -> toolbox = new ToolboxDialog(ToolboxDialog.Kind.SCROLL, model.level());
            case ROTATE -> toolbox = new ToolboxDialog(ToolboxDialog.Kind.ROTATE, model.level());
            case COPY_LEVEL -> toolbox = new ToolboxDialog(ToolboxDialog.Kind.COPY_LEVEL, model.level());
            case SWAP_LEVEL -> toolbox = new ToolboxDialog(ToolboxDialog.Kind.SWAP_LEVEL, model.level());
            case CLEAR_LEVEL -> {
                return ask(Ask.CLEAR_LEVEL);
            }
            case CLEAR_ALL -> {
                return ask(Ask.CLEAR_ALL);
            }
            default -> {
            }
        }
        return NONE;
    }

    private Request ask(Ask question) {
        pending = question;
        return new Confirm(texts.get(question.titleKey()));
    }

    private Request discardFirst(Ask question, Runnable then) {
        if (model.dirty()) {
            return ask(question);
        }
        then.run();
        return NONE;
    }

    private Request test() {
        return new Test(model.tower(), model.number(), model.freezeEnemies());
    }

    // ------------------------------------------------------------------ the library dialog

    private Request listLibraries(LibraryDialog.Mode mode) {
        try {
            library = new LibraryDialog(mode, libraries.list(),
                    mode == LibraryDialog.Mode.SAVE_AS && !model.readOnly() ? model.library().orElse("") : "");
            return NONE;
        } catch (IOException e) {
            LOG.warn("Cannot list the tower libraries", e);
            return new Message(texts.format("editor.error.list", e.getMessage()));
        }
    }

    private Request libraryClick(Hit hit) {
        return switch (hit.widget()) {
            case EDITOR_DIALOG_LIST -> {
                library.select(hit.row());
                yield NONE;
            }
            case EDITOR_DIALOG -> libraryAction(hit.row() == DIALOG_OK ? LibraryDialog.Action.OK
                    : LibraryDialog.Action.CANCEL);
            default -> NONE;
        };
    }

    private Request libraryAction(LibraryDialog.Action action) {
        if (action == LibraryDialog.Action.CANCEL) {
            library = null;
            return NONE;
        }
        if (action == LibraryDialog.Action.NONE) {
            return NONE;
        }
        if (library.mode() == LibraryDialog.Mode.OPEN) {
            Optional<TowerLibraries.Entry> choice = library.choice();
            if (choice.isEmpty()) {
                return NONE;
            }
            library = null;
            pendingEntry = choice.get();
            return model.dirty() ? ask(Ask.DISCARD_OPEN) : open(pendingEntry);
        }
        String name = TowerLibraries.normalized(library.name());
        if (!TowerLibraries.validName(name)) {
            // Final review M6: a reserved name (a shipped library's, or "large") gets its own text rather than the
            // character-set message, which was wrong for that case (a Windows device name is an ordinary, valid
            // name: controller verified on Windows 11 that "con.json" etc. are ordinary files with the extension).
            library.error(TowerLibraries.reserved(name) ? texts.get("editor.library.reserved")
                    : texts.get("editor.library.invalid"));
            return NONE;
        }
        library = null;
        boolean sameLibrary = Optional.of(name).equals(model.library());
        // Re-review R1: Save As under the open library's own name (pre-filled, just Enter) overwrites the same
        // file as Save would, so it must ask the same M2 question first if towers could not be read on Open.
        if (sameLibrary && model.hadDroppedTowers()) {
            pendingName = name;
            return ask(Ask.SAVE_DROPPED);
        }
        if (libraries.exists(name) && !sameLibrary) {
            pendingName = name;
            return ask(Ask.OVERWRITE);
        }
        return save(name);
    }

    /**
     * Opens {@code entry} again when the designer is entered (GameFlow: the library of the last visit). Final
     * review M3: the {@link Request} is returned (not swallowed) so a failure (the file deleted or corrupted since
     * the last visit) still surfaces as a message instead of silently opening a fresh, unsaved library.
     */
    public Request reopen(TowerLibraries.Entry entry) {
        return open(entry);
    }

    private Request open(TowerLibraries.Entry entry) {
        try {
            model.load(libraries.open(entry));
            return NONE;
        } catch (IOException | RuntimeException e) {
            LOG.warn("Cannot open the tower library '{}'", entry.name(), e);
            return new Message(texts.format("editor.error.open", e.getMessage()));
        }
    }

    private Request save(String name) {
        try {
            List<TowerDefinition> towers = model.towersToSave();
            libraries.save(name, towers);
            model.saved(name, towers);
            return NONE;
        } catch (IOException e) {
            LOG.warn("Cannot save the tower library '{}'", name, e);
            return new Message(texts.format("editor.error.save", e.getMessage()));
        }
    }

    // ------------------------------------------------------------------ the Toolbox dialog

    private void toolboxClick(Hit hit) {
        if (hit.widget() != GameFlow.Widget.EDITOR_DIALOG) {
            return;
        }
        int row = hit.row();
        if (row == DIALOG_OK || row == DIALOG_CANCEL) {
            toolboxAction(row == DIALOG_OK ? LibraryDialog.Action.OK : LibraryDialog.Action.CANCEL);
        } else if (row == DIALOG_SWITCH) {
            toolbox.toggleOnlyLevel();
        } else {
            toolbox.choose(row - DIALOG_CHOICE);
        }
    }

    private void toolboxAction(LibraryDialog.Action action) {
        if (action == LibraryDialog.Action.CANCEL) {
            toolbox = null;
        } else if (action == LibraryDialog.Action.OK && toolbox.valid()) {
            ToolboxDialog done = toolbox;
            toolbox = null;
            model.apply(done::apply);
        }
    }

    // ------------------------------------------------------------------ the Settings, Effects and colour dialogs

    private void dialogClick(Hit hit) {
        int row = hit.row();
        if (colour != null) {
            if (hit.widget() == GameFlow.Widget.EDITOR_FIELD) {
                colour.form().click(row / EditorForm.PARTS, row % EditorForm.PARTS);
                colourChanged();
            } else if (hit.widget() == GameFlow.Widget.EDITOR_DIALOG) {
                colourAction(row == DIALOG_OK ? EditorForm.Action.OK : EditorForm.Action.CANCEL);
            }
            return;
        }
        if (hit.widget() == GameFlow.Widget.EDITOR_FIELD) {
            dialogAction(dialog.form().click(row / EditorForm.PARTS, row % EditorForm.PARTS));
        } else if (hit.widget() == GameFlow.Widget.EDITOR_DIALOG && row == DIALOG_RANDOMIZE) {
            if (dialog.randomizable()) {
                dialog.randomize(random);
            }
        } else if (hit.widget() == GameFlow.Widget.EDITOR_DIALOG) {
            dialogAction(row == DIALOG_OK ? EditorForm.Action.OK : EditorForm.Action.CANCEL);
        }
    }

    private void dialogAction(EditorForm.Action action) {
        switch (action) {
            case OK -> {
                TowerDialog done = dialog;
                dialog = null;
                model.edit(done::apply);
            }
            case CANCEL -> dialog = null;
            case COLOUR -> {
                colourField = dialog.form().focus();
                EditorForm.Field field = dialog.form().focused();
                colour = new ColourDialog(field.labelKey(), field.value(), field.twelveBit());
            }
            case NONE -> dialog.refresh();
        }
    }

    /** The colour dialog's channels changed: the Effects dialog's field (and the preview) follows at once. */
    private void colourChanged() {
        dialog.form().field(colourField).value(colour.colour());
    }

    private void colourAction(EditorForm.Action action) {
        if (action == EditorForm.Action.CANCEL) {
            dialog.form().field(colourField).value(colour.initial());
        } else {
            colourChanged();
        }
        if (action != EditorForm.Action.NONE) {
            colour = null;
        }
    }
}
