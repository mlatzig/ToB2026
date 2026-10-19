package de.latzig.game.tob.core.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class TowerRecordsTest {

    @Test
    void acceptsRawOriginalRanges() {
        assertDoesNotThrow(() -> new Conditions(16383, false, 7, 7));
        assertDoesNotThrow(() -> new Moon(7, 7, 511, 127, 3));
        assertDoesNotThrow(() -> new Effects(false, false, false, 3, 7, 3));
        assertDoesNotThrow(() -> new Behaviour(true, true, 3));
    }

    @Test
    void rejectsValuesOutsideTheBitWidth() {
        assertThrows(IllegalArgumentException.class, () -> new Conditions(16384, true, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Conditions(0, true, 8, 0));
        assertThrows(IllegalArgumentException.class, () -> new Behaviour(true, true, 4));
        assertThrows(IllegalArgumentException.class, () -> new Moon(0, 0, 512, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Sounds(4, true, true));
        assertThrows(IllegalArgumentException.class, () -> new Rgb12(16, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Lock(true, "12345678901"));
    }

    @Test
    void moonPresenceIsHeadingNotFour() {
        assertTrue(new Moon(0, 0, 0, 0, 0).present());
        assertFalse(new Moon(4, 0, 0, 0, 0).present());
    }

    @Test
    void rgb12HexRoundTrip() {
        assertEquals("6AE", new Rgb12(6, 10, 14).hex());
        assertEquals(new Rgb12(6, 10, 14), Rgb12.parseHex("6AE"));
        assertThrows(IllegalArgumentException.class, () -> Rgb12.parseHex("6A"));
        assertThrows(IllegalArgumentException.class, () -> Rgb12.parseHex("6AG"));
    }

    @Test
    void paletteNeedsExactly18Colours() {
        assertEquals(18, SampleTowers.palette().colours().size());
        assertThrows(IllegalArgumentException.class, () -> new Palette(List.of(new Rgb12(0, 0, 0))));
    }

    @Test
    void towerChecksTextLengthAndId() {
        TowerDefinition t = SampleTowers.tower("x-000");
        assertThrows(IllegalArgumentException.class, () -> new TowerDefinition(" ", 0, 0, "t", "a", t.conditions(),
                t.behaviour(), t.effects(), t.moon(), t.palette(), t.sounds(), t.lock(), t.grid(), Optional.empty()));
        String tooLong = "x".repeat(TowerDefinition.MAX_TEXT_LENGTH + 1);
        assertThrows(IllegalArgumentException.class, () -> new TowerDefinition("x-001", 0, 0, tooLong, "a",
                t.conditions(), t.behaviour(), t.effects(), t.moon(), t.palette(), t.sounds(), t.lock(), t.grid(),
                Optional.empty()));
    }

    @Test
    void withRemakeReplacesOnlyTheRemakeBlock() {
        TowerDefinition t = SampleTowers.tower("x-000");
        RemakeVisuals other = new RemakeVisuals(0, 0, 0, 0, 0, 0, false, false, 0, 0, 0);

        TowerDefinition changed = t.withRemake(other);

        assertEquals(Optional.of(other), changed.remake());
        assertEquals(t.grid(), changed.grid());
        assertEquals(t.title(), changed.title());
    }

    @Test
    void libraryRejectsDuplicateTowerIds() {
        TowerDefinition t = SampleTowers.tower("dup-000");
        assertThrows(IllegalArgumentException.class, () -> new TowerLibrary("dup", "Dup", "test", List.of(t, t)));
        assertEquals(2, SampleTowers.library().towers().size());
    }
}
