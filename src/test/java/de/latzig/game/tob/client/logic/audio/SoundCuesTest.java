package de.latzig.game.tob.client.logic.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.client.logic.effects.Lightning;
import de.latzig.game.tob.client.logic.effects.Meteors;
import de.latzig.game.tob.client.logic.effects.Terrain;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.Sounds;
import de.latzig.game.tob.core.sim.CellPos;
import de.latzig.game.tob.core.sim.Command;
import de.latzig.game.tob.core.sim.Direction;
import de.latzig.game.tob.core.sim.SimEvent;
import de.latzig.game.tob.core.sim.SimOptions;
import de.latzig.game.tob.core.sim.SimRun;
import de.latzig.game.tob.core.sim.SpiderId;
import de.latzig.game.tob.core.sim.TestTowers;
import de.latzig.game.tob.core.sim.WorldState;

class SoundCuesTest {

    private static final Sounds ALL = new Sounds(0, true, true);
    private static final long SEED = 42L;

    /** A cue and the frame of the step that played it. */
    private record Heard(int frame, SoundCue.OneShot cue) {
    }

    /** Runs {@code steps} steps without input and keeps every one-shot with its frame. */
    private static List<Heard> run(SimRun run, SoundCues cues, int steps) {
        List<Heard> heard = new ArrayList<>();
        for (int i = 0; i < steps; i++) {
            List<SimEvent> events = run.step();
            for (SoundCue.OneShot cue : cues.onStep(events)) {
                heard.add(new Heard(run.sim.frame(), cue));
            }
        }
        return heard;
    }

    private static List<Integer> frames(List<Heard> heard, String sound) {
        return heard.stream().filter(h -> h.cue().sound().equals(sound)).map(Heard::frame).toList();
    }

    private static int slot(WorldState world, ObjectType type) {
        return world.objects().stream().filter(o -> o.type() == type).findFirst().orElseThrow().slot();
    }

    private static SoundCue.OneShot at(String sound, CellPos cell, float lift) {
        return new SoundCue.OneShot(sound, cell.x(), cell.level() + lift, cell.y());
    }

    /** An element's own sound (fix wave I1): keyed by sound and slot, so a replay restarts its one voice. */
    private static SoundCue.OneShot own(String sound, CellPos cell, int slot) {
        return new SoundCue.OneShot(sound, cell.x(), cell.level(), cell.y(), sound + "-" + slot);
    }

    private static WorldState world() {
        return WorldState.load(TestTowers.tower().level(0, "FFFF", "FFFF").object(0, 1, 0, ObjectType.PSH)
                .object(1, 1, 0, ObjectType.ZAP).object(0, 0, 0, ObjectType.KLD).object(1, 0, 0, ObjectType.FRZ)
                .object(2, 0, 0, ObjectType.EXC).object(3, 0, 0, ObjectType.BLK).build(), SimOptions.DEFAULT);
    }

