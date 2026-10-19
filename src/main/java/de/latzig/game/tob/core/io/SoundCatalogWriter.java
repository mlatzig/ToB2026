package de.latzig.game.tob.core.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.latzig.game.tob.core.model.SoundCatalog;

/** Writes {@code sounds/sounds.json} (format {@value #FORMAT}, version {@value #VERSION}). */
public final class SoundCatalogWriter {

    public static final String FORMAT = "tob2026-sounds";
    public static final int VERSION = 1;

    private final ObjectMapper mapper = new ObjectMapper();

    public void write(SoundCatalog catalog, Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(file, writeToString(catalog), StandardCharsets.UTF_8);
    }

    public String writeToString(SoundCatalog catalog) throws IOException {
        ObjectNode root = mapper.createObjectNode();
        root.put("format", FORMAT);
        root.put("version", VERSION);
        ObjectNode sounds = root.putObject("sounds");
        catalog.sounds().forEach((id, s) -> {
            ObjectNode n = sounds.putObject(id);
            n.put("file", s.file());
            n.put("loop", s.loop());
            n.put("positional", s.positional());
            n.put("minDistance", s.minDistance());
            n.put("maxDistance", s.maxDistance());
            n.put("detail", s.detail().name());
            n.put("trigger", s.trigger());
        });
        return JsonSupport.toJson(mapper, root);
    }
}
