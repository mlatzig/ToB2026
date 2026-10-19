package de.latzig.game.tob.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeContext;

import de.latzig.game.tob.client.logic.Texts;
import de.latzig.game.tob.client.logic.frontend.GameFlow;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Hit;
import de.latzig.game.tob.client.logic.frontend.OptionsModel;

/**
 * Phase 7c (author's request), headless: a start whose audio probe finds no device runs without an audio renderer,
 * reaches the main menu and the Options window, whose SOUND rows are disabled with the hint, and plays nothing.
 * <p>
 * Its limit (fix wave, Task 3-4 review #7): a headless jME start never makes an audio renderer, with or without a
 * device, so this test proves the path from the probe's answer to the Options window and the settings, not jME's
 * audio setup. That {@code AudioDevice.apply} removes the renderer from the start settings is
 * {@link AudioDeviceTest}'s part; the real start without a device was checked by hand in WSL (Phase 7c results).
 */
class NoAudioSmokeTest {

    private static final int UNTHROTTLED = Integer.MAX_VALUE;

    /** Main menu, then Options; records what the Options window shows and that a SOUND change is ignored. */
    private static final class Driver extends BaseAppState {

        final CountDownLatch done = new CountDownLatch(1);
        volatile boolean soundAvailable = true;
        volatile Optional<String> hint = Optional.empty();
        volatile int masterAfterChange = -1;
        volatile boolean musicAfterChange;

        @Override
        public void update(float tpf) {
            TobApp app = (TobApp) getApplication();
            GameFlow flow = app.flow();
            if (flow == null || done.getCount() == 0 || flow.fadeAlpha() > 0f) {
                return;
            }
            if (flow.screen() == GameFlow.Screen.MENU) {
                flow.click(Hit.of(GameFlow.Widget.MENU_OPTIONS));
            } else if (flow.screen() == GameFlow.Screen.OPTIONS) {
                OptionsModel options = flow.options();
                soundAvailable = options.soundAvailable();
                hint = options.hint(Texts.english());
                options.change(OptionsModel.Option.MASTER_VOLUME, 1);
                options.change(OptionsModel.Option.MUSIC, 1);
                masterAfterChange = app.settings().current().masterVolume();
                musicAfterChange = app.settings().current().music();
                done.countDown();
            }
        }

        @Override
        protected void initialize(Application app) {
        }

        @Override
        protected void cleanup(Application app) {
        }

        @Override
        protected void onEnable() {
        }

        @Override
        protected void onDisable() {
        }
    }

    @Test
    void withoutAnAudioDeviceTheGameStartsSilentlyAndOptionsSaysSo(@TempDir Path userDir)
            throws InterruptedException {
        Path assets = Path.of(System.getProperty(AppConfig.ASSETS_PROPERTY, AppConfig.DEFAULT_ASSETS));
        assumeTrue(Files.isRegularFile(assets.resolve("towers/original.json")), "generated assets not available");
        Logger.getLogger("com.jme3").setLevel(Level.SEVERE);
        Driver driver = new Driver();
        TobApp app = new TobApp(new AppConfig(assets, Optional.empty(), true, userDir), driver);
        AppSettings settings = new AppSettings(true);
        settings.setFrameRate(UNTHROTTLED);
        // as TobApp.main does when the probe finds no device (simulated: alcOpenDevice returns 0)
        boolean sound = AudioDevice.apply(settings, AudioDevice.available(new AudioDevice.Probe() {
            @Override
            public long open() {
                return 0L;
            }

            @Override
            public void close(long device) {
                throw new AssertionError("no device to close");
            }
        }));
        if (!sound) {
            app.noAudioDevice();
        }
        app.setSettings(settings);
        app.setShowSettings(false);

        app.start(JmeContext.Type.Headless);
        boolean finished = driver.done.await(60, TimeUnit.SECONDS);
        app.stop(true);

        assertTrue(finished, "main menu and Options within 60 s");
        assertEquals(List.of(), app.errors());
        assertFalse(sound);
        assertNull(settings.getAudioRenderer());
        assertNull(app.getAudioRenderer(), "no audio renderer, so no sound and no music is loaded or played");
        assertFalse(driver.soundAvailable);
        assertEquals(Optional.of("No audio device found"), driver.hint);
        assertEquals(100, driver.masterAfterChange, "Master ignores changes");
        assertTrue(driver.musicAfterChange, "the Music switch ignores changes");
    }
}
