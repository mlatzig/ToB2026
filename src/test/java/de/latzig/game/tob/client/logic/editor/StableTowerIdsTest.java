package de.latzig.game.tob.client.logic.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.latzig.game.tob.client.logic.Texts;
import de.latzig.game.tob.core.model.TowerDefinition;

/** Phase 6d decision 7: a tower's id is given once, at creation (a random UUID), and never renumbered. */
class StableTowerIdsTest {

    private static final Texts TEXTS = Texts.english();
    private static final String UUID_TEXT = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    /** Predictable ids: {@code 00000000-0000-0000-0000-000000000001}, {@code …002}, … */
    static TowerIds sequential() {
        long[] next = {0L};
        return new TowerIds(() -> new UUID(0L, ++next[0]));
    }

    private static String id(long n) {
        return new UUID(0L, n).toString();
    }

    private static List<String> ids(List<TowerDefinition> towers) {
        return towers.stream().map(TowerDefinition::id).toList();
    }

    private static EditorModel mine() {
        EditorModel model = new EditorModel(TEXTS, sequential());
        model.load(new TowerLibraries.Opened(new TowerLibraries.Entry("mine", false),
                List.of(EditorModelTest.playable("mine-000"), EditorModelTest.playable("mine-001")), 0));
        return model;
    }

    @Test
    void aNewTowersIdIsARandomUuid() {
        TowerIds ids = new TowerIds();

        String first = ids.next();
        String second = ids.next();

        assertTrue(first.matches(UUID_TEXT), first);
        assertTrue(second.matches(UUID_TEXT), second);
        assertNotEquals(first, second);
        assertEquals("00000000-0000-0000-0000-000000000001", sequential().next(), "the tests' predictable source");
    }

    @Test
    void aTowerGetsItsIdWhenItIsCreatedAndKeepsItWhileItIsEditedOrOthersComeAndGo() {
        EditorModel model = new EditorModel(TEXTS, sequential());
        assertEquals(List.of(id(1)), ids(model.towers()), "the new library's tower");

        model.select(1);
        model.insert();
        model.press(4, 4, false);
        model.release();
        model.select(0);
        model.press(5, 5, false);
        model.release();

        assertEquals(List.of(id(1), id(3), id(2)), ids(model.towers()), "appended, inserted; nothing renumbered");
        model.remove();
        assertEquals(List.of(id(3), id(2)), ids(model.towers()), "removing renumbers nothing either");
        model.newLibrary();
        assertEquals(List.of(id(4)), ids(model.towers()));
    }

    @Test
    void aPastedOrEmptiedTowerIsANewTowerWithANewIdAndUndoBringsTheOldOneBack() {
        EditorModel model = mine();
        model.copy();
        model.select(1);

        model.paste();
        assertEquals(List.of("mine-000", id(2)), ids(model.towers()), "Paste: a copy is a new tower");
        model.delete();
        assertEquals(List.of("mine-000", id(3)), ids(model.towers()), "Delete empties it: a new tower");
        model.undo();
        model.undo();
        assertEquals(List.of("mine-000", "mine-001"), ids(model.towers()), "undo brings the old tower back");
    }

    @Test
    void savingKeepsEveryIdAndOnlyPlacesTheTowersInGroupsOfNine(@TempDir Path dir) throws IOException {
        TowerLibraries libraries = new TowerLibraries(dir.resolve("assets-towers"), dir.resolve("user"));
        EditorModel model = mine();
        model.select(1);
        model.insert();

        List<TowerDefinition> saved = libraries.save("mine", model.towersToSave());

        assertEquals(List.of("mine-000", id(2), "mine-001"), ids(saved), "the ids of the towers before 6d stay too");
        assertEquals(List.of(0, 1, 2), saved.stream().map(TowerDefinition::index).toList(), "placed by position");
        assertEquals(saved, libraries.open(new TowerLibraries.Entry("mine", false)).towers());
    }

    @Test
    void aShippedLibrarySavedAsAnOwnOneGivesItsTowersNewIdsOnce() {
        EditorModel model = new EditorModel(TEXTS, sequential());
        model.load(new TowerLibraries.Opened(new TowerLibraries.Entry("original", true),
                List.of(EditorModelTest.playable("original-000"), EditorModelTest.playable("original-001")), 0));

        List<TowerDefinition> toSave = model.towersToSave();

        assertEquals(List.of(id(2), id(3)), ids(toSave), "no own tower carries a shipped tower's id");
        assertEquals(List.of("original-000", "original-001"), ids(model.towers()), "nothing changes before the write");
        model.saved("mine", toSave);
        assertEquals(toSave, model.towers(), "the written towers, new ids included, become the model's");
        assertFalse(model.readOnly());
        assertEquals(toSave, model.towersToSave(), "an own library's towers keep theirs");
    }

    /** Fix wave (Task 1 Minor): a Delete or Paste refused in a shipped library draws no id. */
    @Test
    void aRefusedDeleteOrPasteInAShippedLibraryDrawsNoId() {
        EditorModel model = new EditorModel(TEXTS, sequential());
        model.load(new TowerLibraries.Opened(new TowerLibraries.Entry("original", true),
                List.of(EditorModelTest.playable("original-000"), EditorModelTest.playable("original-001")), 0));
        model.copy();

        model.delete();
        model.paste();

        assertEquals(List.of("original-000", "original-001"), ids(model.towers()), "read-only: refused");
        assertEquals(List.of(id(2), id(3)), ids(model.towersToSave()), "the next ids are still the next ones");
    }

    @Test
    void anOwnTowersSeedDrawnLookStaysTheSameAfterSaveAndReopen(@TempDir Path dir) throws IOException {
        TowerLibraries libraries = new TowerLibraries(dir.resolve("assets-towers"), dir.resolve("user"));
        EditorModel model = new EditorModel(TEXTS);
        model.press(4, 4, false);
        model.release();
        PreviewParts before = PreviewParts.of(model.tower());

        libraries.save("mine", model.towersToSave());
        model.saved("mine");
        libraries.save("mine", model.towersToSave());
        TowerDefinition reopened = libraries.open(new TowerLibraries.Entry("mine", false)).towers().get(0);

        assertEquals(model.tower().id(), reopened.id());
        assertEquals(before, PreviewParts.of(reopened), "the moon, stars, relief and lightning from the same seed");
    }
}
