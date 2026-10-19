package de.latzig.game.tob.client.view;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jme3.asset.AssetManager;
import com.jme3.audio.AudioData;
import com.jme3.audio.AudioNode;
import com.jme3.audio.AudioRenderer;
import com.jme3.audio.AudioSource.Status;
import com.jme3.audio.AudioStream;
import com.jme3.audio.Filter;
import com.jme3.audio.LowPassFilter;

import de.latzig.game.tob.client.logic.frontend.MusicChoice;
import de.latzig.game.tob.client.logic.frontend.MusicChoice.Change;
import de.latzig.game.tob.client.logic.frontend.MusicChoice.Song;

/**
 * Plays the author's songs (Phase 5c-2 spec §3, decisions 1-4) as {@link MusicChoice} chooses them: one streamed,
 * non-positional {@link AudioNode} per song (OGG Vorbis through jME's {@code jme3-jogg} loader, with the stream
 * cache, so the stream is seekable: jME rewinds it on stop and at its end, and a looping one starts over), made at the
 * song's first start and kept; at most one plays. Dreamland loops, the result songs play once. The node's volume is
 * only the fade factor of {@link MusicChoice#gain} (the author's answer to question 3: the music fades out and in
 * with the screen when the song changes); the listener's gain (Master or {@code -Dtob.volume}, set by the app every
 * frame) multiplies it.
 * <p>
 * Every change is logged, and so is the end of a song played once (the author is hard of hearing: the log shows what
 * plays). A song that cannot be loaded is logged once and stays silent. Without an audio renderer (headless tests) it
 * follows the choice and logs, but loads and plays nothing.
 * <p>
 * An audio device change (fix wave item 1: headphones unplugged or switched): jME 3.9's {@code ALAudioRenderer}
 * notices it on its decoder thread ({@code checkDevice → restartAudioRenderer}), destroys the OpenAL context and makes
 * a new one, but leaves every {@link AudioNode} as it was: the song stays "Playing" on a channel of the destroyed
 * context (jME's {@code playSource} then ignores it, so it would stay silent for good), and every stream keeps the
 * buffer ids of that context (streams are not registered with jME's native object manager, so nobody resets them).
 * Each node therefore carries a neutral dry filter as a marker: jME registers the filter when the node plays and
 * resets it ({@code deleteAllObjects}) with the context, so a marker without an OpenAL id shows the loss in the next
 * frame, independent of how the context handle compares. The player then gives every stream fresh buffers and plays
 * the song again from where its decoder stood, without touching the old channel (it may be another sound's by now).
 * Without EFX the marker gets no id; then {@link #deviceChanged} (the app's context watch) is the trigger.
 */
public final class MusicPlayer {

    private static final Logger LOG = LoggerFactory.getLogger(MusicPlayer.class);

    private final AssetManager assets;
    private final boolean active;
    private final MusicChoice choice = new MusicChoice();
    private final Map<Song, AudioNode> nodes = new EnumMap<>(Song.class);
    private final Set<Song> broken = EnumSet.noneOf(Song.class);
    private AudioNode playing;
    private int changes;
    private float gain;
    /** Whether a marker filter has had an OpenAL id (EFX present): only then does a lost id mean a lost context. */
    private boolean markers;

    /** @param renderer the application's audio renderer, only as a flag: null (no audio) means play nothing */
    public MusicPlayer(AssetManager assets, AudioRenderer renderer) {
        this.assets = assets;
        this.active = renderer != null;
    }

    /** As {@link #update(Optional, MusicChoice.Fade)} without a screen fade. */
    public void update(Optional<Song> wanted) {
        update(wanted, MusicChoice.Fade.NONE);
    }

