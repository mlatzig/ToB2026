package de.latzig.game.tob.core.model;

/** Tower behaviour switches; time bomb speed 0 slow, 1 medium, 2 fast, 3 faster (original, rarely used). */
public record Behaviour(boolean camerasEnabled, boolean minesDestroyFloor, int timeBombSpeed) {

    public Behaviour {
        Checks.range("timeBombSpeed", timeBombSpeed, 0, 3);
    }
}
