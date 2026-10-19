package de.latzig.game.tob.core.io;

import java.util.List;

import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.model.TowerLimits;

/** Semantic checks beyond the value ranges of the model (limits of the original game, see {@link TowerLimits}). */
public final class TowerValidator {

    public static final int MAX_OBJECTS = TowerLimits.MAX_OBJECTS;

    private TowerValidator() {
    }

    public static List<String> validate(TowerDefinition tower) {
        return TowerLimits.problems(tower);
    }
}
