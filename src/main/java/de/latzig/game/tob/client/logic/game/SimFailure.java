package de.latzig.game.tob.client.logic.game;

/** An exception inside a simulation frame, with the tower id and the frame number for the log (spec §6). */
public final class SimFailure extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String towerId;
    private final int frame;

    public SimFailure(String towerId, int frame, RuntimeException cause) {
        super("simulation failed in tower " + towerId + " at frame " + frame + ": " + cause, cause);
        this.towerId = towerId;
        this.frame = frame;
    }

    public String towerId() {
        return towerId;
    }

    /** {@code WorldState.frame()} before the failing step (0 in READY). */
    public int frame() {
        return frame;
    }
}