    /**
     * Called every frame with the song wanted now and the screen's fade: keeps, starts (from its beginning) or stops
     * the song, and sets its volume to the fade factor.
     */
    public void update(Optional<Song> wanted, MusicChoice.Fade fade) {
        if (markers && contextLost()) {
            restore();
        }
        if (playing != null && playing.getStatus() == Status.Stopped) {
            LOG.info("Music: {} ended", choice.song().map(Enum::name).orElse("?"));
            playing = null;
        }
        Change change = choice.next(wanted);
        gain = choice.gain(fade);
        if (change == Change.KEEP) {
            if (playing != null && playing.getVolume() != gain) {
                playing.setVolume(gain);
            }
            return;
        }
        changes++;
        stopPlaying();
        if (change == Change.STOP) {
            LOG.info("Music: stopped");
            return;
        }
        Song song = wanted.orElseThrow();
        AudioNode node = active ? node(song) : null;
        if (node != null) {
            node.setVolume(gain);
            node.play();
        }
        boolean plays = node != null && node.getStatus() == Status.Playing;
        String note = !active ? ", no audio device"
                : node == null ? ", not loaded" : plays ? "" : ", not played (audio disabled)";
        LOG.info("Music: {} ({}){}", song, song.loops() ? "looping" : "once", note);
        playing = plays ? node : null;
        markers |= plays && node.getDryFilter().getId() != -1;
    }

    /**
     * The audio device changed (the app's watch saw a new OpenAL context). With markers the next {@link #update}
     * notices a lost context itself and this only acts if it has not done so yet; without them (no EFX) it restores
     * the songs at once.
     */
    public void deviceChanged() {
        if (!markers || contextLost()) {
            restore();
        }
    }

    /** Whether a song's marker has lost its OpenAL id while its stream still holds buffers: the context was rebuilt. */
    private boolean contextLost() {
        for (AudioNode node : nodes.values()) {
            if (node.getDryFilter().getId() == -1 && ((AudioStream) node.getAudioData()).getIds() != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * After a lost context: every stream forgets its buffers (fresh ones at its next play; the old ones went with the
     * context), and the song that was playing is reset without touching its old channel and played again.
     */
    private void restore() {
        // re-checked below: a rebuilt context without EFX gives the marker no id (it would look lost every frame)
        markers = false;
        boolean wasPlaying = playing != null && playing.getStatus() == Status.Playing;
        if (wasPlaying) {
            playing.setStatus(Status.Stopped);
            playing.setChannel(-1);
        }
        for (AudioNode node : nodes.values()) {
            AudioStream stream = (AudioStream) node.getAudioData();
            if (stream.getIds() != null) {
                stream.resetObject();
            }
        }
        if (!wasPlaying) {
            return;
        }
        String song = choice.song().map(Enum::name).orElse("?");
        AudioStream stream = (AudioStream) playing.getAudioData();
        if (stream.isEOF() && !playing.isLooping()) {
            return;
        }
        playing.setVolume(gain);
        playing.play();
        if (playing.getStatus() == Status.Playing) {
            markers = playing.getDryFilter().getId() != -1;
            LOG.info("Music: {} played again after an audio device change", song);
        } else {
            LOG.warn("Music: {} cannot be played again after an audio device change (audio disabled)", song);
            playing = null;
        }
    }

    private void stopPlaying() {
        if (playing != null) {
            if (playing.getStatus() != Status.Stopped) {
                playing.stop();
            }
            playing = null;
        }
    }

    /** The node of {@code song}, made at its first start; null if it cannot be loaded. */
    private AudioNode node(Song song) {
        AudioNode node = nodes.get(song);
        if (node == null && !broken.contains(song)) {
            try {
                node = new AudioNode(assets, song.file(), AudioData.DataType.Stream);
                node.setPositional(false);
                node.setLooping(song.loops());
                node.setDryFilter(marker());
                nodes.put(song, node);
            } catch (RuntimeException e) {
                LOG.warn("Music: {} cannot be loaded: {}", song.file(), e.toString());
                broken.add(song);
                node = null;
            }
        }
        return node;
    }

    /** A neutral low-pass filter (full gain, full high frequencies): the marker of a lost context. */
    private static Filter marker() {
        return new LowPassFilter(1f, 1f);
    }

    /** The song chosen last (playing, or a result song that has ended), or none. */
    public Optional<Song> song() {
        return choice.song();
    }

    /** The fade factor of the last {@link #update} (0..1; tests). */
    public float gain() {
        return gain;
    }

    /** How many times the song has changed (started or stopped; tests). */
    public int changes() {
        return changes;
    }

    /** Whether songs are played (an audio renderer exists). */
    public boolean isActive() {
        return active;
    }
}
