package de.latzig.game.tob.core.sim;

import static de.latzig.game.tob.core.sim.TestTowers.pos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.TowerDefinition;

class TowerCheckTest {

    /** Every finding of the 144 original towers (counted from the JSON on 2026-10-07); all are original quirks. */
    private static final List<String> KNOWN_QUIRKS = List.of(
            "original-052 HINT ORPHAN_LIFT_STOP (4,10,2)",
            "original-061 HINT SPIDER_WALLED_IN (8,9,0)",
            "tutorial-000 UNPLAYABLE NO_TASK -",
            "tutorial-001 UNPLAYABLE NO_TASK -",
            "tutorial-002 UNPLAYABLE NO_TASK -",
            "tutorial-003 UNPLAYABLE NO_TASK -",
            "tutorial-004 UNPLAYABLE NO_TASK -",
            "tutorial-005 UNPLAYABLE NO_TASK -",
            "tutorial-006 UNPLAYABLE NO_TASK -",
            "demo-000 UNPLAYABLE NO_TASK -",
            "demo-001 UNPLAYABLE NO_TASK -",
            "demo-002 UNPLAYABLE NO_TASK -",
            "demo-003 UNPLAYABLE NO_TASK -",
            "demo-004 UNPLAYABLE NO_TASK -",
            "demo-005 UNPLAYABLE NO_TASK -",
            "demo-006 UNPLAYABLE NO_TASK -",
            "demo-007 UNPLAYABLE NO_TASK -",
            "demo-008 UNPLAYABLE NO_TASK -",
            "demo-009 UNPLAYABLE NO_TASK -",
            "demo-010 UNPLAYABLE NO_TASK -",
            "demo-011 UNPLAYABLE NO_TASK -",
            "demo-012 UNPLAYABLE NO_TASK -",
            "demo-013 UNPLAYABLE NO_TASK -",
            "demo-014 UNPLAYABLE NO_TASK -",
            "demo-015 UNPLAYABLE NO_TASK -",
            "demo-016 UNPLAYABLE NO_TASK -",
            "demo-017 UNPLAYABLE NO_TASK -");

    private static String describe(TowerCheck.Finding finding) {
        return finding.severity() + " " + finding.code() + " " + finding.location().map(CellPos::toString).orElse("-");
    }

    private static List<String> findings(TestTowers.Builder tower) {
        return TowerCheck.check(tower.build()).stream().map(TowerCheckTest::describe).toList();
    }

    private static List<String> findings(TestTowers.Builder tower, TowerCheck.Severity severity) {
        return findings(tower).stream().filter(text -> text.startsWith(severity.name())).toList();
    }

    @Test
    void aSoundTowerHasNoFindings() {
        assertEquals(List.of(), findings(TestTowers.tower().level(0, "FFFFU").level(1, "....u")
                .object(0, 0, 0, ObjectType.ZAP).object(1, 0, 0, ObjectType.GRB).object(2, 0, 0, ObjectType.KLD)
                .object(3, 0, 0, ObjectType.BFL).conditions(0, false, 1, 1)));
    }

    @Test
    void liftStartsWithoutTheirStopAreErrors() {
        assertEquals(List.of("ERROR LIFT_WITHOUT_STOP " + pos(0, 0, 0), "ERROR LIFT_WITHOUT_STOP " + pos(0, 0, 1)),
                findings(TestTowers.tower().level(0, "U").level(1, "D"), TowerCheck.Severity.ERROR));
    }

    @Test
    void orphanStopsAreToleratedHints() {
        assertEquals(List.of("HINT ORPHAN_LIFT_STOP " + pos(1, 0, 0), "HINT ORPHAN_LIFT_STOP " + pos(0, 0, 1)),
                findings(TestTowers.tower().level(0, "Fd").level(1, "u."), TowerCheck.Severity.HINT));
    }

    @Test
    void objectsWithoutABaseAreErrors() {
        // empty, box, orphan up stop (no floor), orphan down stop (blocked)
        List<String> errors = findings(TestTowers.tower().level(0, ".Bud").object(0, 0, 0, ObjectType.BLK)
                .object(1, 0, 0, ObjectType.BLK).object(2, 0, 0, ObjectType.BLK).object(3, 0, 0, ObjectType.BLK),
                TowerCheck.Severity.ERROR);

        assertEquals(List.of("ERROR OBJECT_WITHOUT_BASE " + pos(0, 0, 0), "ERROR OBJECT_WITHOUT_BASE " + pos(1, 0, 0),
                "ERROR OBJECT_WITHOUT_BASE " + pos(2, 0, 0), "ERROR OBJECT_WITHOUT_BASE " + pos(3, 0, 0)), errors);
    }

