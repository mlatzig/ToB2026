package de.latzig.game.tob.client.logic.effects;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import de.latzig.game.tob.client.logic.Socket;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.model.TowerGrid;

/**
 * The meteors of a tower with the meteor flag (Phase 4b-2 spec §2 decision 3): ToB 2005's four meteors
 * ({@code twr.cpp:897-935, 996-1035}, {@code bpe.cpp:760-790}) as pure functions of the simulation time (frames,
 * steps + alpha) and a seeded generator per meteor, like {@link Lightning}. Time: 16 ToB 2005 frames per simulation
 * frame ({@link ExplosionDebris#TOB2005_FRAMES_PER_FRAME}); units: 32 per field.
 * <ul>
 * <li>A flight lasts 512 + rand(512) ToB 2005 frames (32–64 simulation frames, 4.5–9 s) in a straight line from the
 * sky sphere (ToB 2005's atmosphere radius, elevation 0.5–1.3 rad) down to a target on the landscape around the
 * tower. ToB 2005 draws the spin, length, ground distance, ground yaw, elevation and sky yaw in this order; so does
 * this class.</li>
 * <li>The rock ({@code 0920}) turns 0.01 rad per ToB 2005 frame about y and plays its 256-frame animation in a
 * loop.</li>
 * <li>The trail: 24 fireball sprites 8 units apart back along the path, 16 units above the rock, half size 16, frames
 * 8 (next to the rock, hot) to 31 (embers); over the last 10 % of the flight they shrink to nothing.</li>
 * <li>Impact at the end of the flight, at the target; the next flight of the same meteor starts at once.</li>
 * </ul>
 *
 * <p><b>Author decision (acceptance 2026-10-10, overrides spec §2 decision 3's "earlier when entering a floor or box
 * cell" and the live cell test): meteors never fly into the tower.</b> Like the lightning, they only strike the
 * landscape around it. Deviations from ToB 2005, all deliberate:</p>
 * <ul>
 * <li><b>No cell test.</b> ToB 2005 broke a rock apart as soon as it was in a floor, box or lift cell
 * ({@code TWR_isBasisElementNotSpace}, {@code twr.cpp:3099-3117}), using its own {@code ⌊y + 0.5⌋} level rule, which
 * here stopped rocks half a field above a floor or inside it. The meteors read nothing of the world.</li>
 * <li><b>The target lies 500–1500 units from the tower centre</b> ({@value #DISTANCE_MIN} + rand({@value
 * #DISTANCE_RANDOM}), the lightning's range) instead of ToB 2005's 0–1500 ({@code rand() % 1500}), drawn at the same
 * place in the sequence. The whole 16 × 16 grid with the socket's 45° border lies within {@link #TOWER_RADIUS}
 * (11.4 fields) of the centre, so every target is at least 4.2 fields clear of any tower's footprint and socket.</li>
 * <li><b>The sky point lies on the target's side:</b> the sky yaw is the ground yaw plus the ToB 2005 draw
 * ({@code rand() % 6283 / 1000}) mapped to −π/2..π/2, instead of an independent yaw. With the two horizontal
 * directions at most 90° apart, the target at least 15.6 fields out and the sky point at least 38 fields out
 * (atmosphere radius × cos 1.3), the path's horizontal projection keeps at least 14.4 fields from the centre — clear
 * of {@link #TOWER_RADIUS} — so it never passes through the tower's volume, at any height.</li>
 * <li><b>The flight ends on the visible ground:</b> at the {@link Ground} height at the target
 * ({@link Terrain#heightAt}), not at ToB 2005's y −16 units (−0.5 fields here). ToB 2005's own ground lay at −8 units;
 * Phase 4b-1's flat zone lies at {@link Terrain#FLAT_Y} (−0.033), so the old end point lay 0.47 fields under the
 * surface: the rock sank half into it and the debris (which vanishes below the impact point) and the sprites stayed
 * buried — the acceptance defect "no explosions, the meteors sink into the ground". The end is known when the flight
 * is drawn, so {@link #at} never shows a rock below the surface.</li>
 * </ul>
 *
 * <p><b>Bounded memory:</b> a meteor's flights are generated as far as a query needs them; those that ended more than
 * {@value #HISTORY_STEPS} simulation steps before the latest time asked about are dropped (more than the longest
 * flight, about 64 steps), so the lists stay bounded however long the game runs. A query about a time older than
 * that window may miss dropped flights; real callers only ask about the current frame, in order.</p>
 */
public final class Meteors {

    /** ToB 2005's {@code TWR_FXM_COUNT}. */
    public static final int COUNT = 4;
    /** ToB 2005's {@code BPE_SPRITE_ID_FXM_NUM}. */
    public static final int TRAIL = 24;
    /** The fireball frame of the trail sprite next to the rock ({@code BPE_SPRITE_ID_FXM_OFF}). */
    public static final int FIRST_TRAIL_FRAME = 8;
    /** ToB 2005's impact sprites rise 16 units above the impact point. */
    public static final float SPRITE_OFFSET = 16f / 32f;
    /** The rock's animation loop in ToB 2005 frames ({@code TWR_FXM_FRAME_STOP}). */
    public static final int MESH_LOOP = 256;
    /**
     * The radius (fields) around {@link Lightning#CENTRE} that holds every tower: the 16 × 16 grid's corners plus the
     * socket's border ({@link Socket#BORDER}).
     */
    public static final float TOWER_RADIUS = (float) ((TowerGrid.WIDTH / 2.0 + Socket.BORDER) * Math.sqrt(2.0));

