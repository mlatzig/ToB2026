package de.latzig.game.tob.client.logic.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.latzig.game.tob.core.io.TowerFormatException;
import de.latzig.game.tob.core.io.TowerLibraryReader;
import de.latzig.game.tob.core.io.TowerLibraryWriter;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.model.TowerLibrary;

/** Phase 6b spec decision 5: own libraries in {@code <userdir>/towers/}, shipped ones read-only, ids by name. */
class TowerLibrariesTest {

    private static TowerDefinition tower(int x) {
        TowerDefinition blank = EditorTowers.blank("tower-" + x, 0);
        return EditorTowers.withGrid(blank, TowerEdits.place(TowerEdits.place(blank.grid(), EditorElement.FLOOR, x, 7,
                0).grid(), EditorElement.of(ObjectType.GRB), x, 7, 0).grid());
    }

    private static Path shipped(Path folder) throws IOException {
        Path towers = Files.createDirectories(folder.resolve("assets-towers"));
        TowerLibraryWriter writer = new TowerLibraryWriter();
        writer.write(new TowerLibrary("original", "Original", "test",
                List.of(EditorTowers.withId(tower(4), "original-000"))), towers.resolve("original.json"));
        writer.write(new TowerLibrary("demo", "Demo", "test", List.of(EditorTowers.withId(tower(5), "demo-000"))),
                towers.resolve("demo.json"));
        return towers;
    }

    @Test
    void theShippedLibrariesComeFirstReadOnlyThenTheOwnOnesByName(@TempDir Path dir) throws IOException {
        TowerLibraries libraries = new TowerLibraries(shipped(dir), dir.resolve("user"));
        libraries.save("zeta", List.of(tower(4)));
        libraries.save("alpha", List.of(tower(4)));
        Files.writeString(libraries.folder().resolve("Not A Name.json"), "{}", StandardCharsets.UTF_8);

        assertEquals(List.of(new TowerLibraries.Entry("original", true), new TowerLibraries.Entry("demo", true),
                new TowerLibraries.Entry("alpha", false), new TowerLibraries.Entry("zeta", false)), libraries.list());
        assertEquals(dir.resolve("user").resolve("towers"), libraries.folder(), "user data only below tob.userdir");
    }

    @Test
    void savingKeepsTheIdsAndOpeningReadsThemBack(@TempDir Path dir) throws IOException {
        TowerLibraries libraries = new TowerLibraries(shipped(dir), dir.resolve("user"));

        List<TowerDefinition> saved = libraries.save("mine", List.of(tower(4), tower(6)));

        assertEquals(List.of("tower-4", "tower-6"), saved.stream().map(TowerDefinition::id).toList(),
                "Phase 6d decision 7: never renumbered");
        assertEquals(List.of(0, 1), saved.stream().map(TowerDefinition::index).toList(), "placed by position");
        Path file = libraries.folder().resolve("mine.json");
        TowerLibrary written = new TowerLibraryReader().read(file);
        assertEquals("mine", written.id());
        assertEquals(TowerLibraries.SOURCE, written.source());
        TowerLibraries.Opened opened = libraries.open(new TowerLibraries.Entry("mine", false));
        assertEquals(saved, opened.towers());
        assertEquals(0, opened.skipped());
        assertTrue(libraries.exists("mine"));
        try (var files = Files.list(libraries.folder())) {
            assertEquals(List.of(file), files.toList(), "atomic: no temporary file left");
        }
    }

    /**
     * Final review M6 (controller verified on Windows 11, 2026-10-14): "con.json" etc. are ordinary files (the
     * device-name restriction applies only to the bare name without an extension), so a Windows device name is a
     * valid, unreserved library name that saves and reopens like any other.
     */
    @Test
    void aWindowsDeviceNameIsAValidLibraryNameAndSavesAndReopens(@TempDir Path dir) throws IOException {
        TowerLibraries libraries = new TowerLibraries(shipped(dir), dir.resolve("user"));

        libraries.save("con", List.of(tower(4)));

        assertTrue(libraries.exists("con"));
        assertTrue(Files.isRegularFile(libraries.folder().resolve("con.json")));
        assertEquals(List.of("tower-4"), libraries.open(new TowerLibraries.Entry("con", false)).towers().stream()
                .map(TowerDefinition::id).toList());
        assertTrue(libraries.list().contains(new TowerLibraries.Entry("con", false)));
    }

