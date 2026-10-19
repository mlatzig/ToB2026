package de.latzig.game.tob.client.logic.audio;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import de.latzig.game.tob.client.logic.FloorArea;
import de.latzig.game.tob.client.logic.Motion;
import de.latzig.game.tob.client.logic.Pose;
import de.latzig.game.tob.client.logic.effects.AmbientRandom;
import de.latzig.game.tob.client.logic.effects.Lightning;
import de.latzig.game.tob.client.logic.effects.Meteors;
import de.latzig.game.tob.client.logic.effects.Terrain;
import de.latzig.game.tob.core.model.ObjectFamily;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.Sounds;
import de.latzig.game.tob.core.sim.CellPos;
import de.latzig.game.tob.core.sim.ForcerLifts;
import de.latzig.game.tob.core.sim.SimEvent;
import de.latzig.game.tob.core.sim.SimObject;
import de.latzig.game.tob.core.sim.WorldState;

/**
 * Which sounds an attempt plays (Phase 4c spec decision 2), as ToB 2005 does ({@code gml.cpp}, {@code twr.cpp};
 * report 15 §2–3), from the simulation's events and the world state only ({@code core.sim} is unchanged and only
 * read). One instance per attempt; {@link #onStep} is called once per simulation step with its events.
 * <ul>
 * <li>One-shots on events: the zapper spider's shot ({@code SpiderFired}, 2000), a push ({@code PushStarted}: a
 * spider 0092, an element 0092 at 0.5 fields and detail MED), a klondike ({@code Collected}, 2500), the exchanger
 * ({@code Exchanged}, 5400), a freezer ({@code Frozen}, 5900), a lift's stop ({@code LiftRideFinished},
 * {@code ForcerLiftFinished}, 1901) and the explosions: the first {@code Destroyed} of an object by ToB 2005's
 * routing ({@link #explosion}: a spider 0902, a reflector or prism 0904, the exchanger 0901, everything else 0900)
 * and every eaten floor ({@code FloorEaten}, 0901).</li>
 * <li>Cadences, ToB 2005's per-element countdowns ({@link SoundTuning}): a fixed enemy zapper every 12–19 frames
 * while it fires (not every {@code RayFired}); a rotating enemy zapper once per direction (its first shot after a
 * frame without one); a worm every 4 frames and a hopper every 16 frames while the world is not frozen, both
 * restarted when they start a step (and the hopper when it starts to wait); a moving spider every 4 frames:
 * walking, pushed or riding a lift ({@code bMoving}, {@code twr.cpp:2784-2838}), the first step at once when a
 * move starts after a halt. A freeze holds the elements' countdowns, as ToB 2005 does (the spiders move on). The
 * enemy pushers are silent, as in ToB 2005.</li>
 * <li>Voices (fix wave I1): an element's own sound (its shot, push, steps, klondike, exchanger, freezer and the
 * enemies' cadence sounds) carries the key {@code sound-slot}, so {@code SoundView} restarts the element's one
 * voice as ToB 2005 restarts its buffer; explosions and lift stops carry none.</li>
 * <li>Filters: the sound detail ({@link SoundBank#audible}); the tower's {@code sounds.spiderWalk} switches the
 * spiders' steps, {@code sounds.other} every other game sound (not the panel click, the camera noise or the
 * background loop).</li>
 * </ul>
 * Events outside the tower's floor area are dropped first ({@link FloorArea#filter}), as for the views.
 *
 * <p>{@link #loops} gives the loops that run in a frame, from the world state (ToB 2005 starts and stops them each
 * frame from its element state too): a watcher while the world is not frozen and it is not inert (4200); a wiper
 * while it sweeps (its timer runs, 5600); a time bomb while the world is not frozen (6100), its pitch rising with
 * the burnt fuse of its last {@code FuseTick} ({@link SoundTuning#TIME_BOMB_PITCH_RISE}); a converter always
 * (5500); one forcer loop per forcer type at its first forcer while lifts of its direction move (5800); a lift
 * loop per moving forcer lift and per lift ride (1900); the camera noise (0091) and the tower's background loop.
 * {@link #onAmbient} gives the lightning's and the meteors' sounds: ToB 2005's short explosion (0901) at a strike,
 * the generated thunder 5–8 frames later (merged, {@link SoundTuning#THUNDER_MERGE_FRAMES}) and the deep explosion
 * (0903) at a meteor's impact.</p>
 */
