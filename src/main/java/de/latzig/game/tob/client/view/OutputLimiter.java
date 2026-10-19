package de.latzig.game.tob.client.view;

import java.util.Optional;

import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.SOFTHRTF;
import org.lwjgl.openal.SOFTOutputLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenAL Soft's output limiter (Phase 5b §4, the clipping check): at Master 200 % or the developer's
 * {@code -Dtob.volume=4} a single explosion next to the listener exceeds full scale ({@code SoundLevelsTest}). OpenAL
 * Soft limits its output by default only for integer sample types, not for the float32 output it uses on Windows, so
 * the app asks for the limiter explicitly once jME has opened the device: {@code alcResetDeviceSOFT} with
 * {@code ALC_OUTPUT_LIMITER_SOFT = ALC_TRUE} (extension {@value #EXTENSION}; the reset keeps the context and its
 * sources). Called on the render thread, where jME's OpenAL context is current; never without an audio renderer.
 */
public final class OutputLimiter {

    private static final Logger LOG = LoggerFactory.getLogger(OutputLimiter.class);
    public static final String EXTENSION = "ALC_SOFT_output_limiter";
    private static final String RESET_EXTENSION = "ALC_SOFT_HRTF";

    private OutputLimiter() {
    }

    /** The device attributes that switch the limiter on, zero-terminated. */
    public static int[] attributes() {
        return new int[] {SOFTOutputLimiter.ALC_OUTPUT_LIMITER_SOFT, ALC10.ALC_TRUE, 0};
    }

    /**
     * Switches the limiter on for the current OpenAL context's device.
     *
     * @return the limiter's state as the device reports it afterwards; empty if there is no OpenAL context or the
     *         device lacks the extensions
     */
    public static Optional<Boolean> enable() {
        long context = ALC10.alcGetCurrentContext();
        if (context == 0L) {
            return Optional.empty();
        }
        long device = ALC10.alcGetContextsDevice(context);
        if (device == 0L || !ALC10.alcIsExtensionPresent(device, EXTENSION)
                || !ALC10.alcIsExtensionPresent(device, RESET_EXTENSION)) {
            return Optional.empty();
        }
        // Task 14 minor (final review): a failed reset must not pass silently as "the limiter is off".
        if (!SOFTHRTF.alcResetDeviceSOFT(device, attributes())) {
            LOG.warn("alcResetDeviceSOFT failed for the output limiter attributes (ALC error {})",
                    ALC10.alcGetError(device));
        }
        return Optional.of(ALC10.alcGetInteger(device, SOFTOutputLimiter.ALC_OUTPUT_LIMITER_SOFT) == ALC10.ALC_TRUE);
    }
}
