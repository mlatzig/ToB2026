package de.latzig.game.tob.core.sim;

import de.latzig.game.tob.core.model.BaseType;

/**
 * Runtime cell flags with the bit values of the original flag map {@code W002372} (oracle §1.2). The start values
 * come from table {@code L012CAE} ({@code S:17782}): codes 0–6 = $00 $C0 $C8 $E0 $F0 $30 $E8. Bit 6 (surface drawn)
 * and bit 2 (lit) are render-only and are not kept; bit 0 (border) is "outside the grid".
 */
public final class CellFlags {

    /** Bit 7: floor present (walkable unless blocked). */
    public static final int FLOOR = 0x80;
    /** Bit 5: lift cell. */
    public static final int LIFT = 0x20;
    /** Bit 4: the lift rides down from this cell (upper cell of a shaft). */
    public static final int LIFT_DOWN = 0x10;
    /** Bit 3: blocked (box, or the lower cell of a shaft whose platform is up). */
    public static final int BLOCKED = 0x08;

    private CellFlags() {
    }

    public static int of(BaseType base) {
        return switch (base) {
            case EMPTY -> 0;
            case FLOOR -> FLOOR;
            case BOX -> FLOOR | BLOCKED;
            case LIFT_UP_START -> FLOOR | LIFT;
            case LIFT_DOWN_START -> FLOOR | LIFT | LIFT_DOWN;
            case LIFT_UP_STOP -> LIFT | LIFT_DOWN;
            case LIFT_DOWN_STOP -> FLOOR | LIFT | BLOCKED;
        };
    }
}
