package de.latzig.game.tob.client.logic.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.client.logic.Texts;
import de.latzig.game.tob.client.logic.ui.Drawing;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.model.TowerGrid;
import de.latzig.game.tob.core.sim.CellPos;
import de.latzig.game.tob.core.sim.TowerCheck;

/** Phase 6b spec decision 4: invalid placements are marked live and named in the original designer's words. */
class EditorFindingsTest {

    private static final Texts TEXTS = Texts.english();

    /** A floor row on level 0 (cells 4..7, row 7) with a grabber, a klondike and the conditions' one klondike. */
    private static TowerGrid playable() {
        TowerGrid grid = TowerGrid.empty();
        for (int x = 4; x <= 7; x++) {
            grid = TowerEdits.place(grid, EditorElement.FLOOR, x, 7, 0).grid();
        }
        grid = TowerEdits.place(grid, EditorElement.of(ObjectType.GRB), 4, 7, 0).grid();
        return TowerEdits.place(grid, EditorElement.of(ObjectType.KLD), 7, 7, 0).grid();
    }

    private static TowerDefinition tower(TowerGrid grid) {
        return EditorTowers.withGrid(EditorTowers.blank("new-000", 0), grid);
    }

    private static String shown(EditorFindings findings, int x, int y, int level) {
        return TEXTS.get(EditorFindings.messageKey(findings.shown(x, y, level)));
    }

    @Test
    void aPlayableTowerIsValidAndTheStatusLineSaysOkInGrey() {
        EditorFindings findings = EditorFindings.of(tower(playable()));

        assertEquals(EditorFindings.State.VALID, findings.state());
        assertEquals("OK", shown(findings, 0, 0, 0));
        assertEquals(Drawing.GREY, EditorFindings.colour(findings.shown(0, 0, 0)));
        assertEquals(2, findings.objects());
        assertEquals(20, findings.maxObjects(), "6a: within the original area");
    }

    @Test
    void anObjectWithoutFloorIsAnErrorMarkedAtItsCell() {
        TowerGrid grid = TowerEdits.place(playable(), EditorElement.of(ObjectType.BFL), 9, 9, 2).grid();
        EditorFindings findings = EditorFindings.of(tower(grid));

        assertEquals(EditorFindings.State.ERROR, findings.state());
        assertEquals(List.of(new CellPos(9, 9, 2)), findings.on(2).stream()
                .map(f -> f.location().orElseThrow()).toList());
        assertEquals("No floor for object", shown(findings, 0, 0, 0), "the first error, wherever the cursor is");
        assertEquals(EditorFindings.RED, EditorFindings.colour(findings.shown(0, 0, 0)));
    }

    @Test
    void theFindingAtTheCursorComesFirst() {
        TowerGrid grid = TowerEdits.place(playable(), EditorElement.LIFT_UP, 2, 2, TowerGrid.LEVELS - 1).grid();
        grid = TowerEdits.place(grid, EditorElement.of(ObjectType.BFL), 9, 9, 2).grid();
        EditorFindings findings = EditorFindings.of(tower(grid));

        assertEquals("Can't place lift here", shown(findings, 2, 2, TowerGrid.LEVELS - 1));
        assertEquals("No floor for object", shown(findings, 9, 9, 2));
    }

    @Test
    void aTowerThatCannotBeWonIsAWarningInYellow() {
        TowerGrid grid = TowerEdits.remove(playable(), 7, 7, 0).grid();
        EditorFindings findings = EditorFindings.of(tower(grid));

        assertEquals(EditorFindings.State.WARNING, findings.state());
        assertEquals("Not Enough Klondikes", shown(findings, 0, 0, 0));
        assertEquals(EditorFindings.YELLOW, EditorFindings.colour(findings.shown(0, 0, 0)));
        assertEquals("No spiders placed", TEXTS.get(EditorFindings.messageKey(
                EditorFindings.of(tower(TowerGrid.empty())).first(TowerCheck.Severity.UNPLAYABLE))));
    }

    @Test
    void theTwentyFirstObjectIsTooManyWithinTheOriginalArea() {
        TowerGrid grid = playable();
        for (int i = 0; i < 19; i++) {
            grid = TowerEdits.place(grid, EditorElement.FLOOR, 4 + i % 8, 8 + i / 8, 0).grid();
            grid = TowerEdits.place(grid, EditorElement.of(ObjectType.BFL), 4 + i % 8, 8 + i / 8, 0).grid();
        }
        EditorFindings findings = EditorFindings.of(tower(grid));

        assertEquals(21, findings.objects());
        assertEquals("Too many objects placed", shown(findings, 0, 0, 0));
        assertTrue(findings.first(TowerCheck.Severity.ERROR).orElseThrow().location().isEmpty());
        assertFalse(EditorFindings.messageKey(Optional.empty()).isEmpty());
    }

    @Test
    void everyCheckCodeHasItsWords() {
        for (String code : List.of(TowerCheck.OBJECT_WITHOUT_BASE, TowerCheck.LIFT_WITHOUT_STOP, TowerCheck.NO_SPIDER,
                TowerCheck.NO_GRABBER, TowerCheck.TOO_FEW_KLONDIKES, TowerCheck.TOO_FEW_ROBOTS, TowerCheck.NO_TASK,
                TowerCheck.SPIDER_WALLED_IN, TowerCheck.KLONDIKE_UNREACHABLE, TowerCheck.ORPHAN_LIFT_STOP)) {
            String key = EditorFindings.messageKey(Optional.of(new TowerCheck.Finding(TowerCheck.Severity.HINT, code,
                    "", Optional.empty())));
            assertFalse(TEXTS.get(key).isBlank(), code);
        }
        assertEquals("Spider already placed", TEXTS.get(EditorFindings.messageKey(Optional.of(
                new TowerCheck.Finding(TowerCheck.Severity.ERROR, TowerCheck.STRUCTURE, "ZAP placed 2 times",
                        Optional.empty())))));
    }
}
