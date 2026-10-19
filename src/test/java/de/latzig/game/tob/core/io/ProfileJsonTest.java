package de.latzig.game.tob.core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.latzig.game.tob.core.progress.Outcome;
import de.latzig.game.tob.core.progress.Profile;

class ProfileJsonTest {

    private static Profile sample() {
        Profile profile = Profile.create("Mr Gumby", "original").started("original-001")
                .ended("original-001", Outcome.FAILED, 0, 99, Instant.EPOCH).profile().started("original-001");
        return profile.ended("original-001", Outcome.COMPLETED, 2, 777, Instant.parse("2026-10-12T10:00:00Z"))
                .profile().started("original-000").withGroup(1);
    }

    @Test
    void aProfileSurvivesTheRoundTrip() throws IOException {
        Profile profile = sample();

        String json = new ProfileWriter().toJson(profile);

        assertEquals(profile, new ProfileReader().readFromString(json));
        assertTrue(json.startsWith("{\n  \"format\" : \"tob2026-profile\",\n  \"version\" : 1,"), json);
        assertTrue(json.contains("\"firstCompleted\" : \"2026-10-12T10:00:00Z\""), json);
        assertFalse(json.contains("backwardsAllowed"), "Phase 5b decision 4: a global option now: " + json);
        assertFalse(json.contains("\"settings\""), json);
        assertTrue(json.endsWith("}\n"));
    }

    /**
     * Phase 6d decision 5: the marks per own library and the last library are optional fields of version 1: written
     * only when present, read back, and a profile without them (a Phase 5 file) reads as none and the Original.
     */
    @Test
    void theOwnLibrariesMarksAndTheLastLibraryAreOptionalFieldsOfVersion1() throws IOException {
        Profile profile = sample().withCustomLibrary(Optional.of("mine")).withCustomSolved("mine", "b-id")
                .withCustomSolved("mine", "a-id").withCustomSolved("zeta", "c-id");

        String json = new ProfileWriter().toJson(profile);

        assertEquals(profile, new ProfileReader().readFromString(json));
        assertTrue(json.contains("\"version\" : 1,"), json);
        String flat = json.replaceAll("\\s+", " ");
        assertTrue(flat.contains("\"customLibrary\" : \"mine\""), json);
        assertTrue(flat.contains("\"customSolved\" : { \"mine\" : [ \"a-id\", \"b-id\" ], \"zeta\" : [ \"c-id\" ] }"),
                json);
        String plain = new ProfileWriter().toJson(sample());
        assertFalse(plain.contains("custom"), "nothing written without marks and on the Original: " + plain);
        Profile old = new ProfileReader().readFromString(plain);
        assertEquals(Map.of(), old.customSolved());
        assertEquals(Optional.empty(), old.customLibrary());
        assertEquals(Map.of("mine", Set.of("x")), new ProfileReader().readFromString(plain.replace("\"towers\"",
                "\"customSolved\" : { \"mine\" : [ \"x\", 7 ], \"bad\" : 3 }, \"towers\"")).customSolved(),
                "only text ids in arrays");
    }

    @Test
    void anOtherFormatOrANewerVersionIsRefused() {
        String json = "{\"format\":\"tob2026-profile\",\"version\":2,\"name\":\"A\",\"library\":\"original\","
                + "\"group\":0,\"score\":0,\"settings\":{\"backwardsAllowed\":false},\"towers\":{}}";
        IOException newer = assertThrows(IOException.class, () -> new ProfileReader().readFromString(json));
        assertTrue(newer.getMessage().contains("version 2"), newer.getMessage());
        assertThrows(IOException.class, () -> new ProfileReader().readFromString(
                json.replace("tob2026-profile", "tob2026-recording").replace("\"version\":2", "\"version\":1")));
        assertThrows(IOException.class, () -> new ProfileReader().readFromString(
                json.replace("\"version\":2", "\"version\":1").replace("\"A\"", "\"\"")), "an invalid name");
    }

