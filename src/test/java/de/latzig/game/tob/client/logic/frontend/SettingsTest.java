package de.latzig.game.tob.client.logic.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.client.logic.audio.SoundTuning;
import de.latzig.game.tob.client.logic.game.GameAction;
import de.latzig.game.tob.client.logic.game.KeyChord;
import de.latzig.game.tob.client.logic.game.PanelSize;

class SettingsTest {

    @Test
    void theDefaultsAreTheSpecsDecisions() {
        Settings defaults = Settings.DEFAULTS;

        assertEquals(100, defaults.masterVolume(), "decision 3");
        assertEquals(100, defaults.backgroundVolume());
        assertEquals(SoundTuning.DETAIL_HIGH, defaults.soundDetail());
        assertTrue(defaults.fullscreen(), "decision 2: fullscreen at the desktop resolution");
        assertEquals(100, defaults.brightness(), "decision 6");
        assertFalse(defaults.halfMoonDetail());
        assertTrue(defaults.ambientEffects());
        assertEquals(100, defaults.flashStrength());
        assertEquals(PanelSize.FULL_WIDTH, defaults.panelSize(), "Phase 3b-2 variant B");
        assertTrue(defaults.backwardsAllowed(), "decision 4: the original's default");
        assertEquals(Map.of(), defaults.keys(), "no key remapped");
        assertTrue(defaults.hideMouseCursor(), "author's extra (2026-10-14): default On");
        assertTrue(defaults.music(), "Phase 5c-2 decision 4: default on");
        assertFalse(defaults.classicLook(), "Phase 5e decision 5: default Remake");
    }

    /** Task 1 minor (final review): a hand-built {@code keys == null} must not throw in the compact constructor. */
    @Test
    void nullKeysBecomeAnEmptyMap() {
        Settings settings = new Settings(100, 100, SoundTuning.DETAIL_HIGH, true, 100, false, true, 100,
                PanelSize.FULL_WIDTH, true, null, true, true, false);

        assertEquals(Map.of(), settings.keys());
    }

    @Test
    void everyValueIsClampedIntoItsRange() {
        Settings low = Settings.DEFAULTS.withMasterVolume(-10).withBackgroundVolume(-1).withSoundDetail(-1)
                .withBrightness(0).withFlashStrength(-50);
        Settings high = Settings.DEFAULTS.withMasterVolume(250).withBackgroundVolume(999).withSoundDetail(7)
                .withBrightness(400).withFlashStrength(101);

        assertEquals(0, low.masterVolume());
        assertEquals(0, low.backgroundVolume());
        assertEquals(SoundTuning.DETAIL_NONE, low.soundDetail());
        assertEquals(50, low.brightness());
        assertEquals(0, low.flashStrength());
        assertEquals(200, high.masterVolume());
        assertEquals(200, high.backgroundVolume());
        assertEquals(SoundTuning.DETAIL_HIGH, high.soundDetail());
        assertEquals(150, high.brightness());
        assertEquals(100, high.flashStrength());
    }

    @Test
    void theFactorsAndTheHiddenVolumeOverride() {
        Settings settings = Settings.DEFAULTS.withMasterVolume(150).withBackgroundVolume(50).withBrightness(120)
                .withFlashStrength(0);

        assertEquals(1.5f, settings.listenerGain(Optional.empty()), 1e-6f);
        assertEquals(4f, settings.listenerGain(Optional.of(4f)), 1e-6f, "-Dtob.volume replaces Master");
        assertEquals(4f, settings.listenerGain(Optional.of(9f)), 1e-6f, "the override stops at 4");
        assertEquals(0f, settings.listenerGain(Optional.of(-1f)), 1e-6f);
        assertEquals(0.5f, settings.backgroundScale(), 1e-6f);
        assertEquals(1.2f, settings.brightnessFactor(), 1e-6f);
        assertEquals(0f, settings.flashFactor(), 1e-6f);
    }

    @Test
    void theOptionsSurviveTheRoundTripWithTheirFileNames() {
        Settings settings = new Settings(70, 120, SoundTuning.DETAIL_LOW, false, 90, true, false, 30,
                PanelSize.FIT_HEIGHT, false, Map.of(GameAction.FIRE, KeyChord.of(28),
                        GameAction.PROGRAM_UP, KeyChord.ctrl(17)), false, false, true);

        Map<String, String> options = settings.toOptions();

        assertEquals(settings, Settings.fromOptions(options));
        assertEquals("70", options.get("masterVolume"));
        assertEquals("low", options.get("soundDetail"));
        assertEquals("window", options.get("display"));
        assertEquals("half", options.get("moonDetail"));
        assertEquals("fitHeight", options.get("panelSize"));
        assertEquals("false", options.get("backwardsAllowed"));
        assertEquals("false", options.get("hideMouseCursor"), "author's extra (2026-10-14)");
        assertEquals("28", options.get("key.FIRE"));
        assertEquals("ctrl+17", options.get("key.PROGRAM_UP"));
        assertEquals("false", options.get("music"), "Phase 5c-2 decision 4");
        assertEquals("classic", options.get("look"), "Phase 5e decision 5");
        assertEquals(15, options.size(), "13 options and 2 remapped keys");
    }

