package de.latzig.game.tob.core.sim;

/** What a ray did to an occupant it reached ({@link SimEvent.RayHit}). */
public enum RayOutcome {
    /** A zap destroyed the target. */
    DESTROYED,
    /** A reflector or exchanger reversed the zap ray. */
    REFLECTED,
    /** A prism bent the zap ray. */
    DEFLECTED,
    /** A converter turned the ray into the other type. */
    CONVERTED,
    /** A push ray started pushing the target. */
    PUSHED,
    /** A grab took effect (klondike, exchanger; forcer, freezer and wiper from Phase 2d). */
    GRABBED,
    /** A zap reversed a moving worm (the {@code $0800} hook; Phase 2c). */
    REVERSED,
    /** The ray ended at the target without effect. */
    ABSORBED,
    /** A lift rider stopped the zap ray and stayed unharmed. */
    BLOCKED
}