    @Test
    void structureLimitsAreErrors() {
        List<TowerCheck.Finding> errors = TowerCheck.check(TestTowers.tower().level(0, "FF")
                .object(0, 0, 0, ObjectType.ZAP).object(1, 0, 0, ObjectType.ZAP).build()).stream()
                .filter(finding -> finding.severity() == TowerCheck.Severity.ERROR).toList();

        assertEquals(1, errors.size());
        assertEquals(TowerCheck.STRUCTURE, errors.get(0).code());
        assertTrue(errors.get(0).message().contains("ZAP placed 2 times"), errors.get(0).message());
        assertTrue(errors.get(0).location().isEmpty());
    }

    @Test
    void missingTaskPiecesMakeATowerUnplayable() {
        TowerCheck.Severity unplayable = TowerCheck.Severity.UNPLAYABLE;
        assertEquals(List.of("UNPLAYABLE NO_GRABBER -", "UNPLAYABLE TOO_FEW_KLONDIKES -"), findings(
                TestTowers.tower().level(0, "F").object(0, 0, 0, ObjectType.ZAP).conditions(0, false, 1, 0),
                unplayable));
        assertEquals(List.of("UNPLAYABLE TOO_FEW_ROBOTS -"), findings(TestTowers.tower().level(0, "FF")
                .object(0, 0, 0, ObjectType.ZAP).object(1, 0, 0, ObjectType.BFL).conditions(0, false, 0, 2),
                unplayable));
        assertEquals(List.of("UNPLAYABLE NO_SPIDER -", "UNPLAYABLE NO_TASK -"),
                findings(TestTowers.tower(), unplayable));
        assertEquals(List.of("UNPLAYABLE NO_TASK -"),
                findings(TestTowers.tower().level(0, "F").object(0, 0, 0, ObjectType.ZAP), unplayable));
    }

    @Test
    void aWalledInSpiderIsAHint() {
        TowerCheck.Severity hint = TowerCheck.Severity.HINT;
        assertEquals(List.of("HINT SPIDER_WALLED_IN " + pos(0, 0, 0)),
                findings(TestTowers.tower().level(0, "F").object(0, 0, 0, ObjectType.ZAP), hint));
        assertEquals(List.of(), findings(TestTowers.tower().level(0, "Fd").level(1, ".D")
                .object(0, 0, 0, ObjectType.ZAP), hint), "a lift stop next to it");
        assertEquals(List.of(), findings(TestTowers.tower().level(0, "U").level(1, "u")
                .object(0, 0, 0, ObjectType.ZAP), hint), "it stands on a lift");
        assertEquals(List.of(), findings(TestTowers.tower().level(0, "FF").object(0, 0, 0, ObjectType.ZAP)
                .object(1, 0, 0, ObjectType.BLK), hint), "objects do not wall in");
    }

    @Test
    void aKlondikeNoStraightLineReachesIsAHint() {
        TowerCheck.Severity hint = TowerCheck.Severity.HINT;
        assertEquals(List.of("HINT KLONDIKE_UNREACHABLE " + pos(1, 1, 0)), findings(TestTowers.tower()
                .level(0, "BBB", "BFB", "BBB").object(1, 1, 0, ObjectType.KLD), hint));
        assertEquals(List.of(), findings(TestTowers.tower().level(0, "F.F").object(0, 0, 0, ObjectType.KLD), hint),
                "reached across the void");
    }

    @Test
    void theRealTowersShowOnlyTheKnownOriginalQuirks() throws IOException {
        assumeTrue(TowerAssets.available(), "tower assets not generated");
        List<String> actual = new ArrayList<>();
        for (TowerDefinition tower : TowerAssets.allTowers()) {
            for (TowerCheck.Finding finding : TowerCheck.check(tower)) {
                actual.add(tower.id() + " " + describe(finding));
            }
        }

        assertEquals(KNOWN_QUIRKS, actual);
    }
}
