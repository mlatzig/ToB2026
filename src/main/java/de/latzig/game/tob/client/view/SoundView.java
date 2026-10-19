package de.latzig.game.tob.client.view;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.jme3.asset.AssetManager;
import com.jme3.audio.AudioData;
import com.jme3.audio.AudioKey;
import com.jme3.audio.AudioNode;
import com.jme3.audio.AudioRenderer;
import com.jme3.audio.AudioSource.Status;
import com.jme3.audio.Listener;
import com.jme3.renderer.Camera;

import de.latzig.game.tob.client.logic.audio.SoundBank;
import de.latzig.game.tob.client.logic.audio.SoundCue;
import de.latzig.game.tob.client.logic.audio.SoundTuning;

/**
 * Plays the cues of {@code SoundCues} with jME's audio (Phase 4c spec decision 1): true 3D positional sound
 * ({@link AudioNode}, OpenAL), the shown camera as the listener (position and full orientation, no velocity, as
 * ToB 2005's {@code SND_updateAll}). Positional sounds get ToB 2005's minimum distance as jME's reference distance
 * and its maximum distance; jME leaves OpenAL's default distance model ({@code AL_INVERSE_DISTANCE_CLAMPED},
 * rolloff 1), which is DirectSound's: the level is held beyond the maximum distance ({@link SoundTuning}).
 * <ul>
 * <li>One-shots: one node per cue, played on its own channel, so it can be stopped; finished ones are dropped. A
 * cue with a key (an element's own sound) restarts that element's node instead, as ToB 2005 restarts the element's
 * buffer ({@code snd.cpp:971-996}).</li>
 * <li>The voice budget (fix wave I1): at most {@link SoundTuning#EXPLOSION_VOICES} voices per explosion sound, and
 * one-shots and loops together within {@link SoundTuning#MAX_VOICES} of jME's 64 sources; beyond it the oldest
 * one-shot stops first (loops have priority). A loop that found no free source is tried again every
 * {@link SoundTuning#LOOP_RETRY_FRAMES} frames, not every frame.</li>
 * <li>Loops: one node per loop key; a key that is no longer cued stops, a moving loop follows its cue, a time
 * bomb's pitch follows it too (jME's 0.5..2).</li>
 * <li>Pause (spec decision 8): the loops pause and resume, the one-shots play out; {@link #stopAll} (tower change,
 * F9, the start screens) stops everything.</li>
 * </ul>
 * Without an audio renderer (headless tests: {@code settings.setAudioRenderer(null)}) it is a no-op that loads
 * nothing and only counts the cues ({@link #played}, {@link #loopKeys}), so the cue logic can be probed headless.
 * The WAV files are loaded at the tower's start ({@link #preload}) and kept per view.
 */
public final class SoundView {

    /** jME's pitch range ({@code AudioNode.setPitch}). */
    static final float MIN_PITCH = 0.5f;
    static final float MAX_PITCH = 2.0f;
    /** The sounds with an explosion's voice budget ({@link SoundTuning#EXPLOSION_VOICES}). */
    private static final Set<String> EXPLOSIONS = Set.of(SoundBank.EXPLOSION, SoundBank.EXPLOSION_SHORT,
            SoundBank.EXPLOSION_BIG, SoundBank.EXPLOSION_DEEP, SoundBank.EXPLOSION_GLASS, SoundBank.THUNDER);

    /** A one-shot's node and its sound id. */
    private record Voice(String sound, AudioNode node) {
    }

    private final AssetManager assets;
    private final Listener listener;
    private final SoundBank bank;
    private final boolean active;
    private final Map<String, AudioData> data = new HashMap<>();
    private final Map<String, AudioNode> loops = new LinkedHashMap<>();
    /** The one-shots that may still sound, oldest first. */
    private final List<Voice> oneShots = new ArrayList<>();
    /** The elements' own one-shot voices by key ({@link SoundCue.OneShot#key}). */
    private final Map<String, Voice> owned = new HashMap<>();
    /** Per loop that found no free source: the frames until it is tried again. */
    private final Map<String, Integer> retry = new HashMap<>();
    private final Map<String, Integer> played = new TreeMap<>();
    private final Set<String> everLooped = new TreeSet<>();
    private Set<String> loopKeys = Set.of();
    private boolean paused;
    /** The Background option's factor (Phase 5b), applied to the background loops at every {@link #update}. */
    private float background = 1f;

