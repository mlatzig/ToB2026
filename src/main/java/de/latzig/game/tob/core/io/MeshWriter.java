package de.latzig.game.tob.core.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import de.latzig.game.tob.core.model.MeshData;

/** Writes {@code .tobmesh} files (little endian, version {@value #VERSION}). */
public final class MeshWriter {

    static final byte[] MAGIC = "TOBMESH\0".getBytes(StandardCharsets.US_ASCII);
    public static final int VERSION = 1;

    private MeshWriter() {
    }

    public static byte[] toBytes(MeshData mesh) {
        int t = mesh.triangleCount();
        int k = mesh.keyCount();
        boolean colors = mesh.hasColors();
        int perKey = t * 9 * 4 + t * 6 * 4 + (colors ? t * 3 * 4 : 0);
        ByteBuffer buffer = ByteBuffer.allocate(8 + 4 + 4 + 4 + k * 4 + 4 + k * perKey).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(MAGIC).putInt(VERSION).putInt(t).putInt(k);
        for (int key : mesh.keys()) {
            buffer.putInt(key);
        }
        buffer.putInt(mesh.flags());
        for (int i = 0; i < k; i++) {
            for (float value : mesh.positions(i)) {
                buffer.putFloat(value);
            }
            for (float value : mesh.uvs(i)) {
                buffer.putFloat(value);
            }
            if (colors) {
                for (int value : mesh.colors(i)) {
                    buffer.putInt(value);
                }
            }
        }
        return buffer.array();
    }

    public static void write(MeshData mesh, Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(file, toBytes(mesh));
    }
}
