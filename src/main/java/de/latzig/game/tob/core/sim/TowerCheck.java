package de.latzig.game.tob.core.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import de.latzig.game.tob.core.model.BaseType;
import de.latzig.game.tob.core.model.Cell;
import de.latzig.game.tob.core.model.Conditions;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.model.TowerGrid;
import de.latzig.game.tob.core.model.TowerLimits;

/**
 * Static tower check (spec §6). ERROR: the tower cannot be loaded. UNPLAYABLE: it loads, as in the original, but cannot
 * be won. HINT: shown in the editor only. Not a solver.
 */
public final class TowerCheck {

    public enum Severity {
        ERROR, UNPLAYABLE, HINT
    }

    /** One finding; the location is a grid cell, absent for tower-wide findings. */
    public record Finding(Severity severity, String code, String message, Optional<CellPos> location) {
        public Finding {
            Objects.requireNonNull(severity, "severity");
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
            Objects.requireNonNull(location, "location");
        }
    }

    public static final String STRUCTURE = "STRUCTURE";
    public static final String LIFT_WITHOUT_STOP = "LIFT_WITHOUT_STOP";
    public static final String OBJECT_WITHOUT_BASE = "OBJECT_WITHOUT_BASE";
    public static final String NO_SPIDER = "NO_SPIDER";
    public static final String NO_GRABBER = "NO_GRABBER";
    public static final String TOO_FEW_KLONDIKES = "TOO_FEW_KLONDIKES";
    public static final String TOO_FEW_ROBOTS = "TOO_FEW_ROBOTS";
    public static final String NO_TASK = "NO_TASK";
    public static final String ORPHAN_LIFT_STOP = "ORPHAN_LIFT_STOP";
    public static final String SPIDER_WALLED_IN = "SPIDER_WALLED_IN";
    public static final String KLONDIKE_UNREACHABLE = "KLONDIKE_UNREACHABLE";

    /** Range of the original ray trace (16 cells, {@code S:8737}). */
    static final int RAY_RANGE = 16;

    private TowerCheck() {
    }

    /** Findings in this order: structure, then per cell (level, row, column), then the tower-wide ones. */
    public static List<Finding> check(TowerDefinition tower) {
        List<Finding> findings = new ArrayList<>();
        for (String problem : TowerLimits.problems(tower)) {
            findings.add(new Finding(Severity.ERROR, STRUCTURE, problem, Optional.empty()));
        }
        TowerGrid grid = tower.grid();
        int klondikes = 0;
        int countable = 0;
        boolean spider = false;
        boolean grabber = false;
        for (int level = 0; level < TowerGrid.LEVELS; level++) {
            for (int y = 0; y < TowerGrid.DEPTH; y++) {
                for (int x = 0; x < TowerGrid.WIDTH; x++) {
                    CellPos pos = new CellPos(x, y, level);
                    Cell cell = grid.cell(x, y, level);
                    checkLift(grid, pos, cell.base(), findings);
                    if (!cell.hasObject()) {
                        continue;
                    }
                    ObjectType type = cell.object();
                    if (!standable(cell.base())) {
                        findings.add(new Finding(Severity.ERROR, OBJECT_WITHOUT_BASE,
                                type.code() + " stands on " + cell.base(), Optional.of(pos)));
                    }
                    if (type.isSpider()) {
                        spider = true;
                        grabber |= type == ObjectType.GRB;
                        if (walledIn(grid, pos, cell.base())) {
                            findings.add(new Finding(Severity.HINT, SPIDER_WALLED_IN,
                                    type.code() + " has no floor or lift next to it", Optional.of(pos)));
                        }
                    }
                    if (type == ObjectType.KLD) {
                        klondikes++;
                        if (!reachable(grid, pos)) {
                            findings.add(new Finding(Severity.HINT, KLONDIKE_UNREACHABLE,
                                    "no straight line from a floor cell reaches the klondike", Optional.of(pos)));
                        }
                    }
                    if (ObjectDescriptor.countsAsRobot(type)) {
                        countable++;
                    }
                }
            }
        }
        Conditions conditions = tower.conditions();
        if (!spider) {
            findings.add(new Finding(Severity.UNPLAYABLE, NO_SPIDER, "no spider", Optional.empty()));
        }
        if (conditions.klondikes() > 0 && !grabber) {
            findings.add(new Finding(Severity.UNPLAYABLE, NO_GRABBER, "klondikes required but no grabber",
                    Optional.empty()));
        }
        if (klondikes < conditions.klondikes()) {
            findings.add(new Finding(Severity.UNPLAYABLE, TOO_FEW_KLONDIKES,
                    klondikes + " klondikes for " + conditions.klondikes() + " required", Optional.empty()));
        }
        if (countable < conditions.robots()) {
            findings.add(new Finding(Severity.UNPLAYABLE, TOO_FEW_ROBOTS,
                    countable + " destroyable objects for " + conditions.robots() + " robots", Optional.empty()));
        }
        if (conditions.klondikes() == 0 && conditions.robots() == 0) {
            findings.add(new Finding(Severity.UNPLAYABLE, NO_TASK, "no task: 0 klondikes and 0 robots",
                    Optional.empty()));
        }
        return findings;
    }

