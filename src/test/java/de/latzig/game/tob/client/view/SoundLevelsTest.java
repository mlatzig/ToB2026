package de.latzig.game.tob.client.view;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.TreeMap;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.sound.sampled.spi.AudioFileReader;

import org.junit.jupiter.api.Test;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.SOFTOutputLimiter;

import de.latzig.game.tob.client.logic.audio.SoundBank;
import de.latzig.game.tob.client.logic.audio.SoundTuning;
import de.latzig.game.tob.client.logic.frontend.Settings;

/**
 * Phase 5b §4, the clipping check by measurement: the sample peaks of the generated sound files at their bank
 * volumes, and the true peak of the loudest scene (a lightning strike next to the listener over the loudest
 * background loop), at 100 %, 200 % (Master's top) and 400 % (the hidden {@code -Dtob.volume} override's top).
 * The numbers are printed for the results note; the assertions pin the facts the limiter decision rests on.
 */
class SoundLevelsTest {

    /**
     * {@code wav} opened by the JDK's own audio file readers. Phase 5c-2: {@code j-ogg-vorbis} (with
     * {@code jme3-jogg}) registers a reader too, which {@code AudioSystem} would offer every file first and which
     * prints a complaint for every WAV.
     */
    static AudioInputStream open(Path wav) throws IOException, UnsupportedAudioFileException {
        for (AudioFileReader reader : ServiceLoader.load(AudioFileReader.class)) {
            if (!reader.getClass().getName().startsWith("de.jarnbjo.")) {
                try {
                    return reader.getAudioInputStream(wav.toFile());
                } catch (UnsupportedAudioFileException e) {
                    // not this reader's format: the next one
                }
            }
        }
        throw new UnsupportedAudioFileException("no JDK reader for " + wav);
    }

    /** The samples of a PCM WAV as -1..1 (8-bit unsigned or 16-bit signed little-endian; stereo channels mixed). */
    static float[] samples(Path wav) throws IOException {
        try (AudioInputStream in = open(wav)) {
            AudioFormat format = in.getFormat();
            byte[] bytes = in.readAllBytes();
            int bits = format.getSampleSizeInBits();
            int channels = format.getChannels();
            int frameBytes = bits / 8 * channels;
            float[] out = new float[bytes.length / frameBytes];
            for (int frame = 0; frame < out.length; frame++) {
                float sum = 0f;
                for (int c = 0; c < channels; c++) {
                    int at = frame * frameBytes + c * bits / 8;
                    sum += bits == 8 ? ((bytes[at] & 0xFF) - 128) / 128f
                            : (short) ((bytes[at] & 0xFF) | (bytes[at + 1] << 8)) / 32768f;
                }
                out[frame] = sum / channels;
            }
            return out;
        } catch (UnsupportedAudioFileException e) {
            throw new IOException(wav + ": " + e.getMessage(), e);
        }
    }

    static float peak(float[] samples) {
        float peak = 0f;
        for (float sample : samples) {
            peak = Math.max(peak, Math.abs(sample));
        }
        return peak;
    }

    /** The true peak of {@code sounds} started together, each scaled by its volume. */
    static float mixPeak(Map<float[], Float> sounds) {
        int length = 0;
        for (float[] samples : sounds.keySet()) {
            length = Math.max(length, samples.length);
        }
        float peak = 0f;
        for (int i = 0; i < length; i++) {
            float sum = 0f;
            for (Map.Entry<float[], Float> sound : sounds.entrySet()) {
                float[] samples = sound.getKey();
                sum += i < samples.length ? samples[i] * sound.getValue() : 0f;
            }
            peak = Math.max(peak, Math.abs(sum));
        }
        return peak;
    }

    @Test
    void aSingleLoudEffectAlreadyClipsAt200PercentSoTheMixNeedsALimiter() throws IOException {
        assumeTrue(GeneratedAssets.available(), "generated assets not available");
        SoundBank bank = new AssetLibrary(GeneratedAssets.assetManager(), GeneratedAssets.root()).soundBank();
        float master = Settings.VOLUME_MAX / 100f;
        float override = Settings.VOLUME_OVERRIDE_MAX;
        Map<String, Float> levels = new TreeMap<>();
        for (SoundBank.Sound sound : bank.sounds().values()) {
            Path file = GeneratedAssets.root().resolve(sound.file());
            assertTrue(Files.isRegularFile(file), sound.file());
            levels.put(sound.id(), peak(samples(file)) * sound.volume());
        }
        StringBuilder table = new StringBuilder("SoundLevelsTest: peak x volume at 100 % / 200 % / 400 %\n");
        levels.forEach((id, level) -> table.append(String.format(Locale.ROOT, "  %-5s %5.2f %5.2f %5.2f%n", id,
                level, level * master, level * override)));

        Path sounds = GeneratedAssets.root().resolve("sounds");
        Map<float[], Float> strike = new java.util.IdentityHashMap<>();
        strike.put(samples(sounds.resolve("0902-explosion.wav")), 1f);
        strike.put(samples(sounds.resolve("9900-thunder.wav")), SoundTuning.THUNDER_VOLUME);
        strike.put(samples(sounds.resolve("9902-drone-a.wav")), SoundTuning.BACKGROUND_VOLUME * 2f);
        float scene = mixPeak(strike);
        table.append(String.format(Locale.ROOT, "  strike scene (0902 + thunder + drone A at Background 200 %%):"
                + " %5.2f %5.2f %5.2f%n", scene, scene * master, scene * override));
        System.out.print(table);

        float loudest = levels.values().stream().max(Float::compare).orElseThrow();
        assertTrue(levels.get(SoundBank.EXPLOSION_BIG) * master > 1f, "one big explosion clips at 200 %: " + table);
        assertTrue(loudest * master > 1f && loudest * override > 1f, "the loudest effect clips at 200 and 400 %");
        assertTrue(scene > loudest * 0.9f, "the scene is at least as loud as its loudest part: " + scene);
        assertTrue(levels.get(SoundBank.DRONE_A) < 0.1f, "the background loops are quiet: " + levels.get("9902"));
    }

    /** The limiter request: {@code ALC_OUTPUT_LIMITER_SOFT = ALC_TRUE}, zero-terminated (no OpenAL call here). */
    @Test
    void theLimiterAsksOpenAlSoftForItsOutputLimiter() {
        assertArrayEquals(new int[] {SOFTOutputLimiter.ALC_OUTPUT_LIMITER_SOFT, ALC10.ALC_TRUE, 0},
                OutputLimiter.attributes());
        assertEquals("ALC_SOFT_output_limiter", OutputLimiter.EXTENSION);
    }
}
