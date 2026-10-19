package de.latzig.game.tob.core.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The user settings file {@code <userdir>/settings.json} (format {@value #FORMAT}, version {@value #VERSION}):
 * Phase 5a stores the last profile (spec decision 10), Phase 5b adds the options as one flat object of names and
 * values (Phase 5b decision 10: still version 1; missing fields are the caller's defaults, unknown fields and nested
 * values are ignored). Values are written as JSON numbers, booleans or strings and read back as text. Phase 5c-1
 * (spec decision 3) adds the solved tutorial towers, global for every profile, as an array of tower ids
 * ({@code solvedTutorials}, still version 1: a missing array is empty, entries that are not text are ignored; the
 * array is written only when it is not empty). Phase 6d (spec decision 8) adds the Tower Designer's last library
 * ({@code designerLibrary}, a library name; still version 1: missing or not text is none, written only when present).
 * Written atomically; the read-modify-write is the caller's ({@code client.logic.frontend.SettingsFile}).
 */
public final class SettingsJson {

    public static final String FORMAT = "tob2026-settings";
    public static final int VERSION = 1;

    private static final Pattern INTEGER = Pattern.compile("-?\\d{1,9}");

    /**
     * The settings; {@code lastProfile} is the name of the profile played last, {@code options} the options,
     * {@code solvedTutorials} the ids of the solved tutorial towers (sorted), {@code designerLibrary} the library the
     * Tower Designer had open last (Phase 6d).
     */
    public record Settings(Optional<String> lastProfile, Map<String, String> options, Set<String> solvedTutorials,
            Optional<String> designerLibrary) {

        public static final Settings EMPTY = new Settings(Optional.empty(), Map.of(), Set.of(), Optional.empty());

        public Settings {
            Objects.requireNonNull(lastProfile, "lastProfile");
            options = Collections.unmodifiableMap(new LinkedHashMap<>(options));
            solvedTutorials = Collections.unmodifiableSet(new TreeSet<>(solvedTutorials));
            Objects.requireNonNull(designerLibrary, "designerLibrary");
        }

        /** The last profile, the options and the solved tutorial towers; no designer library. */
        public Settings(Optional<String> lastProfile, Map<String, String> options, Set<String> solvedTutorials) {
            this(lastProfile, options, solvedTutorials, Optional.empty());
        }

        /** The last profile and the options, no solved tutorial tower. */
        public Settings(Optional<String> lastProfile, Map<String, String> options) {
            this(lastProfile, options, Set.of());
        }

        /** Only the last profile, no options. */
        public Settings(Optional<String> lastProfile) {
            this(lastProfile, Map.of());
        }

        public Settings withLastProfile(String name) {
            return new Settings(Optional.of(name), options, solvedTutorials, designerLibrary);
        }

        public Settings withOptions(Map<String, String> newOptions) {
            return new Settings(lastProfile, newOptions, solvedTutorials, designerLibrary);
        }

        /** The settings with the tutorial tower {@code towerId} solved as well. */
        public Settings withSolvedTutorial(String towerId) {
            Set<String> solved = new TreeSet<>(solvedTutorials);
            solved.add(Objects.requireNonNull(towerId, "towerId"));
            return new Settings(lastProfile, options, solved, designerLibrary);
        }

        /** The settings with {@code name} as the Tower Designer's last library (Phase 6d decision 8). */
        public Settings withDesignerLibrary(String name) {
            return new Settings(lastProfile, options, solvedTutorials, Optional.of(name));
        }
    }

    private final ObjectMapper mapper = new ObjectMapper();

    /** The settings in {@code file}; {@link Settings#EMPTY} if there is no file yet. */
    public Settings read(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return Settings.EMPTY;
        }
        return readFromString(Files.readString(file, StandardCharsets.UTF_8));
    }

    public Settings readFromString(String json) throws IOException {
        JsonNode root = mapper.readTree(json);
        if (root == null || !root.isObject()) {
            throw new IOException("settings: not a JSON object");
        }
        String format = root.path("format").asText();
        int version = root.path("version").asInt();
        if (!FORMAT.equals(format) || version < 1 || version > VERSION) {
            throw new IOException("settings: unsupported format " + format + " version " + version);
        }
        JsonNode last = root.get("lastProfile");
        Map<String, String> options = new LinkedHashMap<>();
        JsonNode node = root.path("options");
        for (Iterator<Map.Entry<String, JsonNode>> fields = node.fields(); fields.hasNext();) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (field.getValue().isValueNode() && !field.getValue().isNull()) {
                options.put(field.getKey(), field.getValue().asText());
            }
        }
        Set<String> solved = new TreeSet<>();
        JsonNode tutorials = root.path("solvedTutorials");
        for (int i = 0; tutorials.isArray() && i < tutorials.size(); i++) {
            if (tutorials.get(i).isTextual()) {
                solved.add(tutorials.get(i).asText());
            }
        }
        JsonNode designer = root.get("designerLibrary");
        return new Settings(last == null || last.isNull() ? Optional.empty() : Optional.of(last.asText()), options,
                solved, designer != null && designer.isTextual() ? Optional.of(designer.asText()) : Optional.empty());
    }

    public void write(Settings settings, Path file) throws IOException {
        AtomicFiles.writeString(file, toJson(settings));
    }

    public String toJson(Settings settings) throws IOException {
        ObjectNode root = mapper.createObjectNode();
        root.put("format", FORMAT);
        root.put("version", VERSION);
        settings.lastProfile().ifPresent(name -> root.put("lastProfile", name));
        if (!settings.options().isEmpty()) {
            ObjectNode options = root.putObject("options");
            settings.options().forEach((name, value) -> {
                if (INTEGER.matcher(value).matches()) {
                    options.put(name, Integer.parseInt(value));
                } else if (value.equals("true") || value.equals("false")) {
                    options.put(name, Boolean.parseBoolean(value));
                } else {
                    options.put(name, value);
                }
            });
        }
        if (!settings.solvedTutorials().isEmpty()) {
            ArrayNode solved = root.putArray("solvedTutorials");
            settings.solvedTutorials().forEach(solved::add);
        }
        settings.designerLibrary().ifPresent(name -> root.put("designerLibrary", name));
        return JsonSupport.toJson(mapper, root);
    }
}