    /** Phase 5e decision 5: the Look is Remake by default, a missing or unreadable value (older files) too. */
    @Test
    void theLookDefaultsToRemakeAndRoundTrips() {
        assertFalse(Settings.fromOptions(Map.of()).classicLook(), "missing: Remake");
        assertTrue(Settings.fromOptions(Map.of("look", "classic")).classicLook());
        assertTrue(Settings.fromOptions(Map.of("look", "Classic")).classicLook(), "case is ignored");
        assertFalse(Settings.fromOptions(Map.of("look", "remake")).classicLook());
        assertFalse(Settings.fromOptions(Map.of("look", "retro")).classicLook(), "unreadable: the default");

        Settings classic = Settings.DEFAULTS.withClassicLook(true);

        assertTrue(classic.classicLook());
        assertEquals(Settings.DEFAULTS.music(), classic.music(), "the other values stay");
        assertEquals(classic, Settings.fromOptions(classic.toOptions()));
        assertEquals("classic", classic.withMasterVolume(50).withKeys(Map.of()).withMusic(false).toOptions()
                .get(Settings.OPTION_LOOK), "every other change keeps it");
    }

    /** Phase 5c-2 decision 4: the Music switch is on by default, a missing field (older files) reads as on. */
    @Test
    void musicDefaultsToOnAndRoundTrips() {
        assertTrue(Settings.fromOptions(Map.of()).music(), "missing: on");
        assertTrue(Settings.fromOptions(Map.of("music", "true")).music());
        assertFalse(Settings.fromOptions(Map.of("music", "false")).music());
        assertTrue(Settings.fromOptions(Map.of("music", "loud")).music(), "unreadable: the default");

        Settings off = Settings.DEFAULTS.withMusic(false);

        assertFalse(off.music());
        assertEquals(Settings.DEFAULTS.hideMouseCursor(), off.hideMouseCursor(), "the other values stay");
        assertEquals(off, Settings.fromOptions(off.toOptions()));
        assertTrue(off.withMasterVolume(50).withKeys(Map.of()).withHideMouseCursor(false).toOptions()
                .get(Settings.OPTION_MUSIC).equals("false"), "every other change keeps it");
    }

    @Test
    void missingAndBrokenValuesTakeTheirDefaultsAndUnknownNamesAreIgnored() {
        assertEquals(Settings.DEFAULTS, Settings.fromOptions(Map.of()));

        Map<String, String> options = new HashMap<>();
        options.put("masterVolume", "loud");
        options.put("backgroundVolume", "300");
        options.put("soundDetail", "ultra");
        options.put("display", "window");
        options.put("panelSize", "huge");
        options.put("musicVolume", "50");
        options.put("key.FIRE", "ctrl+");
        options.put("key.TELEPORT", "20");
        options.put("key.ZOOM_IN", "ctrl+201");

        Settings read = Settings.fromOptions(options);

        assertEquals(100, read.masterVolume(), "unreadable: the default");
        assertEquals(200, read.backgroundVolume(), "out of range: clamped");
        assertEquals(SoundTuning.DETAIL_HIGH, read.soundDetail());
        assertFalse(read.fullscreen());
        assertEquals(PanelSize.FULL_WIDTH, read.panelSize());
        assertEquals(Map.of(GameAction.ZOOM_IN, KeyChord.ctrl(201)), read.keys(), "broken and unknown keys dropped");
    }

    /** Author's extra (2026-10-14): on by default, a missing field reads as on, and it round-trips either way. */
    @Test
    void hideMouseCursorDefaultsToOnAndRoundTrips() {
        assertTrue(Settings.fromOptions(Map.of()).hideMouseCursor(), "missing: the default, like the other options");
        assertTrue(Settings.fromOptions(Map.of("hideMouseCursor", "true")).hideMouseCursor());
        assertFalse(Settings.fromOptions(Map.of("hideMouseCursor", "false")).hideMouseCursor());

        Settings off = Settings.DEFAULTS.withHideMouseCursor(false);

        assertFalse(off.hideMouseCursor());
        assertEquals(off, Settings.fromOptions(off.toOptions()));
    }

    @Test
    void aKeyChordIsWrittenAsItsCodeWithAnOptionalCtrl() {
        assertEquals("57", KeyChord.of(57).toText());
        assertEquals("ctrl+200", KeyChord.ctrl(200).toText());
        assertEquals(KeyChord.ctrl(200), KeyChord.parse(" CTRL+200 "));
        assertEquals(KeyChord.of(57), KeyChord.parse("57"));
        assertThrows(IllegalArgumentException.class, () -> KeyChord.parse("space"));
        assertThrows(IllegalArgumentException.class, () -> KeyChord.parse("0"));
    }
}
