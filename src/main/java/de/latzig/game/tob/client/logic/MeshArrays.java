package de.latzig.game.tob.client.logic;

/** A triangle list: 9 position, 6 UV and 12 colour (RGBA) floats per triangle; normals come from {@link FaceFrames}. */
public record MeshArrays(float[] positions, float[] uvs, float[] colors) {

    public int triangles() {
        return positions.length / 9;
    }

    public boolean isEmpty() {
        return positions.length == 0;
    }

    /** Highest y of any corner; {@code Float.NEGATIVE_INFINITY} when empty. */
    public float maxY() {
        float max = Float.NEGATIVE_INFINITY;
        for (int i = 1; i < positions.length; i += 3) {
            max = Math.max(max, positions[i]);
        }
        return max;
    }
}
