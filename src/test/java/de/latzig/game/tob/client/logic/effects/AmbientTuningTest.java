package de.latzig.game.tob.client.logic.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AmbientTuningTest {

    @Test
    void theFlashJumpsToFullBeforeTheStrikeAndFadesLinearlyToNothingAtIt() {
        assertEquals(0f, AmbientTuning.flashCurve(AmbientTuning.FLASH_FRAMES + 0.01f), 0f, "not yet");
        assertEquals(1f, AmbientTuning.flashCurve(AmbientTuning.FLASH_FRAMES - 1e-4f), 1e-3f, "full at once");
        assertEquals(0.5f, AmbientTuning.flashCurve(AmbientTuning.FLASH_FRAMES / 2f), 1e-6f);
        assertEquals(0f, AmbientTuning.flashCurve(0f), 0f, "gone at the strike");
        assertEquals(0f, AmbientTuning.flashCurve(-0.5f), 0f, "nothing afterwards");
        assertEquals(0.25f, AmbientTuning.flashCurve(1f, 4f), 1e-6f);
        assertEquals(1.5f, AmbientTuning.FLASH_FRAMES, 0f, "ToB 2005's 24-frame blend at 16 frames per step");
    }

    @Test
    void bothStrengthsScaleTheCurveAndZeroStrengthsGiveNoFlash() {
        for (float curve = 0f; curve <= 1f; curve += 0.125f) {
            assertEquals(AmbientTuning.Flash.NONE, AmbientTuning.Flash.at(curve, 0f, 0f), "curve " + curve);
        }
        assertSame(AmbientTuning.Flash.NONE, AmbientTuning.Flash.at(0f), "no curve: no flash");
        AmbientTuning.Flash half = AmbientTuning.Flash.at(0.5f);
        assertEquals(AmbientTuning.SCENE_FLASH / 2f, half.scene(), 1e-6f);
        assertEquals(AmbientTuning.OVERLAY_FLASH / 2f, half.overlay(), 1e-6f);
        for (float curve = -0.5f; curve <= 1f; curve += 0.125f) {
            AmbientTuning.Flash flash = AmbientTuning.Flash.at(curve);
            assertEquals(flash.scene(), AmbientTuning.sceneFlash(curve), 0f, "fix wave M1: scene at " + curve);
            assertEquals(flash.overlay(), AmbientTuning.overlayFlash(curve), 0f, "fix wave M1: overlay at " + curve);
        }
        assertTrue(AmbientTuning.SCENE_FLASH > 1f, "strong: the lit faces turn white");
        assertTrue(AmbientTuning.OVERLAY_FLASH > 0f && AmbientTuning.OVERLAY_FLASH < 0.5f, "weak overlay");
    }

    /** Phase 5b decision 6: the flash strength scales both parts; brightness scales the light, not the flash. */
    @Test
    void theFlashStrengthScalesBothPartsAndBrightnessOnlyTheOwnLight() {
        assertEquals(AmbientTuning.sceneFlash(0.5f), AmbientTuning.sceneFlash(0.5f, 1f), 0f);
        assertEquals(AmbientTuning.overlayFlash(0.5f), AmbientTuning.overlayFlash(0.5f, 1f), 0f);
        assertEquals(AmbientTuning.sceneFlash(0.5f) * 0.3f, AmbientTuning.sceneFlash(0.5f, 0.3f), 1e-6f);
        assertEquals(AmbientTuning.overlayFlash(0.5f) * 0.3f, AmbientTuning.overlayFlash(0.5f, 0.3f), 1e-6f);
        for (float curve = -0.5f; curve <= 1f; curve += 0.25f) {
            assertEquals(0f, AmbientTuning.sceneFlash(curve, 0f), 0f, "0 %: no flash at " + curve);
            assertEquals(0f, AmbientTuning.overlayFlash(curve, 0f), 0f, "0 %: no overlay at " + curve);
        }
        assertEquals(0.2f + 1f, AmbientTuning.light(0.4f, 0.5f, 1f), 1e-6f);
        assertEquals(0.6f, AmbientTuning.light(0.4f, 1.5f, 0f), 1e-6f);
    }

    @Test
    void theHazePulseAtAmplitudeZeroIsToB2005sStaticHaze() {
        for (float now = 0f; now < 100f; now += 0.37f) {
            assertEquals(1f, AmbientTuning.hazeBrightness(now, 0f, AmbientTuning.HAZE_PULSE_FRAMES), 0f,
                    "exactly 1 at " + now);
        }
    }

    @Test
    void theHazePulsesGentlyAroundOneWithItsPeriod() {
        float period = AmbientTuning.HAZE_PULSE_FRAMES;
        float amplitude = AmbientTuning.HAZE_PULSE;
        assertEquals(1f, AmbientTuning.hazeBrightness(0f), 1e-6f);
        assertEquals(1f + amplitude, AmbientTuning.hazeBrightness(period / 4f), 1e-6f);
        assertEquals(1f - amplitude, AmbientTuning.hazeBrightness(period * 3f / 4f), 1e-6f);
        assertEquals(AmbientTuning.hazeBrightness(1.3f), AmbientTuning.hazeBrightness(1.3f + period), 1e-5f);
        assertTrue(amplitude > 0f && amplitude <= 0.25f, "gentle");
        assertEquals(8f, period, 0f, "the original blue pulse's period");
    }
}
