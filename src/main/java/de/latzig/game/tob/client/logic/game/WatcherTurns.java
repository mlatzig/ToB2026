package de.latzig.game.tob.client.logic.game;

import java.util.HashMap;
import java.util.Map;

import de.latzig.game.tob.client.logic.Motion;
import de.latzig.game.tob.core.sim.Direction;

/**
 * The watcher's displayed heading (spec §4.1): a turn from {@code SimEvent.TurnStarted} interpolates over its 4 or 8
 * frames while the class timer counts down; a quarter turn goes the short way, the half turn counter-clockwise.
 * Without a running turn the watcher faces its {@code moveDirection()} (Phase 2 carry-over 1).
 */
final class WatcherTurns {

    private record Turn(float from, float delta, int frames) {
    }

    private final Map<Integer, Turn> turns = new HashMap<>();

    void start(int slot, Direction from, Direction to, int frames) {
        float delta;
        if (to == from.opposite()) {
            delta = -2 * Motion.HALF_PI;
        } else if (to == from.right()) {
            delta = Motion.HALF_PI;
        } else {
            delta = -Motion.HALF_PI;
        }
        turns.put(slot, new Turn(Motion.heading(from), delta, frames));
    }

    /**
     * @param remaining the watcher's class timer (the turn frames left)
     * @param advance the fraction of the next frame (0 while the world is frozen)
     */
    float heading(int slot, Direction moveDirection, int remaining, float advance) {
        Turn turn = turns.get(slot);
        if (turn == null || remaining == 0 || remaining > turn.frames()) {
            return Motion.heading(moveDirection);
        }
        float progress = Math.min(1f, (turn.frames() - remaining + advance) / turn.frames());
        return turn.from() + turn.delta() * progress;
    }
}
