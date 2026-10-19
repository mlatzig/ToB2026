package de.latzig.game.tob.core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.latzig.game.tob.core.model.AnimationRange;
import de.latzig.game.tob.core.model.BaseTexture;
import de.latzig.game.tob.core.model.ElementDefinition;
import de.latzig.game.tob.core.model.ElementLibrary;
import de.latzig.game.tob.core.model.MeshGroup;
import de.latzig.game.tob.core.model.MeshPart;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.RenderType;

class ElementLibraryJsonTest {

    static ElementLibrary sample() {
        ElementDefinition zap = new ElementDefinition(ObjectType.ZAP, List.of(
                new MeshGroup(1, List.of(new MeshPart("meshes/2000-zapper-n.tobmesh", "textures/2000-zapper-color.png",
                        "textures/2001-zapper-normal.png", RenderType.OPAQUE, 0xFFFFFF))),
                new MeshGroup(2, List.of(new MeshPart("meshes/2010-zapper-e.tobmesh", "textures/2000-zapper-color.png",
                        "textures/2001-zapper-normal.png", RenderType.OPAQUE, 0xFFFFFF)))),
                List.of(new AnimationRange("walk", 0, 512), new AnimationRange("idle", 512, 640)), "test");
        ElementDefinition pne = new ElementDefinition(ObjectType.PNE, List.of(new MeshGroup(1, List.of(
                new MeshPart("meshes/5200-prism-ne.tobmesh", "textures/5200-prism-socket-color.png", null,
                        RenderType.OPAQUE, 0xFFFFFF),
                new MeshPart("meshes/5201-prism-ne.tobmesh", "textures/5210-prism-glass-color.png",
                        "textures/5211-prism-glass-normal.png", RenderType.GLASS, 0x80FF80)))),
                List.of(), "test");
        return new ElementLibrary(Map.of(ObjectType.ZAP, zap, ObjectType.PNE, pne),
                Map.of("playground", new BaseTexture("textures/0001-playground-color.png", null)));
    }

    @Test
    void roundTrips(@TempDir Path dir) throws IOException {
        ElementLibraryWriter writer = new ElementLibraryWriter();
        ElementLibraryReader reader = new ElementLibraryReader();

        assertEquals(sample(), reader.readFromString(writer.writeToString(sample())));
        writer.write(sample(), dir.resolve("a/elements.json"));
        assertEquals(sample(), reader.read(dir.resolve("a/elements.json")));
    }

    @Test
    void writesStableLfJsonWithCodesAsKeys() throws IOException {
        String json = new ElementLibraryWriter().writeToString(sample());

        assertFalse(json.contains("\r"));
        assertTrue(json.contains("\"format\" : \"tob2026-elements\""));
        assertTrue(json.indexOf("\"PNE\"") < json.indexOf("\"ZAP\""), "elements sorted by code");
        assertTrue(json.contains("\"tint\" : \"#80FF80\""));
        assertTrue(json.contains("\"render\" : \"GLASS\""));
        assertEquals(json, new ElementLibraryWriter().writeToString(sample()));
    }

    @Test
    void rejectsBadData() throws IOException {
        String json = new ElementLibraryWriter().writeToString(sample());

        assertThrows(TowerFormatException.class,
                () -> new ElementLibraryReader().readFromString(json.replace("\"ZAP\"", "\"XYZ\"")));
        assertThrows(TowerFormatException.class,
                () -> new ElementLibraryReader().readFromString(json.replace("GLASS", "SHINY")));
        assertThrows(TowerFormatException.class,
                () -> new ElementLibraryReader().readFromString(json.replace("tob2026-elements", "x")));
    }

    @Test
    void modelValidatesArguments() {
        assertThrows(IllegalArgumentException.class, () -> new AnimationRange("x", 10, 5));
        assertThrows(IllegalArgumentException.class, () -> new MeshGroup(0, List.of()));
        ElementDefinition zap = sample().elements().get(ObjectType.ZAP);
        assertThrows(IllegalArgumentException.class,
                () -> new ElementLibrary(Map.of(ObjectType.PSH, zap), Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new ElementDefinition(ObjectType.ZAP, zap.groups(),
                        List.of(new AnimationRange("walk", 0, 10), new AnimationRange("walk", 20, 30)), "test"));
    }
}
