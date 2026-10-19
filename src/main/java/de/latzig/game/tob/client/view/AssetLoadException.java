package de.latzig.game.tob.client.view;

/** A missing or unreadable asset file (spec §6); {@link #file()} is the path relative to the assets root. */
public final class AssetLoadException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String file;

    public AssetLoadException(String file, Throwable cause) {
        super("missing or unreadable asset " + file + (cause == null ? "" : ": " + cause.getMessage()), cause);
        this.file = file;
    }

    public String file() {
        return file;
    }
}
