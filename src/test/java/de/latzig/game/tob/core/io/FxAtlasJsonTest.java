package de.latzig.game.tob.core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.latzig.game.tob.core.model.FxAtlas;
import de.latzig.game.tob.core.model.FxSequence;
import de.latzig.game.tob.core.model.FxSprite;

class FxAtlasJsonTest {

    private static FxAtlas sample() {
        return new FxAtlas("textures/fx.png", 256, 256,
                Map.of("sun", new FxSprite(128, 0, 64, 64), "blend", new FxSprite(0, 200, 128, 32)),
                Map.of("smoke", new FxSequence(0, 64, 32, 32, 4, 6)), "test");
    }

    @Test
    void roundTripsWithLfAndSortedKeys(@TempDir Path dir) throws IOException {
        String json = new FxAtlasWriter().writeToString(sample());

        assertFalse(json.contains("\r"));
        assertTrue(json.contains("\"format\" : \"tob2026-fxatlas\""), json);
        assertTrue(json.contains("\"cellW\" : 32"), json);
        assertTrue(json.indexOf("\"blend\"") < json.indexOf("\"sun\""), "sprites sorted by name");
        assertEquals(sample(), new FxAtlasReader().readFromString(json));
        new FxAtlasWriter().write(sample(), dir.resolve("fx/fx-atlas.json"));
        assertEquals(sample(), new FxAtlasReader().read(dir.resolve("fx/fx-atlas.json")));
    }

    @Test
    void sequencesDefineTheirFramesAsAGrid() {
        FxSequence smoke = sample().sequences().get("smoke");

        assertEquals(2, smoke.rows());
        assertEquals(128, smoke.width());
        assertEquals(64, smoke.height());
        assertEquals(new FxSprite(32, 96, 32, 32), smoke.frame(5));
        assertThrows(IllegalArgumentException.class, () -> smoke.frame(6));
        assertEquals(64, new FxSequence(0, 0, 16, 16, 12, 4).width(), "fewer frames than columns");
    }

    @Test
    void collectsEverythingOutsideTheImage() throws IOException {
        String json = new FxAtlasWriter().writeToString(sample())
                .replace("\"x\" : 128", "\"x\" : 250")
                .replace("\"frames\" : 6", "\"frames\" : 40");

        TowerFormatException e = assertThrows(TowerFormatException.class,
                () -> new FxAtlasReader().readFromString(json));

        assertEquals(2, e.problems().size(), e.problems().toString());
        assertTrue(e.problems().get(0).contains("outside the 256x256 image"), e.problems().toString());
        assertThrows(IllegalArgumentException.class, () -> new FxAtlas("i", 64, 64,
                Map.of("x", new FxSprite(60, 0, 8, 8)), Map.of(), "s"));
        assertThrows(TowerFormatException.class, () -> new FxAtlasReader()
                .readFromString(new FxAtlasWriter().writeToString(sample()).replace("tob2026-fxatlas", "x")));
    }
}
