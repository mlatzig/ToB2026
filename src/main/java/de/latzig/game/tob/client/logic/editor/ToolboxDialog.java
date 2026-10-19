package de.latzig.game.tob.client.logic.editor;

import java.util.List;
import java.util.OptionalInt;

import de.latzig.game.tob.client.logic.ui.UiKey;
import de.latzig.game.tob.core.model.TowerGrid;

/**
 * The Tower Designer's Toolbox dialogs (Phase 6b; ToB 2009's {@code ToolboxScrollDialog}, {@code ToolboxRotateDialog}
 * and {@code ToolboxTowerLevelDialog}): Scroll (North, East, South, West) and Rotate (Left, Right), each with "Only
 * current Level?" (off), and Copy Level and Swap Level with the target level 1–6 (ToB 2009 starts at the current
 * level, where OK does nothing; here OK waits for another level). Left/Right change the choice (ToB 2009 starts at
 * North and Left), Space the switch, Enter is OK, Esc Cancel.
 */
public final class ToolboxDialog {

    public enum Kind {
        SCROLL, ROTATE, COPY_LEVEL, SWAP_LEVEL
    }

    private final Kind kind;
    private final int level;
    private int choice;
    private boolean onlyLevel;

    /** @param level the level shown in the grid (the source of Copy and Swap, "current" of the switch) */
    public ToolboxDialog(Kind kind, int level) {
        this.kind = kind;
        this.level = level;
        this.choice = kind == Kind.COPY_LEVEL || kind == Kind.SWAP_LEVEL ? level : 0;
    }

    public Kind kind() {
        return kind;
    }

    /** The bundle key of the dialog's caption (the menu entry's). */
    public String captionKey() {
        return switch (kind) {
            case SCROLL -> "editor.command.scroll";
            case ROTATE -> "editor.command.rotate";
            case COPY_LEVEL -> "editor.command.copy_level";
            case SWAP_LEVEL -> "editor.command.swap_level";
        };
    }

    /** The bundle key of the choice row's label. */
    public String labelKey() {
        return kind == Kind.SCROLL || kind == Kind.ROTATE ? "editor.toolbox.direction" : "editor.toolbox.target";
    }

    /** The choices' texts as bundle keys, or level numbers for Copy and Swap. */
    public List<String> choices() {
        return switch (kind) {
            case SCROLL -> List.of("editor.toolbox.north", "editor.toolbox.east", "editor.toolbox.south",
                    "editor.toolbox.west");
            case ROTATE -> List.of("editor.toolbox.left", "editor.toolbox.right");
            case COPY_LEVEL, SWAP_LEVEL -> List.of("1", "2", "3", "4", "5", "6");
        };
    }

    public int choice() {
        return choice;
    }

    public void choose(int index) {
        if (index >= 0 && index < choices().size()) {
            choice = index;
        }
    }

    /** Whether the dialog has the "Only current Level?" switch (Scroll and Rotate). */
    public boolean hasSwitch() {
        return kind == Kind.SCROLL || kind == Kind.ROTATE;
    }

    public boolean onlyLevel() {
        return onlyLevel;
    }

    public void toggleOnlyLevel() {
        onlyLevel = hasSwitch() && !onlyLevel;
    }

    /** Whether OK can be pressed: Copy and Swap need a level other than the current one. */
    public boolean valid() {
        return hasSwitch() || choice != level;
    }

    public LibraryDialog.Action key(UiKey key) {
        switch (key) {
            case LEFT -> choose(choice - 1);
            case RIGHT -> choose(choice + 1);
            case SPACE -> toggleOnlyLevel();
            case ENTER -> {
                return LibraryDialog.Action.OK;
            }
            case ESCAPE -> {
                return LibraryDialog.Action.CANCEL;
            }
            default -> {
            }
        }
        return LibraryDialog.Action.NONE;
    }

    /** The dialog's function on {@code grid} (OK). */
    public TowerGrid apply(TowerGrid grid) {
        OptionalInt only = onlyLevel ? OptionalInt.of(level) : OptionalInt.empty();
        return switch (kind) {
            case SCROLL -> EditorToolbox.scroll(grid, EditorToolbox.Direction.values()[choice], only);
            case ROTATE -> EditorToolbox.rotate(grid, choice == 1, only);
            case COPY_LEVEL -> EditorToolbox.copyLevel(grid, level, choice);
            case SWAP_LEVEL -> EditorToolbox.swapLevels(grid, level, choice);
        };
    }
}
