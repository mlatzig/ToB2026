package de.latzig.game.tob.client.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.jme3.asset.AssetManager;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.audio.AudioContext;
import com.jme3.audio.AudioData;
import com.jme3.audio.AudioKey;
import com.jme3.audio.AudioParam;
import com.jme3.audio.AudioRenderer;
import com.jme3.audio.AudioSource;
import com.jme3.audio.AudioSource.Status;
import com.jme3.audio.AudioStream;
import com.jme3.audio.Environment;
import com.jme3.audio.Filter;
import com.jme3.audio.Listener;
import com.jme3.audio.ListenerParam;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.latzig.game.tob.client.logic.frontend.MusicChoice;
import de.latzig.game.tob.client.logic.frontend.MusicChoice.Song;

/**
 * Phase 5c-2 §3: the music player without an audio device, the three songs as jME reads them, and (fix wave) the
 * player on a fake audio renderer that behaves as jME 3.9's {@code ALAudioRenderer} on the paths the player uses:
 * ended, missing, fade-in, audio disabled, and an audio device change (headphones unplugged).
 */
class MusicPlayerTest {

    private final ListAppender<ILoggingEvent> log = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(MusicPlayer.class);

    @BeforeEach
    void captureTheLog() {
        log.start();
        logger.addAppender(log);
    }

    @AfterEach
    void releaseTheLogAndTheRenderer() {
        logger.detachAppender(log);
        AudioContext.setAudioRenderer(null);
    }