    /** Report 15 §3: the events with a 1:1 sound play it once, where the object or cell is. */
    @Test
    void eventsPlayTheirOneShotsWhereTheObjectOrCellIs() {
        WorldState world = world();
        SoundCues cues = new SoundCues(world, SoundTestBank.bank(), ALL, SoundTuning.DETAIL_HIGH, SEED);
        int zap = slot(world, ObjectType.ZAP);
        int psh = slot(world, ObjectType.PSH);
        int blk = slot(world, ObjectType.BLK);
        CellPos zapCell = TestTowers.pos(1, 1, 0);
        CellPos blkCell = TestTowers.pos(3, 0, 0);
        CellPos floor = TestTowers.pos(2, 1, 0);

        List<SoundCue.OneShot> heard = cues.onStep(List.of(
                new SimEvent.SpiderFired(1, zap, zapCell, Direction.NORTH),
                new SimEvent.SpiderFired(1, psh, TestTowers.pos(0, 1, 0), Direction.NORTH),
                new SimEvent.PushStarted(1, blk, blkCell, TestTowers.pos(4, 0, 0)),
                new SimEvent.PushStarted(1, psh, TestTowers.pos(0, 1, 0), TestTowers.pos(0, 2, 0)),
                new SimEvent.Collected(1, slot(world, ObjectType.KLD), 2),
                new SimEvent.Exchanged(1, 2, slot(world, ObjectType.EXC)),
                new SimEvent.Frozen(1, slot(world, ObjectType.FRZ)),
                new SimEvent.LiftRideFinished(1, zap, zapCell),
                new SimEvent.ForcerLiftFinished(1, floor),
                new SimEvent.FloorEaten(1, floor),
                new SimEvent.FloorEaten(1, new CellPos(0, 0, 0))));

        assertEquals(List.of(own(SoundBank.ZAP, zapCell, zap), own(SoundBank.PUSH_ELEMENT, blkCell, blk),
                own(SoundBank.PUSH, TestTowers.pos(0, 1, 0), psh),
                own(SoundBank.KLONDIKE, TestTowers.pos(0, 0, 0), slot(world, ObjectType.KLD)),
                own(SoundBank.EXCHANGER, TestTowers.pos(2, 0, 0), slot(world, ObjectType.EXC)),
                own(SoundBank.FREEZER, TestTowers.pos(1, 0, 0), slot(world, ObjectType.FRZ)),
                at(SoundBank.LIFT_STOP, zapCell, 0.5f), at(SoundBank.LIFT_STOP, floor, 0.5f),
                at(SoundBank.EXPLOSION_SHORT, floor, 0f)), heard,
                "the pusher's shot is silent; a floor outside the tower's floor area is dropped; an element's own "
                        + "sound carries its key (fix wave I1), lift stops and explosions do not");
    }

    /** Spec decision 2: ToB 2005's explosion routing per destroyed object, once per object. */
    @Test
    void aDestroyedObjectPlaysToB2005sExplosionSoundOnce() {
        for (ObjectType type : ObjectType.values()) {
            String expected = switch (type) {
                case ZAP, PSH, GRB -> SoundBank.EXPLOSION_BIG;
                case REL, PNE, PSE, PSW, PNW -> SoundBank.EXPLOSION_GLASS;
                case EXC -> SoundBank.EXPLOSION_SHORT;
                default -> SoundBank.EXPLOSION;
            };
            assertEquals(expected, SoundCues.explosion(type), type.code());
        }
        WorldState world = world();
        SoundCues cues = new SoundCues(world, SoundTestBank.bank(), ALL, SoundTuning.DETAIL_HIGH, SEED);
        int blk = slot(world, ObjectType.BLK);
        CellPos cell = TestTowers.pos(3, 0, 0);

        assertEquals(List.of(at(SoundBank.EXPLOSION, cell, 0f)), cues.onStep(List.of(
                new SimEvent.Destroyed(5, blk, cell), new SimEvent.Destroyed(5, blk, cell))));
        assertEquals(List.of(), cues.onStep(List.of(new SimEvent.Destroyed(9, blk, cell))), "re-armed by a blast");
    }

    /** Spec decision 2: a fixed enemy zapper fires every frame but sounds every 12 + rand(8) frames. */
    @Test
    void aFixedEnemyZapperSoundsOnToB2005sCountdownNotOnEveryShot() {
        SimRun run = SimRun.startTime(TestTowers.tower().level(0, "FF").object(0, 0, 0, ObjectType.FZE));
        SoundCues cues = new SoundCues(run.sim.world(), SoundTestBank.bank(), ALL, SoundTuning.DETAIL_HIGH, SEED);

        List<Integer> frames = frames(run(run, cues, 80), SoundBank.ENEMY_ZAPPER);

        assertEquals(1, frames.get(0), "at its first shot");
        assertTrue(frames.size() >= 4 && frames.size() <= 7, frames.toString());
        for (int i = 1; i < frames.size(); i++) {
            int gap = frames.get(i) - frames.get(i - 1);
            assertTrue(gap >= 12 && gap <= 19, "gap " + gap + " in " + frames);
        }
    }

