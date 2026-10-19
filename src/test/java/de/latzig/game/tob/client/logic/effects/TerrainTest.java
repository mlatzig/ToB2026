package de.latzig.game.tob.client.logic.effects;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.client.logic.MeshArrays;
import de.latzig.game.tob.client.logic.Socket;
import de.latzig.game.tob.client.logic.TowerColours;
import de.latzig.game.tob.core.model.BaseType;
import de.latzig.game.tob.core.model.Cell;
import de.latzig.game.tob.core.model.TowerGrid;
import de.latzig.game.tob.core.sim.TestTowers;

class TerrainTest {

    private static final float EPS = 1e-4f;
    private static final int N = Terrain.DIVISIONS + 1;

    private static float[] heights(Terrain terrain) {
        float[] heights = new float[N * N];
        for (int j = 0; j < N; j++) {
            for (int i = 0; i < N; i++) {
                heights[j * N + i] = terrain.position(i, j)[1];
            }
        }
        return heights;
    }

    @Test
    void theGroundIsFlatAtTheSocketBottomWithinFiftySixCellsOfTheTower() {
        for (int groundHeight = 0; groundHeight <= 10; groundHeight++) {
            Terrain terrain = Terrain.generate(groundHeight, 99L, 7.5f, 7.5f);
            int flat = 0;
            for (int j = 0; j < N; j++) {
                for (int i = 0; i < N; i++) {
                    float[] p = terrain.position(i, j);
                    if (Math.abs(p[0] - 7.5f) < 1800 / 32f && Math.abs(p[2] - 7.5f) < 1800 / 32f) {
                        assertEquals(Terrain.FLAT_Y, p[1], EPS, "flat zone " + i + "," + j);
                        flat++;
                    }
                }
            }
            assertEquals(9, flat, "the central 3 x 3 vertices");
        }
        assertArrayEquals(new float[] {7.5f, Terrain.FLAT_Y, 7.5f}, Terrain.generate(5, 1L, 7.5f, 7.5f)
                .position(4, 4), EPS, "the centre vertex stands under the grid centre");
    }

    @Test
    void theHillsAreFixedPerTowerAndGrowWithTheGroundHeight() {
        float[] hills = heights(Terrain.generate(6, 1234L, 7.5f, 7.5f));

        assertArrayEquals(hills, heights(Terrain.generate(6, 1234L, 7.5f, 7.5f)), "same seed, same hills");
        assertFalse(Arrays.equals(hills, heights(Terrain.generate(6, 1235L, 7.5f, 7.5f))));
        float highest = 0f;
        for (float h : hills) {
            highest = Math.max(highest, h);
        }
        assertTrue(highest > 5f && highest < 120f, "hills of a few dozen cells: " + highest);
        float flatHighest = 0f;
        for (float h : heights(Terrain.generate(0, 1234L, 7.5f, 7.5f))) {
            flatHighest = Math.max(flatHighest, h);
        }
        assertTrue(flatHighest < 1f, "ground height 0 leaves only the 2005 edge lift to the sky plane");
        Terrain expected = Terrain.generate(Terrain.DEFAULT_GROUND_HEIGHT, "test-000".hashCode(), 7.5f, 7.5f);
        assertEquals(expected.position(2, 3)[1], Terrain.of(TestTowers.tower().level(0, "F").build()).position(2, 3)[1],
                EPS,
                "towers without look settings use the default height, seeded by the tower id");
    }

    @Test
    void theMeshIsAnIndexedGridWithUpwardNormalsAndTheGroundColours() {
        TowerColours colours = new TowerColours(0, 0, 0x406080, 0x102030, TowerColours.CROSSED, 0, 0, 0, 0xFFFFFF);
        Terrain.TerrainMesh mesh = Terrain.generate(5, 7L, 7.5f, 7.5f).mesh(colours, 7L);

        assertEquals(N * N * 3, mesh.positions().length);
        assertEquals(Terrain.DIVISIONS * Terrain.DIVISIONS * 6, mesh.indices().length);
        for (int v = 0; v < N * N; v++) {
            float nx = mesh.normals()[v * 3];
            float ny = mesh.normals()[v * 3 + 1];
            float nz = mesh.normals()[v * 3 + 2];
            assertEquals(1f, (float) Math.sqrt(nx * nx + ny * ny + nz * nz), EPS);
            assertTrue(ny > 0f, "every normal points up");
        }
        int centre = 4 * N + 4;
        assertArrayEquals(new float[] {0f, 1f, 0f}, Arrays.copyOfRange(mesh.normals(), centre * 3, centre * 3 + 3),
                EPS, "flat in the middle");
        float red = mesh.colors()[centre * 4] * 255f;
        assertTrue(red >= 0x40 && red < 0x40 + 16, "the flat ground is ground colour 1 plus 0-15: " + red);
        assertEquals(Terrain.TEXTURE_TILES, mesh.uvs()[(N - 1) * 2], EPS, "the texture repeats 15 times");
        // the first triangle faces up (counter-clockwise seen from above)
        int[] t = Arrays.copyOf(mesh.indices(), 3);
        float[] a = Arrays.copyOfRange(mesh.positions(), t[0] * 3, t[0] * 3 + 3);
        float[] b = Arrays.copyOfRange(mesh.positions(), t[1] * 3, t[1] * 3 + 3);
        float[] c = Arrays.copyOfRange(mesh.positions(), t[2] * 3, t[2] * 3 + 3);
        float up = (b[2] - a[2]) * (c[0] - a[0]) - (b[0] - a[0]) * (c[2] - a[2]);
        assertTrue(up > 0f, "normal y of (b - a) x (c - a)");
    }

