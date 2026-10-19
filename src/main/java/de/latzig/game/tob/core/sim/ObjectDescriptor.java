package de.latzig.game.tob.core.sim;

import de.latzig.game.tob.core.model.ObjectType;

/**
 * Start values of an object record from the original descriptor table {@code L012C96} ({@code S:17779-17806},
 * decoded by {@code tob_objtable.py}, oracle §2.1): the flag word (+12) and the start direction (+0C).
 */
public final class ObjectDescriptor {

    private ObjectDescriptor() {
    }

    /** The flag word an object of this type starts with ({@link ObjectFlags} bits). */
    public static int flags(ObjectType type) {
        return switch (type) {
            case ZAP, PSH, GRB -> 0x0005;
            case FZN, FZE, FZS, FZW -> 0x0001;
            case FPN, FPE, FPS, FPW -> 0x0000;
            case RZN -> 0x0001;
            case RPN -> 0x0000;
            case WON, WOE -> 0x0804;
            case WAT -> 0x0204;
            case HOP -> 0x0001;
            case PRM, TMB -> 0x0204;
            case BLK -> 0x0004;
            case REL -> 0x0006;
            case PNE, PSE, PSW, PNW -> 0x0024;
            case LZN, LZE -> 0x0200;
            case KLD -> 0x0209;
            case BFL -> 0x0001;
            case CVN, CVE -> 0x0010;
            case FCU, FCD -> 0x0041;
            case FRZ -> 0x0081;
            case WIP -> 0x0285;
            case EXC -> 0x0082;
        };
    }

    /**
     * The start direction (+0C). Every type has one: the four facings of the fixed enemies, the axis of worms,
     * lizards and converters (N/S = NORTH, E/W = EAST), and the prism code p (25 N/E = NORTH, 26 S/E = EAST,
     * 27 S/W = SOUTH, 28 N/W = WEST; oracle §3.3). All others start facing NORTH.
     */
    public static Direction direction(ObjectType type) {
        return switch (type) {
            case FZE, FPE, WOE, PSE, LZE, CVE -> Direction.EAST;
            case FZS, FPS, PSW -> Direction.SOUTH;
            case FZW, FPW, PNW -> Direction.WEST;
            default -> Direction.NORTH;
        };
    }

    /**
     * Whether destroying a non-spider object of this type can count as a robot (oracle §7.1): every zappable one,
     * because a zap counts ({@code S:8802-8807}), and the exchanger, whose use counts ({@code S:9014-9018}).
     */
    public static boolean countsAsRobot(ObjectType type) {
        return !type.isSpider() && ((flags(type) & ObjectFlags.ZAPPABLE) != 0 || type == ObjectType.EXC);
    }
}