    /** ToB 2005 plays the rotating zapper once per direction: here at the start of each still phase. */
    @Test
    void aRotatingEnemyZapperSoundsOncePerDirection() {
        SimRun run = SimRun.startTime(TestTowers.tower().level(0, "F").object(3, 3, 0, ObjectType.RZN));
        SoundCues cues = new SoundCues(run.sim.world(), SoundTestBank.bank(), ALL, SoundTuning.DETAIL_HIGH, SEED);

        assertEquals(List.of(1, 16, 32, 48, 64), frames(run(run, cues, 64), SoundBank.ROTATING_ZAPPER));
    }

    /**
     * ToB 2005's step sounds: a hopper every 16 frames and a worm every 4, both restarted when a step starts; a
     * walking spider every 4 frames, unless the tower switches it off ({@code sounds.spiderWalk}).
     */
    @Test
    void hoppersWormsAndWalkingSpidersSoundOnTheirOwnCadence() {
        SimRun hopper = SimRun.startTime(TestTowers.tower().level(0, "FFF").object(0, 0, 0, ObjectType.HOP));
        SoundCues hopperCues = new SoundCues(hopper.sim.world(), SoundTestBank.bank(), ALL, SoundTuning.DETAIL_HIGH,
                SEED);
        assertEquals(List.of(1, 17, 33, 49), frames(run(hopper, hopperCues, 56), SoundBank.HOPPER));

        SimRun worm = SimRun.startTime(TestTowers.tower().level(0, "FFFF").object(0, 0, 0, ObjectType.WOE));
        SoundCues wormCues = new SoundCues(worm.sim.world(), SoundTestBank.bank(), ALL, SoundTuning.DETAIL_HIGH,
                SEED);
        assertEquals(List.of(1, 5, 9, 13, 17, 21, 25, 29, 33, 37), frames(run(worm, wormCues, 40), SoundBank.WORM));

        assertEquals(List.of(0, 4, 8, 12, 16, 20, 24, 28), walk(ALL), "8 steps per field");
        assertEquals(List.of(), walk(new Sounds(0, false, true)), "spiderWalk off");
        assertEquals(List.of(0, 4, 8, 12, 16, 20, 24, 28), walk(new Sounds(0, true, false)), "not muted by other");
    }

    /** The zapper spider walks one field; the frames of its step sounds. */
    private static List<Integer> walk(Sounds flags) {
        SimRun run = new SimRun(TestTowers.tower().level(0, "F", "F").object(0, 0, 0, ObjectType.ZAP).build());
        SoundCues cues = new SoundCues(run.sim.world(), SoundTestBank.bank(), flags, SoundTuning.DETAIL_HIGH, SEED);
        List<Heard> heard = new ArrayList<>();
        for (SoundCue.OneShot cue : cues.onStep(run.step(new Command.Forward(SpiderId.ZAPPER)))) {
            heard.add(new Heard(run.sim.frame(), cue));
        }
        heard.addAll(run(run, cues, 40));
        return frames(heard, SoundBank.SPIDER_STEP);
    }

    private static List<String> keys(List<SoundCue.Loop> loops) {
        return loops.stream().map(SoundCue.Loop::key).sorted().toList();
    }

    /**
     * Loops follow the world state: in READY (the world frozen) only the converter runs; then the watcher too; the
     * camera noise and the tower's background loop (spec decision 5) run without a position, not muted by
     * {@code sounds.other}.
     */
    @Test
    void watcherConverterNoiseAndBackgroundLoopRunWhileTheyShould() {
        SimRun run = new SimRun(TestTowers.tower().level(0, "FFF", "FFF").object(0, 0, 0, ObjectType.WAT)
                .object(2, 1, 0, ObjectType.CVN).object(7, 7, 0, ObjectType.ZAP).build());
        WorldState world = run.sim.world();
        String watcher = SoundBank.WATCHER + "-" + slot(world, ObjectType.WAT);
        String converter = SoundBank.CONVERTER + "-" + slot(world, ObjectType.CVN);
        SoundCues cues = new SoundCues(world, SoundTestBank.bank(), new Sounds(2, true, true), SoundTuning.DETAIL_HIGH,
                SEED);

        assertEquals(List.of(converter, "background"), keys(cues.loops(0f, false)), "READY: the world is frozen");
        run.step(new Command.Fire(SpiderId.ZAPPER));
        assertEquals(List.of(watcher, converter, "background", "noise").stream().sorted().toList(),
                keys(cues.loops(0f, true)));
        CellPos cell = TestTowers.pos(2, 1, 0);
        assertTrue(cues.loops(0f, true).containsAll(List.of(
                new SoundCue.Loop(converter, SoundBank.CONVERTER, cell.x(), cell.level(), cell.y(), 1f),
                new SoundCue.Loop("noise", SoundBank.NOISE, 0f, 0f, 0f, 1f),
                new SoundCue.Loop("background", SoundBank.DRONE_A, 0f, 0f, 0f, 1f))));

        SoundCues quiet = new SoundCues(world, SoundTestBank.bank(), new Sounds(1, true, false),
                SoundTuning.DETAIL_HIGH, SEED);
        assertEquals(List.of("background", "noise"), keys(quiet.loops(0f, true)), "sounds.other off");
        assertEquals(SoundBank.BREATH, quiet.loops(0f, false).get(0).sound());
    }

