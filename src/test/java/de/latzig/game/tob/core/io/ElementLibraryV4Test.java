package de.latzig.game.tob.core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.ElementDefinition;
import de.latzig.game.tob.core.model.ElementLibrary;
import de.latzig.game.tob.core.model.Explosion;
import de.latzig.game.tob.core.model.MeshGroup;
import de.latzig.game.tob.core.model.MeshPart;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.RenderType;

class ElementLibraryV4Test {

    private static final MeshPart BODY = new MeshPart("meshes/5000-pushblock.tobmesh",
            "textures/5000-pushblock-color.png", null, RenderType.OPAQUE, 0xFFFFFF, true);

    private static ElementLibrary library() {
        ElementDefinition blk = new ElementDefinition(ObjectType.BLK, List.of(new MeshGroup(1, List.of(BODY))),
                List.of(), "test", new Explosion(6, List.of("meshes/5050-pushblock-expl.tobmesh")));
        ElementDefinition zap = new ElementDefinition(ObjectType.ZAP, List.of(new MeshGroup(1, List.of(BODY))),
                List.of(), "test", new Explosion(8, List.of()));
        return new ElementLibrary(Map.of(ObjectType.BLK, blk, ObjectType.ZAP, zap), Map.of());
    }

    @Test
    void writesAndRoundTripsTheExplosionOfEveryElement() throws IOException {
        String json = new ElementLibraryWriter().writeToString(library());

        assertTrue(json.contains("\"version\" : 4"), json);
        assertTrue(json.contains("\"spriteOffset\" : 8"), json);
        assertTrue(json.contains("\"meshes\" : [") && json.contains("\"meshes/5050-pushblock-expl.tobmesh\""),
                json);
        assertTrue(json.indexOf("\"animations\"") < json.indexOf("\"explosion\"")
                && json.indexOf("\"explosion\"") < json.indexOf("\"source\""), "order animations, explosion, source");
        assertEquals(library(), new ElementLibraryReader().readFromString(json));
    }

    @Test
    void olderVersionsReadAsTheDefaultExplosion() throws IOException {
        String v3 = new ElementLibraryWriter().writeToString(library()).replace("\"version\" : 4", "\"version\" : 3");

        ElementLibrary read = new ElementLibraryReader().readFromString(v3);

        assertEquals(Explosion.DEFAULT, read.elements().get(ObjectType.BLK).explosion());
        assertEquals(new Explosion(6, List.of()), Explosion.DEFAULT);
    }

    @Test
    void aVersion4ElementNeedsAValidExplosion() throws IOException {
        String json = new ElementLibraryWriter().writeToString(library());
        String missing = json.replace("\"explosion\"", "\"explosionX\"");

        TowerFormatException e = assertThrows(TowerFormatException.class,
                () -> new ElementLibraryReader().readFromString(missing));
        assertTrue(e.problems().get(0).contains("explosion"), e.problems().toString());
        assertThrows(TowerFormatException.class, () -> new ElementLibraryReader().readFromString(
                json.replace("\"spriteOffset\" : 8", "\"spriteOffset\" : \"high\"")));
        assertThrows(IllegalArgumentException.class, () -> new ElementDefinition(ObjectType.BLK,
                List.of(new MeshGroup(1, List.of(BODY))), List.of(), "test",
                new Explosion(6, List.of("meshes/a.tobmesh", "meshes/b.tobmesh"))), "more meshes than parts");
    }
}
