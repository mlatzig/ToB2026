package de.latzig.game.tob.client.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.client.logic.game.GameAction;
import de.latzig.game.tob.core.model.Effects;
import de.latzig.game.tob.core.model.RemakeVisuals;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.sim.TestTowers;

/** The floor colour pattern: ToB 2009 {@code PatternType} semantics (Phase 4a decision 2, acceptance 2026-10-10). */
class TowerColoursTest {

    /** Sample palette entry 6 (lit top of square set 2): {@link TowerColours#pattern1()} without look settings. */
    private static final int P1 = 0x662299;
    /** Sample palette entry 3 (lit top of square set 1): {@link TowerColours#pattern2()}. */
    private static final int P2 = 0x3399CC;

    private static final TowerDefinition TOWER = TestTowers.tower().level(0, "F").build();

    private static TowerDefinition withPattern(TowerDefinition t, int colourPattern) {
        Effects e = t.effects();
        return new TowerDefinition(t.id(), t.group(), t.index(), t.title(), t.author(), t.conditions(),
                t.behaviour(), new Effects(e.lightning(), e.meteor(), e.bluePulse(), e.starDensity(), colourPattern,
                        e.skyStyle()), t.moon(), t.palette(), t.sounds(), t.lock(), t.grid(), t.remake());
    }

    @Test
    void theToB2009ParityFlipsPerLevel() {
        TowerColours colours = TowerColours.of(TOWER);

        assertEquals(P1, colours.cellColour(4, 10, 0));
        assertEquals(P2, colours.cellColour(4, 10, 1), "ToB 2009 flips the parity per level");
        assertEquals(colours.cellColour(4, 10, 0), colours.cellColour(4, 10, 2));
    }

    @Test
    void theColourPatternOfTheOriginalTowerDataDoesNotChangeTheFloor() {
        // acceptance 2026-10-10: variant (a) only; the original table L00BE1E (variant (b)) is gone
        TowerColours crossed = TowerColours.of(TOWER);
        for (int pattern = 0; pattern < 8; pattern++) {
            assertEquals(crossed, TowerColours.of(withPattern(TOWER, pattern)), "colour pattern " + pattern);
        }
    }

    @Test
    void theOriginalTableVariantAndItsF11ToggleAreRemoved() {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("de.latzig.game.tob.client.logic.PatternVariant"));
        assertEquals(9, TowerColours.class.getRecordComponents().length, "no originalPattern component");
        assertTrue(Arrays.stream(GameAction.values()).noneMatch(a -> a.name().equals("TOGGLE_PATTERN")));
    }

    @Test
    void lookSettingsKeepTheToB2009Semantics() {
        TowerDefinition remake = TOWER.withRemake(new RemakeVisuals(0xFFFFFF, 3, 0x004B4B, 0x006969, 6, 0xFFFFFF,
                false, false, TowerColours.CROSSED, 0xA500A5, 0xE10000));

        assertEquals(0xA500A5, TowerColours.of(remake).cellColour(4, 10, 0));
        assertEquals(0xE10000, TowerColours.of(remake).cellColour(4, 10, 1));
    }
}
