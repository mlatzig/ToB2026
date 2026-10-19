package de.latzig.game.tob.client.logic;

/**
 * Picks the two keys around an animation key of a {@code .tobmesh}. Positions and colours are blended between them;
 * UVs are stepped and come from the next key, as in the ToB 2005 engine ({@code dxe.cpp:3572}, report 09 §1.2;
 * Phase 1c carry-over 1). Keys outside the mesh's range clamp to its first or last key.
 */
public final class KeyframeSampler {

    /** The keys {@code from} ≤ key ≤ {@code to} (indices) and the blend fraction between them. */
    public record Sample(int from, int to, float fraction) {

        /**
         * The key whose UVs are shown: the first key strictly after the animation key, never the current key
         * itself, as in ToB 2005's {@code DXE_nextFrameKey}/{@code DXE_previousFrameKey}
         * ({@code dxe.cpp:2583-2631}; the UV copy at {@code dxe.cpp:3572} always takes the whole vertex, including
         * its UV, from the next key). {@code to} already is that key in four of the five cases: it is
         * {@code from + 1} once the animation has left {@code from} (between two keys, or exactly on a key that
         * is not the first one), and at or after the last key both functions collapse to the last index instead
         * of wrapping ({@code dxe.cpp:2586-2589}, {@code dxe.cpp:2618-2621}), which {@code to} already clamps to.
         * The fifth case, a key strictly before the first one, is where {@code from}/{@code to}/{@code fraction}
         * alone cannot tell "below the whole range" apart from "exactly on the first key" — both give
         * {@code Sample(0, 1, 0f)} — yet {@code dxe.cpp} answers them differently: strictly below, both
         * {@code DXE_nextFrameKey} and {@code DXE_previousFrameKey} collapse to index 0 (the first key is itself
         * the smallest index with {@code aiFrames[i] > iFrame}, and the previous-key search at
         * {@code dxe.cpp:2624-2628} never fires, keeping the default index 0 set at {@code dxe.cpp:2614-2615}), so
         * the UV comes from key 0, not key 1. {@code keys} and the original {@code key} resolve exactly that
         * ambiguity.
         *
         * @param keys the same key array passed to {@link KeyframeSampler#sample}
         * @param key the same animation key passed to {@link KeyframeSampler#sample}
         */
        public int uvKey(int[] keys, float key) {
            if (from == 0 && fraction == 0f && key < keys[0]) {
                return from;
            }
            return to;
        }
    }

    private KeyframeSampler() {
    }

    public static Sample sample(int[] keys, float key) {
        int from = 0;
        while (from + 1 < keys.length && keys[from + 1] <= key) {
            from++;
        }
        int to = Math.min(from + 1, keys.length - 1);
        if (from == to || key <= keys[from]) {
            return new Sample(from, to, 0f);
        }
        return new Sample(from, to, Math.min(1f, (key - keys[from]) / (keys[to] - keys[from])));
    }
}
