package de.latzig.game.tob.client.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.jme3.math.Vector3f;

import de.latzig.game.tob.client.logic.TowerColours;
import de.latzig.game.tob.client.logic.effects.CloudTexture;
import de.latzig.game.tob.client.logic.effects.MoonDesign;
import de.latzig.game.tob.client.logic.effects.SkyOptions;
import de.latzig.game.tob.core.model.SampleTowers;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.sim.TestTowers;

class SkyPreparationTest {

    private static final SkyOptions HALF = new SkyOptions(true, Optional.empty(), Optional.empty());

    private static void await(SkyPreparation preparation) throws InterruptedException {
        for (int i = 0; i < 600 && !preparation.isDone(); i++) {
            Thread.sleep(50);
        }
    }

    @Test
    void theBriefingPreparesMoonAndCloudsAndTheGameTakesThem() throws InterruptedException {
        assumeTrue(GeneratedAssets.available(), "generated assets not available");
        AssetLibrary assets = new AssetLibrary(GeneratedAssets.assetManager(), GeneratedAssets.root());
        TowerDefinition tower = SampleTowers.tower("sample-000");

        SkyPreparation preparation = SkyPreparation.start(tower, HALF);
        float early = preparation.progress();
        await(preparation);

        assertTrue(early >= 0f && early <= 1f);
        assertTrue(preparation.isDone(), "within 30 s");
        assertEquals(1f, preparation.progress());
        SkyView sky = new SkyView(assets, tower, TowerColours.of(tower), HALF, Optional.of(preparation));
        sky.update(Vector3f.ZERO, 0f);
        assertTrue(sky.moonReady(), "the prepared textures show at the first frame");
        assertTrue(sky.hasClouds());
        sky.dispose();
    }

    /**
     * Task 13 review (Important): Back on the Briefing cancels the preparation. A giant moon at full detail is
     * cancelled at once: its job ends after the current row without textures, and the cloud job is cancelled too (or
     * was already done); the preparation then reports done, so nothing waits for it.
     */
    @Test
    void cancelStopsTheMoonAndTheCloudJob() throws InterruptedException {
        TowerDefinition tower = SampleTowers.tower("sample-000");
        SkyOptions giant = new SkyOptions(false, Optional.empty(), Optional.of(MoonDesign.SizeClass.GIANT));
        SkyPreparation preparation = SkyPreparation.start(tower, giant);
        assertTrue(preparation.moon().isPresent() && preparation.clouds().isPresent(), "moon and clouds");

        preparation.cancel();

        assertTrue(preparation.clouds().orElseThrow().isDone(), "the cloud job is cancelled (or already done)");
        await(preparation);
        assertTrue(preparation.isDone(), "the moon's job ends after its current pass");
        assertTrue(preparation.moon().orElseThrow().isCancelled(), "without textures");
        assertEquals(1f, preparation.progress());
    }

    /** Phase 5e final review: the Classic Look draws no clouds, so its Briefing does not generate them. */
    @Test
    void aClassicPreparationOfACloudyTowerHasNoCloudJob() throws InterruptedException {
        TowerDefinition tower = SampleTowers.tower("sample-000");
        assertTrue(CloudTexture.cover(tower).isPresent(), "a cloudy tower");

        SkyPreparation classic = SkyPreparation.start(tower, HALF, true);

        assertTrue(classic.clouds().isEmpty(), "no cloud job");
        assertTrue(classic.moon().isPresent(), "the moon is still prepared");
        classic.cancel();
        await(classic);
    }

    @Test
    void aPreparationOfAnotherTowerIsIgnoredAndATowerWithoutMoonAndCloudsIsReadyAtOnce() {
        assumeTrue(GeneratedAssets.available(), "generated assets not available");
        AssetLibrary assets = new AssetLibrary(GeneratedAssets.assetManager(), GeneratedAssets.root());
        TowerDefinition plain = TestTowers.tower().level(0, "F").build();
        SkyPreparation none = SkyPreparation.start(plain, HALF);

        assertTrue(none.isDone());
        assertEquals(1f, none.progress());
        TowerDefinition other = SampleTowers.tower("sample-001");
        SkyView sky = new SkyView(assets, other, TowerColours.of(other), HALF, Optional.of(none));
        assertFalse(sky.moonReady(), "its own moon is generated as before");
        sky.dispose();
    }
}
