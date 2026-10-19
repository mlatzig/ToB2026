package de.latzig.game.tob.core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.latzig.game.tob.core.io.TowerLibraryLoader.LoadedLibrary;
import de.latzig.game.tob.core.io.TowerLibraryLoader.LoadedTower;
import de.latzig.game.tob.core.model.BaseType;
import de.latzig.game.tob.core.model.Cell;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.SampleTowers;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.model.TowerGrid;
import de.latzig.game.tob.core.model.TowerLibrary;
import de.latzig.game.tob.core.sim.TowerAssets;

class TowerLibraryLoaderTest {

    private final TowerLibraryWriter writer = new TowerLibraryWriter();
    private final TowerLibraryLoader loader = new TowerLibraryLoader();
    private final ObjectMapper mapper = new ObjectMapper();

    private static TowerDefinition withGrid(String id, TowerGrid grid) {
        TowerDefinition t = SampleTowers.tower(id);
        return new TowerDefinition(t.id(), t.group(), t.index(), t.title(), t.author(), t.conditions(),
                t.behaviour(), t.effects(), t.moon(), t.palette(), t.sounds(), t.lock(), grid, t.remake());
    }

    @Test
    void soundTowersAreLoadedAsPlayable() throws IOException {
        LoadedLibrary library = loader.loadFromString(writer.writeToString(SampleTowers.library()));

        assertEquals("sample", library.id());
        assertEquals(2, library.towers().size());
        assertEquals(SampleTowers.library().towers(), library.playableTowers());
        assertTrue(library.skipped().isEmpty());
    }

    @Test
    void invalidTowersAreListedAndSkipped() throws IOException {
        TowerGrid badLift = TowerGrid.builder().set(5, 5, 0, new Cell(BaseType.LIFT_UP_START, ObjectType.ZAP)).build();
        TowerGrid twoZappers = TowerGrid.builder().set(5, 5, 0, new Cell(BaseType.FLOOR, ObjectType.ZAP))
                .set(6, 5, 0, new Cell(BaseType.FLOOR, ObjectType.ZAP)).build();
        TowerLibrary source = new TowerLibrary("mixed", "Mixed", "unit test", List.of(SampleTowers.tower("ok-000"),
                withGrid("lift-001", badLift), withGrid("twice-002", twoZappers)));

        LoadedLibrary library = loader.loadFromString(writer.writeToString(source));

        assertEquals(List.of(SampleTowers.tower("ok-000")), library.playableTowers());
        List<LoadedTower> skipped = library.skipped();
        assertEquals(List.of("lift-001", "twice-002"), skipped.stream().map(LoadedTower::label).toList());
        assertTrue(skipped.get(0).tower().isPresent());
        assertTrue(skipped.get(0).problems().get(0).startsWith("LIFT_WITHOUT_STOP"), skipped.get(0).problems().get(0));
        assertTrue(skipped.get(1).problems().get(0).contains("ZAP placed 2 times"), skipped.get(1).problems().get(0));
    }

    @Test
    void towersThatCannotBeParsedAreListedInsteadOfThrown() throws IOException {
        ObjectNode root = (ObjectNode) mapper.readTree(writer.writeToString(SampleTowers.library()));
        ((ObjectNode) root.get("towers").get(1).get("conditions")).put("klondikes", 9);

        LoadedLibrary library = loader.loadFromString(mapper.writeValueAsString(root));

        assertEquals(List.of(SampleTowers.tower("sample-000")), library.playableTowers());
        LoadedTower broken = library.towers().get(1);
        assertEquals("sample-001", broken.label());
        assertTrue(broken.tower().isEmpty());
        assertEquals(1, broken.problems().size());
        assertTrue(broken.problems().get(0).contains("klondikes"), broken.problems().get(0));
    }

    @Test
    void aBrokenLibraryHeaderStillThrows() {
        assertThrows(TowerFormatException.class, () -> loader.loadFromString("{\"format\":\"other\",\"version\":1}"));
    }

    @Test
    void everyRealTowerIsPlayable() throws IOException {
        assumeTrue(TowerAssets.available(), "tower assets not generated");
        int towers = 0;
        for (String name : TowerAssets.LIBRARIES) {
            LoadedLibrary library = loader.load(TowerAssets.directory().resolve(name + ".json"));
            assertEquals(List.of(), library.skipped(), name);
            towers += library.playableTowers().size();
        }

        assertEquals(144, towers);
    }
}
