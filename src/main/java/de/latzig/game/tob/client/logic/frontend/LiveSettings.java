package de.latzig.game.tob.client.logic.frontend;

import java.io.IOException;
import java.util.Objects;

/**
 * The options the running game uses (Phase 5b spec §3): {@link #saved} is what {@code settings.json} holds,
 * {@link #current} what applies now — the same, except while the Options window previews a change (decision 1:
 * changes apply at once, Apply saves them, Back restores the saved ones). Views read {@link #current} every frame
 * (volume, brightness, flash strength, panel size) or at the next tower (sound detail, moon detail, ambient effects,
 * keys, Backwards Allowed). Used on the render thread only.
 */
public final class LiveSettings {

    private final SettingsFile file;
    private Settings saved;
    private Settings current;

    private LiveSettings(SettingsFile file, Settings settings) {
        this.file = file;
        this.saved = settings;
        this.current = settings;
    }

    /** The options of {@code file} (decision 10: the defaults for every missing or unreadable value). */
    public static LiveSettings load(SettingsFile file) {
        return new LiveSettings(Objects.requireNonNull(file, "file"), Settings.fromOptions(file.read().options()));
    }

    /** Options that are never written (tests). */
    public static LiveSettings inMemory(Settings settings) {
        return new LiveSettings(null, settings);
    }

    public Settings saved() {
        return saved;
    }

    public Settings current() {
        return current;
    }

    /** Shows {@code settings} at once, without saving them. */
    public void preview(Settings settings) {
        current = Objects.requireNonNull(settings, "settings");
    }

    /** Back to the saved options. */
    public void revert() {
        current = saved;
    }

    /** Saves the current options (read-modify-write: the last profile stays). */
    public void save() throws IOException {
        if (file != null) {
            Settings written = current;
            file.update(settings -> settings.withOptions(written.toOptions()));
        }
        saved = current;
    }
}
