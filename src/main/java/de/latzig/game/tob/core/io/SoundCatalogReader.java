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

import de.latzig.game.tob.core.model.SoundCatalog;
import de.latzig.game.tob.core.model.SoundDetail;
import de.latzig.game.tob.core.model.SoundEntry;

/** Reads {@code sounds/sounds.json}; collects all problems before failing. */
public final class SoundCatalogReader {

    private final ObjectMapper mapper = new ObjectMapper();

    public SoundCatalog read(Path file) throws IOException {
        return readFromString(Files.readString(file, StandardCharsets.UTF_8));
    }

    public SoundCatalog readFromString(String json) throws IOException {
        JsonNode root = mapper.readTree(json);
        if (!SoundCatalogWriter.FORMAT.equals(root.path("format").asText())
                || root.path("version").asInt(-1) != SoundCatalogWriter.VERSION) {
            throw new TowerFormatException(List.of("sounds: unsupported format/version"));
        }
        List<String> problems = new ArrayList<>();
        Map<String, SoundEntry> sounds = new TreeMap<>();
        for (Map.Entry<String, JsonNode> e : root.path("sounds").properties()) {
            try {
                if (!e.getKey().matches("\\d{4}")) {
                    throw new IllegalArgumentException("sound id must have 4 digits");
                }
                sounds.put(e.getKey(), entry(e.getValue()));
            } catch (IllegalArgumentException ex) {
                problems.add("sound " + e.getKey() + ": " + ex.getMessage());
            }
        }
        if (!problems.isEmpty()) {
            throw new TowerFormatException(problems);
        }
        return new SoundCatalog(sounds);
    }

    private static SoundEntry entry(JsonNode n) {
        return new SoundEntry(text(n, "file"), bool(n, "loop"), bool(n, "positional"), number(n, "minDistance"),
                number(n, "maxDistance"), SoundDetail.valueOf(text(n, "detail")), text(n, "trigger"));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            throw new IllegalArgumentException("missing field '" + field + "'");
        }
        return value.asText();
    }

    private static boolean bool(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isBoolean()) {
            throw new IllegalArgumentException("missing boolean '" + field + "'");
        }
        return value.asBoolean();
    }

    private static double number(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber()) {
            throw new IllegalArgumentException("missing number '" + field + "'");
        }
        return value.asDouble();
    }
}
