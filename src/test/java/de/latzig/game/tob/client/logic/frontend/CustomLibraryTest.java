package de.latzig.game.tob.client.logic.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.client.logic.Texts;
import de.latzig.game.tob.client.logic.editor.EditorElement;
import de.latzig.game.tob.client.logic.editor.EditorModelTest;
import de.latzig.game.tob.client.logic.editor.EditorTowers;
import de.latzig.game.tob.client.logic.editor.TowerEdits;
import de.latzig.game.tob.core.model.Conditions;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.progress.Profile;

/**
 * Phase 6d decisions 3 and 6: an own library in groups of 9; a tower the designer marks unplayable is refused
 * (author's acceptance amendment, 2026-10-17: every {@code Severity.UNPLAYABLE} finding, not only without a task —
 * only a tower in {@code EditorFindings.State.VALID} can be started; a hint-level finding does not refuse it).
 */
class CustomLibraryTest {

    private static final Texts TEXTS = Texts.english();

    /** A playable tower whose task is gone: no klondikes and no robots (the original's "no task"). */
    static TowerDefinition noTask(String id) {
        TowerDefinition tower = EditorModelTest.playable(id);
        return new TowerDefinition(tower.id(), tower.group(), tower.index(), "Empty", tower.author(),
                new Conditions(600, false, 0, 0), tower.behaviour(), tower.effects(), tower.moon(), tower.palette(),
                tower.sounds(), tower.lock(), tower.grid(), tower.remake());
    }

    /** A default-blank tower: a goal (1 klondike, decision default) but no spider (author's acceptance finding). */
    static TowerDefinition noSpider(String id) {
        return EditorTowers.blank(id, 0);
    }

    /** A tower with a spider and a grabber but no klondike placed, though its task needs one. */
    static TowerDefinition tooFewKlondikes(String id) {
        TowerDefinition tower = EditorModelTest.playable(id);
        return EditorTowers.withGrid(tower, TowerEdits.remove(tower.grid(), 7, 7, 0).grid());
    }

    /** A playable tower with an isolated walled-in spider elsewhere: a hint, not unplayable. */
    static TowerDefinition hintOnly(String id) {
        TowerDefinition tower = EditorModelTest.playable(id);
        TowerDefinition withFloor = EditorTowers.withGrid(tower,
                TowerEdits.place(tower.grid(), EditorElement.FLOOR, 0, 0, 1).grid());
        return EditorTowers.withGrid(withFloor,
                TowerEdits.place(withFloor.grid(), EditorElement.of(ObjectType.ZAP), 0, 0, 1).grid());
    }

    /** {@code count} playable towers {@code <prefix>-0}, {@code <prefix>-1}, … */
    static List<TowerDefinition> playable(String prefix, int count) {
        List<TowerDefinition> towers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            towers.add(EditorModelTest.playable(prefix + "-" + i));
        }
        return towers;
    }

    @Test
    void theTowersComeInGroupsOfNineInTheirOrder() {
        CustomLibrary library = new CustomLibrary("mine", playable("t", 10));

        assertEquals("mine", library.name());
        assertEquals(10, library.size());
        assertEquals(2, library.groups());
        assertEquals("t-0", library.tower(0, 0).orElseThrow().id());
        assertEquals("t-9", library.tower(1, 0).orElseThrow().id());
        assertEquals(Optional.empty(), library.tower(1, 1), "past the last tower");
        assertEquals(9, library.position("t-9"));
        assertEquals(-1, library.position("other"));
        assertEquals(1, new CustomLibrary("one", playable("t", 1)).groups());
        assertEquals(1, new CustomLibrary("nine", playable("t", 9)).groups());
        assertThrows(IllegalArgumentException.class, () -> new CustomLibrary("none", List.of()));
    }

    @Test
    void aTowerWithoutATaskOrWithAnErrorIsRefusedInTheDesignersWords() {
        TowerDefinition ok = EditorModelTest.playable("ok");
        TowerDefinition broken = EditorTowers.withGrid(EditorModelTest.playable("broken"),
                TowerEdits.place(ok.grid(), EditorElement.of(ObjectType.BFL), 9, 9, 3).grid());
        CustomLibrary library = new CustomLibrary("mine", List.of(ok, noTask("empty"), broken));

        assertTrue(library.startable(0));
        assertEquals(Optional.empty(), library.refusal(0, TEXTS));
        assertFalse(library.startable(1));
        assertEquals(Optional.of("No Task!"), library.refusal(1, TEXTS),
                "author's acceptance amendment 2026-10-17: the designer's own words, not the original's hint");
        assertFalse(library.startable(2));
        assertEquals(Optional.of("No floor for object"), library.refusal(2, TEXTS),
                "the designer's words for the check error, no test-play suffix");
    }

    /** Author's acceptance finding (2026-10-17): a tower without a spider, or too few klondikes, is refused too. */
    @Test
    void aTowerTheDesignerMarksUnwinnableIsRefused() {
        CustomLibrary library = new CustomLibrary("mine", List.of(noSpider("empty"), tooFewKlondikes("few")));

        assertFalse(library.startable(0));
        assertEquals(Optional.of("No spiders placed"), library.refusal(0, TEXTS),
                "the acceptance finding: a tower with a goal but no spider must not be startable");
        assertFalse(library.startable(1));
        assertEquals(Optional.of("Not Enough Klondikes"), library.refusal(1, TEXTS));
    }

    /** Author's acceptance amendment (2026-10-17): a valid tower starts; a hint-only finding does not refuse it. */
    @Test
    void aValidTowerStartsAndAHintOnlyFindingDoesNotRefuseIt() {
        TowerDefinition ok = EditorModelTest.playable("ok");
        TowerDefinition hinted = hintOnly("hinted");
        CustomLibrary library = new CustomLibrary("mine", List.of(ok, hinted));

        assertTrue(library.startable(0));
        assertEquals(Optional.empty(), library.refusal(0, TEXTS));
        assertTrue(library.startable(1), "a hint (a warning) does not make a tower unwinnable");
        assertEquals(Optional.empty(), library.refusal(1, TEXTS));
    }

    @Test
    void theSolvedCountAndThePercentageAreThoseOfTheLibrary() {
        CustomLibrary library = new CustomLibrary("mine", playable("t", 3));
        Profile profile = Profile.create("Anna", "original").withCustomSolved("mine", "t-1")
                .withCustomSolved("other", "t-0").withCustomSolved("mine", "gone");

        assertEquals(1, library.solvedCount(profile), "only this library's marks, only of towers still in it");
        assertEquals(0, library.percent(0));
        assertEquals(34, library.percent(1), "rounded up as the original's");
        assertEquals(67, library.percent(2));
        assertEquals(100, library.percent(3));
        assertEquals(99, new CustomLibrary("big", playable("t", 117)).percent(116), "never 100 before all");
    }
}
