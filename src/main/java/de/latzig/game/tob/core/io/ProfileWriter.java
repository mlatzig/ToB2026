package de.latzig.game.tob.core.io;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.latzig.game.tob.core.progress.Profile;
import de.latzig.game.tob.core.progress.TowerProgress;

/**
 * Writes a profile as JSON (format {@value #FORMAT}, version {@value #VERSION}, LF line endings, towers sorted by id),
 * atomically ({@link AtomicFiles}). {@code firstCompleted} and {@code bestFrames} appear only once a tower is
 * completed. Phase 6d: {@code customLibrary} (after {@code score}) only while an own library is shown,
 * {@code customSolved} (after {@code towers}, libraries and ids sorted) only once an own tower is solved; still
 * version 1.
 */
public final class ProfileWriter {

    public static final String FORMAT = "tob2026-profile";
    public static final int VERSION = 1;

    private final ObjectMapper mapper = new ObjectMapper();

    public void write(Profile profile, Path file) throws IOException {
        AtomicFiles.writeString(file, toJson(profile));
    }

    public String toJson(Profile profile) throws IOException {
        ObjectNode root = mapper.createObjectNode();
        root.put("format", FORMAT);
        root.put("version", VERSION);
        root.put("name", profile.name());
        root.put("library", profile.library());
        root.put("group", profile.group());
        root.put("score", profile.score());
        profile.customLibrary().ifPresent(name -> root.put("customLibrary", name));
        ObjectNode towers = root.putObject("towers");
        for (Map.Entry<String, TowerProgress> entry : profile.towers().entrySet()) {
            TowerProgress progress = entry.getValue();
            ObjectNode node = towers.putObject(entry.getKey());
            node.put("attempts", progress.attempts());
            node.put("counter", progress.counter());
            node.put("completed", progress.completed());
            node.put("score", progress.score());
            progress.firstCompleted().ifPresent(instant -> node.put("firstCompleted", instant.toString()));
            progress.bestFrames().ifPresent(frames -> node.put("bestFrames", frames));
        }
        if (!profile.customSolved().isEmpty()) {
            ObjectNode custom = root.putObject("customSolved");
            profile.customSolved().forEach((library, ids) -> {
                ArrayNode array = custom.putArray(library);
                ids.forEach(array::add);
            });
        }
        return JsonSupport.toJson(mapper, root);
    }
}
