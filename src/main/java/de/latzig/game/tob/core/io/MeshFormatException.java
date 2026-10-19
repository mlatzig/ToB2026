package de.latzig.game.tob.core.io;

import java.io.IOException;

/** A {@code .tobmesh} file is malformed. */
public class MeshFormatException extends IOException {

    private static final long serialVersionUID = 1L;

    public MeshFormatException(String message) {
        super(message);
    }
}
