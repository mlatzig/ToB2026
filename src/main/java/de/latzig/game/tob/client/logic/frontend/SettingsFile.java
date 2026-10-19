package de.latzig.game.tob.client.logic.frontend;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.UnaryOperator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.latzig.game.tob.core.io.SettingsJson;

/**
 * The settings file {@code <userdir>/settings.json} (Phase 5b decision 10): reading never fails (no file yet or an
 * unreadable one reads as {@link SettingsJson.Settings#EMPTY}, i.e. the defaults; the latter is logged), and every
 * write is a read-modify-write of the whole file, written atomically, so the last profile ({@code ProfileStore}) and
 * the options ({@link LiveSettings}) never overwrite each other. Plain {@code java.nio}, no engine.
 */
public final class SettingsFile {

    private static final Logger LOG = LoggerFactory.getLogger(SettingsFile.class);

    private final Path path;
    private final SettingsJson json = new SettingsJson();

    public SettingsFile(Path path) {
        this.path = path;
    }

    public Path path() {
        return path;
    }

    /** The file's settings; empty if there is none, or if it cannot be read (logged; the next write replaces it). */
    public SettingsJson.Settings read() {
        try {
            return json.read(path);
        } catch (IOException | RuntimeException e) {
            LOG.warn("Cannot read {}, using the defaults: {}", path, e.getMessage());
            return SettingsJson.Settings.EMPTY;
        }
    }

    /** Applies {@code change} to what the file holds now and writes the result atomically. */
    public void update(UnaryOperator<SettingsJson.Settings> change) throws IOException {
        json.write(change.apply(read()), path);
    }
}
