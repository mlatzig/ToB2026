package de.latzig.game.tob.core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.latzig.game.tob.core.model.Moon;
import de.latzig.game.tob.core.model.SampleTowers;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.model.TowerLibrary;

class TowerLibraryJsonTest {

    private final TowerLibraryWriter writer = new TowerLibraryWriter();
    private final TowerLibraryReader reader = new TowerLibraryReader();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void roundTripsALibraryThroughAString() throws IOException {
        TowerLibrary library = SampleTowers.library();

        assertEquals(library, reader.readFromString(writer.writeToString(library)));
    }

    @Test
    void roundTripsALibraryThroughAFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("sub/sample.json");
        writer.write(SampleTowers.library(), file);

        assertEquals(SampleTowers.library(), reader.read(file));
    }

    @Test
    void writesTheDocumentedShape() throws IOException {
        String json = writer.writeToString(SampleTowers.library());

        assertTrue(json.contains("\"format\" : \"tob2026-towerlib\""));
        assertTrue(json.contains("\"version\" : 1"));
        assertTrue(json.contains("\"id\" : \"sample-000\""));
        assertTrue(json.contains("\"password\" : \"lebab\""));
        assertTrue(json.contains("\"sunColor\" : \"#FFFFFF\""));
        assertTrue(json.contains("FLR:ZAP"));
        assertFalse(json.contains("\"level\" : 5"), "empty levels are omitted");
    }

    @Test
    void writtenJsonUsesLfOnly() throws IOException {
        String json = writer.writeToString(SampleTowers.library());

        assertFalse(json.contains("\r"), "JSON output must use LF line endings only");
    }

    private static TowerDefinition withMoon(TowerDefinition t, Moon moon) {
        return new TowerDefinition(t.id(), t.group(), t.index(), t.title(), t.author(), t.conditions(),
                t.behaviour(), t.effects(), moon, t.palette(), t.sounds(), t.lock(), t.grid(), t.remake());
    }

    @Test
    void omitsTheMoonSizeFieldWhenAbsent() throws IOException {
        String json = writer.writeToString(SampleTowers.library());

        assertFalse(json.contains("\"size\""), "no stored moon size in the sample library");
    }

    @Test
    void roundTripsAStoredMoonSize() throws IOException {
        TowerDefinition withSize = withMoon(SampleTowers.tower("sized-000"),
                new Moon(0, 0, 8, 28, 2, Optional.of(Moon.MAX_SIZE)));
        TowerLibrary library = new TowerLibrary("sized", "Sized", "unit test", List.of(withSize));

        String json = writer.writeToString(library);
        TowerLibrary read = reader.readFromString(json);

        assertTrue(json.contains("\"size\" : " + Moon.MAX_SIZE));
        assertEquals(Optional.of(Moon.MAX_SIZE), read.towers().get(0).moon().size());
        assertEquals(library, read, "round trips through a string");
    }

    @Test
    void roundTripsWithoutAStoredMoonSize() throws IOException {
        TowerDefinition withoutSize = withMoon(SampleTowers.tower("plain-000"),
                new Moon(0, 0, 8, 28, 2, Optional.empty()));
        TowerLibrary library = new TowerLibrary("plain", "Plain", "unit test", List.of(withoutSize));

        TowerLibrary read = reader.readFromString(writer.writeToString(library));

        assertEquals(Optional.empty(), read.towers().get(0).moon().size());
        assertEquals(library, read);
    }

    @Test
    void writingIsStable() throws IOException {
        assertEquals(writer.writeToString(SampleTowers.library()), writer.writeToString(SampleTowers.library()));
    }

    @Test
    void rejectsWrongFormatAndVersion() throws IOException {
        String json = writer.writeToString(SampleTowers.library());

        assertThrows(TowerFormatException.class,
                () -> reader.readFromString(json.replace("tob2026-towerlib", "something-else")));
        assertThrows(TowerFormatException.class,
                () -> reader.readFromString(json.replace("\"version\" : 1", "\"version\" : 2")));
    }

    @Test
    void reportsAllProblemsWithTowerIds() throws IOException {
        String json = writer.writeToString(SampleTowers.library())
                .replace("FLR:ZAP", "FLR:XYZ")
                .replace("\"starDensity\" : 0", "\"starDensity\" : 9");

        TowerFormatException e = assertThrows(TowerFormatException.class, () -> reader.readFromString(json));

        assertEquals(2, e.problems().size(), e.problems().toString());
        assertTrue(e.problems().get(0).startsWith("sample-000:"), e.problems().toString());
        assertTrue(e.problems().get(1).startsWith("sample-001:"), e.problems().toString());
    }

    @Test
    void reportsMissingFields() throws IOException {
        String json = writer.writeToString(SampleTowers.library()).replace("\"author\"", "\"writer\"");

        TowerFormatException e = assertThrows(TowerFormatException.class, () -> reader.readFromString(json));

        assertTrue(e.problems().get(0).contains("author"), e.problems().toString());
    }

    @Test
    void rejectsMissingTowers() throws IOException {
        ObjectNode root = (ObjectNode) mapper.readTree(writer.writeToString(SampleTowers.library()));
        root.remove("towers");

        TowerFormatException e = assertThrows(TowerFormatException.class,
                () -> reader.readFromString(mapper.writeValueAsString(root)));

        assertTrue(e.problems().get(0).contains("towers"), e.problems().toString());
    }

    @Test
    void rejectsTowersOfWrongType() throws IOException {
        ObjectNode root = (ObjectNode) mapper.readTree(writer.writeToString(SampleTowers.library()));
        root.set("towers", mapper.createObjectNode());

        TowerFormatException e = assertThrows(TowerFormatException.class,
                () -> reader.readFromString(mapper.writeValueAsString(root)));

        assertTrue(e.problems().get(0).contains("towers"), e.problems().toString());
    }

    @Test
    void rejectsNonObjectTowerEntry() throws IOException {
        ObjectNode root = (ObjectNode) mapper.readTree(writer.writeToString(SampleTowers.library()));
        ArrayNode towers = mapper.createArrayNode();
        towers.add(42);
        root.set("towers", towers);

        TowerFormatException e = assertThrows(TowerFormatException.class,
                () -> reader.readFromString(mapper.writeValueAsString(root)));

        assertTrue(e.problems().get(0).startsWith("#0:"), e.problems().toString());
    }

    @Test
    void rejectsShortGroundColors() throws IOException {
        ObjectNode root = (ObjectNode) mapper.readTree(writer.writeToString(SampleTowers.library()));
        ObjectNode remake = (ObjectNode) root.get("towers").get(0).get("remake");
        ArrayNode groundColors = mapper.createArrayNode();
        groundColors.add("#FFFFFF");
        remake.set("groundColors", groundColors);

        TowerFormatException e = assertThrows(TowerFormatException.class,
                () -> reader.readFromString(mapper.writeValueAsString(root)));

        assertTrue(e.problems().get(0).contains("groundColors"), e.problems().toString());
    }
}
