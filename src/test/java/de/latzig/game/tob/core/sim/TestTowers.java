package de.latzig.game.tob.core.sim;

import java.util.Optional;

import de.latzig.game.tob.core.model.BaseType;
import de.latzig.game.tob.core.model.Behaviour;
import de.latzig.game.tob.core.model.Cell;
import de.latzig.game.tob.core.model.Conditions;
import de.latzig.game.tob.core.model.Effects;
import de.latzig.game.tob.core.model.Lock;
import de.latzig.game.tob.core.model.Moon;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.SampleTowers;
import de.latzig.game.tob.core.model.Sounds;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.model.TowerGrid;

/**
 * Small test towers from ASCII levels in the oracle conventions (oracle §10): x 0–7 west → east, z 0–7 south → north,
 * rows written north first (the last row is z = 0). Cells map to ToB 2026 like the converter: grid x = x + 4,
 * grid y = 11 − z. Legend: {@code .} empty, {@code F} floor, {@code B} box, {@code U} lift up start, {@code u} lift up
 * stop, {@code D} lift down start, {@code d} lift down stop.
 */
public final class TestTowers {

    public static final int X_OFFSET = 4;
    public static final int Y_FLIP = 11;

    private TestTowers() {
    }

    /** Oracle coordinates (x, z, level) → ToB 2026 cell. */
    public static CellPos pos(int x, int z, int level) {
        return new CellPos(x + X_OFFSET, Y_FLIP - z, level);
    }

    public static Builder tower() {
        return new Builder();
    }

    public static final class Builder {

        private final TowerGrid.Builder grid = TowerGrid.builder();
        private Conditions conditions = new Conditions(0, false, 0, 0);
        private String id = "test-000";
        private boolean cameras;
        private boolean minesDestroyFloor;
        private int timeBombSpeed;

        private Builder() {
        }

        public Builder level(int level, String... rows) {
            if (rows.length > 8) {
                throw new IllegalArgumentException("at most 8 rows per level");
            }
            for (int r = 0; r < rows.length; r++) {
                int z = rows.length - 1 - r;
                String row = rows[r];
                if (row.length() > 8) {
                    throw new IllegalArgumentException("at most 8 cells per row: " + row);
                }
                for (int x = 0; x < row.length(); x++) {
                    CellPos p = pos(x, z, level);
                    Cell old = grid.get(p.x(), p.y(), level);
                    grid.set(p.x(), p.y(), level, new Cell(base(row.charAt(x)), old.object()));
                }
            }
            return this;
        }

        public Builder object(int x, int z, int level, ObjectType type) {
            CellPos p = pos(x, z, level);
            Cell old = grid.get(p.x(), p.y(), level);
            grid.set(p.x(), p.y(), level, new Cell(old.base(), type));
            return this;
        }

        public Builder conditions(int timeLimitSeconds, boolean timeLimitEnabled, int klondikes, int robots) {
            this.conditions = new Conditions(timeLimitSeconds, timeLimitEnabled, klondikes, robots);
            return this;
        }

        /** The tower's "cameras enabled" switch ({@code W0014DE}); off by default. */
        public Builder cameras(boolean enabled) {
            this.cameras = enabled;
            return this;
        }

        /** The tower's "mines destroy floor" switch ({@code W0014E6}); off by default. */
        public Builder minesDestroyFloor(boolean enabled) {
            this.minesDestroyFloor = enabled;
            return this;
        }

        /** The tower's time bomb speed 0..3 ({@code W0014EA}); 0 by default. */
        public Builder timeBombSpeed(int speed) {
            this.timeBombSpeed = speed;
            return this;
        }

        public Builder id(String newId) {
            this.id = newId;
            return this;
        }

        public TowerDefinition build() {
            return new TowerDefinition(id, 0, 0, "Test", "", conditions,
                    new Behaviour(cameras, minesDestroyFloor, timeBombSpeed),
                    new Effects(false, false, false, 0, 0, 0), new Moon(Moon.NO_MOON, 0, 0, 0, 0),
                    SampleTowers.palette(), new Sounds(0, false, false), new Lock(false, ""), grid.build(),
                    Optional.empty());
        }

        private static BaseType base(char c) {
            return switch (c) {
                case '.' -> BaseType.EMPTY;
                case 'F' -> BaseType.FLOOR;
                case 'B' -> BaseType.BOX;
                case 'U' -> BaseType.LIFT_UP_START;
                case 'u' -> BaseType.LIFT_UP_STOP;
                case 'D' -> BaseType.LIFT_DOWN_START;
                case 'd' -> BaseType.LIFT_DOWN_STOP;
                default -> throw new IllegalArgumentException("unknown cell character '" + c + "'");
            };
        }
    }
}
