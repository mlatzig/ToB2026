package de.latzig.game.tob.docs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

/**
 * Phase 7d decision 4 and §4: the four README screenshots (made offscreen by {@code client.ReadmeScreenshots}) are
 * kept in {@code docs/images}, and the README's block for GitHub shows them below the download link - the game
 * scene in the Remake look large, the Classic Look, the main menu and the Tower Designer small below it.
 */
class ReadmeImagesTest {

    private static final List<String> IMAGES = List.of("game-remake.png", "game-classic.png", "menu.png",
            "designer.png");
    private static final String START = "<!-- github-only -->";
    private static final String END = "<!-- /github-only -->";

    @Test
    void theFourScreenshotsAreInDocsImages() throws IOException {
        Path folder = PublishedDocs.root().resolve("docs/images");
        try (Stream<Path> files = Files.list(folder)) {
            assertEquals(IMAGES.stream().sorted().toList(),
                    files.map(path -> path.getFileName().toString()).sorted().toList(), "docs/images");
        }
        for (String name : IMAGES) {
            BufferedImage image = ImageIO.read(folder.resolve(name).toFile());
            assertEquals(1280, image.getWidth(), name);
            assertEquals(720, image.getHeight(), name);
            assertTrue(Files.size(folder.resolve(name)) < 2_000_000, name + ": small enough for the README");
        }
    }

    @Test
    void theReadmeShowsThemBelowTheTitleAndTheDownloadLink() throws IOException {
        String readme = PublishedDocs.read("README.md");
        assertTrue(readme.startsWith("# ToB 2026\n\n" + START + "\n"), "the block for GitHub follows the title");
        int end = readme.indexOf(END);
        assertTrue(end > 0, "the block is closed");
        String block = readme.substring(0, end);
        int download = block.indexOf("(https://github.com/mlatzig/ToB2026/releases/latest)");
        int large = block.indexOf("](docs/images/game-remake.png)");
        assertTrue(download > 0 && large > download, "the download link, then the large picture");
        for (String name : IMAGES.subList(1, IMAGES.size())) {
            int small = block.indexOf("<img src=\"docs/images/" + name + "\" width=\"32%\"");
            assertTrue(small > large, name + " small, below the large picture");
        }
    }
}
