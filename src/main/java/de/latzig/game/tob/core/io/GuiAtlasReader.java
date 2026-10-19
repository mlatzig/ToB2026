package de.latzig.game.tob.core.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.latzig.game.tob.core.model.GuiAtlas;
import de.latzig.game.tob.core.model.GuiRegion;

/** Reads {@code gui-atlas.json}; collects all problems before failing. */
public final class GuiAtlasReader {

    private final ObjectMapper mapper = new ObjectMapper();

    public GuiAtlas read(Path file) throws IOException {
        return readFromString(Files.readString(file, StandardCharsets.UTF_8));
    }

    public GuiAtlas readFromString(String json) throws IOException {
        JsonNode root = mapper.readTree(json);
        if (!GuiAtlasWriter.FORMAT.equals(root.path("format").asText())
                || root.path("version").asInt(-1) != GuiAtlasWriter.VERSION) {
            throw new TowerFormatException(List.of("gui atlas: unsupported format/version"));
        }
        int imageWidth = root.path("imageWidth").asInt();
        int imageHeight = root.path("imageHeight").asInt();
        List<String> problems = new ArrayList<>();
        Map<String, GuiRegion> regions = new TreeMap<>();
        for (Map.Entry<String, JsonNode> e : root.path("regions").properties()) {
            JsonNode r = e.getValue();
            try {
                GuiRegion region = new GuiRegion(r.path("x").asInt(-1), r.path("y").asInt(-1),
                        r.path("w").asInt(-1), r.path("h").asInt(-1));
                if (region.x() + region.width() > imageWidth || region.y() + region.height() > imageHeight) {
                    problems.add("region " + e.getKey() + ": outside the " + imageWidth + "x" + imageHeight + " image");
                } else {
                    regions.put(e.getKey(), region);
                }
            } catch (IllegalArgumentException ex) {
                problems.add("region " + e.getKey() + ": " + ex.getMessage());
            }
        }
        Map<String, Integer> colors = new TreeMap<>();
        for (Map.Entry<String, JsonNode> e : root.path("colors").properties()) {
            String text = e.getValue().asText();
            if (!text.matches("#[0-9A-Fa-f]{6}")) {
                problems.add("color " + e.getKey() + ": not #RRGGBB: " + text);
            } else {
                colors.put(e.getKey(), Integer.parseInt(text.substring(1), 16));
            }
        }
        Map<String, Integer> geometry = new TreeMap<>();
        for (Map.Entry<String, JsonNode> e : root.path("geometry").properties()) {
            if (!e.getValue().isInt()) {
                problems.add("geometry " + e.getKey() + ": not an integer");
            } else {
                geometry.put(e.getKey(), e.getValue().asInt());
            }
        }
        Map<String, String> general = new TreeMap<>();
        for (Map.Entry<String, JsonNode> e : root.path("general").properties()) {
            general.put(e.getKey(), e.getValue().asText());
        }
        if (!problems.isEmpty()) {
            throw new TowerFormatException(problems);
        }
        try {
            return new GuiAtlas(root.path("image").asText(), imageWidth, imageHeight, regions, colors, geometry,
                    general);
        } catch (IllegalArgumentException e) {
            throw new TowerFormatException(List.of("gui atlas: " + e.getMessage()));
        }
    }
}
