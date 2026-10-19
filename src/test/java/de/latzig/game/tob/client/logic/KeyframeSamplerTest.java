package de.latzig.game.tob.client.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class KeyframeSamplerTest {

    private static final int[] KEYS = {0, 8, 16, 512};

    @Test
    void bracketsTheKeyAndClampsAtBothEnds() {
        assertEquals(new KeyframeSampler.Sample(0, 1, 0f), KeyframeSampler.sample(KEYS, -3f));
        assertEquals(new KeyframeSampler.Sample(0, 1, 0.5f), KeyframeSampler.sample(KEYS, 4f));
        assertEquals(new KeyframeSampler.Sample(1, 2, 0f), KeyframeSampler.sample(KEYS, 8f));
        assertEquals(new KeyframeSampler.Sample(2, 3, 0.25f), KeyframeSampler.sample(KEYS, 140f));
        assertEquals(new KeyframeSampler.Sample(3, 3, 0f), KeyframeSampler.sample(KEYS, 900f));
        assertEquals(new KeyframeSampler.Sample(0, 0, 0f), KeyframeSampler.sample(new int[] {0}, 77f));
    }

    @Test
    void uvsStepToTheNextKeyAsSoonAsTheAnimationLeavesAKey() {
        assertEquals(1, KeyframeSampler.sample(KEYS, 0f).uvKey(KEYS, 0f));
        assertEquals(1, KeyframeSampler.sample(KEYS, 0.1f).uvKey(KEYS, 0.1f));
        assertEquals(2, KeyframeSampler.sample(KEYS, 8f).uvKey(KEYS, 8f));
        assertEquals(3, KeyframeSampler.sample(KEYS, 600f).uvKey(KEYS, 600f));
    }

    @Test
    void uvsShowTheFirstKeyStrictlyAfterTheFrameOnAnExactKey() {
        // ToB 2005 dxe.cpp DXE_nextFrameKey (dxe.cpp:2583-2598): "next" is the first key with
        // aiFrames[i] > iFrame, never the frame's own key, even when the frame sits exactly on a key.
        assertEquals(1, KeyframeSampler.sample(KEYS, 0f).uvKey(KEYS, 0f));
        assertEquals(2, KeyframeSampler.sample(KEYS, 8f).uvKey(KEYS, 8f));
        assertEquals(3, KeyframeSampler.sample(KEYS, 16f).uvKey(KEYS, 16f));
    }

    @Test
    void uvsHoldTheLastKeyAtOrAfterItsOwnFrame() {
        // ToB 2005 dxe.cpp DXE_nextFrameKey (dxe.cpp:2586-2589): once iFrame reaches or passes the
        // last key, the function returns FALSE but still fills iFrameKey with the last entry, so
        // playback holds the last key instead of wrapping back to the first one.
        assertEquals(3, KeyframeSampler.sample(KEYS, 512f).uvKey(KEYS, 512f));
        assertEquals(3, KeyframeSampler.sample(KEYS, 900f).uvKey(KEYS, 900f));
    }

    @Test
    void uvsComeFromTheFirstKeyWhenTheFrameIsStrictlyBeforeTheWholeRange() {
        // ToB 2005 dxe.cpp DXE_nextFrameKey (dxe.cpp:2583-2600) and DXE_previousFrameKey
        // (dxe.cpp:2612-2631): strictly before the first key, both functions collapse to index 0 --
        // the first key is itself the smallest index with aiFrames[i] > iFrame, and the
        // previous-key search never fires, keeping its default index 0 (dxe.cpp:2614-2615,
        // 2624-2628). So the UV must come from key 0, unlike landing exactly on the first key
        // (uvsShowTheFirstKeyStrictlyAfterTheFrameOnAnExactKey), which already shows key 1: both
        // cases give the identical Sample(0, 1, 0f), so uvKey() needs keys/key to tell them apart.
        assertEquals(0, KeyframeSampler.sample(KEYS, -3f).uvKey(KEYS, -3f));
        assertEquals(0, KeyframeSampler.sample(KEYS, -0.5f).uvKey(KEYS, -0.5f));
    }

    @Test
    void faceFramesGiveTheFlatNormalAndTheUTangentWithHandedness() {
        float[] positions = {0, 0, 0, 0, 0, 1, 1, 0, 0};
        float[] uvs = {0, 0, 0, 1, 1, 0};
        float[] normals = new float[9];
        float[] tangents = new float[12];

        FaceFrames.compute(positions, uvs, normals, tangents);

        assertArrayEquals(new float[] {0, 1, 0, 0, 1, 0, 0, 1, 0}, normals, 1e-6f);
        assertArrayEquals(new float[] {1, 0, 0, -1, 1, 0, 0, -1, 1, 0, 0, -1}, tangents, 1e-6f);
    }
}
