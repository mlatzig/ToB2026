package de.latzig.game.tob.docs;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.io.ElementLibraryReader;
import de.latzig.game.tob.core.io.GuiAtlasReader;
import de.latzig.game.tob.core.io.MeshReader;
import de.latzig.game.tob.core.io.MeshWriter;
import de.latzig.game.tob.core.io.TowerLibraryLoader;
import de.latzig.game.tob.core.io.TowerLibraryReader;
import de.latzig.game.tob.core.model.ElementLibrary;
import de.latzig.game.tob.core.model.GuiAtlas;
import de.latzig.game.tob.core.model.MeshData;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.model.TowerLibrary;

/**
 * Phase 7b spec decision 8: the examples of {@code docs/formats/} are read by the project's own readers exactly as
 * the documents describe them, so a format change that breaks an example fails here.
 */
class FormatDocsTest {

    @Test
    void theTowerLibraryExampleReadsAsDescribed() throws IOException {
        String json = PublishedDocs.codeBlocks(PublishedDocs.read("docs/formats/tower-json.md"), "json").get(0);

        TowerLibrary library = new TowerLibraryReader().readFromString(json);

        assertEquals("example", library.id());
        TowerDefinition tower = library.towers().get(0);
        assertEquals("First Steps", tower.title());
        assertEquals(1, tower.conditions().klondikes());
        assertEquals(ObjectType.GRB, tower.grid().cell(6, 7, 0).object());
        assertEquals(ObjectType.KLD, tower.grid().cell(9, 7, 0).object());
        assertTrue(new TowerLibraryLoader().loadFromString(json).skipped().isEmpty(), "the game can load it");
    }

    @Test
    void theElementCatalogueExampleReadsAsDescribed() throws IOException {
        String json = PublishedDocs.codeBlocks(PublishedDocs.read("docs/formats/elements-json.md"), "json").get(0);

        ElementLibrary library = new ElementLibraryReader().readFromString(json);

        assertEquals(1, library.elements().size());
        assertEquals("meshes/2500-klondike.tobmesh",
                library.elements().get(ObjectType.KLD).groups().get(0).parts().get(0).mesh());
        assertEquals(List.of("ground", "static"), List.copyOf(library.base().keySet()));
    }

    @Test
    void theGuiAtlasExampleReadsAsDescribed() throws IOException {
        String json = PublishedDocs.codeBlocks(PublishedDocs.read("docs/formats/gui-atlas-and-font.md"), "json")
                .get(0);

        GuiAtlas atlas = new GuiAtlasReader().readFromString(json);

        assertEquals("gui/gui-atlas.png", atlas.image());
        assertEquals(List.of("editor.kld", "gui.cursor_normal", "panel.bar"), List.copyOf(atlas.regions().keySet()));
        assertEquals(5, atlas.geometry().get("button_border_width"));
    }

    @Test
    void theMeshExampleIsWhatTheWriterWritesForOneTriangle() throws IOException {
        String dump = PublishedDocs.codeBlocks(PublishedDocs.read("docs/formats/tobmesh.md"), "text").get(0);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (String line : dump.split("\n")) {
            String[] fields = line.trim().split("\\s+");
            for (int i = 1; i < fields.length; i++) {
                bytes.write(Integer.parseInt(fields[i], 16));
            }
        }

        MeshData mesh = MeshReader.fromBytes(bytes.toByteArray());

        assertEquals(88, bytes.size());
        assertEquals(1, mesh.triangleCount());
        assertArrayEquals(new int[] {0}, mesh.keys());
        assertArrayEquals(new float[] {-0.5f, 0f, 0f, 0.5f, 0f, 0f, 0f, 1f, 0f}, mesh.positions(0));
        assertArrayEquals(new float[] {0f, 1f, 1f, 1f, 0.5f, 0f}, mesh.uvs(0));
        assertArrayEquals(MeshWriter.toBytes(mesh), bytes.toByteArray(), "byte for byte what the writer writes");
    }
}
