package de.latzig.game.tob.client.logic;

import java.util.Locale;
import java.util.ResourceBundle;

/**
 * The front-end texts (Phase 5a spec decision 15): the resource bundle {@code i18n/frontend} (English only, ASCII,
 * prepared for translation). Numbers are formatted with {@link Locale#ROOT}.
 */
public final class Texts {

    public static final String BUNDLE = "i18n.frontend";

    private final ResourceBundle bundle;

    private Texts(ResourceBundle bundle) {
        this.bundle = bundle;
    }

    public static Texts english() {
        return new Texts(ResourceBundle.getBundle(BUNDLE, Locale.ROOT));
    }

    public String get(String key) {
        return bundle.getString(key);
    }

    public String format(String key, Object... args) {
        return String.format(Locale.ROOT, get(key), args);
    }
}
