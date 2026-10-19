package de.latzig.game.tob.core.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.latzig.game.tob.core.model.MeshData;

class MeshFormatTest {

    private static MeshData twoKeyMesh(boolean colours) {
        float[] p0 = {0, 0, 0, 1, 0, 0, 0, 0, -1};
        float[] p1 = {0, 0.5f, 0, 1, 0.5f, 0, 0, 0.5f, -1};
        float[] uv0 = {0, 0, 1, 0, 0, 1};
        float[] uv1 = {0.5f, 0, 1, 0, 0, 1};
        List<int[]> cols = colours ? List.of(new int[] {0xFFFF0000, 0xFF00FF00, 0xFF0000FF},
                new int[] {0xFFFF0000, 0xFF00FF00, 0xFF0000FF}) : null;
        return new MeshData(1, new int[] {0, 16}, List.of(p0, p1), List.of(uv0, uv1), cols);
    }

    @Test
    void computesFlags() {
        assertEquals(MeshData.FLAG_COLORS | MeshData.FLAG_UVS_ANIMATED, twoKeyMesh(true).flags());
        assertEquals(MeshData.FLAG_UVS_ANIMATED, twoKeyMesh(false).flags());
        assertFalse(twoKeyMesh(false).hasColors());
        assertNull(twoKeyMesh(false).colors(0));
    }

    @Test
    void roundTripsThroughBytesAndFiles(@TempDir Path dir) throws IOException {
        MeshData mesh = twoKeyMesh(true);
        assertEquals(mesh, MeshReader.fromBytes(MeshWriter.toBytes(mesh)));

        Path file = dir.resolve("sub/m.tobmesh");
        MeshWriter.write(mesh, file);
        MeshData read = MeshReader.read(file);
        assertEquals(mesh, read);
        assertArrayEquals(new int[] {0, 16}, read.keys());
        assertEquals(0.5f, read.positions(1)[1]);
        assertArrayEquals(new int[] {0xFFFF0000, 0xFF00FF00, 0xFF0000FF}, read.colors(1));
    }

    @Test
    void writesTheDocumentedHeader() {
        byte[] bytes = MeshWriter.toBytes(twoKeyMesh(false));

        assertEquals("TOBMESH\0", new String(bytes, 0, 8, java.nio.charset.StandardCharsets.US_ASCII));
        assertEquals(1, bytes[8]);                       // version 1, little endian
        assertEquals(1, bytes[12]);                      // triangle count 1
        assertEquals(2, bytes[16]);                      // 2 keys
        assertEquals(16, bytes[24]);                     // second key = 16
        // header 28 + flags 4 + 2 keys * (9 + 6) floats * 4 bytes
        assertEquals(32 + 2 * 15 * 4, bytes.length);

        // Golden-order check: verify byte layout with actual mesh data (with colours)
        MeshData mesh = twoKeyMesh(true);
        byte[] coloredBytes = MeshWriter.toBytes(mesh);
        ByteBuffer buf = ByteBuffer.wrap(coloredBytes).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(mesh.positions(0)[0], buf.getFloat(32), "positions(0)[0]");
        assertEquals(mesh.uvs(0)[0], buf.getFloat(32 + 9 * 4), "uvs(0)[0]");
        assertEquals(mesh.colors(0)[0], buf.getInt(32 + 15 * 4), "colors(0)[0]");
    }

    @Test
    void rejectsInvalidInput() {
        byte[] bytes = MeshWriter.toBytes(twoKeyMesh(true));
        byte[] badMagic = bytes.clone();
        badMagic[0] = 'X';
        byte[] badVersion = bytes.clone();
        badVersion[8] = 2;

        assertThrows(MeshFormatException.class, () -> MeshReader.fromBytes(badMagic));
        assertThrows(MeshFormatException.class, () -> MeshReader.fromBytes(badVersion));
        assertThrows(MeshFormatException.class, () -> MeshReader.fromBytes(Arrays.copyOf(bytes, bytes.length - 4)));
        assertThrows(MeshFormatException.class, () -> MeshReader.fromBytes(Arrays.copyOf(bytes, bytes.length + 4)));
    }

    @Test
    void validatesConstructorArguments() {
        float[] p = new float[9];
        float[] uv = new float[6];
        assertThrows(IllegalArgumentException.class,
                () -> new MeshData(1, new int[] {16, 0}, List.of(p, p), List.of(uv, uv), null));
        assertThrows(IllegalArgumentException.class,
                () -> new MeshData(1, new int[] {0}, List.of(new float[8]), List.of(uv), null));
        assertThrows(IllegalArgumentException.class,
                () -> new MeshData(1, new int[] {0}, List.of(p), List.of(uv), List.of(new int[2])));
        assertTrue(new MeshData(1, new int[] {0}, List.of(p), List.of(uv), null).keyCount() == 1);
    }
}