    @Test
    void aShippedLibraryOpensFromTheAssets(@TempDir Path dir) throws IOException {
        TowerLibraries libraries = new TowerLibraries(shipped(dir), dir.resolve("user"));

        TowerLibraries.Opened original = libraries.open(new TowerLibraries.Entry("original", true));

        assertTrue(original.entry().readOnly());
        assertEquals(List.of("original-000"), original.towers().stream().map(TowerDefinition::id).toList());
        assertFalse(Files.exists(libraries.folder()), "opening writes nothing");
    }

    /** Phase 6d decision 8: the designer's remembered library as an entry: shipped read-only, own if it exists. */
    @Test
    void anEntryIsAShippedLibraryReadOnlyOrAnOwnOneThatExists(@TempDir Path dir) throws IOException {
        TowerLibraries libraries = new TowerLibraries(shipped(dir), dir.resolve("user"));
        libraries.save("mine", List.of(tower(4)));

        assertEquals(Optional.of(new TowerLibraries.Entry("original", true)), libraries.entry("original"));
        assertEquals(Optional.of(new TowerLibraries.Entry("mine", false)), libraries.entry("mine"));
        assertEquals(Optional.empty(), libraries.entry("tutorial"), "a shipped library that is not there");
        assertEquals(Optional.empty(), libraries.entry("gone"), "an own library whose file is gone");
        assertEquals(Optional.empty(), libraries.entry("../mine"), "not a library name");
    }

    @Test
    void onlyLowerCaseNamesThatNoShippedLibraryUsesAreValid() {
        // Final review M6 (controller verified on Windows 11): a Windows device name is a valid library name -
        // "con.json" etc. are ordinary files, the restriction applies only to the bare name without an extension.
        for (String name : List.of("mine", "tower_9", "a", "x234567890123456", "con", "nul", "com1", "lpt9")) {
            assertTrue(TowerLibraries.validName(name), name);
        }
        for (String name : List.of("", "original", "demo", "tutorial", "large", "Mine", "my lib", "-a", "_a",
                "x2345678901234567", "café")) {
            assertFalse(TowerLibraries.validName(name), name);
        }
        assertEquals("mine", TowerLibraries.normalized("  Mine "));
        assertThrows(IllegalArgumentException.class, () -> new TowerLibraries(Path.of("a"), Path.of("b"))
                .save("original", List.of()));
    }

    /** Final review M6: only a shipped library's name (or "large") is reserved, nothing else. */
    @Test
    void reservedTellsAShippedNameApartFromAnInvalidCharacterSetOrAnOrdinaryName() {
        for (String name : List.of("original", "tutorial", "demo", "large")) {
            assertTrue(TowerLibraries.reserved(name), name);
            assertFalse(TowerLibraries.validName(name), name);
        }
        for (String name : List.of("mine", "console", "company", "complaint", "con", "nul", "com1", "lpt9")) {
            assertFalse(TowerLibraries.reserved(name), name);
            assertTrue(TowerLibraries.validName(name), name);
        }
    }

    @Test
    void aBrokenFileFailsAndAnUnreadableTowerIsSkipped(@TempDir Path dir) throws IOException {
        TowerLibraries libraries = new TowerLibraries(shipped(dir), dir.resolve("user"));
        libraries.save("mine", List.of(tower(4), tower(6)));
        Path file = libraries.folder().resolve("mine.json");
        Files.writeString(file, Files.readString(file).replaceFirst("\"title\" : \"\"", "\"title\" : 7"));
        Files.writeString(libraries.folder().resolve("bad.json"), "{\"format\":\"other\"}");

        TowerLibraries.Opened opened = libraries.open(new TowerLibraries.Entry("mine", false));

        assertEquals(1, opened.towers().size());
        assertEquals(1, opened.skipped());
        assertThrows(TowerFormatException.class, () -> libraries.open(new TowerLibraries.Entry("bad", false)));
    }
}