public final class SoundCues {

    /** The {@link AmbientRandom} part of the fixed enemy zappers' cadence. */
    private static final int ZAP_RANDOM_PART = 10;
    /** The {@link AmbientRandom} part of the thunder's delay. */
    private static final int THUNDER_RANDOM_PART = 11;

    /** Sounds {@code sounds.other} = false does not mute. */
    private static final Set<String> NOT_OTHER = Set.of(SoundBank.CLICK, SoundBank.NOISE, SoundBank.SPIDER_STEP,
            SoundBank.BREATH, SoundBank.DRONE_A, SoundBank.DRONE_B);

    private final WorldState world;
    private final SoundBank bank;
    private final Sounds flags;
    private final int detail;
    private final FloorArea area;
    private final Random zapRandom;
    private final Random thunderRandom;
    /** The tower's background loop, or null. */
    private final String background;
    private final Set<Integer> exploded = new HashSet<>();
    /** Per slot: the frame of the next cadence sound (fixed enemy zappers, worms, hoppers, spiders). */
    private final Map<Integer, Integer> next = new HashMap<>();
    /** The rotating enemy zappers that fired in the previous unfrozen step. */
    private Set<Integer> rotatingFired = new HashSet<>();
    /** The simulation frame of the previous step (a freeze holds the countdowns frame by frame). */
    private int lastFrame;
    /** Per time bomb: the burnt fuse in percent of its last {@code FuseTick}. */
    private final Map<Integer, Integer> fuse = new HashMap<>();
    /** The pending thunder's simulation time (NaN: none) and place. */
    private float thunderAt = Float.NaN;
    private float thunderX;
    private float thunderZ;
    private float lastThunder = Float.NEGATIVE_INFINITY;

    /** The sound detail of this tower's cues (tests: Phase 5b takes it from the settings at the tower's start). */
    public int detail() {
        return detail;
    }

    /**
     * @param flags  the tower's sound switches ({@code TowerDefinition.sounds()})
     * @param detail the sound detail, {@link SoundTuning#DETAIL_NONE} .. {@link SoundTuning#DETAIL_HIGH}
     * @param seed   the tower's seed (the same tower, the same cadences)
     */
    public SoundCues(WorldState world, SoundBank bank, Sounds flags, int detail, long seed) {
        this.world = world;
        this.bank = bank;
        this.flags = flags;
        this.detail = detail;
        this.area = FloorArea.of(world.tower());
        this.zapRandom = AmbientRandom.of(seed, ZAP_RANDOM_PART);
        this.thunderRandom = AmbientRandom.of(seed, THUNDER_RANDOM_PART);
        this.background = SoundBank.background(flags.background());
        this.lastFrame = world.frame();
    }

    /**
     * The explosion sound of a destroyed object: ToB 2005's routing ({@code twr.cpp:2958-2964, 3592-3769}). The
     * lizards are not in ToB 2005's switch (it never destroys them); ToB 2026 gives them the "rest" explosion
     * (Phase 4b-1), so they play the normal explosion 0900 (a decision, results note).
     */
    public static String explosion(ObjectType type) {
        if (type.isSpider()) {
            return SoundBank.EXPLOSION_BIG;
        }
        return switch (type.family()) {
            case REFLECTOR, PRISM -> SoundBank.EXPLOSION_GLASS;
            case EXCHANGER -> SoundBank.EXPLOSION_SHORT;
            default -> SoundBank.EXPLOSION;
        };
    }

