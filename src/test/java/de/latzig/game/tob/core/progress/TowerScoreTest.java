package de.latzig.game.tob.core.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TowerScoreTest {

    @Test
    void aFirstTrySuccessWithThreeSurvivorsScoresTwoHundredThirty() {
        TowerScore score = TowerScore.completed(0, 3);

        assertEquals(new TowerScore(100, 100, 30), score);
        assertEquals(230, score.total());
    }

    @Test
    void theRetryBonusLosesFourPerCounterAndNeverGoesBelowZero() {
        assertEquals(92, TowerScore.completed(2, 0).retryBonus(), "one failure");
        assertEquals(4, TowerScore.completed(24, 0).retryBonus());
        assertEquals(0, TowerScore.completed(25, 0).retryBonus());
        assertEquals(0, TowerScore.completed(100, 1).retryBonus(), "max(0, …) (S:6450-6454)");
        assertEquals(110, TowerScore.completed(100, 1).total());
    }

    @Test
    void failuresAddTwoBelowNinetyNineAndRestartsOneBelowHundred() {
        assertEquals(2, TowerScore.afterFailure(0));
        assertEquals(100, TowerScore.afterFailure(98));
        assertEquals(99, TowerScore.afterFailure(99), "S:6771-6772: no increment from 99");
        assertEquals(1, TowerScore.afterRestart(0));
        assertEquals(100, TowerScore.afterRestart(99));
        assertEquals(100, TowerScore.afterRestart(100), "S:6748-6749: no increment from 100");
    }
}