    /**
     * A grabbed wiper's loop runs while it sweeps; a grabbed forcer's loop runs at the forcer and a lift loop at
     * every moving lift until the lifts stop (1901).
     */
    @Test
    void wiperForcerAndLiftLoopsRunWhileTheyMove() {
        SimRun wiper = SimRun.startTime(TestTowers.tower().level(0, "F..", "F..", "F..")
                .object(0, 0, 0, ObjectType.GRB).object(0, 2, 0, ObjectType.WIP));
        SoundCues wiperCues = new SoundCues(wiper.sim.world(), SoundTestBank.bank(), ALL, SoundTuning.DETAIL_HIGH,
                SEED);
        String sweeping = SoundBank.WIPER + "-" + slot(wiper.sim.world(), ObjectType.WIP);
        assertEquals(List.of(), keys(wiperCues.loops(0f, false)));
        wiperCues.onStep(wiper.step(new Command.Fire(SpiderId.GRABBER)));
        assertEquals(List.of(sweeping), keys(wiperCues.loops(0f, false)));
        run(wiper, wiperCues, 63);
        assertEquals(List.of(), keys(wiperCues.loops(0f, false)), "four sweeps, 63 frames");

        SimRun forcer = SimRun.startTime(TestTowers.tower().level(0, "F..", "F..", "F.U").level(1, "..u")
                .object(0, 0, 0, ObjectType.GRB).object(0, 2, 0, ObjectType.FCU));
        forcer.runUntilFrame(9);
        SoundCues forcerCues = new SoundCues(forcer.sim.world(), SoundTestBank.bank(), ALL, SoundTuning.DETAIL_HIGH,
                SEED);
        CellPos lift = TestTowers.pos(2, 0, 0);
        CellPos forcerCell = TestTowers.pos(0, 2, 0);

        forcerCues.onStep(forcer.step(new Command.Fire(SpiderId.GRABBER)));

        assertEquals(List.of(new SoundCue.Loop("forcer-up", SoundBank.FORCER, forcerCell.x(), forcerCell.level(),
                forcerCell.y(), 1f), new SoundCue.Loop("lift-" + lift.index(), SoundBank.LIFT_RUN, lift.x(),
                lift.level() + 0.5f, lift.y(), 1f)), forcerCues.loops(0f, false));
        List<Heard> heard = run(forcer, forcerCues, 31);
        assertEquals(List.of(new Heard(41, at(SoundBank.LIFT_STOP, lift, 0.5f))),
                heard.stream().filter(h -> h.cue().sound().equals(SoundBank.LIFT_STOP)).toList());
        assertEquals(List.of(), forcerCues.loops(0f, false));
    }

