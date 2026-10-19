package de.latzig.game.tob.client.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jme3.asset.AssetManager;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.audio.AudioContext;
import com.jme3.audio.AudioData;
import com.jme3.audio.AudioKey;
import com.jme3.audio.AudioNode;
import com.jme3.audio.AudioParam;
import com.jme3.audio.AudioRenderer;
import com.jme3.audio.AudioSource;
import com.jme3.audio.AudioSource.Status;
import com.jme3.audio.Environment;
import com.jme3.audio.Filter;
import com.jme3.audio.Listener;
import com.jme3.audio.ListenerParam;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;

import de.latzig.game.tob.client.logic.audio.SoundBank;
import de.latzig.game.tob.client.logic.audio.SoundCue;
import de.latzig.game.tob.client.logic.audio.SoundCues;
import de.latzig.game.tob.client.logic.audio.SoundTestBank;
import de.latzig.game.tob.client.logic.audio.SoundTuning;
import de.latzig.game.tob.client.logic.game.InputMapper;
import de.latzig.game.tob.core.model.ObjectFamily;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.sim.Command;
import de.latzig.game.tob.core.sim.SimEvent;
import de.latzig.game.tob.core.sim.SimOptions;
import de.latzig.game.tob.core.sim.Simulation;
import de.latzig.game.tob.core.sim.TowerAssets;

class SoundViewTest {

    /**
     * An audio renderer that only records: jME's {@code AudioNode} calls the renderer of the thread's
     * {@link AudioContext}, and its status and channel follow jME's {@code ALAudioRenderer}: a fixed number of
     * channels (jME: 64 OpenAL sources); a stopped source that finds no free channel stays stopped ("No channel
     * available"). Nothing ever ends by itself, so the live count is the worst case (fix wave I1).
     */
    private static final class RecordingAudio implements AudioRenderer {

        private final List<AudioSource> played = new ArrayList<>();
        private final AudioSource[] channels;
        /** Every {@code playSource} call per source. */
        private final Map<AudioSource, Integer> playCalls = new IdentityHashMap<>();
        /** The {@code playSource} calls that found no free channel. */
        private int noChannel;

        RecordingAudio() {
            this(64);
        }

        RecordingAudio(int channels) {
            this.channels = new AudioSource[channels];
        }

        /** The sources holding a channel (playing or paused). */
        int live() {
            int live = 0;
            for (AudioSource source : channels) {
                live += source == null ? 0 : 1;
            }
            return live;
        }

        @Override
        public void setListener(Listener listener) {
        }

        @Override
        public void setEnvironment(Environment env) {
        }

        @Override
        public void playSourceInstance(AudioSource src) {
            played.add(src);
        }

        @Override
        public void playSource(AudioSource src) {
            playCalls.merge(src, 1, Integer::sum);
            if (src.getStatus() == Status.Playing) {
                return;
            }
            if (src.getStatus() == Status.Stopped) {
                int free = Arrays.asList(channels).indexOf(null);
                if (free < 0) {
                    noChannel++;
                    return;
                }
                channels[free] = src;
                src.setChannel(free);
                played.add(src);
            }
            src.setStatus(Status.Playing);
        }

        @Override
        public void pauseSource(AudioSource src) {
            src.setStatus(Status.Paused);
        }

        @Override
        public void stopSource(AudioSource src) {
            if (src.getChannel() >= 0 && channels[src.getChannel()] == src) {
                channels[src.getChannel()] = null;
            }
            src.setStatus(Status.Stopped);
            src.setChannel(-1);
        }

        @Override
        public void updateSourceParam(AudioSource src, AudioParam param) {
        }

        @Override
        public void updateListenerParam(Listener listener, ListenerParam param) {
        }

        @Override
        public float getSourcePlaybackTime(AudioSource src) {
            return 0f;
        }

        @Override
        public void deleteFilter(Filter filter) {
        }

        @Override
        public void deleteAudioData(AudioData ad) {
        }

        @Override
        public void initialize() {
        }

        @Override
        public void update(float tpf) {
        }

        @Override
        public void pauseAll() {
        }

        @Override
        public void resumeAll() {
        }

        @Override
        public void cleanup() {
        }
    }

    @AfterEach
    void noAudioRenderer() {
        AudioContext.setAudioRenderer(null);
    }