    /**
     * Task 4 review (Important): a profile without a required field is refused with the field's name; unknown fields
     * (a later version's additions at any level) are ignored, and the optional tower fields may be missing.
     */
    @Test
    void aMissingRequiredFieldIsRefusedAndUnknownFieldsAreIgnored() throws IOException {
        String json = "{\"format\":\"tob2026-profile\",\"version\":1,\"name\":\"A\",\"library\":\"original\","
                + "\"group\":0,\"score\":0,\"settings\":{\"backwardsAllowed\":true},"
                + "\"towers\":{\"original-000\":{\"attempts\":1,\"counter\":2,\"completed\":false,\"score\":0}}}";
        Profile plain = new ProfileReader().readFromString(json);
        assertEquals(1, plain.progress("original-000").attempts());
        assertEquals(2, plain.progress("original-000").counter());

        for (String field : List.of("\"name\":\"A\",", "\"group\":0,", "\"score\":0,", "\"counter\":2,")) {
            IOException missing = assertThrows(IOException.class,
                    () -> new ProfileReader().readFromString(json.replace(field, "")), field);
            String name = field.substring(1, field.indexOf('"', 1));
            assertTrue(missing.getMessage().contains("missing field '" + name + "'"), missing.getMessage());
        }
        assertThrows(IOException.class, () -> new ProfileReader().readFromString(
                json.replace(",\"towers\":{\"original-000\":{\"attempts\":1,\"counter\":2,\"completed\":false,"
                        + "\"score\":0}}", "")), "no towers");

        String extended = json.replace("\"group\":0,", "\"group\":0,\"nickname\":\"x\",\"stats\":{\"a\":[1,2]},")
                .replace("\"backwardsAllowed\":true", "\"backwardsAllowed\":true,\"music\":0.5")
                .replace("\"score\":0}}", "\"score\":0,\"stars\":3}}");
        assertEquals(plain, new ProfileReader().readFromString(extended), "unknown fields are ignored");
    }

    /** Phase 5b decision 4: a 5a profile with {@code settings.backwardsAllowed} still loads (the field is ignored). */
    @Test
    void aPhase5aProfileWithBackwardsAllowedStillLoads() throws IOException {
        String json = "{\"format\":\"tob2026-profile\",\"version\":1,\"name\":\"Old\",\"library\":\"original\","
                + "\"group\":2,\"score\":300,\"settings\":{\"backwardsAllowed\":false},\"towers\":{}}";

        Profile profile = new ProfileReader().readFromString(json);

        assertEquals(new Profile("Old", "original", 2, Map.of(), 300L), profile);
    }

    /** Task 4 review (Minor 2): settings.json of another format or a newer version is refused, like a profile. */
    @Test
    void settingsOfAnotherFormatOrANewerVersionAreRefused() {
        SettingsJson json = new SettingsJson();
        for (String text : List.of("{\"format\":\"tob2026-settings\",\"version\":2}",
                "{\"format\":\"tob2026-settings\",\"version\":0}", "{\"format\":\"tob2026-profile\",\"version\":1}",
                "{\"version\":1}")) {
            IOException e = assertThrows(IOException.class, () -> json.readFromString(text), text);
            assertTrue(e.getMessage().startsWith("settings: unsupported format"), e.getMessage());
        }
    }

    @Test
    void writingIsAtomicAndLeavesNoTemporaryFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("savegames/profile-mr%20gumby.json");
        Profile profile = sample();

        new ProfileWriter().write(profile, file);
        new ProfileWriter().write(profile.withGroup(2), file);