    static final int FLIGHT_MIN = 512;
    static final int FLIGHT_RANDOM = 512;
    static final float SKY = (float) (SkyDome.ATMOSPHERE / 32.0);
    /** The target's distance from the centre in units: the lightning's 500–1500. */
    static final int DISTANCE_MIN = 500;
    static final int DISTANCE_RANDOM = 1000;
    static final float TRAIL_SPACING = 8f / 32f;
    static final float TRAIL_LIFT = 16f / 32f;
    static final float TRAIL_HALF_SIZE = 16f / 32f;
    static final float SPIN = 0.01f;

    /** ToB 2005 frames per simulation step, as an exact integer ({@link ExplosionDebris#TOB2005_FRAMES_PER_FRAME}). */
    static final long STEP_FRAMES = Math.round(ExplosionDebris.TOB2005_FRAMES_PER_FRAME);
    /** Flights that ended more than this many simulation steps before the latest query are dropped. */
    static final int HISTORY_STEPS = 128;

    /** The height (fields) of the visible ground under scene point ({@code x}, {@code z}). */
    @FunctionalInterface
    public interface Ground {

        float height(float x, float z);
    }

    /** The terrain's flat zone ({@link Terrain#FLAT_Y}) everywhere: every target lies within it. */
    public static final Ground FLAT_GROUND = (x, z) -> Terrain.FLAT_Y;

    /**
     * One impact: meteor {@code meteor} breaks apart at ({@code x}, {@code y}, {@code z}) (fields), turned by
     * {@code yaw}, at simulation time {@code time}; {@code seed} seeds its debris.
     */
    public record Impact(int meteor, float time, float x, float y, float z, float yaw, long seed) {
    }

    /** One meteor in flight: its rock (position, yaw, mesh key in ToB 2005 frames) and its trail. */
    public record Meteor(int meteor, float x, float y, float z, float yaw, float key,
            List<ExplosionSprites.Sprite> trail) {
    }

    /**
     * One flight from the sky point ({@code sx}, {@code sy}, {@code sz}) to the target on the ground ({@code ex},
     * {@code ey}, {@code ez}): {@code startFrame} is the absolute ToB 2005 frame at which it began (an exact integer,
     * so time never drifts), {@code length} its whole number of ToB 2005 frames.
     */
    private record Flight(long startFrame, int length, float sx, float sy, float sz, float ex, float ey, float ez,
            float yaw, long seed) {

        long endFrame() {
            return startFrame + length;
        }

        /** This flight's start, in simulation time (steps + fraction). */
        float startTime() {
            return (float) (startFrame / (double) STEP_FRAMES);
        }

        /** This flight's end (the impact), in simulation time. */
        float end() {
            return (float) (endFrame() / (double) STEP_FRAMES);
        }

        float x(float f) {
            return sx + (ex - sx) * f / length;
        }

        float y(float f) {
            return sy + (ey - sy) * f / length;
        }

        float z(float f) {
            return sz + (ez - sz) * f / length;
        }
    }

    private final Ground ground;
    private final Random[] randoms = new Random[COUNT];
    /** Per meteor: its flights, oldest first; the last one has not ended before the latest query. */
    private final List<List<Flight>> flights = new ArrayList<>();

    /** The meteors of the tower whose seed is {@code seed} over {@link #FLAT_GROUND}. */
    public Meteors(long seed) {
        this(seed, FLAT_GROUND);
    }

    /** The meteors of the tower whose seed is {@code seed}, landing on {@code ground} ({@link #ground}). */
    public Meteors(long seed, Ground ground) {
        this.ground = ground;
        for (int m = 0; m < COUNT; m++) {
            randoms[m] = AmbientRandom.of(seed, 100 + m);
            List<Flight> list = new ArrayList<>();
            list.add(path(m, 0L));
            flights.add(list);
        }
    }

    /** The visible ground of {@code tower}: its terrain ({@link Terrain#heightAt}). */
    public static Ground ground(TowerDefinition tower) {
        return Terrain.of(tower)::heightAt;
    }

    /** Every impact with {@code from < time ≤ to}, by time. */
    public List<Impact> impacts(float from, float to) {
        List<Impact> out = new ArrayList<>();
        for (int m = 0; m < COUNT; m++) {
            List<Flight> list = extend(m, to);
            for (Flight flight : list) {
                float end = flight.end();
                if (end > from && end <= to) {
                    out.add(new Impact(m, end, flight.ex(), flight.ey(), flight.ez(),
                            flight.yaw() + SPIN * flight.length(), flight.seed()));
                }
            }
        }
        out.sort((a, b) -> Float.compare(a.time(), b.time()));
        return out;
    }