    /**
     * Phase 4b-4 decision 1, ToB 2005's {@code Ground} shader: the ground weight 1 − y / h and the mountain weight
     * y / h (y in ToB 2005 units, h = {@link MaterialTuning#MOUNTAIN_HEIGHT_UNITS}, clamped to 0..1) for every vertex;
     * the vertex colour is ground colour 1 (plus 0–15) mixed into ground colour 2 by the ground weight, so the
     * mountains are in ground colour 2.
     */
    @Test
    void theMountainTextureAndGroundColour2BlendInByHeightAsToB2005() {
        float full = MaterialTuning.MOUNTAIN_HEIGHT_UNITS;
        assertEquals(350f, full, EPS, "ToB 2005's 1000 units with the Phase 4a relief 0.35");
        assertEquals(1f, Terrain.groundWeight(-50), EPS, "below 0: clamped");
        assertEquals(0.75f, Terrain.groundWeight(full / 4), EPS);
        assertEquals(0.25f, Terrain.mountainWeight(full / 4), EPS);
        assertEquals(0f, Terrain.groundWeight(2 * full), EPS);
        assertEquals(1f, Terrain.mountainWeight(2 * full), EPS, "above the full height: clamped");
        TowerColours colours = new TowerColours(0, 0, 0x406080, 0x102030, TowerColours.CROSSED, 0, 0, 0, 0xFFFFFF);
        Terrain terrain = Terrain.generate(5, 7L, 7.5f, 7.5f);
        Terrain.TerrainMesh mesh = terrain.mesh(colours, 7L);
        int mountains = 0;

        assertEquals(N * N * 2, mesh.weights().length);
        for (int j = 0; j < N; j++) {
            for (int i = 0; i < N; i++) {
                int v = j * N + i;
                double units = terrain.height(i, j);
                assertEquals(Terrain.groundWeight(units), mesh.weights()[v * 2], EPS);
                assertEquals(Terrain.mountainWeight(units), mesh.weights()[v * 2 + 1], EPS);
                if (units >= full) {
                    mountains++;
                    assertEquals(0x10 / 255f, mesh.colors()[v * 4], EPS, "a mountain is ground colour 2");
                    assertEquals(0x30 / 255f, mesh.colors()[v * 4 + 2], EPS);
                }
            }
        }
        assertTrue(mountains > 0, "ground height 5 raises some vertices to full mountain");
        int centre = 4 * N + 4;
        assertArrayEquals(new float[] {1f, 0f}, Arrays.copyOfRange(mesh.weights(), centre * 2, centre * 2 + 2), EPS,
                "the flat zone is all ground");
    }

    @Test
    void theSocketLiesOnlyUnderTheExtentOfLevel0() {
        // acceptance 2026-10-10: as the original, ToB 2005 and ToB 2009; higher levels may overhang
        TowerGrid grid = TowerGrid.builder().set(5, 6, 0, new Cell(BaseType.FLOOR, null))
                .set(9, 8, 0, new Cell(BaseType.BOX, null)).set(12, 2, 2, new Cell(BaseType.FLOOR, null))
                .set(1, 14, 5, new Cell(BaseType.BOX, null)).build();

        Socket socket = Socket.of(grid).orElseThrow();

        assertEquals(new Socket(4.5f, 5.5f, 9.5f, 8.5f), socket);
        assertEquals(new Socket(4.5f, 5.5f, 5.5f, 6.5f), Socket.of(TowerGrid.builder()
                .set(5, 6, 0, new Cell(BaseType.FLOOR, null)).set(9, 8, 2, new Cell(BaseType.BOX, null)).build())
                .orElseThrow(), "a box on level 2 overhangs the socket");
        assertTrue(Socket.of(TowerGrid.builder().set(9, 8, 2, new Cell(BaseType.BOX, null)).build()).isEmpty(),
                "no level-0 cells, no socket");
        assertTrue(Socket.of(TowerGrid.empty()).isEmpty(), "no cells, no socket");
        MeshArrays arrays = socket.arrays(0x808080);
        assertEquals(10, arrays.triangles(), "top and four sloped sides, ToB 2009's five quads");
        assertEquals(Socket.TOP, arrays.maxY(), EPS);
        float lowest = Float.MAX_VALUE;
        float west = Float.MAX_VALUE;
        for (int i = 0; i < arrays.positions().length; i += 3) {
            lowest = Math.min(lowest, arrays.positions()[i + 1]);
            west = Math.min(west, arrays.positions()[i]);
        }
        assertEquals(Terrain.FLAT_Y, lowest, EPS, "the socket stands on the flat ground");
        assertEquals(4.5f - 1f / 32f, west, EPS);
        TowerColours colours = new TowerColours(0, 0, 0, 0x0000FF, 0, 0, 0, 0, 0);
        assertEquals(0x4040C0, Socket.colour(colours), "grey mixed half with ground colour 2");
    }

