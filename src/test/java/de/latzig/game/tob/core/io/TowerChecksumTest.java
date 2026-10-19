package de.latzig.game.tob.core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.sim.TestTowers;

class TowerChecksumTest {

    private static TowerDefinition tower(String secondRow) {
        return TestTowers.tower().level(0, "FFF", secondRow).object(0, 0, 0, ObjectType.ZAP).build();
    }

    @Test
    void theChecksumIsAStableSha256OfTheCanonicalTowerJson() {
        String checksum = TowerChecksum.of(tower("FUF"));

        assertTrue(checksum.matches("[0-9a-f]{64}"), checksum);
        assertEquals(checksum, TowerChecksum.of(tower("FUF")), "the same tower built twice");
        assertEquals("a792bb2d786da88397548d4000e23ee6a0411520bd4fdd48f9662412f6448d7b", checksum,
                "pinned: a change of the tower JSON writer invalidates the checksum of every recorded solution");
    }

    @Test
    void anyChangeOfTheTowerChangesTheChecksum() {
        assertNotEquals(TowerChecksum.of(tower("FUF")), TowerChecksum.of(tower("FFF")));
    }
}