    /** The one-shots of one simulation step's events (called once per step, after it). */
    public List<SoundCue.OneShot> onStep(List<SimEvent> events) {
        List<SoundCue.OneShot> out = new ArrayList<>();
        Set<Integer> rotatingNow = new HashSet<>();
        int frame = world.frame();
        boolean frozen = world.isFrozen();
        if (frozen && frame != lastFrame) {
            hold(frame - lastFrame);
        }
        lastFrame = frame;
        for (SimEvent event : area.filter(events)) {
            switch (event) {
                case SimEvent.SpiderFired fired -> {
                    if (world.object(fired.slot()).type() == ObjectType.ZAP) {
                        add(out, SoundBank.ZAP, fired.slot());
                    }
                }
                case SimEvent.PushStarted push -> add(out, world.object(push.slot()).isSpider() ? SoundBank.PUSH
                        : SoundBank.PUSH_ELEMENT, push.slot());
                case SimEvent.Collected collected -> add(out, SoundBank.KLONDIKE, collected.slot());
                case SimEvent.Exchanged exchanged -> add(out, SoundBank.EXCHANGER, exchanged.exchangerSlot());
                case SimEvent.Frozen freezer -> add(out, SoundBank.FREEZER, freezer.slot());
                case SimEvent.Destroyed destroyed -> {
                    if (exploded.add(destroyed.slot())) {
                        explode(out, explosion(world.object(destroyed.slot()).type()), destroyed.slot());
                    }
                }
                case SimEvent.FloorEaten eaten -> add(out, SoundBank.EXPLOSION_SHORT, eaten.cell(), 0f);
                case SimEvent.LiftRideFinished ride -> add(out, SoundBank.LIFT_STOP, ride.cell(),
                        SoundTuning.LIFT_SOUND_HEIGHT);
                case SimEvent.ForcerLiftFinished lift -> add(out, SoundBank.LIFT_STOP, lift.cell(),
                        SoundTuning.LIFT_SOUND_HEIGHT);
                case SimEvent.RayFired ray -> enemyShot(out, ray.slot(), frame, rotatingNow);
                case SimEvent.MoveStarted move -> restartCadence(out, move.slot(), frame, true);
                case SimEvent.Idle idle -> restartCadence(out, idle.slot(), frame, false);
                case SimEvent.FuseTick tick -> fuse.put(tick.slot(), tick.percent());
                default -> {
                }
            }
        }
        if (!frozen) {
            rotatingFired = rotatingNow;
        }
        cadences(out, frame);
        return out;
    }

    /**
     * A frozen world holds the elements' sound countdowns (fix wave, Minor 1): ToB 2005 counts them down only while
     * the element runs, so after a thaw each one goes on where it stopped. The spiders move on while frozen.
     */
    private void hold(int frames) {
        for (Map.Entry<Integer, Integer> entry : next.entrySet()) {
            if (!world.object(entry.getKey()).isSpider()) {
                entry.setValue(entry.getValue() + frames);
            }
        }
    }

    /**
     * The ambient effects' one-shots up to simulation time {@code now}: the strikes and impacts since the last call
     * (from {@code LightningView} and {@code MeteorView}) and the thunder that has become due.
     */
    public List<SoundCue.OneShot> onAmbient(List<Lightning.Strike> strikes, List<Meteors.Impact> impacts,
            float now) {
        List<SoundCue.OneShot> out = new ArrayList<>();
        for (Lightning.Strike strike : strikes) {
            thunder(out, strike.time());
            add(out, SoundBank.EXPLOSION_SHORT, strike.x(), Terrain.FLAT_Y, strike.z());
            thunderAt = strike.time() + SoundTuning.THUNDER_DELAY_MIN
                    + thunderRandom.nextInt(SoundTuning.THUNDER_DELAY_RANDOM);
            thunderX = strike.x();
            thunderZ = strike.z();
        }
        for (Meteors.Impact impact : impacts) {
            add(out, SoundBank.EXPLOSION_DEEP, impact.x(), impact.y(), impact.z());
        }
        thunder(out, now);
        return out;
    }

    /** The pending thunder, if it is due at {@code time}; dropped while the last thunder still rolls. */
    private void thunder(List<SoundCue.OneShot> out, float time) {
        if (thunderAt <= time) {
            if (thunderAt - lastThunder >= SoundTuning.THUNDER_MERGE_FRAMES) {
                add(out, SoundBank.THUNDER, thunderX, Terrain.FLAT_Y, thunderZ);
                lastThunder = thunderAt;
            }
            thunderAt = Float.NaN;
        }
    }

