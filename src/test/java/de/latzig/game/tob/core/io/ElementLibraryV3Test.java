package de.latzig.game.tob.core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.ElementDefinition;
import de.latzig.game.tob.core.model.ElementLibrary;
import de.latzig.game.tob.core.model.Lighting;
import de.latzig.game.tob.core.model.MeshGroup;
import de.latzig.game.tob.core.model.MeshPart;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.RenderType;

class ElementLibraryV3Test {

    private static ElementLibrary library() {
        ElementDefinition cvn = new ElementDefinition(ObjectType.CVN, List.of(new MeshGroup(1, List.of(
                new MeshPart("meshes/5500-converter-ns.tobmesh", "textures/5500-converter-color.png",
                        "textures/5501-converter-normal.png", RenderType.OPAQUE, 0xFFFFFF, true),
                new MeshPart("meshes/5501-converter-ns.tobmesh", "textures/9995-fx-color.png", null,
                        RenderType.GLASS, 0xFFFFFF, true, Lighting.UNLIT)))),
                List.of(), "test");
        return new ElementLibrary(Map.of(ObjectType.CVN, cvn), Map.of());
    }

    @Test
    void writesAndRoundTripsLightingPerPart() throws IOException {
        String json = new ElementLibraryWriter().writeToString(library());

        assertTrue(json.contains("\"version\" : 4"), json);
        assertTrue(json.contains("\"lighting\" : \"UNLIT\""), json);
        assertTrue(json.contains("\"lighting\" : \"LIT\""), json);
        int specular = json.indexOf("\"specular\"");
        int lighting = json.indexOf("\"lighting\"");
        int tint = json.indexOf("\"tint\"");
        assertTrue(specular < lighting && lighting < tint, "order specular, lighting, tint: " + json);
        assertEquals(library(), new ElementLibraryReader().readFromString(json));
    }

    @Test
    void readsVersion2AsLit() throws IOException {
        String v2 = new ElementLibraryWriter().writeToString(library()).replace("\"version\" : 4", "\"version\" : 2")
                .replaceAll("\n\s*\"lighting\" : \"[A-Z]+\",", "");

        ElementLibrary read = new ElementLibraryReader().readFromString(v2);

        for (MeshPart part : read.elements().get(ObjectType.CVN).groups().get(0).parts()) {
            assertEquals(Lighting.LIT, part.lighting(), part.mesh());
        }
    }

    @Test
    void rejectsAMissingOrUnknownLightingInVersion3() throws IOException {
        String v3 = new ElementLibraryWriter().writeToString(library());
        String missing = v3.replaceAll("\n\s*\"lighting\" : \"[A-Z]+\",", "");

        TowerFormatException e = assertThrows(TowerFormatException.class,
                () -> new ElementLibraryReader().readFromString(missing));
        assertTrue(e.problems().get(0).contains("lighting"), e.problems().toString());
        assertThrows(TowerFormatException.class,
                () -> new ElementLibraryReader().readFromString(v3.replace("\"UNLIT\"", "\"GLOWING\"")));
    }

    /** The regenerated asset library (Task 1 Step 6) marks exactly the three ToB 2005 Simple-shader parts unlit. */
    @Test
    void theGeneratedElementsJsonMarksTheThreeSimpleShaderPartsUnlit() throws IOException {
        Path file = Path.of(System.getProperty("tob.assets", "src/main/resources/assets"), "elements.json");
        assumeTrue(Files.isRegularFile(file), "generated assets not available");

        ElementLibrary library = new ElementLibraryReader().read(file);

        List<String> unlit = new ArrayList<>();
        for (ElementDefinition element : library.elements().values()) {
            for (MeshGroup group : element.groups()) {
                for (int i = 0; i < group.parts().size(); i++) {
                    if (group.parts().get(i).lighting() == Lighting.UNLIT) {
                        unlit.add(element.type().code() + "." + group.id() + "." + (i + 1));
                    }
                }
            }
        }
        assertEquals(List.of("CVE.1.2", "CVN.1.2", "EXC.1.3"), unlit);
        assertTrue(Files.readString(file).contains("\"version\" : 4"));
    }
}