        assertEquals(profile.withGroup(2), new ProfileReader().read(file));
        try (Stream<Path> files = Files.list(file.getParent())) {
            assertEquals(List.of(file.getFileName().toString()),
                    files.map(f -> f.getFileName().toString()).toList());
        }
    }

    @Test
    void theSettingsRememberTheLastProfile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("settings.json");
        SettingsJson json = new SettingsJson();

        assertEquals(SettingsJson.Settings.EMPTY, json.read(file), "no file yet");
        json.write(new SettingsJson.Settings(Optional.of("Mr Gumby")), file);

        assertEquals(new SettingsJson.Settings(Optional.of("Mr Gumby")), json.read(file));
        assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains("\"format\" : \"tob2026-settings\""));
        assertEquals(SettingsJson.Settings.EMPTY, json.readFromString(
                "{\"format\":\"tob2026-settings\",\"version\":1}"));
    }

    /**
     * Phase 5b decision 10: version 1 gains the options as typed JSON values; a reader takes every scalar option as
     * text and ignores unknown fields and nested values.
     */
    @Test
    void theSettingsKeepTheOptionsAsTypedValuesAndIgnoreTheUnknown() throws IOException {
        SettingsJson json = new SettingsJson();
        Map<String, String> options = new LinkedHashMap<>();
        options.put("masterVolume", "150");
        options.put("display", "window");
        options.put("ambientEffects", "false");
        options.put("key.FIRE", "ctrl+57");
        SettingsJson.Settings settings = new SettingsJson.Settings(Optional.of("Anna"), options);

        String text = json.toJson(settings);

        assertEquals(settings, json.readFromString(text));
        assertTrue(text.contains("\"version\" : 1"), text);
        assertTrue(text.contains("\"masterVolume\" : 150"), "a number: " + text);
        assertTrue(text.contains("\"ambientEffects\" : false"), "a boolean: " + text);
        assertTrue(text.contains("\"key.FIRE\" : \"ctrl+57\""), text);
        SettingsJson.Settings extended = json.readFromString("{\"format\":\"tob2026-settings\",\"version\":1,"
                + "\"music\":3,\"options\":{\"brightness\":120,\"window\":{\"x\":1},\"display\":\"window\"}}");
        assertEquals(new SettingsJson.Settings(Optional.empty(), Map.of("brightness", "120", "display", "window")),
                extended, "unknown fields and nested values are ignored");
    }

    /**
     * Phase 5c-1 decision 3: the solved tutorial towers are an array of ids in version 1, written only when there is
     * one; a file without the array reads as none, and entries that are not text are ignored.
     */
    @Test
    void theSettingsKeepTheSolvedTutorialTowersSortedAndOnlyWhenThereAreAny() throws IOException {
        SettingsJson json = new SettingsJson();
        SettingsJson.Settings settings = new SettingsJson.Settings(Optional.of("Anna"), Map.of("brightness", "80"))
                .withSolvedTutorial("tutorial-008").withSolvedTutorial("tutorial-007")
                .withSolvedTutorial("tutorial-008");

        String text = json.toJson(settings);

        assertEquals(List.of("tutorial-007", "tutorial-008"), List.copyOf(settings.solvedTutorials()), "sorted, once");
        assertEquals(settings, json.readFromString(text));
        assertTrue(text.contains("\"version\" : 1"), text);
        String flat = text.replaceAll("\\s+", " ");
        assertTrue(flat.contains("\"solvedTutorials\" : [ \"tutorial-007\", \"tutorial-008\" ]"), text);
        assertFalse(json.toJson(new SettingsJson.Settings(Optional.of("Anna"))).contains("solvedTutorials"),
                "no array without a solved tower");
        assertEquals(Set.of(), json.readFromString("{\"format\":\"tob2026-settings\",\"version\":1}")
                .solvedTutorials(), "missing: none solved");
        assertEquals(Set.of("tutorial-001"), json.readFromString("{\"format\":\"tob2026-settings\",\"version\":1,"
                + "\"solvedTutorials\":[\"tutorial-001\",7,{\"x\":1},null]}").solvedTutorials(), "only text");
        assertEquals(settings.withLastProfile("Bob").withOptions(Map.of()).solvedTutorials(),
                settings.solvedTutorials(), "the other parts' changes keep them");
    }

    /** Phase 6d decision 8: the designer's last library, a text field in version 1, written only when there is one. */
    @Test
    void theSettingsRememberTheDesignersLastLibraryOnlyWhenThereIsOne() throws IOException {
        SettingsJson json = new SettingsJson();
        SettingsJson.Settings settings = new SettingsJson.Settings(Optional.of("Anna"), Map.of("brightness", "80"))
                .withSolvedTutorial("tutorial-007").withDesignerLibrary("mine");

        String text = json.toJson(settings);

        assertEquals(Optional.of("mine"), settings.designerLibrary());
        assertEquals(settings, json.readFromString(text));
        assertTrue(text.contains("\"designerLibrary\" : \"mine\""), text);
        assertTrue(text.contains("\"version\" : 1"), text);
        assertFalse(json.toJson(SettingsJson.Settings.EMPTY).contains("designerLibrary"), "none: not written");
        assertEquals(Optional.empty(), json.readFromString("{\"format\":\"tob2026-settings\",\"version\":1,"
                + "\"designerLibrary\":7}").designerLibrary(), "only text");
        assertEquals(Optional.of("mine"), settings.withLastProfile("Bob").withOptions(Map.of())
                .withSolvedTutorial("tutorial-008").designerLibrary(), "the other parts' changes keep it");
    }
}
