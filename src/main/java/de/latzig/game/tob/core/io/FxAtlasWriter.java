package de.latzig.game.tob.core.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.latzig.game.tob.core.model.FxAtlas;

/** Writes {@code fx/fx-atlas.json} (format {@value #FORMAT}, version {@value #VERSION}). */
public final class FxAtlasWriter {

    public static final String FORMAT = "tob2026-fxatlas";
    public static final int VERSION = 1;

    private final ObjectMapper mapper = new ObjectMapper();

    public void write(FxAtlas atlas, Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(file, writeToString(atlas), StandardCharsets.UTF_8);
    }

    public String writeToString(FxAtlas atlas) throws IOException {
        ObjectNode root = mapper.createObjectNode();
        root.put("format", FORMAT);
        root.put("version", VERSION);
        root.put("image", atlas.image());
        root.put("imageWidth", atlas.imageWidth());
        root.put("imageHeight", atlas.imageHeight());
        ObjectNode sprites = root.putObject("sprites");
        atlas.sprites().forEach((name, s) -> {
            ObjectNode n = sprites.putObject(name);
            n.put("x", s.x());
            n.put("y", s.y());
            n.put("w", s.width());
            n.put("h", s.height());
        });
        ObjectNode sequences = root.putObject("sequences");
        atlas.sequences().forEach((name, q) -> {
            ObjectNode n = sequences.putObject(name);
            n.put("x", q.x());
            n.put("y", q.y());
            n.put("cellW", q.cellWidth());
            n.put("cellH", q.cellHeight());
            n.put("columns", q.columns());
            n.put("frames", q.frames());
        });
        root.put("source", atlas.source());
        return JsonSupport.toJson(mapper, root);
    }
}
