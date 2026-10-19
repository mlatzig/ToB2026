package de.latzig.game.tob.core.sim;

/**
 * Klondikes collected ({@code W00188C}) and robots destroyed ({@code W00188E}) with their required values. Increments
 * go through {@code Rules}, which also checks for victory.
 */
public final class Counters {

    private final int klondikesRequired;
    private final int robotsRequired;
    private int klondikesCollected;
    private int robotsDestroyed;

    Counters(int klondikesRequired, int robotsRequired) {
        this.klondikesRequired = klondikesRequired;
        this.robotsRequired = robotsRequired;
    }

    public int klondikesRequired() {
        return klondikesRequired;
    }

    public int robotsRequired() {
        return robotsRequired;
    }

    public int klondikesCollected() {
        return klondikesCollected;
    }

    public int robotsDestroyed() {
        return robotsDestroyed;
    }

    /** Saturating increment ({@code S:8946-8950}); false if the required value is reached already. */
    boolean incrementKlondikes() {
        if (klondikesCollected >= klondikesRequired) {
            return false;
        }
        klondikesCollected++;
        return true;
    }

    /** Saturating increment ({@code S:8802-8807}); false if the required value is reached already. */
    boolean incrementRobots() {
        if (robotsDestroyed >= robotsRequired) {
            return false;
        }
        robotsDestroyed++;
        return true;
    }
}
