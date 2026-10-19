package de.latzig.game.tob.client.logic;

/**
 * Flat face normals and per-face tangents of a triangle list (3 corners per triangle, counter-clockwise front
 * faces), recomputed for every keyframe as in the Phase 0 spike: ToB meshes carry no normals. Tangents carry the
 * handedness in w for {@code Lighting.j3md} normal mapping; the normal maps were converted with green flipped once
 * (Phase 1b), so jME's default {@code NormalType} applies.
 */
public final class FaceFrames {

    private static final float TINY = 1e-12f;

    private FaceFrames() {
    }

    /**
     * @param positions 9 floats per triangle
     * @param uvs 6 floats per triangle
     * @param normals output, 9 floats per triangle
     * @param tangents output, 12 floats per triangle (x, y, z, handedness per corner)
     */
    public static void compute(float[] positions, float[] uvs, float[] normals, float[] tangents) {
        int triangles = positions.length / 9;
        for (int t = 0; t < triangles; t++) {
            int p = t * 9;
            float e1x = positions[p + 3] - positions[p];
            float e1y = positions[p + 4] - positions[p + 1];
            float e1z = positions[p + 5] - positions[p + 2];
            float e2x = positions[p + 6] - positions[p];
            float e2y = positions[p + 7] - positions[p + 1];
            float e2z = positions[p + 8] - positions[p + 2];
            float[] n = normalize(e1y * e2z - e1z * e2y, e1z * e2x - e1x * e2z, e1x * e2y - e1y * e2x, 0f, 1f, 0f);

            int u = t * 6;
            float du1 = uvs[u + 2] - uvs[u];
            float dv1 = uvs[u + 3] - uvs[u + 1];
            float du2 = uvs[u + 4] - uvs[u];
            float dv2 = uvs[u + 5] - uvs[u + 1];
            float r = du1 * dv2 - du2 * dv1;
            float[] tangent;
            float handedness = 1f;
            if (Math.abs(r) < TINY) {
                tangent = perpendicular(n);
            } else {
                float tx = (e1x * dv2 - e2x * dv1) / r;
                float ty = (e1y * dv2 - e2y * dv1) / r;
                float tz = (e1z * dv2 - e2z * dv1) / r;
                float bx = (e2x * du1 - e1x * du2) / r;
                float by = (e2y * du1 - e1y * du2) / r;
                float bz = (e2z * du1 - e1z * du2) / r;
                float dot = n[0] * tx + n[1] * ty + n[2] * tz;
                float[] fallback = perpendicular(n);
                tangent = normalize(tx - n[0] * dot, ty - n[1] * dot, tz - n[2] * dot, fallback[0], fallback[1],
                        fallback[2]);
                float cx = n[1] * tangent[2] - n[2] * tangent[1];
                float cy = n[2] * tangent[0] - n[0] * tangent[2];
                float cz = n[0] * tangent[1] - n[1] * tangent[0];
                handedness = cx * bx + cy * by + cz * bz < 0 ? -1f : 1f;
            }
            for (int c = 0; c < 3; c++) {
                System.arraycopy(n, 0, normals, p + c * 3, 3);
                int q = t * 12 + c * 4;
                tangents[q] = tangent[0];
                tangents[q + 1] = tangent[1];
                tangents[q + 2] = tangent[2];
                tangents[q + 3] = handedness;
            }
        }
    }

    private static float[] normalize(float x, float y, float z, float fx, float fy, float fz) {
        float length2 = x * x + y * y + z * z;
        if (length2 < TINY) {
            return new float[] {fx, fy, fz};
        }
        float inverse = (float) (1.0 / Math.sqrt(length2));
        return new float[] {x * inverse, y * inverse, z * inverse};
    }

    private static float[] perpendicular(float[] n) {
        // n × a with a = x axis, or the y axis if n is close to x
        float ax = Math.abs(n[0]) < 0.9f ? 1f : 0f;
        float ay = 1f - ax;
        return normalize(-n[2] * ay, n[2] * ax, n[0] * ay - n[1] * ax, 1f, 0f, 0f);
    }
}
