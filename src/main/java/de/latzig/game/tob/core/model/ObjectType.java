package de.latzig.game.tob.core.model;

/**
 * Placed object types, one constant per 3-letter code. The codes are the only identifiers that are
 * stable across ToB Original, ToB 2005 and ToB 2009.
 */
public enum ObjectType {
    ZAP(ObjectFamily.ZAPPER, Facing.NONE),
    PSH(ObjectFamily.PUSHER, Facing.NONE),
    GRB(ObjectFamily.GRABBER, Facing.NONE),
    FZN(ObjectFamily.ENEMY_ZAPPER, Facing.NORTH),
    FZE(ObjectFamily.ENEMY_ZAPPER, Facing.EAST),
    FZS(ObjectFamily.ENEMY_ZAPPER, Facing.SOUTH),
    FZW(ObjectFamily.ENEMY_ZAPPER, Facing.WEST),
    FPN(ObjectFamily.ENEMY_PUSHER, Facing.NORTH),
    FPE(ObjectFamily.ENEMY_PUSHER, Facing.EAST),
    FPS(ObjectFamily.ENEMY_PUSHER, Facing.SOUTH),
    FPW(ObjectFamily.ENEMY_PUSHER, Facing.WEST),
    RZN(ObjectFamily.ROTATING_ZAPPER, Facing.NONE),
    RPN(ObjectFamily.ROTATING_PUSHER, Facing.NONE),
    WON(ObjectFamily.WORM, Facing.NORTH_SOUTH),
    WOE(ObjectFamily.WORM, Facing.EAST_WEST),
    WAT(ObjectFamily.WATCHER, Facing.NONE),
    HOP(ObjectFamily.HOPPER, Facing.NONE),
    PRM(ObjectFamily.PROXIMITY_MINE, Facing.NONE),
    TMB(ObjectFamily.TIME_BOMB, Facing.NONE),
    BLK(ObjectFamily.PUSH_BLOCK, Facing.NONE),
    REL(ObjectFamily.REFLECTOR, Facing.NONE),
    PNE(ObjectFamily.PRISM, Facing.NORTH_EAST),
    PSE(ObjectFamily.PRISM, Facing.SOUTH_EAST),
    PSW(ObjectFamily.PRISM, Facing.SOUTH_WEST),
    PNW(ObjectFamily.PRISM, Facing.NORTH_WEST),
    LZN(ObjectFamily.LIZARD, Facing.NORTH_SOUTH),
    LZE(ObjectFamily.LIZARD, Facing.EAST_WEST),
    KLD(ObjectFamily.KLONDIKE, Facing.NONE),
    BFL(ObjectFamily.BONUS_FLAG, Facing.NONE),
    CVN(ObjectFamily.CONVERTER, Facing.NORTH_SOUTH),
    CVE(ObjectFamily.CONVERTER, Facing.EAST_WEST),
    FCU(ObjectFamily.FORCER_UP, Facing.NONE),
    FCD(ObjectFamily.FORCER_DOWN, Facing.NONE),
    FRZ(ObjectFamily.FREEZER, Facing.NONE),
    WIP(ObjectFamily.WIPER, Facing.NONE),
    EXC(ObjectFamily.EXCHANGER, Facing.NONE);

    private final ObjectFamily family;
    private final Facing facing;

    ObjectType(ObjectFamily family, Facing facing) {
        this.family = family;
        this.facing = facing;
    }

    public String code() {
        return name();
    }

    public ObjectFamily family() {
        return family;
    }

    public Facing facing() {
        return facing;
    }

    public boolean isSpider() {
        return family == ObjectFamily.ZAPPER || family == ObjectFamily.PUSHER || family == ObjectFamily.GRABBER;
    }

    public static ObjectType fromCode(String code) {
        for (ObjectType type : values()) {
            if (type.name().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown object code: " + code);
    }
}
