package de.latzig.game.tob.client.logic.game;

/**
 * The frame counter as the enemies see it: it follows {@code WorldState.frame()} while the world is not frozen and
 * holds while it is, so a rotating enemy keeps its displayed angle during a freeze and snaps on thaw (Phase 2
 * carry-over 2).
 */
final class FreezeClock {

    private int frame;

    /** Called after every step with the state after it. */
    void update(boolean frozen, int worldFrame) {
        if (!frozen) {
            frame = worldFrame;
        }
    }

    int frame() {
        return frame;
    }
}