    private static void checkLift(TowerGrid grid, CellPos pos, BaseType base, List<Finding> findings) {
        switch (base) {
            case LIFT_UP_START -> {
                if (baseAt(grid, pos.above()) != BaseType.LIFT_UP_STOP) {
                    findings.add(new Finding(Severity.ERROR, LIFT_WITHOUT_STOP,
                            "lift up start without an up stop above", Optional.of(pos)));
                }
            }
            case LIFT_DOWN_START -> {
                if (baseAt(grid, pos.below()) != BaseType.LIFT_DOWN_STOP) {
                    findings.add(new Finding(Severity.ERROR, LIFT_WITHOUT_STOP,
                            "lift down start without a down stop below", Optional.of(pos)));
                }
            }
            case LIFT_UP_STOP -> {
                if (baseAt(grid, pos.below()) != BaseType.LIFT_UP_START) {
                    findings.add(new Finding(Severity.HINT, ORPHAN_LIFT_STOP,
                            "up stop without a lift start below; inert, as in the original", Optional.of(pos)));
                }
            }
            case LIFT_DOWN_STOP -> {
                if (baseAt(grid, pos.above()) != BaseType.LIFT_DOWN_START) {
                    findings.add(new Finding(Severity.HINT, ORPHAN_LIFT_STOP,
                            "down stop without a lift start above; inert, as in the original", Optional.of(pos)));
                }
            }
            default -> {
            }
        }
    }

    private static BaseType baseAt(TowerGrid grid, CellPos pos) {
        return pos.inGrid() ? grid.cell(pos.x(), pos.y(), pos.level()).base() : BaseType.EMPTY;
    }

    private static boolean standable(BaseType base) {
        return base == BaseType.FLOOR || base == BaseType.LIFT_UP_START || base == BaseType.LIFT_DOWN_START;
    }

    private static boolean isLift(BaseType base) {
        return base == BaseType.LIFT_UP_START || base == BaseType.LIFT_DOWN_START || base == BaseType.LIFT_UP_STOP
                || base == BaseType.LIFT_DOWN_STOP;
    }

    /** No floor or lift cell (any lift code: stops become floor when the lift moves) next to a spider off a lift. */
    private static boolean walledIn(TowerGrid grid, CellPos pos, BaseType base) {
        if (isLift(base)) {
            return false;
        }
        for (Direction direction : Direction.values()) {
            BaseType neighbour = baseAt(grid, pos.step(direction));
            if (neighbour == BaseType.FLOOR || isLift(neighbour)) {
                return false;
            }
        }
        return true;
    }

    /** Some non-empty cell within ray range on a straight line, with no box in between. */
    private static boolean reachable(TowerGrid grid, CellPos klondike) {
        for (Direction direction : Direction.values()) {
            CellPos pos = klondike;
            for (int distance = 0; distance < RAY_RANGE; distance++) {
                pos = pos.step(direction);
                if (!pos.inGrid()) {
                    break;
                }
                BaseType base = baseAt(grid, pos);
                if (base == BaseType.BOX) {
                    break;
                }
                if (base != BaseType.EMPTY) {
                    return true;
                }
            }
        }
        return false;
    }
}
