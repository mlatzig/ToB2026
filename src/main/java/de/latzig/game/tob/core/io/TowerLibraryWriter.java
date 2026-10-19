package de.latzig.game.tob.core.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.latzig.game.tob.core.model.Rgb12;
import de.latzig.game.tob.core.model.RemakeVisuals;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.model.TowerGrid;
import de.latzig.game.tob.core.model.TowerLibrary;

/** Writes a tower library as JSON (format {@value #FORMAT}, version {@value #VERSION}) with a stable field order. */
public final class TowerLibraryWriter {

    public static final String FORMAT = "tob2026-towerlib";
    public static final int VERSION = 1;

    private final ObjectMapper mapper = new ObjectMapper();
    private final ObjectWriter writer = mapper.writer(lfPrettyPrinter());

    private static DefaultPrettyPrinter lfPrettyPrinter() {
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
        DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
        printer.indentObjectsWith(indenter);
        printer.indentArraysWith(indenter);
        return printer;
    }

    public void write(TowerLibrary library, Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(file, writeToString(library), StandardCharsets.UTF_8);
    }

    public String writeToString(TowerLibrary library) throws IOException {
        return writer.writeValueAsString(toJson(library)) + "\n";
    }

    /**
     * The canonical JSON of one tower (Phase 3c, {@link TowerChecksum}): the fields and their order as in a library
     * file, compact, without a trailing newline.
     */
    public String towerToString(TowerDefinition tower) throws IOException {
        return mapper.writeValueAsString(toJson(tower));
    }

    private ObjectNode toJson(TowerLibrary library) {
        ObjectNode root = mapper.createObjectNode();
        root.put("format", FORMAT);
        root.put("version", VERSION);
        root.put("id", library.id());
        root.put("name", library.name());
        root.put("source", library.source());
        ArrayNode towers = root.putArray("towers");
        for (TowerDefinition tower : library.towers()) {
            towers.add(toJson(tower));
        }
        return root;
    }

    private ObjectNode toJson(TowerDefinition t) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", t.id());
        node.put("group", t.group());
        node.put("index", t.index());
        node.put("title", t.title());
        node.put("author", t.author());

        ObjectNode conditions = node.putObject("conditions");
        conditions.put("timeLimit", t.conditions().timeLimitSeconds());
        conditions.put("timeLimitEnabled", t.conditions().timeLimitEnabled());
        conditions.put("klondikes", t.conditions().klondikes());
        conditions.put("robots", t.conditions().robots());

        ObjectNode behaviour = node.putObject("behaviour");
        behaviour.put("cameras", t.behaviour().camerasEnabled());
        behaviour.put("minesDestroyFloor", t.behaviour().minesDestroyFloor());
        behaviour.put("timeBombSpeed", t.behaviour().timeBombSpeed());

        ObjectNode effects = node.putObject("effects");
        effects.put("lightning", t.effects().lightning());
        effects.put("meteor", t.effects().meteor());
        effects.put("bluePulse", t.effects().bluePulse());
        effects.put("starDensity", t.effects().starDensity());
        effects.put("colourPattern", t.effects().colourPattern());
        effects.put("skyStyle", t.effects().skyStyle());

        ObjectNode moon = node.putObject("moon");
        moon.put("heading", t.moon().heading());
        moon.put("type", t.moon().type());
        moon.put("x", t.moon().x());
        moon.put("y", t.moon().y());
        moon.put("rotation", t.moon().rotation());
        t.moon().size().ifPresent(s -> moon.put("size", s));

        ArrayNode palette = node.putArray("palette");
        for (Rgb12 colour : t.palette().colours()) {
            palette.add(colour.hex());
        }

        ObjectNode sounds = node.putObject("sounds");
        sounds.put("background", t.sounds().background());
        sounds.put("spiderWalk", t.sounds().spiderWalk());
        sounds.put("other", t.sounds().other());

        ObjectNode lock = node.putObject("lock");
        lock.put("locked", t.lock().locked());
        lock.put("password", t.lock().password());

        ArrayNode levels = node.putArray("levels");
        for (int level = 0; level < TowerGrid.LEVELS; level++) {
            if (t.grid().isLevelEmpty(level)) {
                continue;
            }
            ObjectNode levelNode = levels.addObject();
            levelNode.put("level", level);
            ArrayNode rows = levelNode.putArray("rows");
            GridRowCodec.encodeLevel(t.grid(), level).forEach(rows::add);
        }

        t.remake().ifPresent(remake -> node.set("remake", toJson(remake)));
        return node;
    }

    private ObjectNode toJson(RemakeVisuals r) {
        ObjectNode node = mapper.createObjectNode();
        node.put("sunColor", colour(r.sunColor()));
        node.put("sunSize", r.sunSize());
        node.putArray("groundColors").add(colour(r.groundColor1())).add(colour(r.groundColor2()));
        node.put("groundHeight", r.groundHeight());
        node.put("liftColor", colour(r.liftColor()));
        node.put("clouds", r.clouds());
        node.put("heavyClouds", r.heavyClouds());
        node.put("pattern", r.pattern());
        node.putArray("patternColors").add(colour(r.patternColor1())).add(colour(r.patternColor2()));
        return node;
    }

    static String colour(int rgb) {
        return String.format("#%06X", rgb);
    }
}
