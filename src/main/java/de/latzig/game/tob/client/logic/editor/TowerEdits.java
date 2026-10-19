package de.latzig.game.tob.client.logic.editor;

import java.util.Optional;

import de.latzig.game.tob.core.model.BaseType;
import de.latzig.game.tob.core.model.Cell;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.TowerGrid;

/**
 * The Tower Designer's placements (Phase 6b spec decision 4) as pure functions on a {@link TowerGrid}: the original
 * designer's cell rules ({@code L00F886}, {@code S:14223-14378}; ToB 2026 keeps its cell model: explicit lift stops,
 * a down lift's start on top) with the spec's changes: placing is always allowed — what is invalid stays and is
 * marked by the rule pass ({@link EditorFindings}) — and the unambiguous follow-ups are fixed silently.
 * <ul>
 * <li>Floor: the cell's base becomes floor, its object stays.</li>
 * <li>Box (the original's support): the object goes; an empty cell above becomes floor (ToB 2009's synchronizer
 * and the original).</li>
 * <li>Up lift: start here, stop above (its object goes); down lift: start here, stop below (its object goes). On
 * the top level (up) or level 0 (down) the start stays alone and is marked ("Can't place lift here").</li>
 * <li>Object: set on the cell whatever its base (an object without floor is marked); a spider placed again moves
 * there (the second replaces the first).</li>
 * <li>Overwriting or removing a lift cell removes the whole lift: its stop becomes empty, its start floor.</li>
 * <li>Removing (right button): the object first, else the base — except a floor on a box, which the original
 * refuses ("Supported block, can't remove").</li>
 * </ul>
 */
public final class TowerEdits {

    /** The bundle key of the original's "Supported block, can't remove". */
    public static final String SUPPORTED = "editor.notice.supported";

    /** A placement's result: the new grid (the same instance if nothing changed) and a notice, if any. */
    public record Result(TowerGrid grid, Optional<String> notice) {

        static Result of(TowerGrid grid) {
            return new Result(grid, Optional.empty());
        }
    }

    private TowerEdits() {
    }

    /** Places {@code element} at ({@code x}, {@code y}, {@code level}). */
    public static Result place(TowerGrid grid, EditorElement element, int x, int y, int level) {
        TowerGrid.Builder b = grid.toBuilder();
        switch (element.kind()) {
            case FLOOR -> {
                unlink(b, x, y, level);
                b.set(x, y, level, new Cell(BaseType.FLOOR, b.get(x, y, level).object()));
            }
            case BOX -> {
                unlink(b, x, y, level);
                b.set(x, y, level, new Cell(BaseType.BOX, null));
                if (level + 1 < TowerGrid.LEVELS && b.get(x, y, level + 1).base() == BaseType.EMPTY) {
                    b.set(x, y, level + 1, new Cell(BaseType.FLOOR, b.get(x, y, level + 1).object()));
                }
            }
            case LIFT_UP -> lift(b, x, y, level, BaseType.LIFT_UP_START, BaseType.LIFT_UP_STOP, level + 1);
            case LIFT_DOWN -> lift(b, x, y, level, BaseType.LIFT_DOWN_START, BaseType.LIFT_DOWN_STOP, level - 1);
            case OBJECT -> {
                ObjectType object = element.object();
                if (object.isSpider()) {
                    removeEverywhere(b, object);
                }
                b.set(x, y, level, new Cell(b.get(x, y, level).base(), object));
            }
        }
        return Result.of(built(grid, b));
    }

    /** Removes the object at the cell, else its base (the right mouse button). */
    public static Result remove(TowerGrid grid, int x, int y, int level) {
        Cell cell = grid.cell(x, y, level);
        TowerGrid.Builder b = grid.toBuilder();
        if (cell.hasObject()) {
            b.set(x, y, level, new Cell(cell.base(), null));
            return Result.of(b.build());
        }
        if (cell.base() == BaseType.EMPTY) {
            return Result.of(grid);
        }
        if (cell.base() == BaseType.FLOOR && level > 0 && grid.cell(x, y, level - 1).base() == BaseType.BOX) {
            return new Result(grid, Optional.of(SUPPORTED));
        }
        unlink(b, x, y, level);
        b.set(x, y, level, Cell.EMPTY);
        return Result.of(b.build());
    }

    private static void lift(TowerGrid.Builder b, int x, int y, int level, BaseType start, BaseType stop,
            int stopLevel) {
        unlink(b, x, y, level);
        b.set(x, y, level, new Cell(start, b.get(x, y, level).object()));
        if (stopLevel >= 0 && stopLevel < TowerGrid.LEVELS) {
            unlink(b, x, y, stopLevel);
            b.set(x, y, stopLevel, new Cell(stop, null));
        }
    }

    /**
     * If the cell belongs to a lift, its other cell loses the lift: a stop becomes empty, a start floor (both keep
     * their object). The cell itself is left to the caller.
     */
    private static void unlink(TowerGrid.Builder b, int x, int y, int level) {
        int partner = switch (b.get(x, y, level).base()) {
            case LIFT_UP_START, LIFT_DOWN_STOP -> level + 1;
            case LIFT_UP_STOP, LIFT_DOWN_START -> level - 1;
            default -> -1;
        };
        if (partner < 0 || partner >= TowerGrid.LEVELS || !paired(b.get(x, y, level).base(),
                b.get(x, y, partner).base())) {
            return;
        }
        Cell other = b.get(x, y, partner);
        boolean stop = other.base() == BaseType.LIFT_UP_STOP || other.base() == BaseType.LIFT_DOWN_STOP;
        b.set(x, y, partner, new Cell(stop ? BaseType.EMPTY : BaseType.FLOOR, other.object()));
    }

    private static boolean paired(BaseType base, BaseType other) {
        return switch (base) {
            case LIFT_UP_START -> other == BaseType.LIFT_UP_STOP;
            case LIFT_UP_STOP -> other == BaseType.LIFT_UP_START;
            case LIFT_DOWN_START -> other == BaseType.LIFT_DOWN_STOP;
            case LIFT_DOWN_STOP -> other == BaseType.LIFT_DOWN_START;
            default -> false;
        };
    }

    private static void removeEverywhere(TowerGrid.Builder b, ObjectType object) {
        for (int level = 0; level < TowerGrid.LEVELS; level++) {
            for (int y = 0; y < TowerGrid.DEPTH; y++) {
                for (int x = 0; x < TowerGrid.WIDTH; x++) {
                    Cell cell = b.get(x, y, level);
                    if (cell.object() == object) {
                        b.set(x, y, level, new Cell(cell.base(), null));
                    }
                }
            }
        }
    }

    /** The builder's grid, or {@code grid} itself if nothing changed (no undo step for a repeated placement). */
    private static TowerGrid built(TowerGrid grid, TowerGrid.Builder b) {
        TowerGrid next = b.build();
        return next.equals(grid) ? grid : next;
    }
}
