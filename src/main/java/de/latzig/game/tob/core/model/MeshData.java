package de.latzig.game.tob.core.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Immutable keyframe mesh in ToB 2026 coordinates (right-handed, 1 field = 1.0, origin at the bottom centre of
 * the field, counter-clockwise front faces). One array per key; keys are frames (16 frames = 1 simulation unit).
 */
public final class MeshData {

    public static final int FLAG_COLORS = 1;
    public static final int FLAG_UVS_ANIMATED = 2;
    public static final int FLAG_COLORS_ANIMATED = 4;

    private final int triangleCount;
    private final int[] keys;
    private final List<float[]> positions;
    private final List<float[]> uvs;
    private final List<int[]> colors;

    public MeshData(int triangleCount, int[] keys, List<float[]> positions, List<float[]> uvs,
            List<int[]> colorsOrNull) {
        if (triangleCount < 1) {
            throw new IllegalArgumentException("triangleCount must be positive: " + triangleCount);
        }
        if (keys.length < 1) {
            throw new IllegalArgumentException("at least one key required");
        }
        for (int i = 1; i < keys.length; i++) {
            if (keys[i] <= keys[i - 1]) {
                throw new IllegalArgumentException("keys must be strictly ascending: " + Arrays.toString(keys));
            }
        }
        this.triangleCount = triangleCount;
        this.keys = keys.clone();
        this.positions = copyFloats("positions", positions, keys.length, triangleCount * 9);
        this.uvs = copyFloats("uvs", uvs, keys.length, triangleCount * 6);
        this.colors = colorsOrNull == null ? null : copyInts(colorsOrNull, keys.length, triangleCount * 3);
    }

    private static List<float[]> copyFloats(String name, List<float[]> source, int keyCount, int length) {
        if (source.size() != keyCount) {
            throw new IllegalArgumentException(name + ": expected " + keyCount + " keys, got " + source.size());
        }
        List<float[]> copy = new ArrayList<>(keyCount);
        for (float[] values : source) {
            if (values.length != length) {
                throw new IllegalArgumentException(name + ": expected " + length + " values, got " + values.length);
            }
            copy.add(values.clone());
        }
        return List.copyOf(copy);
    }

    private static List<int[]> copyInts(List<int[]> source, int keyCount, int length) {
        if (source.size() != keyCount) {
            throw new IllegalArgumentException("colors: expected " + keyCount + " keys, got " + source.size());
        }
        List<int[]> copy = new ArrayList<>(keyCount);
        for (int[] values : source) {
            if (values.length != length) {
                throw new IllegalArgumentException("colors: expected " + length + " values, got " + values.length);
            }
            copy.add(values.clone());
        }
        return List.copyOf(copy);
    }

    public int triangleCount() {
        return triangleCount;
    }

    public int[] keys() {
        return keys.clone();
    }

    public int keyCount() {
        return keys.length;
    }

    public float[] positions(int keyIndex) {
        return positions.get(keyIndex).clone();
    }

    public float[] uvs(int keyIndex) {
        return uvs.get(keyIndex).clone();
    }

    /** @return colours (ARGB) of the key, or {@code null} if the mesh has no colours */
    public int[] colors(int keyIndex) {
        return colors == null ? null : colors.get(keyIndex).clone();
    }

    public boolean hasColors() {
        return colors != null;
    }

    public int flags() {
        int flags = 0;
        if (colors != null) {
            flags |= FLAG_COLORS;
            if (varies(colors)) {
                flags |= FLAG_COLORS_ANIMATED;
            }
        }
        if (variesFloat(uvs)) {
            flags |= FLAG_UVS_ANIMATED;
        }
        return flags;
    }

    private static boolean variesFloat(List<float[]> perKey) {
        for (int i = 1; i < perKey.size(); i++) {
            if (!Arrays.equals(perKey.get(0), perKey.get(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean varies(List<int[]> perKey) {
        for (int i = 1; i < perKey.size(); i++) {
            if (!Arrays.equals(perKey.get(0), perKey.get(i))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof MeshData m) || m.triangleCount != triangleCount || !Arrays.equals(m.keys, keys)
                || (m.colors == null) != (colors == null)) {
            return false;
        }
        for (int i = 0; i < keys.length; i++) {
            if (!Arrays.equals(m.positions.get(i), positions.get(i)) || !Arrays.equals(m.uvs.get(i), uvs.get(i))
                    || (colors != null && !Arrays.equals(m.colors.get(i), colors.get(i)))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int h = 31 * triangleCount + Arrays.hashCode(keys);
        for (float[] p : positions) {
            h = 31 * h + Arrays.hashCode(p);
        }
        for (float[] u : uvs) {
            h = 31 * h + Arrays.hashCode(u);
        }
        if (colors != null) {
            for (int[] c : colors) {
                h = 31 * h + Arrays.hashCode(c);
            }
        }
        return h;
    }

    @Override
    public String toString() {
        return "MeshData[triangles=" + triangleCount + ", keys=" + Arrays.toString(keys) + "]";
    }
}
