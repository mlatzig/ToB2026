package de.latzig.game.tob.client.logic.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import de.latzig.game.tob.core.model.Conditions;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.sim.Counters;
import de.latzig.game.tob.core.sim.Rules;
import de.latzig.game.tob.core.sim.WorldState;

/**
 * The texts of the HUD and the panel: the caption strip, the title bar, the big message, the goal line and the help
 * lines (Phase 3a spec §3 {@code HudOverlay}, Phase 3b-1 spec §4.3, Phase 3b-2 spec §3.1 and §3.3).
 */
public final class HudText {

    public static final String HELP = "ARROWS MOVE/TURN  SPACE FIRE  F1-F3 SPIDER  F5-F8 CAMERA  TAB PANEL  P PAUSE"
            + "  ESC MENU  F9 RESTART  F10 ABANDON\n"
            + "W WRITE  CTRL+ARROWS/SPACE STEP  BACKSPACE DELETE  C CLEAR  1-8 CURSOR  S STOP/IN  R RUN/OUT"
            + "  CTRL+R RUN ALL  PGUP/PGDN ZOOM";

    private HudText() {
    }

    /**
     * The caption strip "KLONDIKES 1/2  ROBOTS 0/3  TIME 1:58" (ToB 2009 {@code buildPanelMessage}): each counter only
     * if its goal is above 0, the collected count capped at the goal; the time only with an enabled time limit.
     */
    public static String caption(WorldState world) {
        Counters counters = world.counters();
        List<String> parts = new ArrayList<>();
        if (counters.klondikesRequired() > 0) {
            parts.add("KLONDIKES " + Math.min(counters.klondikesCollected(), counters.klondikesRequired()) + "/"
                    + counters.klondikesRequired());
        }
        if (counters.robotsRequired() > 0) {
            parts.add("ROBOTS " + Math.min(counters.robotsDestroyed(), counters.robotsRequired()) + "/"
                    + counters.robotsRequired());
        }
        if (world.tower().conditions().timeLimitEnabled()) {
            parts.add(String.format(Locale.ROOT, "TIME %d:%02d", world.timeLeft() / 60, world.timeLeft() % 60));
        }
        return String.join("  ", parts);
    }

    /**
     * The title bar "001) Softly Softly! (Pete Cooke)" (ToB 2009 {@code Panel.reset}): the 1-based number of the tower
     * in its library, the title and, if there is one, the author.
     */
    public static String title(int number, TowerDefinition tower) {
        return tower.author().isBlank() ? String.format(Locale.ROOT, "%03d) %s", number, tower.title())
                : String.format(Locale.ROOT, "%03d) %s (%s)", number, tower.title(), tower.author());
    }

    /**
     * The tower goal: "GOAL: COLLECT 2 KLONDIKES AND DESTROY 3 ROBOTS", one part if the other count is 0,
     * "PRACTICE TOWER - NO GOAL" for 0/0; an enabled time limit appends " WITHIN 120 SECONDS".
     */
    public static String goal(Conditions conditions) {
        List<String> parts = new ArrayList<>();
        if (conditions.klondikes() > 0) {
            parts.add("COLLECT " + count(conditions.klondikes(), "KLONDIKE"));
        }
        if (conditions.robots() > 0) {
            parts.add("DESTROY " + count(conditions.robots(), "ROBOT"));
        }
        String goal = parts.isEmpty() ? "PRACTICE TOWER - NO GOAL" : "GOAL: " + String.join(" AND ", parts);
        return conditions.timeLimitEnabled() ? goal + " WITHIN " + count(conditions.timeLimitSeconds(), "SECOND")
                : goal;
    }

    /** The goal line shows below READY, before the first normal frame, and while paused; never after an exit. */
    public static Optional<String> goal(WorldState world, boolean paused) {
        boolean shown = world.exitCode() == 0 && (world.isReady() || paused);
        return shown ? Optional.of(goal(world.tower().conditions())) : Optional.empty();
    }

    private static String count(int value, String noun) {
        return value + " " + noun + (value == 1 ? "" : "S");
    }

    /**
     * The brightness of the centre message at {@code seconds}: 0 black .. 1 white, pulsing as ToB 2009
     * ({@code AbstractGamePanelWindow.update}: {@code 0.5 + 0.5 · sin(10 t)}).
     */
    public static float pulse(double seconds) {
        return (float) (0.5 + 0.5 * Math.sin(seconds * 10.0));
    }

    public static Optional<String> message(WorldState world, boolean paused) {
        return message(world.exitCode(), world.isReady(), paused);
    }

    /** The exit message wins over PAUSED, PAUSED over READY; abandon and restart show none. */
    static Optional<String> message(int exitCode, boolean ready, boolean paused) {
        return switch (exitCode) {
            case Rules.EXIT_COMPLETED -> Optional.of("COMPLETED");
            case Rules.EXIT_TIMEOUT -> Optional.of("TIME UP");
            case Rules.EXIT_SPIDERS_DESTROYED, Rules.EXIT_GRABBER_DESTROYED -> Optional.of("FAILED");
            case Rules.EXIT_ABANDONED, Rules.EXIT_RESTART -> Optional.empty();
            default -> paused ? Optional.of("PAUSED") : ready ? Optional.of("READY") : Optional.empty();
        };
    }
}
