package de.latzig.game.tob.client.logic.editor;

import java.util.List;
import java.util.Optional;

import de.latzig.game.tob.client.logic.ui.Drawing;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.model.TowerExtent;
import de.latzig.game.tob.core.model.TowerLimits;
import de.latzig.game.tob.core.sim.CellPos;
import de.latzig.game.tob.core.sim.TowerCheck;
import de.latzig.game.tob.core.sim.TowerCheck.Finding;
import de.latzig.game.tob.core.sim.TowerCheck.Severity;

/**
 * The Tower Designer's rule pass (Phase 6b spec decision 4): {@link TowerCheck} over the edited tower (with 6a's
 * {@link TowerLimits} and {@link TowerExtent}: 20 objects within the original area, 64 beyond), run after every edit.
 * Located findings mark their cells in the grid; the status line names one finding in the original designer's words
 * ({@link #messageKey}) — the one at the cursor, else the first error, else the first "cannot be won", else the first
 * hint — in ToB 2009's colours: grey OK, yellow a warning, red an error. ToB 2009's library list colours a tower by
 * {@link #state}.
 */
public final class EditorFindings {

    /** A tower's state in ToB 2009's list colours: playable (green), cannot be won (grey), broken (red). */
    public enum State {
        VALID, WARNING, ERROR
    }

    /** ToB 2009's {@code Colors.YELLOW} of the status line's warnings. */
    public static final int YELLOW = 0xFFFF00;
    /** ToB 2009's {@code Colors.RED} of the status line's errors and the error marks. */
    public static final int RED = 0xFF0000;

    private final List<Finding> findings;
    private final int objects;
    private final int maxObjects;

    private EditorFindings(List<Finding> findings, int objects, int maxObjects) {
        this.findings = List.copyOf(findings);
        this.objects = objects;
        this.maxObjects = maxObjects;
    }

    public static EditorFindings of(TowerDefinition tower) {
        return new EditorFindings(TowerCheck.check(tower), tower.grid().objectCount(),
                TowerLimits.maxObjects(tower.grid()));
    }

    public List<Finding> all() {
        return findings;
    }

    /** The objects placed and the limit for this tower's size (20 or 64). */
    public int objects() {
        return objects;
    }

    public int maxObjects() {
        return maxObjects;
    }

    public State state() {
        if (first(Severity.ERROR).isPresent()) {
            return State.ERROR;
        }
        return first(Severity.UNPLAYABLE).isPresent() ? State.WARNING : State.VALID;
    }

    public Optional<Finding> first(Severity severity) {
        return findings.stream().filter(f -> f.severity() == severity).findFirst();
    }

    /** The findings located on {@code level}, in check order (errors and hints). */
    public List<Finding> on(int level) {
        return findings.stream().filter(f -> f.location().map(p -> p.level() == level).orElse(false)).toList();
    }

    /** The finding the status line names with the cursor at ({@code x}, {@code y}, {@code level}), if any. */
    public Optional<Finding> shown(int x, int y, int level) {
        CellPos cursor = new CellPos(x, y, level);
        Optional<Finding> here = findings.stream().filter(f -> f.location().equals(Optional.of(cursor)))
                .findFirst();
        if (here.isPresent()) {
            return here;
        }
        for (Severity severity : Severity.values()) {
            Optional<Finding> first = first(severity);
            if (first.isPresent()) {
                return first;
            }
        }
        return Optional.empty();
    }

    /** The status line's colour for {@code finding}: red for an error, yellow otherwise; grey without one. */
    public static int colour(Optional<Finding> finding) {
        return finding.map(f -> f.severity() == Severity.ERROR ? RED : YELLOW).orElse(Drawing.GREY);
    }

    /** The bundle key of the status line's words for {@code finding} (the original designer's messages). */
    public static String messageKey(Optional<Finding> finding) {
        if (finding.isEmpty()) {
            return "editor.finding.ok";
        }
        Finding f = finding.get();
        if (f.code().equals(TowerCheck.STRUCTURE)) {
            return f.message().contains("objects") ? "editor.finding.objects" : "editor.finding.spider";
        }
        return switch (f.code()) {
            case TowerCheck.OBJECT_WITHOUT_BASE -> "editor.finding.no_floor";
            case TowerCheck.LIFT_WITHOUT_STOP -> "editor.finding.lift";
            case TowerCheck.NO_SPIDER -> "editor.finding.no_spider";
            case TowerCheck.NO_GRABBER -> "editor.finding.no_grabber";
            case TowerCheck.TOO_FEW_KLONDIKES -> "editor.finding.klondikes";
            case TowerCheck.TOO_FEW_ROBOTS -> "editor.finding.robots";
            case TowerCheck.NO_TASK -> "editor.finding.no_task";
            case TowerCheck.SPIDER_WALLED_IN -> "editor.finding.walled_in";
            case TowerCheck.KLONDIKE_UNREACHABLE -> "editor.finding.unreachable";
            default -> "editor.finding.orphan_stop";
        };
    }
}
