package de.latzig.game.tob.client.logic.frontend;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import de.latzig.game.tob.client.logic.audio.SoundTuning;
import de.latzig.game.tob.client.logic.game.GameAction;
import de.latzig.game.tob.client.logic.game.KeyChord;
import de.latzig.game.tob.client.logic.game.PanelSize;

/**
 * The player's options (Phase 5b spec decisions 2–6), global for every profile: volumes in percent (Master and
 * Background 0–200 % in steps of 10), the sound detail ({@link SoundTuning#DETAIL_NONE} ..
 * {@link SoundTuning#DETAIL_HIGH}), fullscreen or window, brightness 50–150 %, the moon texture detail, the ambient
 * effects, the lightning flash's strength 0–100 %, the panel size, Backwards Allowed, the remapped keys and
 * (author's extra, 2026-10-14) whether the GAME screen's mouse cursor auto-hides, (Phase 5c-2 decision 4) the
 * Music switch, default on, and (Phase 5e decision 5) the Look, Remake (default) or Classic. Immutable; the compact
 * constructor clamps every value into its range. {@link #keys} holds only the actions bound differently from the
 * defaults (an empty map: every key as shipped).
 * <p>
 * {@code settings.json} stores the options as a flat map of names to text ({@link #toOptions}, {@link #fromOptions}):
 * a missing or unreadable value takes its default and unknown names are ignored (decision 10), so the file needs no
 * migration.
 */