    /** Spec decision 6: the time bomb's loop rises in pitch with the burnt fuse and ends with its explosion. */
    @Test
    void theTimeBombsLoopRisesInPitchWithItsFuse() {
        SimRun run = SimRun.startTime(TestTowers.tower().level(0, "F").object(0, 0, 0, ObjectType.TMB)
                .timeBombSpeed(3));
        SoundCues cues = new SoundCues(run.sim.world(), SoundTestBank.bank(), ALL, SoundTuning.DETAIL_HIGH, SEED);
        assertEquals(1f, cues.loops(0f, false).get(0).pitch());

        run(run, cues, 100);
        assertEquals(1f + SoundTuning.TIME_BOMB_PITCH_RISE * 0.5f, cues.loops(0f, false).get(0).pitch(), 1e-6f,
                "speed 3: half the fuse burnt in frame 100");
        List<Heard> heard = run(run, cues, 100);
        assertEquals(List.of(200), frames(heard, SoundBank.EXPLOSION), "it explodes in frame 200");
        assertEquals(List.of(), cues.loops(0f, false));
    }

    /** The thunders of {@code cues} from {@code from} to {@code to}, a quarter frame apart. */
    private static List<Float> thunders(SoundCues cues, float from, float to) {
        List<Float> times = new ArrayList<>();
        for (float now = from; now <= to; now += 0.25f) {
            for (SoundCue.OneShot cue : cues.onAmbient(List.of(), List.of(), now)) {
                if (cue.sound().equals(SoundBank.THUNDER)) {
                    times.add(now);
                }
            }
        }
        return times;
    }

    /**
     * Spec decisions 3 and 4: a strike cracks at once (0901) and thunders 5–8 frames later; a re-strike before the
     * thunder moves it; a thunder while the last one still rolls is dropped; a meteor's impact plays 0903.
     */
    @Test
    void aStrikeCracksAndThundersFiveToEightFramesLaterAndThundersMerge() {
        SoundCues cues = new SoundCues(world(), SoundTestBank.bank(), ALL, SoundTuning.DETAIL_HIGH, SEED);

        assertEquals(List.of(new SoundCue.OneShot(SoundBank.EXPLOSION_SHORT, 20f, Terrain.FLAT_Y, 30f)),
                cues.onAmbient(List.of(new Lightning.Strike(0, 10f, 20f, 30f, 1L)), List.of(), 10f));
        List<Float> first = thunders(cues, 10.25f, 30f);
        assertEquals(1, first.size());
        assertTrue(first.get(0) >= 15f && first.get(0) <= 18f, first.toString());

        cues.onAmbient(List.of(new Lightning.Strike(1, 50f, 5f, 5f, 2L)), List.of(), 50f);
        cues.onAmbient(List.of(new Lightning.Strike(1, 54f, 5f, 5f, 3L)), List.of(), 54f);
        List<Float> burst = thunders(cues, 54.25f, 80f);
        assertEquals(1, burst.size(), "a burst thunders once");
        assertTrue(burst.get(0) >= 59f && burst.get(0) <= 62f, "after the re-strike: " + burst);

        cues.onAmbient(List.of(new Lightning.Strike(2, burst.get(0) + 1f, 5f, 5f, 4L)), List.of(), burst.get(0) + 1f);
        assertEquals(List.of(), thunders(cues, burst.get(0) + 1.25f, burst.get(0) + 20f), "the last still rolls");

        assertEquals(List.of(new SoundCue.OneShot(SoundBank.EXPLOSION_DEEP, 1f, 2f, 3f)), cues.onAmbient(List.of(),
                List.of(new Meteors.Impact(0, 200f, 1f, 2f, 3f, 0f, 5L)), 200f));
    }

    /** ToB 2005's sound detail and the tower's {@code sounds.other} switch. */
    @Test
    void theSoundDetailAndTheOtherSwitchLeaveSoundsOut() {
        WorldState world = world();
        int psh = slot(world, ObjectType.PSH);
        List<SimEvent> events = List.of(new SimEvent.Collected(1, slot(world, ObjectType.KLD), 2),
                new SimEvent.PushStarted(1, psh, TestTowers.pos(0, 1, 0), TestTowers.pos(0, 2, 0)));

        SoundCues low = new SoundCues(world, SoundTestBank.bank(), ALL, SoundTuning.DETAIL_LOW, SEED);
        assertEquals(List.of(SoundBank.PUSH), low.onStep(events).stream().map(SoundCue::sound).toList(),
                "the klondike is MED, the spider's push LOW");
        assertEquals(Optional.empty(), low.click(), "the click is MED");

        SoundCues none = new SoundCues(world, SoundTestBank.bank(), ALL, SoundTuning.DETAIL_NONE, SEED);
        assertEquals(List.of(), none.onStep(events));

        SoundCues quiet = new SoundCues(world, SoundTestBank.bank(), new Sounds(0, true, false),
                SoundTuning.DETAIL_HIGH, SEED);
        assertEquals(List.of(), quiet.onStep(events), "sounds.other off");
        assertEquals(Optional.of(new SoundCue.OneShot(SoundBank.CLICK, 0f, 0f, 0f)), quiet.click(),
                "the panel click is no game sound");
    }

