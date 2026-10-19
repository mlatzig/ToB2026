package de.latzig.game.tob.client.logic.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.Conditions;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.sim.Command;
import de.latzig.game.tob.core.sim.Rules;
import de.latzig.game.tob.core.sim.SimOptions;
import de.latzig.game.tob.core.sim.Simulation;
import de.latzig.game.tob.core.sim.SpiderId;
import de.latzig.game.tob.core.sim.TestTowers;
import de.latzig.game.tob.core.sim.WorldState;

class HudTextTest {

    private static final SpiderId Z = SpiderId.ZAPPER;

    private static Simulation simulation() {
        return Simulation.start(TestTowers.tower().level(0, "FF", "FF").object(0, 0, 0, ObjectType.ZAP)
                .cameras(true).conditions(120, true, 2, 0).build(), SimOptions.DEFAULT);
    }

    @Test
    void theCaptionCountsCappedAtTheGoalAndTheTimeLeft() {
        Simulation sim = simulation();
        assertEquals("KLONDIKES 0/2  TIME 2:00", HudText.caption(sim.world()));

        sim.step(List.of(new Command.Fire(Z)));
        for (int i = 0; i < 7 * 61; i++) {
            sim.step(List.of());
        }
        assertEquals("KLONDIKES 0/2  TIME 0:59", HudText.caption(sim.world()), "61 seconds of 7 frames");
    }

    @Test
    void theCaptionShowsCountersAndTimeOnlyWhereTheTowerHasThem() {
        WorldState world = Simulation.start(TestTowers.tower().level(0, "F").object(0, 0, 0, ObjectType.GRB)
                .conditions(120, true, 2, 3).build(), SimOptions.DEFAULT).world();
        assertEquals("KLONDIKES 0/2  ROBOTS 0/3  TIME 2:00", HudText.caption(world));
        assertEquals(Optional.of("READY"), HudText.message(world, false));
        WorldState open = Simulation.start(TestTowers.tower().level(0, "F").object(0, 0, 0, ObjectType.ZAP)
                .conditions(600, false, 0, 0).build(), SimOptions.DEFAULT).world();
        assertEquals("", HudText.caption(open), "practice tower without time limit");
        WorldState robots = Simulation.start(TestTowers.tower().level(0, "F").object(0, 0, 0, ObjectType.ZAP)
                .conditions(600, false, 0, 4).build(), SimOptions.DEFAULT).world();
        assertEquals("ROBOTS 0/4", HudText.caption(robots), "a disabled time limit is not shown");
    }

    @Test
    void theHudMessagesFollowTheExitCode() {
        assertEquals(Optional.of("COMPLETED"), HudText.message(Rules.EXIT_COMPLETED, false, true));
        assertEquals(Optional.of("TIME UP"), HudText.message(Rules.EXIT_TIMEOUT, false, false));
        assertEquals(Optional.of("FAILED"), HudText.message(Rules.EXIT_SPIDERS_DESTROYED, false, false));
        assertEquals(Optional.of("FAILED"), HudText.message(Rules.EXIT_GRABBER_DESTROYED, false, false));
        assertEquals(Optional.empty(), HudText.message(Rules.EXIT_ABANDONED, false, false));
        assertEquals(Optional.of("PAUSED"), HudText.message(0, true, true));
        assertEquals(Optional.empty(), HudText.message(0, false, false));
    }

    @Test
    void theTitleBarNamesNumberTitleAndAuthor() {
        TowerDefinition tower = TestTowers.tower().level(0, "F").build();
        assertEquals("007) Test", HudText.title(7, tower), "no author: no brackets (ToB 2009 PANEL_TITLE_FORMAT1)");
        TowerDefinition authored = new TowerDefinition("x-000", 0, 0, "Softly Softly!", "Pete Cooke",
                tower.conditions(), tower.behaviour(), tower.effects(), tower.moon(), tower.palette(), tower.sounds(),
                tower.lock(), tower.grid(), tower.remake());
        assertEquals("001) Softly Softly! (Pete Cooke)", HudText.title(1, authored));
        assertEquals("117) Softly Softly! (Pete Cooke)", HudText.title(117, authored));
    }

    @Test
    void theGoalLineCoversEveryVariant() {
        assertEquals("GOAL: COLLECT 1 KLONDIKE", HudText.goal(new Conditions(600, false, 1, 0)));
        assertEquals("GOAL: COLLECT 3 KLONDIKES", HudText.goal(new Conditions(600, false, 3, 0)));
        assertEquals("GOAL: DESTROY 4 ROBOTS", HudText.goal(new Conditions(0, false, 0, 4)));
        assertEquals("GOAL: DESTROY 1 ROBOT", HudText.goal(new Conditions(0, false, 0, 1)));
        assertEquals("GOAL: COLLECT 2 KLONDIKES AND DESTROY 3 ROBOTS", HudText.goal(new Conditions(0, false, 2, 3)));
        assertEquals("PRACTICE TOWER - NO GOAL", HudText.goal(new Conditions(600, false, 0, 0)));
        assertEquals("GOAL: DESTROY 4 ROBOTS WITHIN 120 SECONDS", HudText.goal(new Conditions(120, true, 0, 4)));
        assertEquals("GOAL: COLLECT 1 KLONDIKE WITHIN 1 SECOND", HudText.goal(new Conditions(1, true, 1, 0)));
    }

    @Test
    void theGoalLineShowsWhileReadyAndWhilePaused() {
        Simulation sim = simulation();
        assertEquals(Optional.of("GOAL: COLLECT 2 KLONDIKES WITHIN 120 SECONDS"), HudText.goal(sim.world(), false));

        sim.step(List.of(new Command.Fire(Z)));
        assertEquals(Optional.empty(), HudText.goal(sim.world(), false), "READY has ended");
        assertTrue(HudText.goal(sim.world(), true).isPresent(), "paused");

        sim.step(List.of(new Command.Abandon()));
        assertEquals(Optional.empty(), HudText.goal(sim.world(), true), "not after an exit");
    }

    @Test
    void theCentreMessagePulsesBetweenBlackAndWhiteLikeToB2009() {
        assertEquals(0.5f, HudText.pulse(0.0), 1e-6f);
        assertEquals(1f, HudText.pulse(Math.PI / 20), 1e-6f, "sin(10 t) = 1");
        assertEquals(0f, HudText.pulse(3 * Math.PI / 20), 1e-6f, "sin(10 t) = -1");
    }

    @Test
    void theHelpLinesNameTheNewKeys() {
        String[] lines = HudText.HELP.split("\n");

        assertEquals(2, lines.length);
        assertTrue(lines[0].contains("F5-F8 CAMERA"));
        assertTrue(lines[0].contains("TAB PANEL"));
        assertTrue(lines[0].contains("ESC MENU"));
        assertTrue(lines[1].contains("S STOP/IN  R RUN/OUT"));
        assertTrue(lines[1].contains("PGUP/PGDN ZOOM"));
        assertTrue(lines[0].endsWith("F9 RESTART  F10 ABANDON"), "Phase 5a: no F10 TEXTURES");
        assertFalse(HudText.HELP.contains("F11"));
        assertFalse(HudText.HELP.contains("TEXTURES"));
    }
}