public record Settings(int masterVolume, int backgroundVolume, int soundDetail, boolean fullscreen, int brightness,
        boolean halfMoonDetail, boolean ambientEffects, int flashStrength, PanelSize panelSize,
        boolean backwardsAllowed, Map<GameAction, KeyChord> keys, boolean hideMouseCursor, boolean music,
        boolean classicLook) {

    public static final int VOLUME_MIN = 0;
    public static final int VOLUME_MAX = 200;
    public static final int VOLUME_STEP = 10;
    public static final int BRIGHTNESS_MIN = 50;
    public static final int BRIGHTNESS_MAX = 150;
    public static final int BRIGHTNESS_STEP = 10;
    public static final int FLASH_MIN = 0;
    public static final int FLASH_MAX = 100;
    public static final int FLASH_STEP = 10;
    /** The hidden developer override {@code -Dtob.volume} replaces Master up to this gain (decision 3). */
    public static final float VOLUME_OVERRIDE_MAX = 4f;

    public static final String OPTION_MASTER_VOLUME = "masterVolume";
    public static final String OPTION_BACKGROUND_VOLUME = "backgroundVolume";
    public static final String OPTION_SOUND_DETAIL = "soundDetail";
    public static final String OPTION_DISPLAY = "display";
    public static final String OPTION_BRIGHTNESS = "brightness";
    public static final String OPTION_MOON_DETAIL = "moonDetail";
    public static final String OPTION_AMBIENT_EFFECTS = "ambientEffects";
    public static final String OPTION_FLASH_STRENGTH = "flashStrength";
    public static final String OPTION_PANEL_SIZE = "panelSize";
    public static final String OPTION_BACKWARDS_ALLOWED = "backwardsAllowed";
    /** Author's extra (2026-10-14): the GAME screen's software cursor auto-hide, default on ({@link #DEFAULTS}). */
    public static final String OPTION_HIDE_MOUSE_CURSOR = "hideMouseCursor";
    /** Phase 5c-2 decision 4: the menu music on or off, default on ({@link #DEFAULTS}). */
    public static final String OPTION_MUSIC = "music";
    /** Phase 5e decision 5: the Look, {@code "remake"} (default, {@link #DEFAULTS}) or {@code "classic"}. */
    public static final String OPTION_LOOK = "look";
    /** A remapped key is stored as {@code key.<GameAction>} = {@link KeyChord#toText()}. */
    public static final String KEY_PREFIX = "key.";

    /** The sound detail's names in the file, by level 0..3. */
    private static final String[] DETAIL_NAMES = {"none", "low", "medium", "high"};

    public static final Settings DEFAULTS = new Settings(100, 100, SoundTuning.DETAIL_HIGH, true, 100, false, true,
            100, PanelSize.FULL_WIDTH, true, Map.of(), true, true, false);

    public Settings {
        masterVolume = Math.clamp(masterVolume, VOLUME_MIN, VOLUME_MAX);
        backgroundVolume = Math.clamp(backgroundVolume, VOLUME_MIN, VOLUME_MAX);
        soundDetail = Math.clamp(soundDetail, SoundTuning.DETAIL_NONE, SoundTuning.DETAIL_HIGH);
        brightness = Math.clamp(brightness, BRIGHTNESS_MIN, BRIGHTNESS_MAX);
        flashStrength = Math.clamp(flashStrength, FLASH_MIN, FLASH_MAX);
        panelSize = panelSize == null ? PanelSize.FULL_WIDTH : panelSize;
        Map<GameAction, KeyChord> copy = new EnumMap<>(GameAction.class);
        // Task 1 minor (final review): a hand-built Settings with keys == null must not throw in putAll
        copy.putAll(keys == null ? Map.of() : keys);
        keys = Collections.unmodifiableMap(copy);
    }

    public Settings withMasterVolume(int value) {
        return new Settings(value, backgroundVolume, soundDetail, fullscreen, brightness, halfMoonDetail,
                ambientEffects, flashStrength, panelSize, backwardsAllowed, keys, hideMouseCursor, music, classicLook);
    }

    public Settings withBackgroundVolume(int value) {
        return new Settings(masterVolume, value, soundDetail, fullscreen, brightness, halfMoonDetail, ambientEffects,
                flashStrength, panelSize, backwardsAllowed, keys, hideMouseCursor, music, classicLook);
    }

    public Settings withSoundDetail(int value) {
        return new Settings(masterVolume, backgroundVolume, value, fullscreen, brightness, halfMoonDetail,
                ambientEffects, flashStrength, panelSize, backwardsAllowed, keys, hideMouseCursor, music, classicLook);
    }

    public Settings withFullscreen(boolean value) {
        return new Settings(masterVolume, backgroundVolume, soundDetail, value, brightness, halfMoonDetail,
                ambientEffects, flashStrength, panelSize, backwardsAllowed, keys, hideMouseCursor, music, classicLook);
    }

    public Settings withBrightness(int value) {
        return new Settings(masterVolume, backgroundVolume, soundDetail, fullscreen, value, halfMoonDetail,
                ambientEffects, flashStrength, panelSize, backwardsAllowed, keys, hideMouseCursor, music, classicLook);
    }

    public Settings withHalfMoonDetail(boolean value) {
        return new Settings(masterVolume, backgroundVolume, soundDetail, fullscreen, brightness, value,
                ambientEffects, flashStrength, panelSize, backwardsAllowed, keys, hideMouseCursor, music, classicLook);
    }

    public Settings withAmbientEffects(boolean value) {
        return new Settings(masterVolume, backgroundVolume, soundDetail, fullscreen, brightness, halfMoonDetail,
                value, flashStrength, panelSize, backwardsAllowed, keys, hideMouseCursor, music, classicLook);
    }

    public Settings withFlashStrength(int value) {
        return new Settings(masterVolume, backgroundVolume, soundDetail, fullscreen, brightness, halfMoonDetail,
                ambientEffects, value, panelSize, backwardsAllowed, keys, hideMouseCursor, music, classicLook);
    }

    public Settings withPanelSize(PanelSize value) {
        return new Settings(masterVolume, backgroundVolume, soundDetail, fullscreen, brightness, halfMoonDetail,
                ambientEffects, flashStrength, value, backwardsAllowed, keys, hideMouseCursor, music, classicLook);
    }

    public Settings withBackwardsAllowed(boolean value) {
        return new Settings(masterVolume, backgroundVolume, soundDetail, fullscreen, brightness, halfMoonDetail,
                ambientEffects, flashStrength, panelSize, value, keys, hideMouseCursor, music, classicLook);
    }

    public Settings withKeys(Map<GameAction, KeyChord> value) {
        return new Settings(masterVolume, backgroundVolume, soundDetail, fullscreen, brightness, halfMoonDetail,
                ambientEffects, flashStrength, panelSize, backwardsAllowed, value, hideMouseCursor, music, classicLook);
    }

    /** Author's extra (2026-10-14): the GAME screen's software cursor auto-hide, applied live. */
    public Settings withHideMouseCursor(boolean value) {
        return new Settings(masterVolume, backgroundVolume, soundDetail, fullscreen, brightness, halfMoonDetail,
                ambientEffects, flashStrength, panelSize, backwardsAllowed, keys, value, music, classicLook);
    }

    /** Phase 5c-2 decision 4: the menu music on or off, applied at once. */
    public Settings withMusic(boolean value) {
        return new Settings(masterVolume, backgroundVolume, soundDetail, fullscreen, brightness, halfMoonDetail,
                ambientEffects, flashStrength, panelSize, backwardsAllowed, keys, hideMouseCursor, value, classicLook);
    }

    /** Phase 5e decision 5: the Look, applied at the next scene build (a tower start, a menu visit). */
    public Settings withClassicLook(boolean value) {
        return new Settings(masterVolume, backgroundVolume, soundDetail, fullscreen, brightness, halfMoonDetail,
                ambientEffects, flashStrength, panelSize, backwardsAllowed, keys, hideMouseCursor, music, value);
    }

    /**
     * The audio listener's gain: Master / 100, or the hidden developer override {@code -Dtob.volume} (decision 3:
     * it replaces Master, 0 to {@value #VOLUME_OVERRIDE_MAX}).
     */
    public float listenerGain(Optional<Float> override) {
        return override.map(gain -> Math.clamp(gain, 0f, VOLUME_OVERRIDE_MAX)).orElse(masterVolume / 100f);
    }

    /** The background loops' factor on top of their tuned level ({@link SoundTuning#BACKGROUND_VOLUME}). */
    public float backgroundScale() {
        return backgroundVolume / 100f;
    }

    /** The factor of the scene's sun and ambient light (decision 6). */
    public float brightnessFactor() {
        return brightness / 100f;
    }

    /** The factor of the lightning flash (decision 6: 0 for photosensitive players). */
    public float flashFactor() {
        return flashStrength / 100f;
    }

    /** The options as {@code settings.json} stores them: name → text, the remapped keys as {@code key.<action>}. */
    public Map<String, String> toOptions() {
        Map<String, String> options = new LinkedHashMap<>();
        options.put(OPTION_MASTER_VOLUME, Integer.toString(masterVolume));
        options.put(OPTION_BACKGROUND_VOLUME, Integer.toString(backgroundVolume));
        options.put(OPTION_SOUND_DETAIL, DETAIL_NAMES[soundDetail]);
        options.put(OPTION_DISPLAY, fullscreen ? "fullscreen" : "window");
        options.put(OPTION_BRIGHTNESS, Integer.toString(brightness));
        options.put(OPTION_MOON_DETAIL, halfMoonDetail ? "half" : "full");
        options.put(OPTION_AMBIENT_EFFECTS, Boolean.toString(ambientEffects));
        options.put(OPTION_FLASH_STRENGTH, Integer.toString(flashStrength));
        options.put(OPTION_PANEL_SIZE, panelSize == PanelSize.FIT_HEIGHT ? "fitHeight" : "fullWidth");
        options.put(OPTION_BACKWARDS_ALLOWED, Boolean.toString(backwardsAllowed));
        options.put(OPTION_HIDE_MOUSE_CURSOR, Boolean.toString(hideMouseCursor));
        options.put(OPTION_MUSIC, Boolean.toString(music));
        options.put(OPTION_LOOK, classicLook ? "classic" : "remake");
        keys.forEach((action, chord) -> options.put(KEY_PREFIX + action.name(), chord.toText()));
        return options;
    }

    /**
     * The options of {@link #toOptions}'s form: a missing or unreadable value is its default (an out-of-range one is
     * clamped); unknown names are ignored.
     */
    public static Settings fromOptions(Map<String, String> options) {
        Settings d = DEFAULTS;
        Map<GameAction, KeyChord> keys = new EnumMap<>(GameAction.class);
        options.forEach((name, value) -> {
            if (name.startsWith(KEY_PREFIX)) {
                try {
                    keys.put(GameAction.valueOf(name.substring(KEY_PREFIX.length())), KeyChord.parse(value));
                } catch (IllegalArgumentException e) {
                    // an unknown action or an unreadable chord: the action keeps its default key
                }
            }
        });
        return new Settings(number(options, OPTION_MASTER_VOLUME, d.masterVolume),
                number(options, OPTION_BACKGROUND_VOLUME, d.backgroundVolume),
                detail(options.get(OPTION_SOUND_DETAIL), d.soundDetail),
                choice(options, OPTION_DISPLAY, "fullscreen", "window", d.fullscreen),
                number(options, OPTION_BRIGHTNESS, d.brightness),
                choice(options, OPTION_MOON_DETAIL, "half", "full", d.halfMoonDetail),
                choice(options, OPTION_AMBIENT_EFFECTS, "true", "false", d.ambientEffects),
                number(options, OPTION_FLASH_STRENGTH, d.flashStrength),
                choice(options, OPTION_PANEL_SIZE, "fitHeight", "fullWidth", false) ? PanelSize.FIT_HEIGHT
                        : PanelSize.FULL_WIDTH,
                choice(options, OPTION_BACKWARDS_ALLOWED, "true", "false", d.backwardsAllowed), keys,
                choice(options, OPTION_HIDE_MOUSE_CURSOR, "true", "false", d.hideMouseCursor),
                choice(options, OPTION_MUSIC, "true", "false", d.music),
                choice(options, OPTION_LOOK, "classic", "remake", d.classicLook));
    }

    private static int number(Map<String, String> options, String name, int fallback) {
        try {
            String value = options.get(name);
            return value == null ? fallback : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** {@code yes} → true, {@code no} → false (ignoring case), anything else → {@code fallback}. */
    private static boolean choice(Map<String, String> options, String name, String yes, String no, boolean fallback) {
        String value = options.get(name);
        if (value == null) {
            return fallback;
        }
        if (value.trim().equalsIgnoreCase(yes)) {
            return true;
        }
        return value.trim().equalsIgnoreCase(no) ? false : fallback;
    }

    private static int detail(String value, int fallback) {
        for (int level = 0; value != null && level < DETAIL_NAMES.length; level++) {
            if (DETAIL_NAMES[level].equals(value.trim().toLowerCase(Locale.ROOT))) {
                return level;
            }
        }
        return fallback;
    }
}
