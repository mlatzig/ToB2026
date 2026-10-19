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

import de.latzig.game.tob.core.model.FxAtlas;
import de.latzig.game.tob.core.model.FxSequence;
import de.latzig.game.tob.core.model.FxSprite;

/** Reads {@code fx/fx-atlas.json}; collects all problems before failing. */
public final class FxAtlasReader {

    private final ObjectMapper mapper = new ObjectMapper();

    public FxAtlas read(Path file) throws IOException {
        return readFromString(Files.readString(file, StandardCharsets.UTF_8));
    }

    public FxAtlas readFromString(String json) throws IOException {
        JsonNode root = mapper.readTree(json);
        if (!FxAtlasWriter.FORMAT.equals(root.path("format").asText())
                || root.path("version").asInt(-1) != FxAtlasWriter.VERSION) {
            throw new TowerFormatException(List.of("fx atlas: unsupported format/version"));
        }
        int w = root.path("imageWidth").asInt(-1);
        int h = root.path("imageHeight").asInt(-1);
        List<String> problems = new ArrayList<>();
        Map<String, FxSprite> sprites = new TreeMap<>();
        for (Map.Entry<String, JsonNode> e : root.path("sprites").properties()) {
            JsonNode n = e.getValue();
            try {
                FxSprite s = new FxSprite(n.path("x").asInt(-1), n.path("y").asInt(-1), n.path("w").asInt(-1),
                        n.path("h").asInt(-1));
                if (!FxAtlas.inside(s.x(), s.y(), s.width(), s.height(), w, h)) {
                    problems.add("sprite " + e.getKey() + ": outside the " + w + "x" + h + " image");
                } else {
                    sprites.put(e.getKey(), s);
                }
            } catch (IllegalArgumentException ex) {
                problems.add("sprite " + e.getKey() + ": " + ex.getMessage());
            }
        }
        Map<String, FxSequence> sequences = new TreeMap<>();
        for (Map.Entry<String, JsonNode> e : root.path("sequences").properties()) {
            JsonNode n = e.getValue();
            try {
                FxSequence q = new FxSequence(n.path("x").asInt(-1), n.path("y").asInt(-1),
                        n.path("cellW").asInt(-1), n.path("cellH").asInt(-1), n.path("columns").asInt(-1),
                        n.path("frames").asInt(-1));
                if (!FxAtlas.inside(q.x(), q.y(), q.width(), q.height(), w, h)) {
                    problems.add("sequence " + e.getKey() + ": outside the " + w + "x" + h + " image");
                } else {
                    sequences.put(e.getKey(), q);
                }
            } catch (IllegalArgumentException ex) {
                problems.add("sequence " + e.getKey() + ": " + ex.getMessage());
            }
        }
        if (!root.path("image").isTextual() || !root.path("source").isTextual()) {
            problems.add("fx atlas: missing image or source");
        }
        if (!problems.isEmpty()) {
            throw new TowerFormatException(problems);
        }
        try {
            return new FxAtlas(root.get("image").asText(), w, h, sprites, sequences, root.get("source").asText());
        } catch (IllegalArgumentException e) {
            throw new TowerFormatException(List.of("fx atlas: " + e.getMessage()));
        }
    }
}
