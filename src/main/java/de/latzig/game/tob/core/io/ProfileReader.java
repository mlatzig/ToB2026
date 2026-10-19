package de.latzig.game.tob.core.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.latzig.game.tob.core.progress.Profile;
import de.latzig.game.tob.core.progress.TowerProgress;

/**
 * Reads profiles written by {@link ProfileWriter}: version 1; a newer version or another format is refused. Unknown
 * fields are ignored, among them a Phase 5a profile's {@code settings.backwardsAllowed} (Phase 5b decision 4). Phase
 * 6d: the optional {@code customLibrary} (missing or not text: the original towers) and {@code customSolved}
 * (missing: none; only arrays of text ids count).
 */
public final class ProfileReader {

    private final ObjectMapper mapper = new ObjectMapper();

    public Profile read(Path file) throws IOException {
        return readFromString(Files.readString(file, StandardCharsets.UTF_8));
    }

    public Profile readFromString(String json) throws IOException {
        JsonNode root = mapper.readTree(json);
        try {
            String format = text(root, "format");
            int version = field(root, "version").asInt();
            if (!ProfileWriter.FORMAT.equals(format) || version < 1 || version > ProfileWriter.VERSION) {
                throw new IllegalArgumentException("unsupported format " + format + " version " + version
                        + " (expected " + ProfileWriter.FORMAT + " 1 to " + ProfileWriter.VERSION + ")");
            }
            Map<String, TowerProgress> towers = new TreeMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = field(root, "towers").fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                JsonNode node = entry.getValue();
                towers.put(entry.getKey(), new TowerProgress(field(node, "attempts").asInt(),
                        field(node, "counter").asInt(), field(node, "completed").asBoolean(),
                        field(node, "score").asInt(),
                        node.has("firstCompleted") ? Optional.of(Instant.parse(text(node, "firstCompleted")))
                                : Optional.empty(),
                        node.has("bestFrames") ? OptionalInt.of(node.get("bestFrames").asInt()) : OptionalInt.empty()));
            }
            Map<String, Set<String>> customSolved = new TreeMap<>();
            Iterator<Map.Entry<String, JsonNode>> libraries = root.path("customSolved").fields();
            while (libraries.hasNext()) {
                Map.Entry<String, JsonNode> entry = libraries.next();
                Set<String> ids = new TreeSet<>();
                for (int i = 0; entry.getValue().isArray() && i < entry.getValue().size(); i++) {
                    if (entry.getValue().get(i).isTextual()) {
                        ids.add(entry.getValue().get(i).asText());
                    }
                }
                customSolved.put(entry.getKey(), ids);
            }
            JsonNode shown = root.get("customLibrary");
            return new Profile(text(root, "name"), text(root, "library"), field(root, "group").asInt(), towers,
                    field(root, "score").asLong(), customSolved,
                    shown != null && shown.isTextual() ? Optional.of(shown.asText()) : Optional.empty());
        } catch (IllegalArgumentException | NullPointerException | DateTimeParseException e) {
            throw new IOException("profile: " + e.getMessage(), e);
        }
    }

    private static JsonNode field(JsonNode node, String name) {
        JsonNode value = node.get(name);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("missing field '" + name + "'");
        }
        return value;
    }

    private static String text(JsonNode node, String name) {
        return field(node, name).asText();
    }
}