    /** The four meteors in flight at {@code now}. */
    public List<Meteor> at(float now) {
        List<Meteor> out = new ArrayList<>(COUNT);
        for (int m = 0; m < COUNT; m++) {
            List<Flight> list = extend(m, now);
            Flight flight = flightAt(list, now);
            float f = Math.clamp((now - flight.startTime()) * ExplosionDebris.TOB2005_FRAMES_PER_FRAME, 0f,
                    flight.length());
            float x = flight.x(f);
            float y = flight.y(f);
            float z = flight.z(f);
            float alpha = f / flight.length();
            float size = alpha > 0.9f ? Math.max(0f, (1f - alpha) * 10f) : 1f;
            float bx = flight.sx() - flight.ex();
            float by = flight.sy() - flight.ey();
            float bz = flight.sz() - flight.ez();
            float length = (float) Math.sqrt(bx * bx + by * by + bz * bz);
            List<ExplosionSprites.Sprite> trail = new ArrayList<>(TRAIL);
            if (size > 0f) {
                for (int s = 0; s < TRAIL; s++) {
                    float back = TRAIL_SPACING * (s + 1) / length;
                    trail.add(new ExplosionSprites.Sprite(ExplosionSprites.FIREBALL, FIRST_TRAIL_FRAME + s,
                            x + bx * back, y + TRAIL_LIFT + by * back, z + bz * back, TRAIL_HALF_SIZE * size));
                }
            }
            out.add(new Meteor(m, x, y, z, flight.yaw() + SPIN * f, f % MESH_LOOP, trail));
        }
        return out;
    }

    /** The flights kept over every meteor right now (tests, to check the bound holds). */
    int cachedFlights() {
        int total = 0;
        for (List<Flight> list : flights) {
            total += list.size();
        }
        return total;
    }

    /**
     * The minimum horizontal distance (fields) from {@link Lightning#CENTRE} of every flight path kept right now
     * (tests).
     */
    float closestApproach() {
        float closest = Float.POSITIVE_INFINITY;
        for (List<Flight> list : flights) {
            for (Flight flight : list) {
                float c = Lightning.CENTRE;
                closest = Math.min(closest,
                        segmentDistance(flight.sx() - c, flight.sz() - c, flight.ex() - c, flight.ez() - c));
            }
        }
        return closest;
    }

    /**
     * Meteor {@code m}'s flights, generated until one ends after {@code until}; those that ended more than
     * {@link #HISTORY_STEPS} steps before {@code until} are dropped.
     */
    private List<Flight> extend(int m, float until) {
        List<Flight> list = flights.get(m);
        while (list.get(list.size() - 1).end() <= until) {
            list.add(path(m, list.get(list.size() - 1).endFrame()));
        }
        float threshold = until - HISTORY_STEPS;
        int drop = 0;
        while (drop < list.size() - 1 && list.get(drop).end() < threshold) {
            drop++;
        }
        if (drop > 0) {
            list.subList(0, drop).clear();
        }
        return list;
    }

    /** The flight on at {@code now}: the last one that started at or before it (the first if none). */
    private static Flight flightAt(List<Flight> list, float now) {
        for (int i = list.size() - 1; i > 0; i--) {
            if (list.get(i).startTime() <= now) {
                return list.get(i);
            }
        }
        return list.get(0);
    }

    /** A new flight of meteor {@code m} starting at absolute ToB 2005 frame {@code startFrame}. */
    private Flight path(int m, long startFrame) {
        Random random = randoms[m];
        float yaw = random.nextInt(6283) / 1000f;
        int length = FLIGHT_MIN + random.nextInt(FLIGHT_RANDOM);
        float distance = (DISTANCE_MIN + random.nextInt(DISTANCE_RANDOM)) / 32f;
        float groundYaw = random.nextInt(6283) / 1000f;
        float elevation = random.nextInt(800) / 1000f + 0.5f;
        float skyYaw = groundYaw + (random.nextInt(6283) / 1000f - (float) Math.PI) / 2f;
        float horizontal = SKY * (float) Math.cos(elevation);
        float ex = Lightning.CENTRE + distance * (float) Math.sin(groundYaw);
        float ez = Lightning.CENTRE + distance * (float) Math.cos(groundYaw);
        return new Flight(startFrame, length,
                Lightning.CENTRE + horizontal * (float) Math.sin(skyYaw), SKY * (float) Math.sin(elevation),
                Lightning.CENTRE + horizontal * (float) Math.cos(skyYaw),
                ex, ground.height(ex, ez), ez, yaw, random.nextLong());
    }

    /** The distance from the origin of the segment (ax, az)–(bx, bz). */
    private static float segmentDistance(float ax, float az, float bx, float bz) {
        float dx = bx - ax;
        float dz = bz - az;
        float t = Math.clamp(-(ax * dx + az * dz) / (dx * dx + dz * dz), 0f, 1f);
        return (float) Math.hypot(ax + t * dx, az + t * dz);
    }
}
