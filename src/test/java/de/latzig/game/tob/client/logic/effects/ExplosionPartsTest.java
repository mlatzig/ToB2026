package de.latzig.game.tob.client.logic.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.client.logic.MeshFrame;
import de.latzig.game.tob.core.io.ElementLibraryReader;
import de.latzig.game.tob.core.model.ElementLibrary;
import de.latzig.game.tob.core.model.ObjectType;

class ExplosionPartsTest {

    private static ElementLibrary library() throws IOException {
        Path file = Path.of(System.getProperty("tob.assets", "src/main/resources/assets"), "elements.json");
        assumeTrue(Files.isRegularFile(file), "generated assets not available");
        return new ElementLibraryReader().read(file);
    }

    private static List<String> meshes(List<ExplosionParts.Piece> pieces) {
        return pieces.stream().map(ExplosionParts.Piece::mesh).toList();
    }

    @Test
    void anEnemyPusherExplodesItsOwnPartsAtTheKeyShown() throws IOException {
        ElementLibrary library = library();

        List<ExplosionParts.Piece> pieces = ExplosionParts.of(library.elements().get(ObjectType.FPN),
                MeshFrame.of(1, 40f));

        // spec §2 decision 3: not ToB 2005's FPW+1 (twr.cpp:3619-3635), the west pusher's second part
        assertEquals(List.of("meshes/3000-enemypusher-n.tobmesh", "meshes/3001-enemypusher-n.tobmesh"),
                meshes(pieces));
        assertEquals(List.of(40f, 40f), pieces.stream().map(ExplosionParts.Piece::key).toList());
        assertEquals(library.elements().get(ObjectType.FPN).groups().get(0).parts(),
                pieces.stream().map(ExplosionParts.Piece::part).toList());
        assertEquals(List.of("meshes/4020-hopper-e.tobmesh"),
                meshes(ExplosionParts.of(library.elements().get(ObjectType.HOP), MeshFrame.of(3, 100f))),
                "the group shown: a hopper jumping east");
    }

    @Test
    void preBrokenElementsUseTheirDebrisMeshesWithTheFirstGroupsMaterials() throws IOException {
        ElementLibrary library = library();

        List<ExplosionParts.Piece> prism = ExplosionParts.of(library.elements().get(ObjectType.PNW),
                MeshFrame.of(1, 0f));
        List<ExplosionParts.Piece> exchanger = ExplosionParts.of(library.elements().get(ObjectType.EXC),
                MeshFrame.of(1, 48f));

        assertEquals(List.of("meshes/5250-prism-expl-nw.tobmesh", "meshes/5251-prism-expl-nw.tobmesh"),
                meshes(prism));
        assertEquals(library.elements().get(ObjectType.PNW).groups().get(0).parts().get(1).texture(),
                prism.get(1).part().texture(), "the glass half keeps the glass texture");
        assertEquals(List.of("meshes/5450-exchanger-expl.tobmesh", "meshes/5451-exchanger-expl.tobmesh"),
                meshes(exchanger), "no sparkle debris");
        assertEquals(List.of(0f, 0f), exchanger.stream().map(ExplosionParts.Piece::key).toList());
        assertEquals("meshes/1550-expl_floor.tobmesh", ExplosionParts.FLOOR_DEBRIS);
    }
}
