package de.latzig.game.tob.client.logic.effects;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The lightning schedule of a tower with the lightning flag (Phase 4b-2 spec §2 decisions 1, 2 and 5): ToB 2005's
 * three independent bolts ({@code twr.cpp:873-895, 966-990}) with the original's bursts ({@code L00C134},
 * {@code S:10459-10476}), as pure functions of the simulation time (frames, steps + alpha) and a seeded generator per
 * bolt. Time: 16 ToB 2005 frames per simulation frame ({@link ExplosionDebris#TOB2005_FRAMES_PER_FRAME}), the
 * effect clock of Phase 4b-1; units: 32 per field.
 * <ul>
 * <li>A bolt waits 256 + rand(768) ToB 2005 frames (16–64 simulation frames, 2.2–9 s) and strikes at a random yaw,
 * 500–1500 units from the tower centre (ToB 2005 draws countdown, distance, yaw in this order).</li>
 * <li>Burst: after a strike the same bolt strikes again at the same place with probability 1/2 after 5–12 frames,
 * the original's countdown {@code (r & 7) + 4} plus its palette-restore frame, which does not count down
 * (report 14 §2); otherwise it waits anew at a new place.</li>
 * <li>The bolt ({@code 0910}, ToB 2005's {@code TWR_FXL_FRAME_STOP}) is shown over the last 64 ToB 2005 frames (4
 * simulation frames) before its strike, its keys played backwards (64 → 0).</li>
 * <li>Every strike flashes over its bolt's last {@link AmbientTuning#FLASH_FRAMES} ({@link AmbientTuning#flashCurve}),
 * the brightest running flash counts.</li>
 * </ul>
 */
public final class Lightning {

    /** ToB 2005's {@code TWR_FXL_COUNT}. */
    public static final int BOLTS = 3;
    /** The tower centre in fields: ToB 2005's origin, the middle of the 16 × 16 grid. */
    public static final float CENTRE = 7.5f;
    /** The bolt's height: ToB 2005 places its 32-field billboard at y 496 units. */
    public static final float BOLT_Y = 496f / 32f;
    /** The bolt is shown over the last 64 ToB 2005 frames before its strike. */
    public static final float BOLT_FRAMES = 64f / ExplosionDebris.TOB2005_FRAMES_PER_FRAME;

    static final int WAIT_MIN = 256;
    static final int WAIT_RANDOM = 768;
    static final int BURST_MIN = 5;
    static final int BURST_RANDOM = 8;
    static final int DISTANCE_MIN = 500;
    static final int DISTANCE_RANDOM = 1000;

    /**
     * One strike: bolt {@code bolt} hits the ground at ({@code x}, {@code z}) (fields) at simulation time
     * {@code time}; {@code seed} seeds its debris.
     */
    public record Strike(int bolt, float time, float x, float z, long seed) {
    }

    /** A bolt shown at ({@code x}, {@code z}) with mesh key {@code key} (ToB 2005 frames, 64 → 0). */
    public record Bolt(int bolt, float x, float z, float key) {
    }

    private final Random[] randoms = new Random[BOLTS];
    private final List<List<Strike>> strikes = new ArrayList<>();

    public Lightning(long seed) {
        for (int b = 0; b < BOLTS; b++) {
            randoms[b] = AmbientRandom.of(seed, b);
            List<Strike> list = new ArrayList<>();
            list.add(place(b, wait(randoms[b])));
            strikes.add(list);
        }
    }

    /** Every strike with {@code from < time ≤ to}, by time. */
    public List<Strike> strikes(float from, float to) {
        List<Strike> out = new ArrayList<>();
        for (int b = 0; b < BOLTS; b++) {
            List<Strike> list = extend(b, to);
            for (int i = firstAfter(list, from); i < list.size() && list.get(i).time() <= to; i++) {
                out.add(list.get(i));
            }
        }
        out.sort((a, c) -> Float.compare(a.time(), c.time()));
        return out;
    }

    /** The bolts shown at {@code now}: those whose next strike lies less than {@link #BOLT_FRAMES} ahead. */
    public List<Bolt> bolts(float now) {
        List<Bolt> out = new ArrayList<>();
        for (int b = 0; b < BOLTS; b++) {
            List<Strike> list = extend(b, now);
            int i = firstAtOrAfter(list, now);
            Strike next = list.get(i);
            float ahead = next.time() - now;
            if (ahead < BOLT_FRAMES) {
                out.add(new Bolt(b, next.x(), next.z(), ahead * ExplosionDebris.TOB2005_FRAMES_PER_FRAME));
            }
        }
        return out;
    }

    /** The flash curve at {@code now}: the brightest flash of the strikes coming up (0..1). */
    public float flash(float now) {
        float flash = 0f;
        for (Strike strike : strikes(now, now + AmbientTuning.FLASH_FRAMES)) {
            flash = Math.max(flash, AmbientTuning.flashCurve(strike.time() - now));
        }
        return flash;
    }

    /** The strikes of bolt {@code b}, generated until one lies after {@code until}. */
    private List<Strike> extend(int b, float until) {
        List<Strike> list = strikes.get(b);
        Random random = randoms[b];
        while (list.get(list.size() - 1).time() <= until) {
            Strike last = list.get(list.size() - 1);
            if (random.nextBoolean()) {
                list.add(new Strike(b, last.time() + BURST_MIN + random.nextInt(BURST_RANDOM), last.x(), last.z(),
                        random.nextLong()));
            } else {
                list.add(place(b, last.time() + wait(random)));
            }
        }
        return list;
    }

    /** A strike of bolt {@code b} at {@code time} at a new place. */
    private Strike place(int b, float time) {
        Random random = randoms[b];
        float distance = (DISTANCE_MIN + random.nextInt(DISTANCE_RANDOM)) / 32f;
        float yaw = random.nextInt(6283) / 1000f;
        return new Strike(b, time, CENTRE + (float) Math.sin(yaw) * distance,
                CENTRE + (float) Math.cos(yaw) * distance, random.nextLong());
    }

    private static float wait(Random random) {
        return (WAIT_MIN + random.nextInt(WAIT_RANDOM)) / ExplosionDebris.TOB2005_FRAMES_PER_FRAME;
    }

    /** The index of the first strike with a time after {@code time} (the list's size if none). */
    private static int firstAfter(List<Strike> list, float time) {
        int low = 0;
        int high = list.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (list.get(mid).time() <= time) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }

    /** The index of the first strike with a time at or after {@code time} (the list's size if none). */
    private static int firstAtOrAfter(List<Strike> list, float time) {
        int low = 0;
        int high = list.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (list.get(mid).time() < time) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }
}
