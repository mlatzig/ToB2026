package de.latzig.game.tob.client.logic.audio;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import de.latzig.game.tob.core.model.SoundCatalog;
import de.latzig.game.tob.core.model.SoundDetail;
import de.latzig.game.tob.core.model.SoundEntry;

/**
 * Every sound the client plays (Phase 4c): ToB 2005's 24 sounds from {@code sounds.json} with their loop and
 * positional flags, distances and detail levels, plus what {@code sounds.json} does not hold (it stays unchanged):
 * the pushed elements' variant of the push sound ({@link #PUSH_ELEMENT}) and the four generated sounds
 * ({@code convert.SoundSynth}): the thunder and the three background loops.
 */
public final class SoundBank {

    public static final String CLICK = "0090";
    public static final String NOISE = "0091";
    public static final String PUSH = "0092";
    /** A pushed element's push: the same file, minimum distance 0.5 fields, detail MED ({@code twr.cpp:1184}). */
    public static final String PUSH_ELEMENT = "0092e";
    public static final String EXPLOSION = "0900";
    public static final String EXPLOSION_SHORT = "0901";
    public static final String EXPLOSION_BIG = "0902";
    public static final String EXPLOSION_DEEP = "0903";
    public static final String EXPLOSION_GLASS = "0904";
    public static final String SPIDER_STEP = "1000";
    public static final String LIFT_RUN = "1900";
    public static final String LIFT_STOP = "1901";
    public static final String ZAP = "2000";
    public static final String KLONDIKE = "2500";
    public static final String ENEMY_ZAPPER = "3200";
    public static final String ROTATING_ZAPPER = "3300";
    public static final String HOPPER = "4000";
    public static final String WORM = "4100";
    public static final String WATCHER = "4200";
    public static final String EXCHANGER = "5400";
    public static final String CONVERTER = "5500";
    public static final String WIPER = "5600";
    public static final String FORCER = "5800";
    public static final String FREEZER = "5900";
    public static final String TIME_BOMB = "6100";
    public static final String THUNDER = "9900";
    public static final String BREATH = "9901";
    public static final String DRONE_A = "9902";
    public static final String DRONE_B = "9903";

    /**
     * One sound: {@code file} relative to the assets root; positional sounds attenuate from {@code refDistance} to
     * {@code maxDistance} (fields); {@code detail} is the lowest sound detail it plays at; {@code volume} and
     * {@code pitch} are the playback's.
     */
    public record Sound(String id, String file, boolean loop, boolean positional, float refDistance,
            float maxDistance, SoundDetail detail, float volume, float pitch) {
    }

    private final Map<String, Sound> sounds;

    private SoundBank(Map<String, Sound> sounds) {
        this.sounds = Collections.unmodifiableMap(sounds);
    }

    /**
     * The bank of {@code catalog} ({@code sounds.json}) and the client's own entries.
     *
     * @throws IllegalArgumentException if {@code catalog} lacks the push sound
     */
    public static SoundBank of(SoundCatalog catalog) {
        Map<String, Sound> sounds = new TreeMap<>();
        for (Map.Entry<String, SoundEntry> e : catalog.sounds().entrySet()) {
            SoundEntry entry = e.getValue();
            sounds.put(e.getKey(), new Sound(e.getKey(), entry.file(), entry.loop(), entry.positional(),
                    (float) entry.minDistance(), (float) entry.maxDistance(), entry.detail(), 1f, 1f));
        }
        Sound push = sounds.get(PUSH);
        if (push == null) {
            throw new IllegalArgumentException("sounds.json has no push sound " + PUSH);
        }
        sounds.put(PUSH_ELEMENT, new Sound(PUSH_ELEMENT, push.file(), false, true,
                SoundTuning.ELEMENT_PUSH_MIN_DISTANCE, push.maxDistance(), SoundDetail.MED, 1f, 1f));
        // the thunder attenuates like the deep explosion it comes from (0903: 64 / 8192 units)
        sounds.put(THUNDER, new Sound(THUNDER, "sounds/9900-thunder.wav", false, true, 2f, 256f, SoundDetail.HIGH,
                SoundTuning.THUNDER_VOLUME, SoundTuning.THUNDER_PITCH));
        sounds.put(BREATH, background(BREATH, "sounds/9901-breath.wav"));
        sounds.put(DRONE_A, background(DRONE_A, "sounds/9902-drone-a.wav"));
        sounds.put(DRONE_B, background(DRONE_B, "sounds/9903-drone-b.wav"));
        return new SoundBank(sounds);
    }

    private static Sound background(String id, String file) {
        return new Sound(id, file, true, false, 0f, 0f, SoundDetail.MED, SoundTuning.BACKGROUND_VOLUME, 1f);
    }

    /** Whether {@code id} is one of the three background loops (Phase 5b: the Background option scales them). */
    public static boolean isBackground(String id) {
        return BREATH.equals(id) || DRONE_A.equals(id) || DRONE_B.equals(id);
    }

    /** The background loop of the tower field {@code sounds.background} 1–3; null for 0 (none). */
    public static String background(int kind) {
        return switch (kind) {
            case 1 -> BREATH;
            case 2 -> DRONE_A;
            case 3 -> DRONE_B;
            default -> null;
        };
    }

    /** @throws IllegalArgumentException for an unknown id */
    public Sound sound(String id) {
        Sound sound = sounds.get(id);
        if (sound == null) {
            throw new IllegalArgumentException("unknown sound " + id);
        }
        return sound;
    }

    /** Every sound by id. */
    public Map<String, Sound> sounds() {
        return sounds;
    }

    /**
     * Whether sound {@code id} plays at sound detail {@code detail} ({@link SoundTuning#DETAIL_NONE} ..
     * {@link SoundTuning#DETAIL_HIGH}): as ToB 2005's {@code SND_playBuffer}, a sound plays unless its detail is
     * above the setting; {@code sounds.json}'s LOW, MED, HIGH are ToB 2005's {@code SND_DETAIL_LOW} (1),
     * {@code SND_DETAIL_MEDIUM} (2) and {@code SND_DETAIL_HIGH} (3).
     */
    public boolean audible(String id, int detail) {
        return sound(id).detail().ordinal() + 1 <= detail;
    }
}
