package de.latzig.game.tob.core.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.latzig.game.tob.core.model.GuiAtlas;

/** Writes {@code gui-atlas.json} (format {@value #FORMAT}, version {@value #VERSION}). */
public final class GuiAtlasWriter {

    public static final String FORMAT = "tob2026-guiatlas";
    public static final int VERSION = 1;

    private final ObjectMapper mapper = new ObjectMapper();

    public void write(GuiAtlas atlas, Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(file, writeToString(atlas), StandardCharsets.UTF_8);
    }

    public String writeToString(GuiAtlas atlas) throws IOException {
        ObjectNode root = mapper.createObjectNode();
        root.put("format", FORMAT);
        root.put("version", VERSION);
        root.put("image", atlas.image());
        root.put("imageWidth", atlas.imageWidth());
        root.put("imageHeight", atlas.imageHeight());
        ObjectNode regions = root.putObject("regions");
        atlas.regions().forEach((name, r) -> {
            ObjectNode n = regions.putObject(name);
            n.put("x", r.x());
            n.put("y", r.y());
            n.put("w", r.width());
            n.put("h", r.height());
        });
        ObjectNode colors = root.putObject("colors");
        atlas.colors().forEach((name, c) -> colors.put(name, String.format("#%06X", c)));
        ObjectNode geometry = root.putObject("geometry");
        atlas.geometry().forEach(geometry::put);
        ObjectNode general = root.putObject("general");
        atlas.general().forEach(general::put);
        return JsonSupport.toJson(mapper, root);
    }
}