    private List<String> lines() {
        return log.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private long count(String part) {
        return lines().stream().filter(line -> line.contains(part)).count();
    }

    /** An asset manager that finds the songs (the test is skipped without them). */
    private static AssetManager songs() {
        assumeTrue(Files.isRegularFile(GeneratedAssets.root().resolve(Song.DREAMLAND.file())),
                "the songs are not in the assets");
        AssetManager assets = GeneratedAssets.assetManager();
        assets.registerLocator(GeneratedAssets.root().toAbsolutePath().toString(), FileLocator.class);
        return assets;
    }

    private static FakeRenderer renderer(boolean efx, boolean disabled) {
        FakeRenderer renderer = new FakeRenderer(efx, disabled);
        AudioContext.setAudioRenderer(renderer);
        return renderer;
    }

    @Test
    void withoutAnAudioDeviceItFollowsTheChoiceAndPlaysNothing() {
        MusicPlayer player = new MusicPlayer(GeneratedAssets.assetManager(), null);

        assertFalse(player.isActive());
        player.update(Optional.of(Song.DREAMLAND));
        player.update(Optional.of(Song.DREAMLAND));
        assertEquals(Optional.of(Song.DREAMLAND), player.song());
        assertEquals(1, player.changes(), "the same song keeps playing");
        player.update(Optional.empty());
        player.update(Optional.of(Song.FADEOUT));
        assertEquals(Optional.of(Song.FADEOUT), player.song());
        assertEquals(3, player.changes(), "stop, then the result song");
        assertEquals(1f, player.gain(), 1e-6f, "started without a fade: full volume");
        player.update(Optional.of(Song.FADEOUT), new MusicChoice.Fade(true, Optional.empty(), 0.25f));
        assertEquals(0.75f, player.gain(), 1e-6f, "question 3: out with the screen");
        assertEquals(3, player.changes());
        player.deviceChanged();
        assertEquals(3, player.changes(), "nothing plays, nothing to restart");
    }

    /** The jme3-jogg loader reads every song as a seekable stream (looping needs it) of the converted length. */
    @Test
    void theThreeSongsAreSeekableStereoOggStreamsOfTheirLength() {
        AssetManager assets = songs();
        Map<Song, Float> seconds = Map.of(Song.DREAMLAND, 246f, Song.IMPROVI, 291f, Song.FADEOUT, 113f);

        for (Song song : Song.values()) {
            AudioData data = assets.loadAudio(new AudioKey(song.file(), true, true));

            AudioStream stream = assertInstanceOf(AudioStream.class, data, song.name());
            assertTrue(stream.isSeekable(), song + ": seekable");
            assertEquals(2, stream.getChannels(), song + ": stereo");
            assertEquals(44100, stream.getSampleRate(), song.name());
            assertEquals(seconds.get(song), stream.getDuration(), 1.5f, song + ": its length");
            stream.close();
        }
    }

    /**
     * Fix wave item 1: the headphones are unplugged while Dreamland plays. jME rebuilds the OpenAL context but leaves
     * the node "Playing" on a channel and with stream buffers of the destroyed context. The player notices it in the
     * next frame (its marker filter has lost its OpenAL id), plays the song again on fresh buffers, never touches the
     * old channel, and logs it. The app's context watch calling {@link MusicPlayer#deviceChanged} afterwards changes
     * nothing.
     */
    @Test
    void afterAnAudioDeviceChangeTheSongPlaysAgainOnFreshBuffers() {
        FakeRenderer renderer = renderer(true, false);
        MusicPlayer player = new MusicPlayer(songs(), renderer);
        player.update(Optional.of(Song.DREAMLAND));
        assertEquals(1, renderer.played.size());
        AudioSource node = renderer.played.get(0);

        renderer.restart();
        player.update(Optional.of(Song.DREAMLAND));

        assertEquals(List.of(node, node), renderer.played, "played again");
        assertEquals(List.of(), renderer.stopped, "the old channel is not stopped (it may be another sound's now)");
        assertEquals(List.of(), renderer.errors);
        assertEquals(Status.Playing, node.getStatus());
        assertEquals(1, count("Music: DREAMLAND played again after an audio device change"), lines().toString());
        assertEquals(1, player.changes(), "the same song, no change");

        player.update(Optional.of(Song.DREAMLAND));
        player.deviceChanged();
        player.update(Optional.of(Song.DREAMLAND), new MusicChoice.Fade(true, Optional.empty(), 0.5f));
        assertEquals(2, renderer.played.size(), "once per device change");
        assertEquals(List.of(), renderer.errors);
        assertEquals(0.5f, node.getVolume(), 1e-6f);
    }

    /** Item 1 without EFX (no marker): the app's context watch is the trigger. */
    @Test
    void withoutEfxTheAppsContextWatchPlaysTheSongAgain() {
        FakeRenderer renderer = renderer(false, false);
        MusicPlayer player = new MusicPlayer(songs(), renderer);
        player.update(Optional.of(Song.FADEOUT));
        renderer.restart();
        player.update(Optional.of(Song.FADEOUT));
        assertEquals(1, renderer.played.size(), "nothing to notice without a marker");

        player.deviceChanged();
        player.update(Optional.of(Song.FADEOUT));

        assertEquals(2, renderer.played.size(), "played again");
        assertEquals(List.of(), renderer.stopped);
        assertEquals(List.of(), renderer.errors);
        assertEquals(1, count("Music: FADEOUT played again after an audio device change"), lines().toString());
    }

    /**
     * Item 1 (re-review Minor 1): the rebuilt context has no EFX, so the marker gets no new id; the song is played
     * again once, not in every frame.
     */
    @Test
    void aRebuiltContextWithoutEfxPlaysTheSongAgainOnlyOnce() {
        FakeRenderer renderer = renderer(true, false);
        MusicPlayer player = new MusicPlayer(songs(), renderer);
        player.update(Optional.of(Song.DREAMLAND));
        renderer.efx(false);
        renderer.restart();

        player.update(Optional.of(Song.DREAMLAND));
        player.update(Optional.of(Song.DREAMLAND));
        player.update(Optional.of(Song.DREAMLAND));

        assertEquals(2, renderer.played.size(), "played again once");
        assertEquals(List.of(), renderer.errors);
        assertEquals(1, count("played again after an audio device change"), lines().toString());
    }

    /** Item 1: a song stopped before the device change gets fresh buffers when it starts again (back to the menus). */
    @Test
    void aSongStoppedBeforeADeviceChangeStartsLaterOnFreshBuffers() {
        FakeRenderer renderer = renderer(true, false);
        MusicPlayer player = new MusicPlayer(songs(), renderer);
        player.update(Optional.of(Song.DREAMLAND));
        player.update(Optional.empty());
        renderer.restart();

        player.update(Optional.empty());
        player.update(Optional.of(Song.DREAMLAND));

        assertEquals(2, renderer.played.size());
        assertEquals(Status.Playing, renderer.played.get(1).getStatus());
        assertEquals(List.of(), renderer.errors);
    }

    /** Item 4 (T7 M3): a result song that has ended is logged once and not started again while its window stays. */
    @Test
    void aResultSongThatEndedIsNotStartedAgain() {
        FakeRenderer renderer = renderer(true, false);
        MusicPlayer player = new MusicPlayer(songs(), renderer);
        player.update(Optional.of(Song.IMPROVI));
        renderer.finish(renderer.played.get(0));

        player.update(Optional.of(Song.IMPROVI));
        player.update(Optional.of(Song.IMPROVI));

        assertEquals(Optional.of(Song.IMPROVI), player.song());
        assertEquals(1, player.changes());
        assertEquals(1, renderer.played.size(), "played once");
        assertEquals(1, count("Music: IMPROVI ended"), lines().toString());
        assertEquals(List.of(), renderer.errors);
    }

    /**
     * Items 2-4 (T7 M1, M3, M4): a song that cannot be loaded is a warning without a stack trace, logged once; its
     * start says "not loaded", nothing plays and nothing throws.
     */
    @Test
    void aMissingSongIsAWarningOnceAndStaysSilent() {
        FakeRenderer renderer = renderer(true, false);
        MusicPlayer player = new MusicPlayer(GeneratedAssets.assetManager(), renderer);

        player.update(Optional.of(Song.DREAMLAND));
        player.update(Optional.empty());
        player.update(Optional.of(Song.DREAMLAND));

        assertEquals(Optional.of(Song.DREAMLAND), player.song());
        assertEquals(List.of(), renderer.played);
        List<ILoggingEvent> missing = log.list.stream()
                .filter(event -> event.getFormattedMessage().contains("cannot be loaded")).toList();
        assertEquals(1, missing.size(), lines().toString());
        assertEquals(Level.WARN, missing.get(0).getLevel());
        assertNull(missing.get(0).getThrowableProxy(), "no stack trace");
        assertEquals(2, count("Music: DREAMLAND (looping), not loaded"), lines().toString());
    }

    /** Item 4 (T7 M3, question 3): a song started at the black point begins at 0 and follows the fade-in. */
    @Test
    void aSongStartedAtTheBlackPointFadesInWithTheScreen() {
        FakeRenderer renderer = renderer(true, false);
        MusicPlayer player = new MusicPlayer(songs(), renderer);

        player.update(Optional.of(Song.DREAMLAND), new MusicChoice.Fade(false, Optional.empty(), 1f));
        AudioSource node = renderer.played.get(0);
        assertEquals(0f, node.getVolume(), 1e-6f);
        player.update(Optional.of(Song.DREAMLAND), new MusicChoice.Fade(false, Optional.empty(), 0.5f));
        assertEquals(0.5f, node.getVolume(), 1e-6f);
        player.update(Optional.of(Song.DREAMLAND));
        assertEquals(1f, node.getVolume(), 1e-6f);
        assertEquals(List.of(), renderer.errors);
    }

    /** Item 2 (T7 M1): when OpenAL is disabled the start says so, and no bogus "ended" follows. */
    @Test
    void withAudioDisabledTheStartSaysSoAndNoEndFollows() {
        FakeRenderer renderer = renderer(true, true);
        MusicPlayer player = new MusicPlayer(songs(), renderer);

        player.update(Optional.of(Song.DREAMLAND));
        player.update(Optional.of(Song.DREAMLAND));

        assertEquals(1, count("Music: DREAMLAND (looping), not played (audio disabled)"), lines().toString());
        assertEquals(0, count("ended"), lines().toString());
        assertEquals(List.of(), renderer.played);
    }

    /**
     * jME 3.9's {@code ALAudioRenderer} as far as the music uses it. A source gets a channel of the current OpenAL
     * context; a stream gets its buffer ids at its first play (or when it needs an update); a dry filter gets an id
     * (with EFX) and is registered. {@link #restart} is {@code checkDevice -> restartAudioRenderer}: the context is
     * destroyed and made again and the registered filters are reset ({@code deleteAllObjects}), but neither the
     * sources' status and channel nor a stream's buffer ids are touched (streams are not registered). Every use of a
     * channel or of buffers of a destroyed context is recorded as an error.
     */
    static final class FakeRenderer implements AudioRenderer {

        private boolean efx;
        private final boolean disabled;
        private final List<Filter> registered = new ArrayList<>();
        final List<AudioSource> played = new ArrayList<>();
        final List<AudioSource> stopped = new ArrayList<>();
        final List<String> errors = new ArrayList<>();
        /** The context's generation; channels and buffer ids carry it (generation * 1000 + n). */
        private int generation = 1;
        private int next;

        FakeRenderer(boolean efx, boolean disabled) {
            this.efx = efx;
            this.disabled = disabled;
        }

        private int newId() {
            return generation * 1000 + ++next;
        }

        private boolean stale(int id) {
            return id >= 0 && id / 1000 != generation;
        }

        /** Whether the (next) context has EFX. */
        void efx(boolean on) {
            efx = on;
        }

        void restart() {
            generation++;
            next = 0;
            registered.forEach(Filter::resetObject);
            registered.clear();
        }

        /** The song's end as jME's render thread reclaims it. */
        void finish(AudioSource src) {
            src.setStatus(Status.Stopped);
            src.setChannel(-1);
        }

        @Override
        public void playSource(AudioSource src) {
            if (disabled || src.getStatus() == Status.Playing) {
                return;
            }
            if (stale(src.getChannel())) {
                errors.add("play on a channel of a destroyed context");
            }
            src.setChannel(newId());
            if (src.getAudioData() instanceof AudioStream stream) {
                if (stream.isUpdateNeeded()) {
                    stream.setIds(new int[] {newId(), newId(), newId()});
                    stream.clearUpdateNeeded();
                } else if (stale(stream.getIds()[0])) {
                    errors.add("buffers of a destroyed context attached");
                }
            }
            Filter dry = src.getDryFilter();
            if (efx && dry != null && dry.getId() == -1) {
                dry.setId(newId());
                dry.clearUpdateNeeded();
                registered.add(dry);
            }
            src.setStatus(Status.Playing);
            played.add(src);
        }

        @Override
        public void stopSource(AudioSource src) {
            if (disabled || src.getStatus() == Status.Stopped) {
                return;
            }
            if (stale(src.getChannel())) {
                errors.add("stop on a channel of a destroyed context");
            }
            src.setStatus(Status.Stopped);
            src.setChannel(-1);
            stopped.add(src);
        }

        @Override
        public void updateSourceParam(AudioSource src, AudioParam param) {
            if (stale(src.getChannel())) {
                errors.add(param + " set on a channel of a destroyed context");
            }
        }

        @Override
        public void setListener(Listener listener) {
        }

        @Override
        public void setEnvironment(Environment env) {
        }

        @Override
        public void playSourceInstance(AudioSource src) {
        }

        @Override
        public void pauseSource(AudioSource src) {
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
}
