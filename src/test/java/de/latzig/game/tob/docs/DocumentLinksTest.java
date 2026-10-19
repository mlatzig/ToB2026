package de.latzig.game.tob.docs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Phase 7b spec §1 and §3: no document links to the removed Phase 0 evidence folder (roadmap §4.1, author decision
 * 2026-10-15; it stays in the SVN history), and the published documents (README, CREDITS, {@code docs/formats})
 * keep the project's Markdown rules: LF, lines of at most 120 characters outside tables, relative links that
 * resolve, no e-mail address. Text inside fenced code blocks is a quotation, not a link.
 */
class DocumentLinksTest {

    /** A path to a file in the removed folder, or a Markdown link into it. */
    private static final Pattern EVIDENCE = Pattern.compile("evidence/phase0/\\w|\\]\\([^)]*evidence/");
    private static final Pattern LINK = Pattern.compile("\\]\\(([^)\\s]+)\\)");
    private static final List<String> PUBLISHED = List.of("README.md", "CREDITS.md", "docs/formats/README.md",
            "docs/formats/elements-json.md", "docs/formats/gui-atlas-and-font.md", "docs/formats/tobmesh.md",
            "docs/formats/tower-json.md");

    @Test
    void noDocumentLinksToTheRemovedEvidenceFolder() throws IOException {
        Path root = PublishedDocs.root();
        List<Path> documents = new ArrayList<>(List.of(root.resolve("README.md"), root.resolve("CREDITS.md")));
        try (Stream<Path> docs = Files.walk(root.resolve("docs"))) {
            docs.filter(path -> path.toString().endsWith(".md")).sorted().forEach(documents::add);
        }
        // Phase 7d: the author's working copy has the specs and plans; the published repository only the documents
        boolean specs = Files.isDirectory(root.resolve("docs").resolve("specs"));
        assertTrue(specs ? documents.size() > 50 : documents.size() >= PUBLISHED.size(),
                "the specs, plans and format documents were found: " + documents.size());
        List<String> found = new ArrayList<>();
        for (Path document : documents) {
            List<String> lines = proseLines(Files.readString(document, StandardCharsets.UTF_8));
            for (int i = 0; i < lines.size(); i++) {
                if (EVIDENCE.matcher(lines.get(i)).find()) {
                    found.add(root.relativize(document).toString().replace('\\', '/') + ":" + (i + 1));
                }
            }
        }
        assertEquals(List.of(), found, "links to the removed docs/evidence folder");
    }

    @Test
    void thePublishedDocumentsAreTidy() throws IOException {
        Path root = PublishedDocs.root();
        try (Stream<Path> formats = Files.list(root.resolve("docs/formats"))) {
            assertEquals(PUBLISHED.subList(2, PUBLISHED.size()), formats.map(path -> "docs/formats/"
                    + path.getFileName()).sorted().toList(), "the format documents");
        }
        for (String name : PUBLISHED) {
            String text = PublishedDocs.read(name);
            assertFalse(text.contains("\r"), name + ": LF line endings");
            assertTrue(text.endsWith("\n") && !text.endsWith("\n\n"), name + ": ends with one newline");
            List<String> lines = List.of(text.split("\n", -1));
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                assertTrue(line.startsWith("|") || line.codePointCount(0, line.length()) <= 120,
                        name + ":" + (i + 1) + " is longer than 120 characters");
            }
            List<String> prose = proseLines(text);
            for (int i = 0; i < prose.size(); i++) {
                Matcher link = LINK.matcher(prose.get(i));
                while (link.find()) {
                    String target = link.group(1).replaceFirst("#.*$", "");
                    if (target.isEmpty() || target.matches("[a-z]+:.*")) {
                        continue;
                    }
                    Path resolved = root.resolve(name).getParent().resolve(target).normalize();
                    assertTrue(Files.exists(resolved), name + ":" + (i + 1) + " links to a missing file: " + target);
                }
            }
            CreditsDocTest.assertNoMailText(text, name);
        }
    }

    /** The document's lines with the lines of fenced code blocks (fences included) blanked out. */
    private static List<String> proseLines(String markdown) {
        String text = markdown.replace("\r\n", "\n");
        List<String> lines = new ArrayList<>(List.of(text.split("\n", -1)));
        for (PublishedDocs.CodeBlock block : PublishedDocs.fencedBlocks(text)) {
            for (int i = block.open(); i <= block.close(); i++) {
                lines.set(i, "");
            }
        }
        return lines;
    }
}
