package de.latzig.game.tob.client.logic.audio;

/** A sound to play (Phase 4c): {@link SoundCues} decides, {@code client.view.SoundView} plays. */
public sealed interface SoundCue {

    /** The {@link SoundBank} id of the sound. */
    String sound();

    /**
     * A sound played once at ({@code x}, {@code y}, {@code z}) in fields (ignored by non-positional sounds).
     * {@code key} names the element's own voice (fix wave I1), e.g. {@code "4000-12"}: as ToB 2005's per-element
     * buffer ({@code SND_playBuffer} stops and replays it, {@code snd.cpp:971-996}), a cue with the key of a voice
     * still sounding restarts that voice instead of stacking a second one; null: a voice of its own.
     */
    record OneShot(String sound, float x, float y, float z, String key) implements SoundCue {

        /** A one-shot with a voice of its own (no key). */
        public OneShot(String sound, float x, float y, float z) {
            this(sound, x, y, z, null);
        }
    }

    /**
     * A looping sound that runs while a frame's loops hold its {@code key}, at ({@code x}, {@code y}, {@code z})
     * with {@code pitch} (1 = as recorded).
     */
    record Loop(String key, String sound, float x, float y, float z, float pitch) implements SoundCue {
    }
}
