package de.latzig.game.tob.client.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.Effects;
import de.latzig.game.tob.core.model.Palette;
import de.latzig.game.tob.core.model.Rgb12;
import de.latzig.game.tob.core.model.SampleTowers;
import de.latzig.game.tob.core.model.TowerDefinition;

/** Phase 5e (Classic Look, decision 3): the floor squares, lifts and ground in the original's colours. */
class ClassicColoursTest {

    /** Original tower 001 "The Sacrifice" (the reference screenshot's tower). */
    static final Palette SACRIFICE = new Palette(Stream.of("000", "600", "800", "E00", "060", "080",
            "0E0", "000", "000", "0AA", "0EE", "00A", "00E", "777", "BBB", "FFF", "CA0", "860")
            .map(Rgb12::parseHex).toList());

    /** Grid (x, y) of the original's square (ox, oz): x = ox + 4, y = 11 − oz. */
    private static boolean set2(int pattern, int ox, int oz, int level) {
        return ClassicColours.usesSet2(pattern, ox + 4, 11 - oz, level);
    }

    @Test
    void patternZeroIsACheckerboardOnEveryLevel() {
        assertTrue(set2(0, 1, 0, 0), "row z0 = $AA: odd x");
        assertFalse(set2(0, 0, 0, 0));
        assertTrue(set2(0, 0, 1, 0), "row z1 = $55: even x");
        assertTrue(set2(0, 1, 0, 3), "the same on every level");
    }

    @Test
    void patternsOneTwoAndFourChangeWithTheLevelAndZeroAndThreeDoNot() {
        assertTrue(set2(1, 5, 0, 0), "rows: z0 all set 2 on level 0");
        assertFalse(set2(1, 5, 1, 0));
        assertFalse(set2(1, 5, 0, 1), "and the other way round on level 1");
        assertTrue(set2(1, 5, 0, 2));
        assertTrue(set2(2, 4, 0, 0), "$F0: x 4-7 of row z0");
        assertFalse(set2(2, 3, 0, 0));
        assertTrue(set2(2, 3, 1, 0), "$0F: x 0-3 of row z1");
        assertFalse(set2(2, 4, 0, 1), "rows swap on level 1");
        assertTrue(set2(2, 3, 0, 1), "$0F on level 1: x 0-3 of row z0");
        assertTrue(set2(2, 4, 0, 2), "level 2 as level 0");
        assertTrue(set2(3, 2, 0, 0), "one in four: z0 all set 2");
        assertFalse(set2(3, 2, 1, 2), "z1: even x set 1");
        assertTrue(set2(3, 3, 1, 2));
        assertEquals(set2(3, 3, 1, 0), set2(3, 3, 1, 1), "pattern 3: the same on every level");
        assertTrue(set2(4, 6, 6, 0), "alternating levels");
        assertFalse(set2(4, 6, 6, 1));
    }

    @Test
    void cellsBeyondTheOriginalsEightByEightByFourRepeatThePattern() {
        assertEquals(set2(2, 4, 0, 0), set2(2, 12, 0, 0), "x + 8");
        assertEquals(set2(2, 4, 0, 0), set2(2, -4, 0, 0), "x - 8");
        assertEquals(set2(2, 3, 1, 0), set2(2, 3, -7, 0), "z - 8");
        assertEquals(set2(1, 5, 0, 1), set2(1, 5, 0, 5), "level + 4");
    }

    @Test
    void topsAndSidesAreTheLitTopsAndTheSidesOfTheirSetLiftsAreTheFixedGreys() {
        ClassicColours colours = new ClassicColours(SACRIFICE, 3);

        assertEquals(0x00EE00, colours.top(4, 11, 0), "square set 2, lit top (entry 6)");
        assertEquals(0x006600, colours.side(4, 11, 0), "square set 2, side (entry 4)");
        assertEquals(0xEE0000, colours.top(4, 10, 0), "square set 1, lit top (entry 3)");
        assertEquals(0x660000, colours.side(4, 10, 0), "square set 1, side (entry 1)");
        assertEquals(0xBBBBBB, colours.liftTop(), "entry 14");
        assertEquals(0x777777, colours.liftSide(), "entry 13");
        assertEquals(0xCCAA00, colours.ground(), "the lit floor, entry 16");
        assertThrows(IllegalArgumentException.class, () -> new ClassicColours(SACRIFICE, 5));
    }

    @Test
    void aTowersPatternFiveToSevenHasNoOriginalAndDrawsAsZero() {
        TowerDefinition tower = SampleTowers.tower("sample-001");
        Effects effects = tower.effects();
        TowerDefinition faded = new TowerDefinition(tower.id(), tower.group(), tower.index(), tower.title(),
                tower.author(), tower.conditions(), tower.behaviour(), new Effects(effects.lightning(),
                        effects.meteor(), effects.bluePulse(), effects.starDensity(), 6, effects.skyStyle()),
                tower.moon(), tower.palette(), tower.sounds(), tower.lock(), tower.grid(), tower.remake());

        assertEquals(0, ClassicColours.of(faded).pattern());
        assertEquals(effects.colourPattern(), ClassicColours.of(tower).pattern());
        assertEquals(tower.palette(), ClassicColours.of(tower).palette());
    }
}