    /**
     * @param renderer the application's audio renderer, only as a flag: null (no audio) means only count; the nodes
     *                 play through jME's thread-local {@code AudioContext} renderer
     * @param listener the application's audio listener (null without an audio renderer)
     */
    public SoundView(AssetManager assets, AudioRenderer renderer, Listener listener, SoundBank bank) {
        this.assets = assets;
        this.listener = listener;
        this.bank = bank;
        this.active = renderer != null && listener != null;
        // Phase 5b: the listener's gain is the Master option, set by the app every frame (TobApp.simpleUpdate)
    }

    /** The Background option's factor (Phase 5b decision 3, 0..2); the background loops follow it at once. */
    public void backgroundVolume(float scale) {
        background = scale;
    }

    /**
     * Loads every WAV of the bank now (fix wave, Minor 7: at the tower's start, not at the first use in the render
     * thread). Without an audio renderer it loads nothing.
     *
     * @throws AssetLoadException if a WAV is missing or unreadable
     */
    public void preload() {
        if (!active) {
            return;
        }
        for (SoundBank.Sound sound : bank.sounds().values()) {
            audio(sound.file());
        }
    }

    /** Plays {@code cues} once each, within the voice budget. */
    public void play(List<SoundCue.OneShot> cues) {
        oneShots.removeIf(voice -> voice.node().getStatus() == Status.Stopped);
        for (SoundCue.OneShot cue : cues) {
            played.merge(cue.sound(), 1, Integer::sum);
            if (active) {
                playOne(cue);
            }
        }
    }

    private void playOne(SoundCue.OneShot cue) {
        Voice voice = cue.key() == null ? null : owned.get(cue.key());
        if (voice != null) {
            // ToB 2005's SND_playBuffer: the element's own buffer stops and plays again
            stop(voice.node());
            oneShots.remove(voice);
        } else {
            voice = new Voice(cue.sound(), node(cue.sound(), 1f));
            if (cue.key() != null) {
                owned.put(cue.key(), voice);
            }
        }
        if (EXPLOSIONS.contains(cue.sound())) {
            limitExplosions(cue.sound());
        }
        makeRoom();
        if (oneShots.size() + loops.size() >= SoundTuning.MAX_VOICES) {
            return; // the loops alone fill the budget
        }
        AudioNode node = voice.node();
        node.setLocalTranslation(cue.x(), cue.y(), cue.z());
        node.updateGeometricState();
        node.play();
        oneShots.add(voice);
    }

    /** Stops the oldest voices of explosion {@code sound} until a new one fits its budget. */
    private void limitExplosions(String sound) {
        int count = 0;
        for (Voice voice : oneShots) {
            count += voice.sound().equals(sound) ? 1 : 0;
        }
        for (Iterator<Voice> it = oneShots.iterator(); it.hasNext() && count >= SoundTuning.EXPLOSION_VOICES;) {
            Voice voice = it.next();
            if (voice.sound().equals(sound)) {
                stop(voice.node());
                it.remove();
                count--;
            }
        }
    }

    /** Stops the oldest one-shots until one more voice fits {@link SoundTuning#MAX_VOICES}. */
    private void makeRoom() {
        while (!oneShots.isEmpty() && oneShots.size() + loops.size() >= SoundTuning.MAX_VOICES) {
            stop(oneShots.remove(0).node());
        }
    }