    /**
     * The loops of this frame at {@code alpha} of the next simulation frame; {@code noise}: the shown camera is
     * disabled or broken ({@code CameraController.showsNoise}).
     */
    public List<SoundCue.Loop> loops(float alpha, boolean noise) {
        List<SoundCue.Loop> out = new ArrayList<>();
        boolean frozen = world.isFrozen();
        ForcerLifts forcers = world.forcerLifts();
        boolean forcing = forcers.isActive() && !frozen && !forcers.platforms().isEmpty();
        Set<ObjectFamily> forcerLoops = new HashSet<>();
        for (SimObject object : world.objects()) {
            if (object.cell() == null || !object.isAlive()) {
                continue;
            }
            int slot = object.slot();
            ObjectFamily family = object.type().family();
            switch (family) {
                case WATCHER -> {
                    if (!frozen && !object.isDeactivated()) {
                        loop(out, SoundBank.WATCHER + "-" + slot, SoundBank.WATCHER, object, alpha, 0f, 1f);
                    }
                }
                case WIPER -> {
                    if (!frozen && object.classTimer() != 0) {
                        loop(out, SoundBank.WIPER + "-" + slot, SoundBank.WIPER, object, alpha, 0f, 1f);
                    }
                }
                case TIME_BOMB -> {
                    if (!frozen) {
                        float burnt = fuse.getOrDefault(slot, 0) / 100f;
                        loop(out, SoundBank.TIME_BOMB + "-" + slot, SoundBank.TIME_BOMB, object, alpha, 0f,
                                1f + SoundTuning.TIME_BOMB_PITCH_RISE * burnt);
                    }
                }
                case CONVERTER -> loop(out, SoundBank.CONVERTER + "-" + slot, SoundBank.CONVERTER, object, alpha, 0f,
                        1f);
                case FORCER_UP, FORCER_DOWN -> {
                    if (forcing && forcers.isUp() == (family == ObjectFamily.FORCER_UP) && forcerLoops.add(family)) {
                        loop(out, family == ObjectFamily.FORCER_UP ? "forcer-up" : "forcer-down", SoundBank.FORCER,
                                object, alpha, 0f, 1f);
                    }
                }
                default -> {
                }
            }
            if (object.isRiding() && (!frozen || object.isSpider())) {
                loop(out, "ride-" + slot, SoundBank.LIFT_RUN, object, alpha, SoundTuning.LIFT_SOUND_HEIGHT, 1f);
            }
        }
        if (forcers.isActive() && !frozen && audible(SoundBank.LIFT_RUN)) {
            for (CellPos platform : forcers.platforms()) {
                out.add(new SoundCue.Loop("lift-" + platform.index(), SoundBank.LIFT_RUN, platform.x(),
                        platform.level() + SoundTuning.LIFT_SOUND_HEIGHT, platform.y(), 1f));
            }
        }
        if (noise && audible(SoundBank.NOISE)) {
            out.add(new SoundCue.Loop("noise", SoundBank.NOISE, 0f, 0f, 0f, 1f));
        }
        if (background != null && audible(background)) {
            out.add(new SoundCue.Loop("background", background, 0f, 0f, 0f, 1f));
        }
        return out;
    }

    private void loop(List<SoundCue.Loop> out, String key, String sound, SimObject object, float alpha, float lift,
            float pitch) {
        if (audible(sound)) {
            Pose at = Motion.position(object, world.isFrozen(), alpha);
            out.add(new SoundCue.Loop(key, sound, at.x(), at.y() + lift, at.z(), pitch));
        }
    }

    /** The panel click (a panel button pressed), unless the sound detail leaves it out. */
    public Optional<SoundCue.OneShot> click() {
        return audible(SoundBank.CLICK) ? Optional.of(new SoundCue.OneShot(SoundBank.CLICK, 0f, 0f, 0f))
                : Optional.empty();
    }

    /** ToB 2005's enemy zapper sounds ({@code gml.cpp:1085-1094, 1131-1139}); the player's shots are not here. */
    private void enemyShot(List<SoundCue.OneShot> out, int slot, int frame, Set<Integer> rotatingNow) {
        SimObject shooter = world.object(slot);
        if (shooter.type().family() == ObjectFamily.ENEMY_ZAPPER) {
            if (frame >= next.getOrDefault(slot, 0)) {
                add(out, SoundBank.ENEMY_ZAPPER, slot);
                next.put(slot, frame + SoundTuning.ENEMY_ZAPPER_FRAMES
                        + zapRandom.nextInt(SoundTuning.ENEMY_ZAPPER_RANDOM));
            }
        } else if (shooter.type().family() == ObjectFamily.ROTATING_ZAPPER) {
            if (rotatingNow.add(slot) && !rotatingFired.contains(slot)) {
                add(out, SoundBank.ROTATING_ZAPPER, slot);
            }
        }
    }

