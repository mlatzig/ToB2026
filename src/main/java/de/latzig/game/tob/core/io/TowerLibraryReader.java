package de.latzig.game.tob.core.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.latzig.game.tob.core.model.Behaviour;
import de.latzig.game.tob.core.model.Conditions;
import de.latzig.game.tob.core.model.Effects;
import de.latzig.game.tob.core.model.Lock;
import de.latzig.game.tob.core.model.Moon;
import de.latzig.game.tob.core.model.Palette;
import de.latzig.game.tob.core.model.RemakeVisuals;
import de.latzig.game.tob.core.model.Rgb12;
import de.latzig.game.tob.core.model.Sounds;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.model.TowerGrid;
import de.latzig.game.tob.core.model.TowerLibrary;

/** Reads tower libraries written by {@link TowerLibraryWriter}; collects all problems before failing. */
public final class TowerLibraryReader {

    private final ObjectMapper mapper = new ObjectMapper();

    public TowerLibrary read(Path file) throws IOException {
        return readFromString(Files.readString(file, StandardCharsets.UTF_8));
    }

    public TowerLibrary readFromString(String json) throws IOException {
        JsonNode root = mapper.readTree(json);
        List<String> problems = new ArrayList<>();
        try {
            String format = text(root, "format");
            int version = integer(root, "version");
            if (!TowerLibraryWriter.FORMAT.equals(format) || version != TowerLibraryWriter.VERSION) {
                throw new IllegalArgumentException("unsupported format " + format + " version " + version
                        + " (expected " + TowerLibraryWriter.FORMAT + " " + TowerLibraryWriter.VERSION + ")");
            }
        } catch (IllegalArgumentException e) {
            throw new TowerFormatException(List.of("library: " + e.getMessage()));
        }

        JsonNode towerNodes;
        try {
            towerNodes = array(root, "towers");
        } catch (IllegalArgumentException e) {
            throw new TowerFormatException(List.of("library: " + e.getMessage()));
        }

        List<TowerDefinition> towers = new ArrayList<>();
        for (int i = 0; i < towerNodes.size(); i++) {
            JsonNode node = towerNodes.get(i);
            if (node == null || !node.isObject()) {
                problems.add("#" + i + ": tower must be an object");
                continue;
            }
            String label = node.path("id").isTextual() ? node.path("id").asText() : "#" + i;
            try {
                TowerDefinition tower = tower(node);
                for (String problem : TowerValidator.validate(tower)) {
                    problems.add(label + ": " + problem);
                }
                towers.add(tower);
            } catch (IllegalArgumentException | NullPointerException e) {
                problems.add(label + ": " + e.getMessage());
            }
        }
        if (!problems.isEmpty()) {
            throw new TowerFormatException(problems);
        }
        try {
            return new TowerLibrary(text(root, "id"), text(root, "name"), text(root, "source"), towers);
        } catch (IllegalArgumentException e) {
            throw new TowerFormatException(List.of("library: " + e.getMessage()));
        }
    }

    static TowerDefinition tower(JsonNode node) {
        JsonNode conditions = object(node, "conditions");
        JsonNode behaviour = object(node, "behaviour");
        JsonNode effects = object(node, "effects");
        JsonNode moon = object(node, "moon");
        JsonNode sounds = object(node, "sounds");
        JsonNode lock = object(node, "lock");

        List<Rgb12> colours = new ArrayList<>();
        for (JsonNode colour : array(node, "palette")) {
            colours.add(Rgb12.parseHex(colour.asText()));
        }

        TowerGrid.Builder grid = TowerGrid.builder();
        for (JsonNode levelNode : array(node, "levels")) {
            int level = integer(levelNode, "level");
            List<String> rows = new ArrayList<>();
            for (JsonNode row : array(levelNode, "rows")) {
                rows.add(row.asText());
            }
            GridRowCodec.decodeLevel(rows, level, grid);
        }

        Optional<RemakeVisuals> remake = node.has("remake") ? Optional.of(remake(node.get("remake")))
                : Optional.empty();

        return new TowerDefinition(text(node, "id"), integer(node, "group"), integer(node, "index"),
                text(node, "title"), text(node, "author"),
                new Conditions(integer(conditions, "timeLimit"), bool(conditions, "timeLimitEnabled"),
                        integer(conditions, "klondikes"), integer(conditions, "robots")),
                new Behaviour(bool(behaviour, "cameras"), bool(behaviour, "minesDestroyFloor"),
                        integer(behaviour, "timeBombSpeed")),
                new Effects(bool(effects, "lightning"), bool(effects, "meteor"), bool(effects, "bluePulse"),
                        integer(effects, "starDensity"), integer(effects, "colourPattern"),
                        integer(effects, "skyStyle")),
                new Moon(integer(moon, "heading"), integer(moon, "type"), integer(moon, "x"), integer(moon, "y"),
                        integer(moon, "rotation"), moon.has("size") ? Optional.of(integer(moon, "size"))
                                : Optional.empty()),
                new Palette(colours),
                new Sounds(integer(sounds, "background"), bool(sounds, "spiderWalk"), bool(sounds, "other")),
                new Lock(bool(lock, "locked"), text(lock, "password")),
                grid.build(), remake);
    }

    private static RemakeVisuals remake(JsonNode r) {
        JsonNode ground = array(r, "groundColors");
        JsonNode pattern = array(r, "patternColors");
        requireSize(ground, "groundColors", 2);
        requireSize(pattern, "patternColors", 2);
        return new RemakeVisuals(colour(text(r, "sunColor")), integer(r, "sunSize"), colour(ground.get(0).asText()),
                colour(ground.get(1).asText()), integer(r, "groundHeight"), colour(text(r, "liftColor")),
                bool(r, "clouds"), bool(r, "heavyClouds"), integer(r, "pattern"), colour(pattern.get(0).asText()),
                colour(pattern.get(1).asText()));
    }

    private static void requireSize(JsonNode array, String name, int size) {
        if (array.size() != size) {
            throw new IllegalArgumentException("field '" + name + "' must have " + size + " entries");
        }
    }

    static int colour(String text) {
        if (text == null || !text.matches("#[0-9A-Fa-f]{6}")) {
            throw new IllegalArgumentException("not a #RRGGBB colour: " + text);
        }
        return Integer.parseInt(text.substring(1), 16);
    }

    private static JsonNode field(JsonNode node, String name) {
        JsonNode value = node.get(name);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("missing field '" + name + "'");
        }
        return value;
    }

    static String text(JsonNode node, String name) {
        JsonNode value = field(node, name);
        if (!value.isTextual()) {
            throw new IllegalArgumentException("field '" + name + "' must be a string");
        }
        return value.asText();
    }

    static int integer(JsonNode node, String name) {
        JsonNode value = field(node, name);
        if (!value.isInt()) {
            throw new IllegalArgumentException("field '" + name + "' must be an integer");
        }
        return value.asInt();
    }

    private static boolean bool(JsonNode node, String name) {
        JsonNode value = field(node, name);
        if (!value.isBoolean()) {
            throw new IllegalArgumentException("field '" + name + "' must be a boolean");
        }
        return value.asBoolean();
    }

    private static JsonNode object(JsonNode node, String name) {
        JsonNode value = field(node, name);
        if (!value.isObject()) {
            throw new IllegalArgumentException("field '" + name + "' must be an object");
        }
        return value;
    }

    static JsonNode array(JsonNode node, String name) {
        JsonNode value = field(node, name);
        if (!value.isArray()) {
            throw new IllegalArgumentException("field '" + name + "' must be an array");
        }
        return value;
    }
}
