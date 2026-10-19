package de.latzig.game.tob.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Phase 7d: every test class that starts jME's offscreen surface carries {@link Offscreen}, so that a build without
 * a graphics card ({@code -DexcludedGroups=offscreen}, the GitHub Actions release workflow) leaves exactly these out.
 * Reads the test sources from the project root, Maven's working directory.
 */
class OffscreenTest {

    /** The call that opens the offscreen surface, assembled so that this file does not contain it. */
    private static final String START = "start(JmeContext.Type." + "OffscreenSurface)";

    @Test
    void everyTestThatRendersOffscreenIsTagged() throws IOException {
        List<String> tagged = new ArrayList<>();
        List<String> untagged = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/test/java"))) {
            // test classes only: the tool ReadmeScreenshots renders offscreen too, but no build runs it
            List<Path> tests = files.filter(p -> p.toString().endsWith("Test.java")).sorted().toList();
            for (Path file : tests) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                if (text.contains(START)) {
                    (text.contains("\n@Offscreen\nclass ") ? tagged : untagged).add(file.getFileName().toString());
                }
            }
        }
        assertEquals(List.of(), untagged, "offscreen tests without @Offscreen");
        assertTrue(tagged.size() >= 9, "the offscreen tests were found: " + tagged);
        assertEquals("offscreen", Offscreen.class.getAnnotation(Tag.class).value());
        assertTrue(MenuScreenshotTest.class.isAnnotationPresent(Offscreen.class));
    }
}