    /**
     * A worm or hopper starts a step (or a hopper its wait): its sound plays now and its cadence starts over
     * ({@code gml.cpp:1186-1188, 1340-1346}).
     */
    private void restartCadence(List<SoundCue.OneShot> out, int slot, int frame, boolean step) {
        ObjectFamily family = world.object(slot).type().family();
        if (family == ObjectFamily.WORM && step) {
            add(out, SoundBank.WORM, slot);
            next.put(slot, frame + SoundTuning.WORM_STEP_FRAMES);
        } else if (family == ObjectFamily.HOPPER) {
            add(out, SoundBank.HOPPER, slot);
            next.put(slot, frame + SoundTuning.HOPPER_STEP_FRAMES);
        }
    }

    /**
     * The worms', hoppers' and moving spiders' sounds that are due ({@code gml.cpp:1196-1200, 1347-1351},
     * {@code twr.cpp:2813-2838}).
     */
    private void cadences(List<SoundCue.OneShot> out, int frame) {
        boolean frozen = world.isFrozen();
        for (SimObject object : world.objects()) {
            if (object.cell() == null || !object.isAlive()) {
                continue;
            }
            ObjectFamily family = object.type().family();
            int period;
            String sound;
            if (object.isSpider()) {
                // every move steps: walking, pushed, riding a lift (bMoving); a halt resets the countdown
                // (fSndFrame = 0), so the next move steps at once
                if (!object.isMoving()) {
                    next.remove(object.slot());
                    continue;
                }
                period = SoundTuning.SPIDER_STEP_FRAMES;
                sound = SoundBank.SPIDER_STEP;
            } else if (family == ObjectFamily.WORM && !frozen && !object.isDeactivated()) {
                period = SoundTuning.WORM_STEP_FRAMES;
                sound = SoundBank.WORM;
            } else if (family == ObjectFamily.HOPPER && !frozen) {
                period = SoundTuning.HOPPER_STEP_FRAMES;
                sound = SoundBank.HOPPER;
            } else {
                continue;
            }
            if (frame >= next.getOrDefault(object.slot(), 0)) {
                add(out, sound, object.slot());
                next.put(object.slot(), frame + period);
            }
        }
    }

    /** An element's own sound at the element, keyed to its one voice. */
    private void add(List<SoundCue.OneShot> out, String sound, int slot) {
        if (audible(sound)) {
            Pose at = Motion.position(world.object(slot), world.isFrozen(), 0f);
            out.add(new SoundCue.OneShot(sound, at.x(), at.y(), at.z(), sound + "-" + slot));
        }
    }

    /** An object's explosion at the object, without a key ({@code SoundView}'s explosion budget). */
    private void explode(List<SoundCue.OneShot> out, String sound, int slot) {
        if (audible(sound)) {
            Pose at = Motion.position(world.object(slot), world.isFrozen(), 0f);
            out.add(new SoundCue.OneShot(sound, at.x(), at.y(), at.z()));
        }
    }

    private void add(List<SoundCue.OneShot> out, String sound, float x, float y, float z) {
        if (audible(sound)) {
            out.add(new SoundCue.OneShot(sound, x, y, z));
        }
    }

    private void add(List<SoundCue.OneShot> out, String sound, CellPos cell, float lift) {
        if (audible(sound)) {
            out.add(new SoundCue.OneShot(sound, cell.x(), cell.level() + lift, cell.y()));
        }
    }

    /** The sound detail and the tower's switches ({@code sounds.spiderWalk}, {@code sounds.other}). */
    private boolean audible(String sound) {
        if (!bank.audible(sound, detail)) {
            return false;
        }
        if (sound.equals(SoundBank.SPIDER_STEP)) {
            return flags.spiderWalk();
        }
        return flags.other() || NOT_OTHER.contains(sound);
    }
}