    /** A 16-bit PCM mono WAV of 100 silent samples at 44100 Hz. */
    private static byte[] silence() {
        ByteBuffer out = ByteBuffer.allocate(44 + 200).order(ByteOrder.LITTLE_ENDIAN);
        out.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(236).put("WAVE".getBytes(StandardCharsets.US_ASCII))
                .put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1).putShort((short) 1)
                .putInt(44100).putInt(88200).putShort((short) 2).putShort((short) 16)
                .put("data".getBytes(StandardCharsets.US_ASCII)).putInt(200);
        return out.array();
    }

    /** An asset manager that finds a silent WAV for every sound of {@link SoundTestBank}. */
    private static AssetManager silentSounds(Path root) throws IOException {
        Files.createDirectories(root.resolve("sounds"));
        for (SoundBank.Sound sound : SoundTestBank.bank().sounds().values()) {
            Files.write(root.resolve(sound.file()), silence());
        }
        AssetManager assets = GeneratedAssets.assetManager();
        assets.registerLocator(root.toString(), FileLocator.class);
        return assets;
    }

    /** Spec §4: without an audio renderer (headless) the view loads nothing and only counts. */
    @Test
    void withoutAnAudioRendererTheViewOnlyCounts() {
        SoundView view = new SoundView(GeneratedAssets.assetManager(), null, null, SoundTestBank.bank());

        view.play(List.of(new SoundCue.OneShot(SoundBank.EXPLOSION, 1f, 2f, 3f),
                new SoundCue.OneShot(SoundBank.EXPLOSION, 1f, 2f, 3f),
                new SoundCue.OneShot(SoundBank.ZAP, 0f, 0f, 0f)));
        view.update(List.of(new SoundCue.Loop("4200-3", SoundBank.WATCHER, 1f, 0f, 1f, 1f)), true, null);

        assertFalse(view.isActive());
        assertEquals(Map.of(SoundBank.EXPLOSION, 2, SoundBank.ZAP, 1), view.played());
        assertEquals(Set.of("4200-3"), view.loopKeys());
        assertTrue(view.isPaused());
        view.stopAll();
        assertEquals(Set.of(), view.loopKeys());
        assertEquals(Set.of("4200-3"), view.everLooped());
    }

    /**
     * Spec decisions 1 and 8: one-shots play where they happen with ToB 2005's distances; loops start, follow their
     * cue, pause and resume with the game while one-shots play out, stop when no longer cued and with
     * {@link SoundView#stopAll}; the listener is the camera.
     */
    @Test
    void oneShotsAndLoopsPlayPauseAndStopLikeTheGame(@TempDir Path root) throws IOException {
        RecordingAudio audio = new RecordingAudio();
        AudioContext.setAudioRenderer(audio);
        Listener listener = new Listener();
        SoundView view = new SoundView(silentSounds(root), audio, listener, SoundTestBank.bank());
        Camera camera = new Camera(640, 480);
        camera.setLocation(new Vector3f(4f, 5f, 6f));

        view.play(List.of(new SoundCue.OneShot(SoundBank.EXPLOSION, 1f, 2f, 3f)));
        AudioNode bang = (AudioNode) audio.played.get(0);
        assertEquals(new Vector3f(1f, 2f, 3f), bang.getPosition());
        assertTrue(bang.isPositional());
        assertEquals(1f, bang.getRefDistance());
        assertEquals(32f, bang.getMaxDistance());
        assertFalse(bang.isLooping());

        view.update(List.of(new SoundCue.Loop("6100-4", SoundBank.TIME_BOMB, 1f, 0f, 1f, 1f)), false, camera);
        AudioNode bomb = view.loop("6100-4");
        assertSame(bomb, audio.played.get(1));
        assertTrue(bomb.isLooping());
        assertEquals(Status.Playing, bomb.getStatus());
        assertEquals(new Vector3f(4f, 5f, 6f), listener.getLocation());

        view.update(List.of(new SoundCue.Loop("6100-4", SoundBank.TIME_BOMB, 2f, 0f, 1f, 1.25f)), false, camera);
        assertEquals(new Vector3f(2f, 0f, 1f), bomb.getPosition(), "the loop follows its cue");
        assertEquals(1.25f, bomb.getPitch());
        assertEquals(2, audio.played.size(), "no new node");

        view.update(List.of(new SoundCue.Loop("6100-4", SoundBank.TIME_BOMB, 2f, 0f, 1f, 1.25f)), true, camera);
        assertEquals(Status.Paused, bomb.getStatus());
        assertEquals(Status.Playing, bang.getStatus(), "one-shots play out");
        view.update(List.of(new SoundCue.Loop("6100-4", SoundBank.TIME_BOMB, 2f, 0f, 1f, 1.25f)), false, camera);
        assertEquals(Status.Playing, bomb.getStatus(), "resumed");

        view.update(List.of(new SoundCue.Loop("noise", SoundBank.NOISE, 0f, 0f, 0f, 1f)), false, camera);
        assertEquals(Status.Stopped, bomb.getStatus(), "no longer cued");
        assertNull(view.loop("6100-4"));
        assertFalse(view.loop("noise").isPositional());

        view.stopAll();
        assertEquals(Status.Stopped, bang.getStatus());
        assertEquals(Status.Stopped, ((AudioNode) audio.played.get(2)).getStatus());
        assertEquals(List.of(), view.oneShots());
    }

    /**
     * Every sound of the generated assets loads with jME's WAV loader at 44100 Hz; every positional one is mono (jME
     * plays only mono sounds positionally).
     */
    @Test
    void everySoundLoadsAndThePositionalOnesAreMono() {
        assumeTrue(GeneratedAssets.available(), "generated assets not available");
        AssetManager manager = GeneratedAssets.assetManager();
        SoundBank bank = new AssetLibrary(manager, GeneratedAssets.root()).soundBank();

        for (SoundBank.Sound sound : bank.sounds().values()) {
            AudioData audio = manager.loadAudio(new AudioKey(sound.file(), false));
            assertNotNull(audio, sound.file());
            assertEquals(44100, audio.getSampleRate(), sound.file());
            if (sound.positional()) {
                assertEquals(1, audio.getChannels(), sound.file());
            }
        }
    }

    /**
     * Phase 5b decision 3: the view leaves the listener's gain to the app (Master, every frame), and the background
     * loops follow the Background option live, on top of their tuned level; other loops keep their own volume.
     */
    @Test
    void theBackgroundLoopsFollowTheBackgroundVolumeLive(@TempDir Path root) throws IOException {
        RecordingAudio audio = new RecordingAudio();
        AudioContext.setAudioRenderer(audio);
        Listener listener = new Listener();
        listener.setVolume(3f);
        SoundView view = new SoundView(silentSounds(root), audio, listener, SoundTestBank.bank());
        Camera camera = new Camera(640, 480);
        List<SoundCue.Loop> loops = List.of(new SoundCue.Loop("background", SoundBank.BREATH, 0f, 0f, 0f, 1f),
                new SoundCue.Loop("6100-4", SoundBank.TIME_BOMB, 1f, 0f, 1f, 1f));

        view.update(loops, false, camera);
        assertEquals(3f, listener.getVolume(), "the view no longer sets the listener's gain");
        assertEquals(SoundTuning.BACKGROUND_VOLUME, view.loop("background").getVolume(), 1e-6f);
        assertEquals(1f, view.loop("6100-4").getVolume(), 1e-6f);

        view.backgroundVolume(2f);
        view.update(loops, false, camera);
        assertEquals(2f * SoundTuning.BACKGROUND_VOLUME, view.loop("background").getVolume(), 1e-6f, "live");
        assertEquals(1f, view.loop("6100-4").getVolume(), 1e-6f);
        assertTrue(SoundBank.isBackground(SoundBank.DRONE_B));
        assertFalse(SoundBank.isBackground(SoundBank.THUNDER));
    }

    /** A view playing into {@code audio} with a silent WAV for every sound below {@code root}. */
    private static SoundView view(RecordingAudio audio, Path root) throws IOException {
        AudioContext.setAudioRenderer(audio);
        return new SoundView(silentSounds(root), audio, new Listener(), SoundTestBank.bank());
    }

    /** The loops {@code keys} (the time bomb's sound, at the origin). */
    private static List<SoundCue.Loop> loops(String... keys) {
        List<SoundCue.Loop> loops = new ArrayList<>();
        for (String key : keys) {
            loops.add(new SoundCue.Loop(key, SoundBank.TIME_BOMB, 0f, 0f, 0f, 1f));
        }
        return loops;
    }

    /**
     * Fix wave I1 (a): an element's own sound restarts its one voice, as ToB 2005's {@code SND_playBuffer} stops and
     * replays the element's buffer ({@code snd.cpp:971-996}); a cue without a key gets a voice of its own.
     */
    @Test
    void anElementsOwnSoundRestartsItsVoiceInsteadOfStacking(@TempDir Path root) throws IOException {
        RecordingAudio audio = new RecordingAudio();
        SoundView view = view(audio, root);

        view.play(List.of(new SoundCue.OneShot(SoundBank.HOPPER, 1f, 0f, 1f, "4000-5")));
        AudioNode hopper = view.oneShots().get(0);
        view.play(List.of(new SoundCue.OneShot(SoundBank.HOPPER, 2f, 0f, 1f, "4000-5")));

        assertEquals(List.of(hopper), view.oneShots(), "one voice per element");
        assertEquals(List.of(hopper, hopper), audio.played, "stopped and played again");
        assertEquals(new Vector3f(2f, 0f, 1f), hopper.getPosition(), "at the element's new place");
        assertEquals(Status.Playing, hopper.getStatus());

        view.play(List.of(new SoundCue.OneShot(SoundBank.HOPPER, 3f, 0f, 1f, "4000-6"),
                new SoundCue.OneShot(SoundBank.LIFT_STOP, 0f, 0f, 0f), new SoundCue.OneShot(SoundBank.LIFT_STOP,
                        0f, 0f, 0f)));
        assertEquals(4, view.oneShots().size(), "another hopper and two unkeyed lift stops: voices of their own");
        assertNotSame(hopper, view.oneShots().get(1));
        assertEquals(Map.of(SoundBank.HOPPER, 3, SoundBank.LIFT_STOP, 2), view.played());
    }

    /** Fix wave I1 (b): at most 32 voices per explosion sound, the oldest stops first ({@code TWR_EXPLOSION_MAX}). */
    @Test
    void anExplosionSoundKeepsAtMostThirtyTwoVoices(@TempDir Path root) throws IOException {
        RecordingAudio audio = new RecordingAudio();
        SoundView view = view(audio, root);
        List<SoundCue.OneShot> blasts = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            blasts.add(new SoundCue.OneShot(SoundBank.EXPLOSION, i, 0f, 0f));
        }

        view.play(blasts);
        view.play(List.of(new SoundCue.OneShot(SoundBank.EXPLOSION_SHORT, 0f, 0f, 0f)));

        assertEquals(SoundTuning.EXPLOSION_VOICES + 1, audio.live());
        assertEquals(Status.Stopped, ((AudioNode) audio.played.get(7)).getStatus(), "the oldest stopped");
        assertEquals(Status.Playing, ((AudioNode) audio.played.get(8)).getStatus());
        assertEquals(0, audio.noChannel);
    }

    /**
     * Fix wave I1 (c): one-shots and loops together stay within {@link SoundTuning#MAX_VOICES} (56 of jME's 64
     * sources): a new one-shot stops the oldest one-shot; a new loop does too, loops come first.
     */
    @Test
    void oneShotsAndLoopsStayWithinTheVoiceCapLoopsFirst(@TempDir Path root) throws IOException {
        RecordingAudio audio = new RecordingAudio();
        SoundView view = view(audio, root);
        Camera camera = new Camera(640, 480);
        view.update(loops("a", "b", "c"), false, camera);
        List<SoundCue.OneShot> stops = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            stops.add(new SoundCue.OneShot(SoundBank.LIFT_STOP, i, 0f, 0f));
        }

        view.play(stops);
        assertEquals(SoundTuning.MAX_VOICES, audio.live());
        assertEquals(SoundTuning.MAX_VOICES - 3, view.oneShots().size());
        assertEquals(Status.Stopped, ((AudioNode) audio.played.get(3)).getStatus(), "the oldest one-shot stopped");

        view.update(loops("a", "b", "c", "d"), false, camera);
        assertEquals(Status.Playing, view.loop("d").getStatus(), "a new loop gets a voice");
        assertEquals(SoundTuning.MAX_VOICES, audio.live());
        assertEquals(SoundTuning.MAX_VOICES - 4, view.oneShots().size());
        assertEquals(0, audio.noChannel);
    }

    /**
     * Fix wave I1 (d): a loop that finds no free channel (jME: "No channel available", logged per attempt) is not
     * retried every frame but every {@link SoundTuning#LOOP_RETRY_FRAMES} frames.
     */
    @Test
    void aLoopWithoutAFreeChannelIsRetriedOnlyEveryThirtyFrames(@TempDir Path root) throws IOException {
        RecordingAudio audio = new RecordingAudio(2);
        SoundView view = view(audio, root);
        Camera camera = new Camera(640, 480);

        view.update(loops("a", "b", "c"), false, camera);
        AudioNode c = view.loop("c");
        assertEquals(Status.Stopped, c.getStatus(), "two channels only");
        for (int frame = 1; frame < SoundTuning.LOOP_RETRY_FRAMES; frame++) {
            view.update(loops("a", "b", "c"), false, camera);
        }
        assertEquals(1, audio.playCalls.get(c), "not retried every frame");
        view.update(loops("b", "c"), false, camera);
        assertEquals(2, audio.playCalls.get(c), "retried after " + SoundTuning.LOOP_RETRY_FRAMES + " frames");
        assertEquals(Status.Playing, c.getStatus(), "a's channel is free now");
    }

    /**
     * Fix wave (Minor 7): {@link SoundView#preload} loads every WAV at the tower's start, so the first explosion or
     * thunder does not load in the render thread; a missing WAV is a missing asset ({@link AssetLoadException},
     * the tower start's "Missing or unreadable asset" message and the picker); without an audio renderer nothing
     * is loaded.
     */
    @Test
    void preloadLoadsEveryWavAtTowerStartAndNamesAMissingOne(@TempDir Path root, @TempDir Path broken)
            throws IOException {
        RecordingAudio audio = new RecordingAudio();
        SoundView view = view(audio, root);
        view.preload();
        try (var files = Files.list(root.resolve("sounds"))) {
            for (Path file : files.toList()) {
                Files.delete(file);
            }
        }
        view.play(List.of(new SoundCue.OneShot(SoundBank.THUNDER, 0f, 0f, 0f)));
        view.update(loops("a"), false, new Camera(640, 480));
        assertEquals(Status.Playing, view.oneShots().get(0).getStatus(), "loaded at the start");
        assertEquals(Status.Playing, view.loop("a").getStatus());

        SoundView missing = view(audio, broken);
        Files.delete(broken.resolve("sounds/9900-thunder.wav"));
        AssetLoadException e = assertThrows(AssetLoadException.class, missing::preload);
        assertEquals("sounds/9900-thunder.wav", e.file());

        new SoundView(GeneratedAssets.assetManager(), null, null, SoundTestBank.bank()).preload();
    }

    /**
     * Fix wave I1, the review's case: fast-forward on {@code original-044} (14 hoppers) plays one simulation step per
     * frame. With nothing ever ending (the recording renderer), the voices stay within the cap, jME never runs out
     * of channels, and each hopper sounds on one voice.
     */
    @Test
    void fastForwardOnTheHopperTowerStaysWithinTheVoiceCap(@TempDir Path root) throws IOException {
        assumeTrue(TowerAssets.available(), "tower assets not generated");
        TowerDefinition tower = TowerAssets.allTowers().stream().filter(t -> t.id().equals("original-044"))
                .findFirst().orElseThrow();
        RecordingAudio audio = new RecordingAudio();
        SoundView view = view(audio, root);
        Camera camera = new Camera(640, 480);
        Simulation sim = Simulation.start(tower, SimOptions.DEFAULT);
        SoundCues cues = new SoundCues(sim.world(), SoundTestBank.bank(), tower.sounds(), SoundTuning.DETAIL_HIGH,
                tower.id().hashCode());
        long hoppers = sim.world().objects().stream().filter(o -> o.type().family() == ObjectFamily.HOPPER).count();
        assertTrue(hoppers >= 10, "original-044's hoppers: " + hoppers);

        int most = 0;
        List<Command> start = List.of(new Command.Fire(InputMapper.initialSpider(sim.world())));
        for (int frame = 0; frame < 400 && !sim.isFinished(); frame++) {
            List<SimEvent> events = sim.step(frame == 0 ? start : List.of());
            view.play(cues.onStep(events));
            view.update(cues.loops(0f, false), false, camera);
            most = Math.max(most, audio.live());
        }
        long hopperVoices = audio.played.stream().filter(s -> ((AudioNode) s).getAudioKey().getName()
                .endsWith("/" + SoundBank.HOPPER + ".wav")).distinct().count();
        System.out.println("SoundViewTest: original-044 fast-forward, most voices " + most + ", sounds "
                + view.played() + ", hopper voices " + hopperVoices);

        assertTrue(most <= SoundTuning.MAX_VOICES, "at most " + SoundTuning.MAX_VOICES + ": " + most);
        assertEquals(0, audio.noChannel, "jME never runs out of channels");
        assertTrue(view.played().getOrDefault(SoundBank.HOPPER, 0) > hoppers, "the hoppers hop: " + view.played());
        assertTrue(hopperVoices <= hoppers, "one voice per hopper: " + hopperVoices);
    }
}
