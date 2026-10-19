package de.latzig.game.tob.core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.AnimationRange;
import de.latzig.game.tob.core.model.ElementDefinition;
import de.latzig.game.tob.core.model.ElementLibrary;
import de.latzig.game.tob.core.model.MeshGroup;
import de.latzig.game.tob.core.model.MeshPart;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.RenderType;

class ElementLibraryV2Test {

    private static ElementLibrary library() {
        ElementDefinition frz = new ElementDefinition(ObjectType.FRZ, List.of(new MeshGroup(1, List.of(
                new MeshPart("meshes/5900-freezer.tobmesh", "textures/5900-freezer-color.png",
                        "textures/5901-freezer-normal.png", RenderType.OPAQUE, 0xFFFFFF, true),
                new MeshPart("meshes/5901-freezer.tobmesh", "textures/5900-freezer-color.png",
                        "textures/5901-freezer-normal.png", RenderType.OPAQUE, 0xFFFFFF, false),
                new MeshPart("meshes/5902-freezer.tobmesh", "textures/5900-freezer-color.png",
                        "textures/5901-freezer-normal.png", RenderType.GLASS, 0xFFFFFF, true)))),
                List.of(new AnimationRange("run", 0, 2048)), "test");
        return new ElementLibrary(Map.of(ObjectType.FRZ, frz), Map.of());
    }

    private static String toVersion1(String v3) {
        return v3.replace("\"version\" : 4", "\"version\" : 1")
                .replaceAll("\\n\\s*\"specular\" : (true|false),", "")
                .replaceAll("\\n\\s*\"lighting\" : \"[A-Z]+\",", "");
    }

    @Test
    void writesSpecularPerPart() throws IOException {
        String json = new ElementLibraryWriter().writeToString(library());

        assertTrue(json.contains("\"version\" : 4"), json);
        assertTrue(json.contains("\"specular\" : false"), json);
        assertEquals(3, json.split("\"specular\"", -1).length - 1, json);
        int render = json.indexOf("\"render\"");
        int specular = json.indexOf("\"specular\"");
        int tint = json.indexOf("\"tint\"");
        assertTrue(render < specular && specular < tint, "order render, specular, tint: " + json);
    }

    @Test
    void roundTripsVersion2() throws IOException {
        ElementLibrary read = new ElementLibraryReader()
                .readFromString(new ElementLibraryWriter().writeToString(library()));

        assertEquals(library(), read);
        assertFalse(read.elements().get(ObjectType.FRZ).groups().get(0).parts().get(1).specular());
    }

    @Test
    void readsVersion1WithSpecularTrue() throws IOException {
        String v1 = toVersion1(new ElementLibraryWriter().writeToString(library()));
        assertFalse(v1.contains("specular"), v1);

        ElementLibrary read = new ElementLibraryReader().readFromString(v1);

        for (MeshPart part : read.elements().get(ObjectType.FRZ).groups().get(0).parts()) {
            assertTrue(part.specular(), part.mesh());
        }
    }

    @Test
    void rejectsMissingSpecularInVersion2AndUnknownVersions() throws IOException {
        String v2 = new ElementLibraryWriter().writeToString(library());
        String withoutSpecular = toVersion1(v2).replace("\"version\" : 1", "\"version\" : 2");

        TowerFormatException e = assertThrows(TowerFormatException.class,
                () -> new ElementLibraryReader().readFromString(withoutSpecular));
        assertTrue(e.problems().get(0).contains("specular"), e.problems().toString());
        assertThrows(TowerFormatException.class,
                () -> new ElementLibraryReader().readFromString(v2.replace("\"version\" : 4", "\"version\" : 5")));
    }

    @Test
    void fiveArgumentPartKeepsThePhase1bLook() {
        assertTrue(new MeshPart("m", "t", null, RenderType.OPAQUE, 0xFFFFFF).specular());
    }
}
