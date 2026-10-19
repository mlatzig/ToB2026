package de.latzig.game.tob.core.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProgressionTest {

    private static int mask(String towers) {
        int mask = 0;
        for (char c : towers.toCharArray()) {
            mask |= 1 << (c - 'A');
        }
        return mask;
    }

    @Test
    void aFreshGroupOpensTheTopRow() {
        assertEquals(mask("ABC"), Progression.accessibleMask(0), "S:6383-6385");
        assertTrue(Progression.isAccessible(0, 0));
        assertFalse(Progression.isAccessible(0, 3), "D is locked");
    }

    @Test
    void aCompletedTowerOpensItsNeighboursAndIsNoLongerAccessible() {
        assertEquals(mask("BD"), Progression.accessibleMask(mask("A")), "A opens B and D; C closes again");
        assertEquals(mask("BDFH"), Progression.accessibleMask(mask("E")));
        assertEquals(mask("ACEGI"), Progression.accessibleMask(mask("BH")), "B: A C E, H: E G I");
        assertFalse(Progression.isAccessible(mask("A"), 0), "a completed tower is not accessible (S:6153-6154)");
    }

    @Test
    void completingGOpensDAndENotH() {
        assertEquals(mask("DE"), Progression.accessibleMask(mask("G")), "matrix row G = $0018 (S:6133)");
        assertEquals(mask("FH"), Progression.accessibleMask(mask("I")));
    }

    @Test
    void theMatrixIsTheOriginalOne() {
        int[] expected = {0x00A, 0x015, 0x022, 0x051, 0x0AA, 0x114, 0x018, 0x150, 0x0A0};
        for (int index = 0; index < 9; index++) {
            assertEquals(expected[index], Progression.accessibleMask(1 << index), "row " + Progression.letter(index));
        }
    }

    @Test
    void groupsChangeBackAlwaysButForwardOnlyAfterSevenOfNine() {
        assertFalse(Progression.canGoBack(0));
        assertTrue(Progression.canGoBack(1));
        assertFalse(Progression.canGoForward(0, mask("ABCDEF")), "6 of 9");
        assertTrue(Progression.canGoForward(0, mask("ABCDEFG")), "7 of 9 (S:6410-6411)");
        assertTrue(Progression.canGoForward(11, mask("ABCDEFGHI")));
        assertFalse(Progression.canGoForward(12, mask("ABCDEFGHI")), "the last group (S:6412-6413)");
    }

    @Test
    void thePercentRoundsUpExceptAtNinetyNine() {
        assertEquals(0, Progression.completedPercent(0));
        assertEquals(1, Progression.completedPercent(1), "100/117 = 0.85 rounds up");
        assertEquals(2, Progression.completedPercent(2));
        assertEquals(50, Progression.completedPercent(58), "5800/117 = 49.6");
        assertEquals(99, Progression.completedPercent(116), "11600/117 = 99.1 stays 99 (S:7465-7466)");
        assertEquals(100, Progression.completedPercent(117));
    }

    @Test
    void theDoneMaskAndTheLetters() {
        boolean[] completed = new boolean[9];
        completed[0] = true;
        completed[8] = true;
        assertEquals(mask("AI"), Progression.doneMask(completed));
        assertEquals('A', Progression.letter(0));
        assertEquals('I', Progression.letter(8));
        assertEquals(117, Progression.TOWERS);
    }
}
