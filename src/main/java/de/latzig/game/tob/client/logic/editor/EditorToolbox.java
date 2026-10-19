package de.latzig.game.tob.client.logic.editor;

import java.util.OptionalInt;

import de.latzig.game.tob.core.model.Cell;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.TowerGrid;

/**
 * The Tower Designer's Toolbox menu (Phase 6b; ToB 2009's {@code EditorTowerUtility}, pure functions returning a new
 * grid): scroll the tower or one level by a cell (wrapping round, as ToB 2009), rotate it by 90° (objects turn with
 * it: facing, axis and prism corner), copy or swap a level, clear a level or everything. Scrolling or rotating one
 * level only may break its lifts; the rule pass marks them.
 */
public final class EditorToolbox {

    /** A scroll direction: north is row 0. */
    public enum Direction {
        NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0);

        private final int dx;
        private final int dy;

        Direction(int dx, int dy) {
            this.dx = dx;
            this.dy = dy;
        }
    }

    private EditorToolbox() {
    }

    /** Moves every cell one step towards {@code direction}, the edge row wrapping round; one level or all. */
    public static TowerGrid scroll(TowerGrid grid, Direction direction, OptionalInt level) {
        TowerGrid.Builder b = grid.toBuilder();
        for (int l = 0; l < TowerGrid.LEVELS; l++) {
            if (level.isPresent() && level.getAsInt() != l) {
                continue;
            }
            for (int y = 0; y < TowerGrid.DEPTH; y++) {
                for (int x = 0; x < TowerGrid.WIDTH; x++) {
                    b.set(Math.floorMod(x + direction.dx, TowerGrid.WIDTH),
                            Math.floorMod(y + direction.dy, TowerGrid.DEPTH), l, grid.cell(x, y, l));
                }
            }
        }
        return b.build();
    }

    /** Turns the grid by 90° clockwise seen from above ({@code clockwise}) or anticlockwise; one level or all. */
    public static TowerGrid rotate(TowerGrid grid, boolean clockwise, OptionalInt level) {
        TowerGrid.Builder b = grid.toBuilder();
        for (int l = 0; l < TowerGrid.LEVELS; l++) {
            if (level.isPresent() && level.getAsInt() != l) {
                continue;
            }
            for (int y = 0; y < TowerGrid.DEPTH; y++) {
                for (int x = 0; x < TowerGrid.WIDTH; x++) {
                    Cell cell = grid.cell(x, y, l);
                    Cell turned = new Cell(cell.base(), cell.hasObject() ? turn(cell.object(), clockwise) : null);
                    if (clockwise) {
                        b.set(TowerGrid.DEPTH - 1 - y, x, l, turned);
                    } else {
                        b.set(y, TowerGrid.WIDTH - 1 - x, l, turned);
                    }
                }
            }
        }
        return b.build();
    }

    /** Level {@code from} copied onto level {@code to}. */
    public static TowerGrid copyLevel(TowerGrid grid, int from, int to) {
        TowerGrid.Builder b = grid.toBuilder();
        for (int y = 0; y < TowerGrid.DEPTH; y++) {
            for (int x = 0; x < TowerGrid.WIDTH; x++) {
                b.set(x, y, to, grid.cell(x, y, from));
            }
        }
        return b.build();
    }

    /** Levels {@code a} and {@code b} swapped. */
    public static TowerGrid swapLevels(TowerGrid grid, int a, int b) {
        TowerGrid.Builder builder = grid.toBuilder();
        for (int y = 0; y < TowerGrid.DEPTH; y++) {
            for (int x = 0; x < TowerGrid.WIDTH; x++) {
                builder.set(x, y, a, grid.cell(x, y, b));
                builder.set(x, y, b, grid.cell(x, y, a));
            }
        }
        return builder.build();
    }

    /** One level emptied, or every level. */
    public static TowerGrid clear(TowerGrid grid, OptionalInt level) {
        if (level.isEmpty()) {
            return TowerGrid.empty();
        }
        TowerGrid.Builder b = grid.toBuilder();
        for (int y = 0; y < TowerGrid.DEPTH; y++) {
            for (int x = 0; x < TowerGrid.WIDTH; x++) {
                b.set(x, y, level.getAsInt(), Cell.EMPTY);
            }
        }
        return b.build();
    }

    /** ToB 2009's {@code rotateRightObjectElement}, and its inverse for anticlockwise. */
    static ObjectType turn(ObjectType object, boolean clockwise) {
        if (!clockwise) {
            return turn(turn(turn(object, true), true), true);
        }
        return switch (object) {
            case FZN -> ObjectType.FZE;
            case FZE -> ObjectType.FZS;
            case FZS -> ObjectType.FZW;
            case FZW -> ObjectType.FZN;
            case FPN -> ObjectType.FPE;
            case FPE -> ObjectType.FPS;
            case FPS -> ObjectType.FPW;
            case FPW -> ObjectType.FPN;
            case WOE -> ObjectType.WON;
            case WON -> ObjectType.WOE;
            case LZE -> ObjectType.LZN;
            case LZN -> ObjectType.LZE;
            case CVE -> ObjectType.CVN;
            case CVN -> ObjectType.CVE;
            case PNW -> ObjectType.PNE;
            case PNE -> ObjectType.PSE;
            case PSE -> ObjectType.PSW;
            case PSW -> ObjectType.PNW;
            default -> object;
        };
    }
}
