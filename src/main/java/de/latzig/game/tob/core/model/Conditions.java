package de.latzig.game.tob.core.model;

/** Win conditions. The time limit is kept as the original raw value even if it is disabled. */
public record Conditions(int timeLimitSeconds, boolean timeLimitEnabled, int klondikes, int robots) {

    public Conditions {
        Checks.range("timeLimitSeconds", timeLimitSeconds, 0, 16383);
        Checks.range("klondikes", klondikes, 0, 7);
        Checks.range("robots", robots, 0, 7);
    }
}