    @Test
    void theSocketIsToB2009sOneUnitSlabWithA45DegreeSlopeAndOneSideColour() {
        // Phase 4b-1 spec §2 decision 6: BasisElementsRenderer.createSocket, SOCKET_HEIGHT = 1 unit (1/32 field);
        // the bottom corners lie SOCKET_HEIGHT lower and SOCKET_HEIGHT further out (v4..v7), so the slope is 45°.
        Socket socket = new Socket(2.5f, 3.5f, 6.5f, 5.5f);
        MeshArrays arrays = socket.arrays(0x808080);

        assertEquals(1f / 32f, Socket.HEIGHT, EPS);
        assertEquals(Socket.HEIGHT, Socket.BORDER, EPS, "45 degrees");
        assertEquals(Socket.TOP - 1f / 32f, Socket.BOTTOM, EPS);
        assertEquals(Socket.BOTTOM, Terrain.FLAT_Y, EPS, "the terrain's flat zone moves with the socket bottom");
        float[] p = arrays.positions();
        float[] uv = arrays.uvs();
        // quad 0 (triangles 0, 1) is the top: its UVs repeat once per field (4 x 2 fields)
        float maxU = 0f;
        float maxV = 0f;
        for (int c = 0; c < 6; c++) {
            maxU = Math.max(maxU, uv[c * 2]);
            maxV = Math.max(maxV, uv[c * 2 + 1]);
        }
        assertEquals(4f, maxU, EPS);
        assertEquals(2f, maxV, EPS);
        // quads 1..4 are the sides: every corner has the one side UV, and each corner lies on the top or the bottom
        for (int c = 6; c < 30; c++) {
            assertEquals(Socket.SIDE_UV, uv[c * 2], 0f, "side u " + c);
            assertEquals(Socket.SIDE_UV, uv[c * 2 + 1], 0f, "side v " + c);
            float y = p[c * 3 + 1];
            assertTrue(Math.abs(y - Socket.TOP) < EPS || Math.abs(y - Socket.BOTTOM) < EPS, "corner height " + y);
            if (Math.abs(y - Socket.BOTTOM) < EPS) {
                float x = p[c * 3];
                float z = p[c * 3 + 2];
                boolean outside = Math.abs(x - (2.5f - Socket.BORDER)) < EPS || Math.abs(x - (6.5f + Socket.BORDER))
                        < EPS || Math.abs(z - (3.5f - Socket.BORDER)) < EPS || Math.abs(z - (5.5f + Socket.BORDER))
                        < EPS;
                assertTrue(outside, "a bottom corner lies one unit beyond the top edge: " + x + ", " + z);
            }
        }
    }

    @Test
    void theHeightAtAPointLiesOnTheMeshTrianglesAndIsTheFlatZoneNearTheTower() {
        Terrain terrain = Terrain.generate(8, 4321L, 7.5f, 7.5f);
        for (int j = 0; j < N; j++) {
            for (int i = 0; i < N; i++) {
                float[] p = terrain.position(i, j);
                assertEquals(p[1], terrain.heightAt(p[0], p[2]), EPS, "on vertex " + i + "," + j);
            }
        }
        // Inside quad (5, 5): the diagonal runs from vertex (5, 5) to (6, 6); a point a quarter along it from (5, 5)
        // and a point in each of the two triangles interpolate their own three corners.
        float[] a = terrain.position(5, 5);
        float[] b = terrain.position(6, 5);
        float[] c = terrain.position(5, 6);
        float[] d = terrain.position(6, 6);
        assertEquals(a[1] + 0.25f * (d[1] - a[1]), terrain.heightAt(a[0] + 0.25f * (d[0] - a[0]),
                a[2] + 0.25f * (d[2] - a[2])), EPS, "on the diagonal");
        assertEquals(a[1] + 0.5f * (b[1] - a[1]) + 0.25f * (d[1] - b[1]), terrain.heightAt(a[0] + 0.5f * (b[0] - a[0]),
                a[2] + 0.25f * (d[2] - b[2])), EPS, "triangle (5,5) (6,6) (6,5)");
        assertEquals(a[1] + 0.25f * (d[1] - c[1]) + 0.5f * (c[1] - a[1]), terrain.heightAt(a[0] + 0.25f * (d[0] - c[0]),
                a[2] + 0.5f * (c[2] - a[2])), EPS, "triangle (5,5) (5,6) (6,6)");
        for (float r = 0f; r <= 1500f / 32f; r += 2f) {
            for (double yaw = 0; yaw < 2 * Math.PI; yaw += 0.3) {
                float x = 7.5f + r * (float) Math.sin(yaw);
                float z = 7.5f + r * (float) Math.cos(yaw);
                assertEquals(Terrain.FLAT_Y, terrain.heightAt(x, z), EPS, "every meteor lands on the flat zone");
            }
        }
    }
}
