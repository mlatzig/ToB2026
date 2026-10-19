package de.latzig.game.tob.core.sim;

/**
 * Bits of the per-object flag word (record offset +12, oracle §2.1). The original tests the low byte as +13 and the
 * high byte as +12; here the word is one int. Start values come from {@link ObjectDescriptor#flags}.
 */
public final class ObjectFlags {

    /** Destroyed by a zap ray ({@code S:8774}). */
    public static final int ZAPPABLE = 0x0001;
    /** Reverses a zap ray ({@code S:8776}). */
    public static final int REFLECTS = 0x0002;
    /** Moved by a push ray ({@code S:8884}). */
    public static final int PUSHABLE = 0x0004;
    /** A grab collects it ({@code S:8937}). */
    public static final int KLONDIKE = 0x0008;
    /** Converts a parallel zap or push ray into the other type ({@code S:8780}, {@code S:8886}). */
    public static final int CONVERTER = 0x0010;
    /** Bends a zap ray ({@code S:8778}). */
    public static final int PRISM = 0x0020;
    /** Forcer, activated by a grab ({@code S:8939}). */
    public static final int FORCER = 0x0040;
    /** Tool activated by a grab: freezer, wiper, exchanger ({@code S:8941}). */
    public static final int TOOL = 0x0080;
    /** Runtime: the klondike was grabbed ({@code S:8945}). */
    public static final int KLONDIKE_GRABBED = 0x0100;
    /** Explosion graphic variant ({@code S:19366}); render only. */
    public static final int EXPLOSION_VARIANT = 0x0200;
    /** Runtime: the floor goes when the object is removed ({@code S:9711}, {@code S:9952}). */
    public static final int DESTROYS_FLOOR = 0x0400;
    /** A zap reverses the moving object ({@code S:8782}). */
    public static final int ZAP_REVERSES = 0x0800;

    private ObjectFlags() {
    }
}
