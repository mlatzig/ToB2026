package de.latzig.game.tob.client.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.client.logic.effects.MaterialTuning;

class ShadowSetupTest {

    /** Phase 4b-4 decision 6: stronger shadows, 0.7 instead of 0.55, one tuning value. */
    @Test
    void theShadowsDarkenByTheTuningValue() {
        ShadowSetup shadows = new ShadowSetup(GeneratedAssets.assetManager());

        assertEquals(0.7f, MaterialTuning.SHADOW_INTENSITY, 1e-6f);
        assertEquals(MaterialTuning.SHADOW_INTENSITY, shadows.renderer().getShadowIntensity(), 1e-6f);
    }
}