    /**
     * Runs exactly the loops {@code cues} of this frame, paused or not, and moves the listener to {@code camera}.
     */
    public void update(List<SoundCue.Loop> cues, boolean pause, Camera camera) {
        Set<String> wanted = new TreeSet<>();
        for (SoundCue.Loop cue : cues) {
            wanted.add(cue.key());
        }
        loopKeys = Collections.unmodifiableSet(wanted);
        everLooped.addAll(wanted);
        paused = pause;
        if (!active) {
            return;
        }
        listener.setLocation(camera.getLocation());
        listener.setRotation(camera.getRotation());
        for (Iterator<Map.Entry<String, AudioNode>> it = loops.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, AudioNode> entry = it.next();
            if (!wanted.contains(entry.getKey())) {
                stop(entry.getValue());
                it.remove();
            }
        }
        retry.keySet().retainAll(wanted);
        oneShots.removeIf(voice -> voice.node().getStatus() == Status.Stopped);
        for (SoundCue.Loop cue : cues) {
            AudioNode node = loops.get(cue.key());
            if (node == null) {
                makeRoom();
                node = node(cue.sound(), cue.pitch());
                node.setLooping(true);
                loops.put(cue.key(), node);
            }
            node.setLocalTranslation(cue.x(), cue.y(), cue.z());
            node.updateGeometricState();
            float pitch = pitch(bank.sound(cue.sound()).pitch() * cue.pitch());
            if (node.getPitch() != pitch) {
                node.setPitch(pitch);
            }
            float volume = volume(bank.sound(cue.sound()));
            if (node.getVolume() != volume) {
                node.setVolume(volume);
            }
            if (pause) {
                if (node.getStatus() == Status.Playing) {
                    node.pause();
                }
            } else if (node.getStatus() == Status.Paused) {
                node.play();
            } else if (node.getStatus() == Status.Stopped) {
                start(cue.key(), node);
            }
        }
    }

    /**
     * Starts a stopped loop unless its last start failed lately (fix wave I1 (d)): a start that finds no free source
     * leaves the node stopped (jME logs "No channel available") and is tried again only after
     * {@link SoundTuning#LOOP_RETRY_FRAMES} frames.
     */
    private void start(String key, AudioNode node) {
        Integer wait = retry.get(key);
        if (wait != null && wait > 1) {
            retry.put(key, wait - 1);
            return;
        }
        node.play();
        if (node.getStatus() == Status.Stopped) {
            retry.put(key, SoundTuning.LOOP_RETRY_FRAMES);
        } else {
            retry.remove(key);
        }
    }

    /** Stops every sound (tower change, F9, the start screens). */
    public void stopAll() {
        if (active) {
            loops.values().forEach(SoundView::stop);
            oneShots.forEach(voice -> stop(voice.node()));
        }
        loops.clear();
        oneShots.clear();
        owned.clear();
        retry.clear();
        loopKeys = Set.of();
    }

    /** Whether sounds are played (an audio renderer exists). */
    public boolean isActive() {
        return active;
    }

    /** The one-shots played so far per sound id (tests: also counted without an audio renderer). */
    public Map<String, Integer> played() {
        return Collections.unmodifiableMap(played);
    }

    /** The loop keys of the last {@link #update} (tests). */
    public Set<String> loopKeys() {
        return loopKeys;
    }

    /** Every loop key cued so far (tests). */
    public Set<String> everLooped() {
        return Collections.unmodifiableSet(everLooped);
    }

    /** Whether the last {@link #update} paused the loops (tests). */
    public boolean isPaused() {
        return paused;
    }

    /** The loop node of {@code key}, or null (tests). */
    AudioNode loop(String key) {
        return loops.get(key);
    }

    /** The one-shot nodes that may still sound, oldest first (tests). */
    List<AudioNode> oneShots() {
        return oneShots.stream().map(Voice::node).toList();
    }

    private AudioNode node(String id, float pitch) {
        SoundBank.Sound sound = bank.sound(id);
        AudioNode node = new AudioNode(audio(sound.file()), new AudioKey(sound.file(), false));
        node.setPositional(sound.positional());
        if (sound.positional()) {
            node.setRefDistance(sound.refDistance());
            node.setMaxDistance(sound.maxDistance());
        }
        node.setVolume(volume(sound));
        node.setPitch(pitch(sound.pitch() * pitch));
        return node;
    }

    /** A node's volume: the sound's own, the background loops' scaled by the Background option. */
    private float volume(SoundBank.Sound sound) {
        return SoundBank.isBackground(sound.id()) ? sound.volume() * background : sound.volume();
    }

    /** The WAV {@code file}, loaded once per view. */
    private AudioData audio(String file) {
        AudioData audio = data.get(file);
        if (audio == null) {
            try {
                audio = assets.loadAudio(new AudioKey(file, false));
            } catch (RuntimeException e) {
                throw new AssetLoadException(file, e);
            }
            data.put(file, audio);
        }
        return audio;
    }

    private static float pitch(float pitch) {
        return Math.max(MIN_PITCH, Math.min(MAX_PITCH, pitch));
    }

    private static void stop(AudioNode node) {
        if (node.getStatus() != Status.Stopped) {
            node.stop();
        }
    }
}
