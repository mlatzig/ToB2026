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

import de.latzig.game.tob.core.model.SoundCatalog;
import de.latzig.game.tob.core.model.SoundDetail;
import de.latzig.game.tob.core.model.SoundEntry;

class SoundCatalogJsonTest {

    private static SoundCatalog sample() {
        return new SoundCatalog(Map.of(
                "2000", new SoundEntry("sounds/2000-zap.wav", false, true, 0.125, 32.0, SoundDetail.LOW,
                        "player zapper fires"),
                "0091", new SoundEntry("sounds/0091-noise.wav", true, false, 0.0, 0.0, SoundDetail.MED,
                        "camera disabled")));
    }

    @Test
    void roundTripsWithLfAndSortedIds(@TempDir Path dir) throws IOException {
        String json = new SoundCatalogWriter().writeToString(sample());

        assertFalse(json.contains("\r"));
        assertTrue(json.contains("\"format\" : \"tob2026-sounds\""), json);
        assertTrue(json.contains("\"minDistance\" : 0.125"), json);
        assertTrue(json.contains("\"maxDistance\" : 32.0"), json);
        assertTrue(json.contains("\"detail\" : \"LOW\""), json);
        assertTrue(json.indexOf("\"0091\"") < json.indexOf("\"2000\""), "ids sorted");
        assertEquals(sample(), new SoundCatalogReader().readFromString(json));
        new SoundCatalogWriter().write(sample(), dir.resolve("sounds/sounds.json"));
        assertEquals(sample(), new SoundCatalogReader().read(dir.resolve("sounds/sounds.json")));
    }

    @Test
    void modelValidatesDistancesAndIds() {
        assertThrows(IllegalArgumentException.class,
                () -> new SoundEntry("f", false, true, 0.0, 32.0, SoundDetail.LOW, "t"));
        assertThrows(IllegalArgumentException.class,
                () -> new SoundEntry("f", false, true, 2.0, 1.0, SoundDetail.LOW, "t"));
        assertThrows(IllegalArgumentException.class,
                () -> new SoundEntry("f", false, false, 0.0, 1.0, SoundDetail.LOW, "t"));
        SoundEntry ok = new SoundEntry("f", false, false, 0.0, 0.0, SoundDetail.HIGH, "t");
        assertThrows(IllegalArgumentException.class, () -> new SoundCatalog(Map.of("200", ok)));
    }

    @Test
    void collectsBadEntries() throws IOException {
        String json = new SoundCatalogWriter().writeToString(sample())
                .replace("\"LOW\"", "\"LOUD\"")
                .replace("\"loop\" : true", "\"loop\" : \"yes\"");

        TowerFormatException e = assertThrows(TowerFormatException.class,
                () -> new SoundCatalogReader().readFromString(json));

        assertEquals(2, e.problems().size(), e.problems().toString());
        assertThrows(TowerFormatException.class, () -> new SoundCatalogReader()
                .readFromString(new SoundCatalogWriter().writeToString(sample()).replace("tob2026-sounds", "x")));
    }
}
