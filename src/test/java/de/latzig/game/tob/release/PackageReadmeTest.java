package de.latzig.game.tob.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Phase 7d (7c final review M6): the packages' {@code README.md} and {@code CREDITS.md} - without the block for
 * GitHub, with links to files outside the package pointing into the repository, naming the repository's issues.
 */
class PackageReadmeTest {

    private static final String REPO = "https://github.com/mlatzig/ToB2026";

    @Test
    void theBlockForGitHubIsLeftOut() {
        String readme = "# Title\n\n<!-- github-only -->\n![shot](docs/images/a.png)\n<!-- /github-only -->\n\nText.\n";
        assertEquals("# Title\n\nText.\n", PackageReadme.forPackage(readme, Platform.DOCUMENTS));
        assertEquals("No block.\n", PackageReadme.forPackage("No block.\n", Platform.DOCUMENTS));
    }

    @Test
    void aBlockThatIsNotOpenedAndClosedInOrderIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> PackageReadme.withoutGitHubBlock("<!-- github-only -->\nx\n"));
        assertThrows(IllegalArgumentException.class,
                () -> PackageReadme.withoutGitHubBlock("<!-- /github-only -->\nx\n<!-- github-only -->\n"));
    }

    @Test
    void linksToFilesOutsideThePackagePointIntoTheRepository() {
        String text = "See [CREDITS.md](CREDITS.md), [formats](docs/formats/README.md#towers), "
                + "[issues](" + REPO + "/issues) and [above](#playing).\n";
        assertEquals("See [CREDITS.md](CREDITS.md), [formats](" + REPO + "/blob/main/docs/formats/README.md#towers), "
                + "[issues](" + REPO + "/issues) and [above](#playing).\n",
                PackageReadme.forPackage(text, Platform.DOCUMENTS));
    }

    @Test
    void thePackagedReadmeAndCreditsNameTheRepositoryAndLinkNoMissingFile() throws IOException {
        assertEquals(REPO, PackageReadme.REPOSITORY);
        Pattern link = Pattern.compile("\\]\\(([^)\\s]+)\\)");
        for (String document : List.of("README.md", "CREDITS.md")) {
            String packaged = PackageReadme.forPackage(Files.readString(Path.of(document), StandardCharsets.UTF_8),
                    Platform.DOCUMENTS);
            assertTrue(packaged.contains("<" + REPO + "/issues>"), document + " names the repository's issues");
            assertFalse(packaged.contains("github-only") || packaged.contains("<img "),
                    document + ": the block for GitHub is left out");
            Matcher matcher = link.matcher(packaged);
            while (matcher.find()) {
                String target = matcher.group(1).replaceFirst("#.*$", "");
                assertTrue(target.isEmpty() || target.startsWith("https://") || Platform.DOCUMENTS.contains(target),
                        document + " links to a file that is not in the package: " + target);
            }
        }
    }
}
