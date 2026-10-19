package de.latzig.game.tob.core.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.latzig.game.tob.core.model.AnimationRange;
import de.latzig.game.tob.core.model.BaseTexture;
import de.latzig.game.tob.core.model.ElementDefinition;
import de.latzig.game.tob.core.model.ElementLibrary;
import de.latzig.game.tob.core.model.Explosion;
import de.latzig.game.tob.core.model.Lighting;
import de.latzig.game.tob.core.model.MeshGroup;
import de.latzig.game.tob.core.model.MeshPart;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.RenderType;

/** Reads {@code elements.json} versions 1 to 4; collects all problems before failing. */
public final class ElementLibraryReader {

    private final ObjectMapper mapper = new ObjectMapper();

    public ElementLibrary read(Path file) throws IOException {
        return readFromString(Files.readString(file, StandardCharsets.UTF_8));
    }

    public ElementLibrary readFromString(String json) throws IOException {
        JsonNode root = mapper.readTree(json);
        int version = root.path("version").asInt(-1);
        if (!ElementLibraryWriter.FORMAT.equals(root.path("format").asText()) || version < 1
                || version > ElementLibraryWriter.VERSION) {
            throw new TowerFormatException(List.of("elements: unsupported format/version"));
        }
        List<String> problems = new ArrayList<>();
        Map<ObjectType, ElementDefinition> elements = new EnumMap<>(ObjectType.class);
        for (Map.Entry<String, JsonNode> entry : root.path("elements").properties()) {
            try {
                ObjectType type = ObjectType.fromCode(entry.getKey());
                elements.put(type, element(type, entry.getValue(), version));
            } catch (IllegalArgumentException | NullPointerException e) {
                problems.add(entry.getKey() + ": " + e.getMessage());
            }
        }
        Map<String, BaseTexture> base = new TreeMap<>();
        for (Map.Entry<String, JsonNode> entry : root.path("base").properties()) {
            JsonNode b = entry.getValue();
            if (!b.path("texture").isTextual()) {
                problems.add("base." + entry.getKey() + ": missing texture");
                continue;
            }
            base.put(entry.getKey(), new BaseTexture(b.get("texture").asText(),
                    b.path("normalMap").isTextual() ? b.get("normalMap").asText() : null));
        }
        if (!problems.isEmpty()) {
            throw new TowerFormatException(problems);
        }
        return new ElementLibrary(elements, base);
    }

    private static ElementDefinition element(ObjectType type, JsonNode node, int version) {
        List<MeshGroup> groups = new ArrayList<>();
        for (Map.Entry<String, JsonNode> g : node.path("groups").properties()) {
            List<MeshPart> parts = new ArrayList<>();
            for (JsonNode p : g.getValue().path("parts")) {
                parts.add(new MeshPart(text(p, "mesh"), text(p, "texture"),
                        p.path("normalMap").isTextual() ? p.get("normalMap").asText() : null,
                        RenderType.valueOf(text(p, "render")), colour(text(p, "tint")), specular(p, version),
                        lighting(p, version)));
            }
            groups.add(new MeshGroup(Integer.parseInt(g.getKey()), parts));
        }
        List<AnimationRange> animations = new ArrayList<>();
        for (Map.Entry<String, JsonNode> a : node.path("animations").properties()) {
            JsonNode range = a.getValue();
            if (!range.isArray() || range.size() != 2) {
                throw new IllegalArgumentException("animation " + a.getKey() + " must be [from, to]");
            }
            animations.add(new AnimationRange(a.getKey(), range.get(0).asInt(), range.get(1).asInt()));
        }
        return new ElementDefinition(type, groups, animations, text(node, "source"),
                version < 4 ? Explosion.DEFAULT : explosion(node));
    }

    /** Version 4: {@code "explosion": {"spriteOffset": 6, "meshes": [...]}}; required. */
    private static Explosion explosion(JsonNode node) {
        JsonNode explosion = node.get("explosion");
        if (explosion == null || !explosion.isObject()) {
            throw new IllegalArgumentException("missing field 'explosion'");
        }
        JsonNode offset = explosion.get("spriteOffset");
        if (offset == null || !offset.isInt()) {
            throw new IllegalArgumentException("missing field 'explosion.spriteOffset'");
        }
        List<String> meshes = new ArrayList<>();
        for (JsonNode mesh : explosion.path("meshes")) {
            if (!mesh.isTextual()) {
                throw new IllegalArgumentException("explosion mesh is not a path: " + mesh);
            }
            meshes.add(mesh.asText());
        }
        return new Explosion(offset.asInt(), meshes);
    }

    /** Version 1 has no {@code specular} field: parts keep the Phase 0/1b look (specular on). */
    private static boolean specular(JsonNode part, int version) {
        if (version == 1) {
            return true;
        }
        JsonNode value = part.get("specular");
        if (value == null || !value.isBoolean()) {
            throw new IllegalArgumentException("missing field 'specular'");
        }
        return value.asBoolean();
    }

    /** Versions 1 and 2 have no {@code lighting} field: every part is lit. */
    private static Lighting lighting(JsonNode part, int version) {
        if (version < 3) {
            return Lighting.LIT;
        }
        return Lighting.valueOf(text(part, "lighting"));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            throw new IllegalArgumentException("missing field '" + field + "'");
        }
        return value.asText();
    }

    private static int colour(String text) {
        if (!text.matches("#[0-9A-Fa-f]{6}")) {
            throw new IllegalArgumentException("not a #RRGGBB colour: " + text);
        }
        return Integer.parseInt(text.substring(1), 16);
    }
}
