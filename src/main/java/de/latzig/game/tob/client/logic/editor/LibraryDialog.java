package de.latzig.game.tob.client.logic.editor;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import de.latzig.game.tob.client.logic.ui.ScrollBox;
import de.latzig.game.tob.client.logic.ui.UiKey;

/**
 * The Tower Designer's library dialog (Phase 6b; ToB 2009's {@code TowerLibraryDialog}): File → Open lists every
 * library (the shipped ones read-only); File → Save As lists the own libraries and takes a name, typed or picked from
 * the list. Up/Down move in the list, Enter is OK, Esc Cancel; typed letters, digits and {@code _} go to the name (in
 * lower case, at most {@value TowerLibraries#MAX_NAME}), Backspace deletes.
 */
public final class LibraryDialog {

    public enum Mode {
        OPEN, SAVE_AS
    }

    /** What a key or a button asks for. */
    public enum Action {
        NONE, OK, CANCEL
    }

    /** The list's visible rows. */
    public static final int ROWS = 8;

    private final Mode mode;
    private final List<TowerLibraries.Entry> entries;
    private final ScrollBox box = new ScrollBox(ROWS);
    private final StringBuilder name = new StringBuilder();
    private int selected = -1;
    private String error = "";

    /** @param name the Save As dialog's name to start with (the open library's), else empty */
    public LibraryDialog(Mode mode, List<TowerLibraries.Entry> entries, String name) {
        this.mode = mode;
        this.entries = List.copyOf(mode == Mode.OPEN ? entries
                : entries.stream().filter(entry -> !entry.readOnly()).toList());
        box.size(this.entries.size());
        this.name.append(name);
        if (mode == Mode.OPEN && !this.entries.isEmpty()) {
            select(0);
        }
    }

    public Mode mode() {
        return mode;
    }

    public List<TowerLibraries.Entry> entries() {
        return entries;
    }

    /** The selected row, −1 for none. */
    public int selected() {
        return selected;
    }

    /** Selects row {@code index}; in Save As its name becomes the name. */
    public void select(int index) {
        if (index < 0 || index >= entries.size()) {
            return;
        }
        selected = index;
        box.show(index);
        if (mode == Mode.SAVE_AS) {
            name.setLength(0);
            name.append(entries.get(index).name());
        }
    }

    public ScrollBox box() {
        return box;
    }

    /** The library chosen for Open. */
    public Optional<TowerLibraries.Entry> choice() {
        return selected < 0 ? Optional.empty() : Optional.of(entries.get(selected));
    }

    /** Save As's name as typed. */
    public String name() {
        return name.toString();
    }

    /** The message under the name field (an invalid name), else empty. */
    public String error() {
        return error;
    }

    public void error(String message) {
        error = message;
    }

    /** A typed character: Save As's name takes letters, digits and {@code _}. */
    public void type(char c) {
        if (mode == Mode.SAVE_AS && name.length() < TowerLibraries.MAX_NAME
                && (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_')) {
            name.append(String.valueOf(c).toLowerCase(Locale.ROOT));
            error = "";
        }
    }

    public Action key(UiKey key) {
        switch (key) {
            case UP -> select(Math.max(0, selected - 1));
            case DOWN -> select(Math.min(entries.size() - 1, selected + 1));
            case BACKSPACE -> {
                if (!name.isEmpty()) {
                    name.setLength(name.length() - 1);
                }
            }
            case ENTER -> {
                return Action.OK;
            }
            case ESCAPE -> {
                return Action.CANCEL;
            }
            default -> {
            }
        }
        return Action.NONE;
    }
}
