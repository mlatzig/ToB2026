package de.latzig.game.tob.core.io;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.latzig.game.tob.core.model.MeshData;

/** Reads {@code .tobmesh} files written by {@link MeshWriter}. */
public final class MeshReader {

    private MeshReader() {
    }

    public static MeshData read(Path file) throws IOException {
        try {
            return fromBytes(Files.readAllBytes(file));
        } catch (MeshFormatException e) {
            throw new MeshFormatException(file + ": " + e.getMessage());
        }
    }

    public static MeshData fromBytes(byte[] data) throws MeshFormatException {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            byte[] magic = new byte[MeshWriter.MAGIC.length];
            buffer.get(magic);
            if (!Arrays.equals(magic, MeshWriter.MAGIC)) {
                throw new MeshFormatException("not a .tobmesh file");
            }
            int version = buffer.getInt();
            if (version != MeshWriter.VERSION) {
                throw new MeshFormatException("unsupported version " + version);
            }
            int t = buffer.getInt();
            int k = buffer.getInt();
            if (t < 1 || k < 1) {
                throw new MeshFormatException("invalid counts: triangles " + t + ", keys " + k);
            }
            int[] keys = new int[k];
            for (int i = 0; i < k; i++) {
                keys[i] = buffer.getInt();
            }
            int flags = buffer.getInt();
            boolean hasColors = (flags & MeshData.FLAG_COLORS) != 0;
            List<float[]> positions = new ArrayList<>();
            List<float[]> uvs = new ArrayList<>();
            List<int[]> colors = hasColors ? new ArrayList<>() : null;
            for (int i = 0; i < k; i++) {
                positions.add(floats(buffer, t * 9));
                uvs.add(floats(buffer, t * 6));
                if (hasColors) {
                    int[] c = new int[t * 3];
                    for (int j = 0; j < c.length; j++) {
                        c[j] = buffer.getInt();
                    }
                    colors.add(c);
                }
            }
            if (buffer.hasRemaining()) {
                throw new MeshFormatException(buffer.remaining() + " trailing bytes");
            }
            return new MeshData(t, keys, positions, uvs, colors);
        } catch (BufferUnderflowException e) {
            throw new MeshFormatException("truncated data");
        } catch (IllegalArgumentException e) {
            throw new MeshFormatException(e.getMessage());
        }
    }

    private static float[] floats(ByteBuffer buffer, int count) {
        float[] values = new float[count];
        for (int i = 0; i < count; i++) {
            values[i] = buffer.getFloat();
        }
        return values;
    }
}
