package de.latzig.game.tob.client.logic.game;

import static de.latzig.game.tob.client.logic.game.GameAction.BACK;
import static de.latzig.game.tob.client.logic.game.GameAction.FIRE;
import static de.latzig.game.tob.client.logic.game.GameAction.FORWARD;
import static de.latzig.game.tob.client.logic.game.GameAction.SELECT_GRABBER;
import static de.latzig.game.tob.client.logic.game.GameAction.TURN_LEFT;
import static de.latzig.game.tob.client.logic.game.GameAction.TURN_RIGHT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.sim.LargeTestTowers;
import de.latzig.game.tob.core.sim.Rules;
import de.latzig.game.tob.core.sim.SimOptions;
import de.latzig.game.tob.core.sim.Simulation;

/**
 * Phase 6a spec decision 6: the solvable large test tower ({@link LargeTestTowers#SOLVABLE}) can be won with the keys
 * of the game, one key every {@link SoftlySoftlySolutionTest#FRAMES_PER_KEY} frames: the grabber grabs the klondike
 * at the north end of its column, walks east along the south row onto the lift, rides it up and grabs the second
 * klondike at the north end of the level-1 column. The author's own recording becomes the recorded solution
 * ({@code src/test/resources/solutions/large-003.json}) after the manual acceptance.
 */
class LargeSolvableTowerTest {

    static List<GameAction> keys() {
        List<GameAction> keys = new ArrayList<>(List.of(SELECT_GRABBER, FIRE, TURN_RIGHT));
        keys.addAll(Collections.nCopies(15, FORWARD));
        keys.addAll(List.of(BACK, TURN_LEFT, FIRE));
        return keys;
    }

    @Test
    void theGrabberCollectsBothKlondikesWithTheKeysOfTheGame() {
        TowerDefinition tower = LargeTestTowers.tower(LargeTestTowers.SOLVABLE);
        Simulation sim = Simulation.start(tower, SimOptions.DEFAULT);
        InputMapper mapper = new InputMapper();
        List<String> sent = new ArrayList<>();

        for (GameAction key : keys()) {
            mapper.press(key);
            for (int frame = 0; frame < SoftlySoftlySolutionTest.FRAMES_PER_KEY && !sim.isFinished(); frame++) {
                mapper.next(sim).ifPresentOrElse(command -> {
                    sent.add(command.toString());
                    sim.step(List.of(command));
                }, () -> sim.step(List.of()));
            }
        }
        for (int frame = 0; frame < 100 && !sim.isFinished(); frame++) {
            sim.step(List.of());
        }

        assertEquals(keys().size(), sent.size(), "every key became a command: " + sent);
        assertTrue(sim.isFinished(), "finished");
        assertEquals(Rules.EXIT_COMPLETED, sim.exitCode());
        assertEquals(2, sim.world().counters().klondikesCollected());
    }
}
