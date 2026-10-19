package de.latzig.game.tob.core.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.latzig.game.tob.core.model.AnimationRange;
import de.latzig.game.tob.core.model.ElementDefinition;
import de.latzig.game.tob.core.model.ElementLibrary;
import de.latzig.game.tob.core.model.MeshGroup;
import de.latzig.game.tob.core.model.MeshPart;

/** Writes {@code elements.json} (format {@value #FORMAT}, version {@value #VERSION}). */
public final class ElementLibraryWriter {

    public static final String FORMAT = "tob2026-elements";
    /**
     * Version 2 adds {@code specular} per part (Phase 1c); version 3 adds {@code lighting} per part (Phase 3a);
     * version 4 adds {@code explosion} per element (Phase 4b-1).
     */
    public static final int VERSION = 4;

    private final ObjectMapper mapper = new ObjectMapper();

    public void write(ElementLibrary library, Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(file, writeToString(library), StandardCharsets.UTF_8);
    }

    public String writeToString(ElementLibrary library) throws IOException {
        ObjectNode root = mapper.createObjectNode();
        root.put("format", FORMAT);
        root.put("version", VERSION);
        ObjectNode elements = root.putObject("elements");
        for (ElementDefinition definition : library.elements().values()) {
            ObjectNode node = elements.putObject(definition.type().code());
            ObjectNode groups = node.putObject("groups");
            for (MeshGroup group : definition.groups()) {
                ArrayNode parts = groups.putObject(String.valueOf(group.id())).putArray("parts");
                for (MeshPart part : group.parts()) {
                    ObjectNode p = parts.addObject();
                    p.put("mesh", part.mesh());
                    p.put("texture", part.texture());
                    if (part.normalMap() != null) {
                        p.put("normalMap", part.normalMap());
                    }
                    p.put("render", part.render().name());
                    p.put("specular", part.specular());
                    p.put("lighting", part.lighting().name());
                    p.put("tint", String.format("#%06X", part.tint()));
                }
            }
            ObjectNode animations = node.putObject("animations");
            for (AnimationRange range : definition.animations()) {
                animations.putArray(range.name()).add(range.fromFrame()).add(range.toFrame());
            }
            ObjectNode explosion = node.putObject("explosion");
            explosion.put("spriteOffset", definition.explosion().spriteOffset());
            ArrayNode meshes = explosion.putArray("meshes");
            definition.explosion().meshes().forEach(meshes::add);
            node.put("source", definition.source());
        }
        ObjectNode base = root.putObject("base");
        library.base().forEach((name, texture) -> {
            ObjectNode b = base.putObject(name);
            b.put("texture", texture.texture());
            if (texture.normalMap() != null) {
                b.put("normalMap", texture.normalMap());
            }
        });
        return JsonSupport.toJson(mapper, root);
    }
}
