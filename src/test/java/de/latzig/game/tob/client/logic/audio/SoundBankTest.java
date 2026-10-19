package de.latzig.game.tob.client.logic.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.SoundCatalog;
import de.latzig.game.tob.core.model.SoundDetail;
import de.latzig.game.tob.core.model.SoundEntry;

class SoundBankTest {

    /** A catalog with the push sound and one explosion, as {@code sounds.json} has them. */
    static SoundCatalog catalog() {
        return new SoundCatalog(Map.of(
                "0092", new SoundEntry("sounds/0092-push.wav", false, true, 0.0625, 32.0, SoundDetail.LOW, "push"),
                "0901", new SoundEntry("sounds/0901-explosion.wav", false, true, 2.0, 256.0, SoundDetail.HIGH,
                        "short"),
                "0091", new SoundEntry("sounds/0091-noise.wav", true, false, 0.0, 0.0, SoundDetail.MED, "noise")));
    }

    @Test
    void theCatalogsSoundsKeepTheirToB2005Settings() {
        SoundBank bank = SoundBank.of(catalog());

        assertEquals(new SoundBank.Sound("0092", "sounds/0092-push.wav", false, true, 0.0625f, 32f, SoundDetail.LOW,
                1f, 1f), bank.sound(SoundBank.PUSH));
        assertEquals(new SoundBank.Sound("0091", "sounds/0091-noise.wav", true, false, 0f, 0f, SoundDetail.MED, 1f,
                1f), bank.sound(SoundBank.NOISE));
        assertThrows(IllegalArgumentException.class, () -> bank.sound("4711"));
        assertThrows(IllegalArgumentException.class, () -> SoundBank.of(new SoundCatalog(Map.of())));
    }

    @Test
    void theClientAddsTheElementPushTheThunderAndTheBackgroundLoops() {
        SoundBank bank = SoundBank.of(catalog());

        assertEquals(new SoundBank.Sound("0092e", "sounds/0092-push.wav", false, true, 0.5f, 32f, SoundDetail.MED, 1f,
                1f), bank.sound(SoundBank.PUSH_ELEMENT), "twr.cpp:1184: 16 units, MED");
        SoundBank.Sound thunder = bank.sound(SoundBank.THUNDER);
        assertEquals("sounds/9900-thunder.wav", thunder.file());
        assertFalse(thunder.loop());
        assertTrue(thunder.positional());
        assertEquals(SoundTuning.THUNDER_VOLUME, thunder.volume());
        assertEquals(SoundTuning.THUNDER_PITCH, thunder.pitch());
        assertEquals(SoundBank.BREATH, SoundBank.background(1));
        assertEquals(SoundBank.DRONE_A, SoundBank.background(2));
        assertEquals(SoundBank.DRONE_B, SoundBank.background(3));
        assertNull(SoundBank.background(0));
        SoundBank.Sound drone = bank.sound(SoundBank.DRONE_B);
        assertEquals("sounds/9903-drone-b.wav", drone.file());
        assertTrue(drone.loop());
        assertFalse(drone.positional(), "spec decision 5: non-positional");
        assertEquals(SoundTuning.BACKGROUND_VOLUME, drone.volume());
        assertEquals(8, bank.sounds().size(), "3 from the catalog + 5 own entries");
    }

    /** Spec §5: sounds.json's LOW, MED, HIGH are ToB 2005's SND_DETAIL_LOW, _MEDIUM, _HIGH (1, 2, 3). */
    @Test
    void aSoundPlaysUnlessItsDetailIsAboveTheSetting() {
        SoundBank bank = SoundBank.of(catalog());

        assertFalse(bank.audible(SoundBank.PUSH, SoundTuning.DETAIL_NONE));
        assertTrue(bank.audible(SoundBank.PUSH, SoundTuning.DETAIL_LOW));
        assertFalse(bank.audible(SoundBank.NOISE, SoundTuning.DETAIL_LOW));
        assertTrue(bank.audible(SoundBank.NOISE, SoundTuning.DETAIL_MEDIUM));
        assertFalse(bank.audible(SoundBank.EXPLOSION_SHORT, SoundTuning.DETAIL_MEDIUM));
        assertTrue(bank.audible(SoundBank.EXPLOSION_SHORT, SoundTuning.DETAIL_HIGH));
        assertEquals(SoundTuning.DETAIL_HIGH, SoundTuning.DETAIL, "spec decision 1: high by default");
    }
}
