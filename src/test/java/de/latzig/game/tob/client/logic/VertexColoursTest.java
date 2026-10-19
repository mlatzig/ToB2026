package de.latzig.game.tob.client.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.io.MeshReader;
import de.latzig.game.tob.core.model.MeshData;

class VertexColoursTest {

    @Test
    void aVertexColourIsAveragedWithTheObjectColourPerChannelAndKeepsItsAlpha() {
        assertEquals(0xFF9F9F9F, VertexColours.average(0xFF404040, 0xFFFFFF), "(0x40 + 0xFF) / 2, truncated");
        assertEquals(0x80FFFFFF, VertexColours.average(0x80FFFFFF, 0xFFFFFF), "white on white stays white");
        assertEquals(0xFF000000, VertexColours.average(0xFF000000, 0x000000), "black on black stays black");
        assertEquals(0xFF087F7F, VertexColours.average(0xFF10FF00, 0x0000FF), "per channel, truncated");
        assertEquals(0x878787, VertexColours.average(0xFFFFFF, 0x101010) & 0xFFFFFF,
                "a meteor's debris: ToB 2005's object colour 16/16/16 on white");
    }

    @Test
    void everyKeyOfAMeshIsAveragedAndAMeshWithoutColoursIsLeftAsItIs() {
        float[] positions = {0, 0, 0, 1, 0, 0, 0, 1, 0};
        float[] uvs = {0, 0, 1, 0, 0, 1};
        MeshData coloured = new MeshData(1, new int[] {0, 16}, List.of(positions, positions), List.of(uvs, uvs),
                List.of(new int[] {0xFF000000, 0xFF404040, 0xFFFFFFFF}, new int[] {0xFFFFFFFF, 0xFF404040, 0}));

        MeshData averaged = VertexColours.average(coloured, 0xFFFFFF);

        assertArrayEquals(new int[] {0xFF7F7F7F, 0xFF9F9F9F, 0xFFFFFFFF}, averaged.colors(0));
        assertArrayEquals(new int[] {0xFFFFFFFF, 0xFF9F9F9F, 0x007F7F7F}, averaged.colors(1));
        assertArrayEquals(coloured.positions(1), averaged.positions(1));
        assertArrayEquals(coloured.keys(), averaged.keys());
        MeshData plain = new MeshData(1, new int[] {0}, List.of(positions), List.of(uvs), null);
        assertSame(plain, VertexColours.average(plain, 0xFFFFFF));
    }

    /**
     * The enemy zapper's needle (zapper-debug report): its vertex colours pulse between 0x404040 and white; averaged
     * with the white object colour its darkest phase is 0x9F9F9F, so it stays visible at all times.
     */
    @Test
    void theEnemyZapperNeedlesDarkestPhaseIsMidGreyAfterAveraging() throws IOException {
        Path file = Path.of("src/main/resources/assets/meshes/3201-enemyzapper-n.tobmesh");
        assumeTrue(Files.isRegularFile(file), "generated assets not available");
        MeshData needle = MeshReader.read(file);
        int darkest = 255;
        int darkestBefore = 255;
        MeshData averaged = VertexColours.average(needle, 0xFFFFFF);
        for (int k = 0; k < needle.keyCount(); k++) {
            for (int c : needle.colors(k)) {
                darkestBefore = Math.min(darkestBefore, c & 0xFF);
            }
            for (int c : averaged.colors(k)) {
                darkest = Math.min(darkest, Math.min(c & 0xFF, Math.min((c >> 8) & 0xFF, (c >> 16) & 0xFF)));
            }
        }
        assertEquals(0x40, darkestBefore, "the converted needle's darkest phase");
        assertEquals(0x9F, darkest);
        assertTrue(averaged.hasColors());
    }
}
