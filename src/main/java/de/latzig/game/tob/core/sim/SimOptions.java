package de.latzig.game.tob.core.sim;

/**
 * Game options passed to the simulation at start. {@code backwardsAllowed} is the original "Backwards Allowed" option
 * ({@code W0017C8}); default off. The menu switch arrives in Phase 5. {@code enemiesFrozen} is the original Tower
 * Designer's "Enemy Movement" off ({@code W0014C6}, Extras → Designer Setup; Phase 6b): only a test play from the
 * editor sets it, never a normal game.
 */
public record SimOptions(boolean backwardsAllowed, boolean enemiesFrozen) {

    public static final SimOptions DEFAULT = new SimOptions(false);

    /** Options of a normal game: the enemies move. */
    public SimOptions(boolean backwardsAllowed) {
        this(backwardsAllowed, false);
    }
}
