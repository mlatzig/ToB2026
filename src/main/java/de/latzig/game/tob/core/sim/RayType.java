package de.latzig.game.tob.core.sim;

/** The three ray types: zap ({@code L00A7AC}), push ({@code L00A966}) and grab ({@code L00AA3A}). */
public enum RayType {
    ZAP, PUSH, GRAB;

    /** The ray of a spider: zapper → zap, pusher → push, grabber → grab ({@code L00A796}, {@code S:8730-8736}). */
    public static RayType of(SpiderId spider) {
        return switch (spider) {
            case ZAPPER -> ZAP;
            case PUSHER -> PUSH;
            case GRABBER -> GRAB;
        };
    }
}
