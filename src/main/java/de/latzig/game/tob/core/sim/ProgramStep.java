package de.latzig.game.tob.core.sim;

/**
 * One program slot with its original byte code (oracle §4.1): 0 empty, 1 forward, 2 back/lift, 3 left, 4 right,
 * 5 fire.
 */
public enum ProgramStep {
    EMPTY(0), FORWARD(1), BACK(2), LEFT(3), RIGHT(4), FIRE(5);

    private final int code;

    ProgramStep(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
