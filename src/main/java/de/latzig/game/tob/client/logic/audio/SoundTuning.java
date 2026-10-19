package de.latzig.game.tob.client.logic.audio;

import de.latzig.game.tob.client.logic.effects.ExplosionDebris;

/**
 * Every tuning value of the Phase 4c sound in one place (spec decisions 1–6): the author tunes them at the
 * acceptance, Phase 5 turns volume and detail into options. Times are simulation frames (0.14 s each, 16 ToB 2005
 * frames, {@link ExplosionDebris#TOB2005_FRAMES_PER_FRAME}); distances are in fields (32 ToB 2005 units).
 *
 * <p>The distance model needs no value here: jME 3.9 never sets OpenAL's distance model, so OpenAL's default
 * {@code AL_INVERSE_DISTANCE_CLAMPED} with rolloff 1 applies, which is DirectSound's default model exactly
 * (gain = minimum distance / distance, held at the maximum distance's level beyond it). Every positional sound
 * therefore gets its ToB 2005 minimum distance as jME's reference distance and its maximum distance unchanged
 * ({@code sounds.json}).</p>
 */
public final class SoundTuning {

    /** No sound at all: ToB 2005's {@code SND_DETAIL_NONE}. */
    public static final int DETAIL_NONE = 0;
    /** ToB 2005's {@code SND_DETAIL_LOW}: only the sounds of detail LOW. */
    public static final int DETAIL_LOW = 1;
    /** ToB 2005's {@code SND_DETAIL_MEDIUM}: LOW and MED. */
    public static final int DETAIL_MEDIUM = 2;
    /** ToB 2005's {@code SND_DETAIL_HIGH}: every sound. */
    public static final int DETAIL_HIGH = 3;
    /**
     * The sound detail (spec decision 1: high). ToB 2005 plays a sound only if its detail is not above this
     * ({@code snd.cpp:985-986}); its shipped {@code tob.ini} sets {@code SoundDetail=3} (high), {@code snd.h}'s
     * fallback is medium.
     */
    public static final int DETAIL = DETAIL_HIGH;

    /**
     * The background loops' volume (spec decision 5: quiet). Acceptance 2026-10-12: 0.35 was too loud against the
     * lightning and thunder (drone A on {@code original-018}), lowered to 0.15. Phase 5b: the Background option
     * (0–200 %) scales it live; the Master option is the listener's gain.
     */
    public static final float BACKGROUND_VOLUME = 0.15f;

    /**
     * The thunder's delay after a lightning strike: 5 + rand(4) = 5–8 frames (0.7–1.1 s), the original's delayed
     * sound ({@code S:10412-10486}, report 14 §2).
     */
    public static final int THUNDER_DELAY_MIN = 5;
    /** The random part of the thunder's delay: 0..3 frames. */
    public static final int THUNDER_DELAY_RANDOM = 4;
    /**
     * Thunders merge (spec decision 3): a strike before the pending thunder has played moves it to the new strike
     * (the original's re-strike resets the delay), and a thunder due less than this many frames (3.4 s) after the
     * last one played is dropped. The generated thunder lasts 7.93 s (0903 at 0.6 speed plus a 3 s tail,
     * {@code convert.SoundSynth}), so the tails of up to two or three thunders can still overlap; the window is
     * tuned at the acceptance.
     */
    public static final int THUNDER_MERGE_FRAMES = 24;
    /** The thunder's volume (spec decision 3: quieter than the strike). */
    public static final float THUNDER_VOLUME = 0.6f;
    /**
     * The thunder's playback pitch, on top of the generated file's 0.6 ({@code convert.SoundSynth}); jME accepts
     * 0.5..2.
     */
    public static final float THUNDER_PITCH = 1.0f;

    /**
     * The time bomb's pitch and rate rise (spec decision 6): pitch 1 + this × burnt fuse (0..0.9 from
     * {@code FuseTick}), so 1.45 in the last tenth; 0 = exactly ToB 2005's constant loop.
     */
    public static final float TIME_BOMB_PITCH_RISE = 0.5f;

    /**
     * A fixed enemy zapper's sound cadence: every 192 + rand(128) ToB 2005 frames ({@code TWR_ENZ_FRAME_STEP},
     * {@code TWR_ENZ_FRAME_SNDRAND}, {@code gml.cpp:1088-1093}) = 12 + rand(8) frames, while it fires.
     */
    public static final int ENEMY_ZAPPER_FRAMES = 12;
    /** The random part of the fixed enemy zapper's cadence: 0..7 frames. */
    public static final int ENEMY_ZAPPER_RANDOM = 8;
    /** A worm's step sound every 64 ToB 2005 frames ({@code TWR_WRM_FRAME_STEP}) = 4 frames. */
    public static final int WORM_STEP_FRAMES = 4;
    /** A hopper's landing sound every 256 ToB 2005 frames ({@code TWR_HOP_FRAME_STEP}) = 16 frames. */
    public static final int HOPPER_STEP_FRAMES = 16;
    /** A walking spider's step sound every 64 ToB 2005 frames ({@code TWR_SPD_WALK_SNDSTEP}) = 4 frames. */
    public static final int SPIDER_STEP_FRAMES = 4;

    /**
     * A pushed element's push sound: minimum distance 16 units = 0.5 fields and detail MED ({@code twr.cpp:1184});
     * a pushed spider's is {@code sounds.json}'s 2 units and LOW.
     */
    public static final float ELEMENT_PUSH_MIN_DISTANCE = 0.5f;
    /** The lift sounds play 16 units (0.5 fields) above the lift's cell ({@code twr.cpp:1930, 1936}). */
    public static final float LIFT_SOUND_HEIGHT = 0.5f;

    /**
     * The voice budget (fix wave I1): one-shots and loops together use at most this many of jME's 64 OpenAL sources
     * ({@code ALAudioRenderer}); a new one-shot or loop beyond it stops the oldest one-shot first (loops have
     * priority), and the rest stays free for the click and new loops. Fast-forward runs a simulation step per render
     * frame, so the cadences come up to 20 times as fast; without the budget jME runs out of sources, drops sounds
     * and logs "No channel available" for each.
     */
    public static final int MAX_VOICES = 56;
    /**
     * At most this many voices per explosion sound (0900–0904, the thunder), the oldest stops first: ToB 2005's
     * explosion queue of 32 entries with one buffer per sound each ({@code TWR_EXPLOSION_MAX}, {@code twr.cpp:1057}).
     */
    public static final int EXPLOSION_VOICES = 32;
    /**
     * A loop that found no free source is tried again only after this many frames (render frames), not every frame:
     * jME logs every failed start.
     */
    public static final int LOOP_RETRY_FRAMES = 30;

    private SoundTuning() {
    }
}