    /** The keys of the one-shots of {@code sound} in {@code heard}. */
    private static List<String> keysOf(List<Heard> heard, String sound) {
        return heard.stream().filter(h -> h.cue().sound().equals(sound)).map(h -> h.cue().key()).distinct()
                .toList();
    }

    /**
     * Fix wave I1: ToB 2005 gives every element its own buffer and restarts it ({@code snd.cpp:971-996}); the
     * cadence sounds carry the element's key, the explosions none (their own budget, {@code TWR_EXPLOSION_MAX}).
     */
    @Test
    void cadenceSoundsCarryTheirElementsKeyAndExplosionsNone() {
        SimRun hopper = SimRun.startTime(TestTowers.tower().level(0, "FFF").object(0, 0, 0, ObjectType.HOP)
                .object(2, 2, 0, ObjectType.FZE));
        int hop = slot(hopper.sim.world(), ObjectType.HOP);
        int fze = slot(hopper.sim.world(), ObjectType.FZE);
        SoundCues cues = new SoundCues(hopper.sim.world(), SoundTestBank.bank(), ALL, SoundTuning.DETAIL_HIGH, SEED);

        List<Heard> heard = run(hopper, cues, 40);

        assertEquals(List.of(SoundBank.HOPPER + "-" + hop), keysOf(heard, SoundBank.HOPPER));
        assertEquals(List.of(SoundBank.ENEMY_ZAPPER + "-" + fze), keysOf(heard, SoundBank.ENEMY_ZAPPER));
        assertEquals(List.of(SoundBank.SPIDER_STEP + "-0"), walkKeys());
        WorldState world = world();
        SoundCues blast = new SoundCues(world, SoundTestBank.bank(), ALL, SoundTuning.DETAIL_HIGH, SEED);
        int blk = slot(world, ObjectType.BLK);
        assertNull(blast.onStep(List.of(new SimEvent.Destroyed(5, blk, TestTowers.pos(3, 0, 0)))).get(0).key());
    }

    private static List<String> walkKeys() {
        SimRun run = new SimRun(TestTowers.tower().level(0, "F", "F").object(0, 0, 0, ObjectType.ZAP).build());
        SoundCues cues = new SoundCues(run.sim.world(), SoundTestBank.bank(), ALL, SoundTuning.DETAIL_HIGH, SEED);
        List<Heard> heard = new ArrayList<>();
        for (SoundCue.OneShot cue : cues.onStep(run.step(new Command.Forward(SpiderId.ZAPPER)))) {
            heard.add(new Heard(run.sim.frame(), cue));
        }
        heard.addAll(run(run, cues, 40));
        return keysOf(heard, SoundBank.SPIDER_STEP);
    }

    /**
     * Fix wave I2: ToB 2005 counts the step sound down for every spider move ({@code bMoving},
     * {@code twr.cpp:2784-2838}): a pushed spider (double speed, so half the steps) steps on its first push frame
     * and every 4 frames.
     */
    @Test
    void aPushedSpiderStepsOnItsFirstPushFrameAndEveryFourFrames() {
        SimRun run = new SimRun(TestTowers.tower().level(0, "F", "F", "F").object(0, 0, 0, ObjectType.PSH)
                .object(0, 1, 0, ObjectType.ZAP).build());
        SoundCues cues = new SoundCues(run.sim.world(), SoundTestBank.bank(), ALL, SoundTuning.DETAIL_HIGH, SEED);
        List<Heard> heard = new ArrayList<>();
        for (SoundCue.OneShot cue : cues.onStep(run.step(new Command.Fire(SpiderId.PUSHER)))) {
            heard.add(new Heard(run.sim.frame(), cue));
        }
        heard.addAll(run(run, cues, 30));

        assertEquals(List.of(0), frames(heard, SoundBank.PUSH), "the push");
        assertEquals(List.of(0, 4, 8, 12), frames(heard, SoundBank.SPIDER_STEP), "16 push frames, 4 steps");
        assertEquals(List.of(SoundBank.SPIDER_STEP + "-" + SpiderId.ZAPPER.slot()),
                keysOf(heard, SoundBank.SPIDER_STEP), "the pushed zapper steps, the pusher stands");
    }

