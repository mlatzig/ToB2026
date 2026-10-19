package de.latzig.game.tob.client;

import java.nio.ByteBuffer;

import org.lwjgl.openal.ALC10;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jme3.system.AppSettings;

/**
 * Phase 7c (author's request): without an audio device the game starts without sound. jME 3.9's
 * {@code LwjglALC.createALC} does not check {@code alcOpenDevice}'s result, so with no device the start fails with
 * a {@code NullPointerException} in {@code alcCreateContext}. Before the start, {@link #available} therefore opens
 * and closes the default device once; without one, {@link #apply} sets no audio renderer, as the headless tests do:
 * jME then makes no audio renderer and listener, and {@code SoundView} and {@code MusicPlayer} load and play
 * nothing.
 */
public final class AudioDevice {

    private static final Logger LOG = LoggerFactory.getLogger(AudioDevice.class);

    /** Logged once when the game starts without sound. */
    public static final String NO_DEVICE = "No audio device - sound and music off";

    /** Opens and closes an audio device (a seam: the tests simulate a missing or failing device). */
    public interface Probe {
        /** Opens the default device; 0 if there is none. */
        long open();

        void close(long device);
    }

    /** The OpenAL probe through LWJGL (the same call jME makes). */
    public static final Probe OPENAL = new Probe() {
        @Override
        public long open() {
            return ALC10.alcOpenDevice((ByteBuffer) null);
        }

        @Override
        public void close(long device) {
            ALC10.alcCloseDevice(device);
        }
    };

    private AudioDevice() {
    }

    /**
     * Whether {@code probe} finds a device: it opens one (and closes it again). No device, or a failure such as
     * missing OpenAL natives, means no - also a device that opens but cannot be closed again: OpenAL is then in a
     * state jME's own setup should not rely on, and a silent start beats a crash.
     */
    public static boolean available(Probe probe) {
        try {
            long device = probe.open();
            if (device == 0L) {
                return false;
            }
            probe.close(device);
            return true;
        } catch (RuntimeException | LinkageError e) {
            LOG.warn("Cannot open an audio device: {}", e.toString());
            return false;
        }
    }

    /**
     * Applies the probe's answer to the start settings: without a device no audio renderer (logged as
     * {@value #NO_DEVICE}); with one the settings stay as they are.
     *
     * @return whether the game has sound
     */
    public static boolean apply(AppSettings settings, boolean available) {
        if (!available) {
            settings.setAudioRenderer(null);
            LOG.info(NO_DEVICE);
        }
        return available;
    }
}
