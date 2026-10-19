package de.latzig.game.tob.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.jme3.system.AppSettings;

/**
 * Phase 7c (author's request): no audio device - no crash. The probe is simulated; no test opens a real device.
 */
class AudioDeviceTest {

    /** A probe that returns {@code device} and records what it closes. */
    private static AudioDevice.Probe probe(long device, List<Long> closed) {
        return new AudioDevice.Probe() {
            @Override
            public long open() {
                return device;
            }

            @Override
            public void close(long opened) {
                closed.add(opened);
            }
        };
    }

    /** A probe whose open fails with {@code failure}. */
    private static AudioDevice.Probe failing(Throwable failure) {
        return new AudioDevice.Probe() {
            @Override
            public long open() {
                if (failure instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw (Error) failure;
            }

            @Override
            public void close(long device) {
                throw new AssertionError("nothing to close");
            }
        };
    }

    @Test
    void aDeviceIsFoundAndClosedAgain() {
        List<Long> closed = new ArrayList<>();
        assertTrue(AudioDevice.available(probe(42L, closed)));
        assertEquals(List.of(42L), closed, "jME opens it again itself");
    }

    @Test
    void noDeviceOrAFailingOpenalMeanNoSound() {
        List<Long> closed = new ArrayList<>();
        assertFalse(AudioDevice.available(probe(0L, closed)), "alcOpenDevice found no device");
        assertEquals(List.of(), closed);
        assertFalse(AudioDevice.available(failing(new UnsatisfiedLinkError("no openal"))), "natives missing");
        assertFalse(AudioDevice.available(failing(new IllegalStateException("broken"))));
    }

    /**
     * Fix wave (Task 3-4 review #7): a device that opens but fails to close counts as no device - OpenAL is in a
     * state jME should not rely on, and the game rather starts silently than crash in its own audio setup.
     */
    @Test
    void aDeviceThatCannotBeClosedMeansNoSound() {
        AudioDevice.Probe unclosable = new AudioDevice.Probe() {
            @Override
            public long open() {
                return 42L;
            }

            @Override
            public void close(long device) {
                throw new IllegalStateException("alcCloseDevice failed");
            }
        };
        assertFalse(AudioDevice.available(unclosable));
    }

    @Test
    void withoutADeviceTheGameStartsWithoutAnAudioRenderer() {
        AppSettings settings = new AppSettings(true);
        String renderer = settings.getAudioRenderer();
        assertEquals(AppSettings.LWJGL_OPENAL, renderer, "jME's default");

        assertTrue(AudioDevice.apply(settings, true));
        assertEquals(renderer, settings.getAudioRenderer(), "with a device nothing changes");

        assertFalse(AudioDevice.apply(settings, false));
        assertNull(settings.getAudioRenderer(), "no device: no renderer, no listener, no sounds, no music");
    }
}
