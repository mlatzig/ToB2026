package de.latzig.game.tob.core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.latzig.game.tob.core.model.GuiAtlas;
import de.latzig.game.tob.core.model.GuiRegion;

class GuiAtlasJsonTest {

    static GuiAtlas sample() {
        return new GuiAtlas("gui/gui-atlas.png", 1024, 1024,
                Map.of("gui.window_left_active_caption", GuiRegion.fromInclusive(512, 0, 516, 23),
                        "panel.zapper0", GuiRegion.fromInclusive(12, 520, 211, 579)),
                Map.of("window_border", 0xFFFFFF), Map.of("window_caption_height", 24),
                Map.of("font.size", "20.0"));
    }

    @Test
    void convertsInclusiveRectangles() {
        assertEquals(new GuiRegion(512, 0, 5, 24), GuiRegion.fromInclusive(512, 0, 516, 23));
        assertThrows(IllegalArgumentException.class, () -> GuiRegion.fromInclusive(10, 10, 9, 20));
    }

    @Test
    void rejectsRegionsOutsideTheImage() {
        assertThrows(IllegalArgumentException.class, () -> new GuiAtlas("x.png", 100, 100,
                Map.of("a", new GuiRegion(90, 0, 20, 10)), Map.of(), Map.of(), Map.of()));
    }

    @Test
    void roundTripsWithStableLfOutput() throws IOException {
        String json = new GuiAtlasWriter().writeToString(sample());

        assertEquals(sample(), new GuiAtlasReader().readFromString(json));
        assertFalse(json.contains("\r"));
        assertTrue(json.contains("\"format\" : \"tob2026-guiatlas\""));
        assertTrue(json.indexOf("gui.window_left_active_caption") < json.indexOf("panel.zapper0"));
        assertTrue(json.contains("\"window_border\" : \"#FFFFFF\""));
    }

    @Test
    void reportsBadRegions() throws IOException {
        String json = new GuiAtlasWriter().writeToString(sample()).replace("\"w\" : 5", "\"w\" : 0");

        assertThrows(TowerFormatException.class, () -> new GuiAtlasReader().readFromString(json));
    }

    @Test
    void reportsAllOutOfBoundsRegions() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        String json = new GuiAtlasWriter().writeToString(sample());
        JsonNode root = mapper.readTree(json);

        // Move first region outside the image (x too large)
        ((com.fasterxml.jackson.databind.node.ObjectNode) root.path("regions")
                .path("gui.window_left_active_caption")).put("x", 1020);

        // Move second region outside the image (y too large)
        ((com.fasterxml.jackson.databind.node.ObjectNode) root.path("regions")
                .path("panel.zapper0")).put("y", 1000);

        // Break one colour (not #RRGGBB format)
        ((com.fasterxml.jackson.databind.node.ObjectNode) root.path("colors"))
                .put("window_border", "INVALID");

        String badJson = mapper.writeValueAsString(root);

        TowerFormatException ex = assertThrows(TowerFormatException.class,
                () -> new GuiAtlasReader().readFromString(badJson));
        List<String> problems = ex.problems();
        assertEquals(3, problems.size(), "Expected exactly 3 problems: " + problems);
    }
}
