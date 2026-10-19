package de.latzig.game.tob.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.jme3.system.AppSettings;

import de.latzig.game.tob.client.logic.frontend.Settings;
import de.latzig.game.tob.client.logic.game.RecordingSwitch;

/**
 * Phase 5b decision 2: the display mode is chosen once, at the start, from the saved options; Phase 7c: the
 * developer recording's switch of a packaged start.
 */
class TobAppSettingsTest {

    @Test
    void fullscreenOpensAtTheDesktopsVideoMode() {
        AppSettings settings = TobApp.appSettings(Settings.DEFAULTS, 144);

        assertTrue(settings.isFullscreen(), "the default");
        assertEquals(0, settings.getWindowWidth(), "0 x 0: jME takes the monitor's video mode");
        assertEquals(0, settings.getWindowHeight());
        assertEquals(144, settings.getFrequency(), "the desktop's refresh rate: no video mode switch");
        assertEquals(-1, TobApp.appSettings(Settings.DEFAULTS, 0).getFrequency(), "unknown: GLFW chooses");
        assertEquals("ToB 2026", settings.getTitle());
        assertEquals(4, settings.getSamples());
        assertTrue(settings.isVSync());
    }

    @Test
    void windowModeKeepsTheResizable1280x720Window() {
        AppSettings settings = TobApp.appSettings(Settings.DEFAULTS.withFullscreen(false), 144);

        assertFalse(settings.isFullscreen());
        assertEquals(TobApp.WINDOW_WIDTH, settings.getWindowWidth());
        assertEquals(TobApp.WINDOW_HEIGHT, settings.getWindowHeight());
        assertEquals(1280, settings.getWidth());
        assertEquals(720, settings.getHeight());
        assertTrue(settings.isResizable());
    }

    /**
     * Phase 7c, fix wave (Task 1-2 review #1): a start from a package ({@code jpackage.app-path} set) locks the
     * developer recording off, whatever {@code -Dtob.record} says; a development start keeps the normal switch.
     */
    @Test
    void aPackagedStartLocksTheDeveloperRecordingOff() {
        RecordingSwitch packaged = TobApp.recordingSwitch("C:\\Games\\ToB2026\\ToB2026.exe", true);
        assertFalse(packaged.isOn(), "-Dtob.record is ignored");
        packaged.toggle();
        assertFalse(packaged.isOn(), "F12 does nothing");

        RecordingSwitch development = TobApp.recordingSwitch(null, true);
        assertTrue(development.isOn(), "-Dtob.record=true");
        development.toggle();
        assertFalse(development.isOn(), "F12 switches it");
        assertFalse(TobApp.recordingSwitch(null, false).isOn(), "off by default");
        assertTrue(TobApp.recordingSwitch(" ", true).isOn(), "a blank launcher path is no package");
    }
}