    /** Fix wave I2: a spider riding a lift steps too ({@code gml.cpp:642}, {@code bMoving} with the lift). */
    @Test
    void aSpiderRidingALiftStepsEveryFourFrames() {
        SimRun run = new SimRun(TestTowers.tower().level(0, "U").level(1, "u").object(0, 0, 0, ObjectType.ZAP)
                .build());
        SoundCues cues = new SoundCues(run.sim.world(), SoundTestBank.bank(), ALL, SoundTuning.DETAIL_HIGH, SEED);
        List<Heard> heard = new ArrayList<>();
        for (SoundCue.OneShot cue : cues.onStep(run.step(new Command.Back(SpiderId.ZAPPER)))) {
            heard.add(new Heard(run.sim.frame(), cue));
        }
        heard.addAll(run(run, cues, 40));

        assertEquals(List.of(0, 4, 8, 12, 16, 20, 24, 28), frames(heard, SoundBank.SPIDER_STEP), "32 ride frames");
        assertEquals(1, frames(heard, SoundBank.LIFT_STOP).size(), "and the lift stops once");
    }

    /**
     * Fix wave (Minor 1): ToB 2005 pauses each element's sound countdown while the world is frozen, so a hopper
     * neither sounds on the first thawed frame nor loses its place: its next sound comes the rest of its 16 frames
     * after the thaw, and a rotating zapper frozen in the middle of a direction does not sound again at the thaw.
     */
    @Test
    void aFreezeHoldsTheElementsSoundCountdowns() {
        SimRun run = SimRun.startTime(TestTowers.tower().level(0, "F", "F", ".", "FFF")
                .object(0, 0, 0, ObjectType.GRB).object(0, 1, 0, ObjectType.FRZ).object(0, 3, 0, ObjectType.HOP)
                .object(5, 5, 0, ObjectType.RZN).conditions(0, false, 0, 0));
        SoundCues cues = new SoundCues(run.sim.world(), SoundTestBank.bank(), ALL, SoundTuning.DETAIL_HIGH, SEED);
        List<Heard> heard = run(run, cues, 9);
        assertEquals(List.of(1), frames(heard, SoundBank.HOPPER));
        assertEquals(List.of(1), frames(heard, SoundBank.ROTATING_ZAPPER));

        for (SoundCue.OneShot cue : cues.onStep(run.step(new Command.Fire(SpiderId.GRABBER)))) {
            heard.add(new Heard(run.sim.frame(), cue));
        }
        assertTrue(run.sim.world().isFrozen(), "the freezer froze the world in frame 10");
        int frozenFrames = 0;
        while (run.sim.world().isFrozen()) {
            heard.addAll(run(run, cues, 1));
            frozenFrames++;
        }
        int thaw = run.sim.frame();
        heard.addAll(run(run, cues, 30));

        List<Integer> hopper = frames(heard, SoundBank.HOPPER);
        assertEquals(1, hopper.get(0));
        assertTrue(hopper.get(1) > thaw + 1, "not on the first thawed frame: " + hopper + ", thaw " + thaw);
        assertEquals(17 + frozenFrames, hopper.get(1), "due in frame 17, held for the frozen frames: " + hopper);
        assertEquals(List.of(1), frames(heard, SoundBank.ROTATING_ZAPPER).stream().filter(f -> f < thaw + 6)
                .toList(), "no new direction sound at the thaw");
    }
}
