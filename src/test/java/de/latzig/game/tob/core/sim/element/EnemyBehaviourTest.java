package de.latzig.game.tob.core.sim.element;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EnemyBehaviourTest {

    @Test
    void theRotationIsTableL00AF3C() {
        // S:9229-9236: 9 still frames (0, 8, 16, 24 ...) then 7 turning frames per quarter
        int[] expected = new int[64];
        for (int frame = 0; frame < 64; frame++) {
            int quarter = frame / 16;
            int inQuarter = frame % 16;
            expected[frame] = quarter * 8 + Math.max(0, inQuarter - 8);
        }
        for (int frame = 0; frame < 128; frame++) {
            assertEquals(expected[frame & 63], EnemyBehaviour.rotation(frame), "frame " + frame);
        }
    }

    @Test
    void stillFramesAreTheMultiplesOfEight() {
        int still = 0;
        for (int frame = 0; frame < 64; frame++) {
            boolean isStill = EnemyBehaviour.rotation(frame) % EnemyBehaviour.STILL == 0;
            assertEquals(frame % 16 <= 8, isStill, "frame " + frame);
            still += isStill ? 1 : 0;
        }
        assertEquals(36, still, "9 per quarter");
    }
}
