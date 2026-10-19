package de.latzig.game.tob.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ObjectTypeTest {

    @Test
    void hasAll36OriginalObjectCodes() {
        assertEquals(36, ObjectType.values().length);
    }

    @Test
    void resolvesCodesWithFamilyAndFacing() {
        assertEquals(ObjectFamily.ENEMY_ZAPPER, ObjectType.fromCode("FZN").family());
        assertEquals(Facing.NORTH, ObjectType.fromCode("FZN").facing());
        assertEquals(Facing.WEST, ObjectType.fromCode("FPW").facing());
        assertEquals(Facing.NORTH_SOUTH, ObjectType.fromCode("WON").facing());
        assertEquals(Facing.EAST_WEST, ObjectType.fromCode("LZE").facing());
        assertEquals(Facing.SOUTH_WEST, ObjectType.fromCode("PSW").facing());
        assertEquals(ObjectFamily.KLONDIKE, ObjectType.fromCode("KLD").family());
        assertEquals(Facing.NONE, ObjectType.fromCode("KLD").facing());
        assertEquals("EXC", ObjectType.EXC.code());
    }

    @Test
    void knowsTheThreeSpiders() {
        assertTrue(ObjectType.ZAP.isSpider());
        assertTrue(ObjectType.PSH.isSpider());
        assertTrue(ObjectType.GRB.isSpider());
        assertFalse(ObjectType.FZN.isSpider());
    }

    @Test
    void rejectsUnknownCode() {
        assertThrows(IllegalArgumentException.class, () -> ObjectType.fromCode("XYZ"));
        assertThrows(IllegalArgumentException.class, () -> ObjectType.fromCode("SPC"));
    }
}
