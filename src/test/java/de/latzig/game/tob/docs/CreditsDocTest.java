package de.latzig.game.tob.docs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.client.logic.Texts;

/**
 * Phase 7b spec §3 and §5 (the third-party list is written by hand and checked here): {@code CREDITS.md} names the
 * people with their documented roles, and its two software tables list exactly the Maven jars of the build's test
 * class path (runtime libraries and test tools, transitive ones included) with their versions; the in-game credits
 * name the same runtime components.
 */
class CreditsDocTest {

    static final String RUNTIME = "## Third-party software";
    static final String TOOLS = "## Build and test tools (not shipped)";
    private static final Pattern ARTIFACT = Pattern.compile("`([a-z0-9.-]+)`");

    /** One row of a software table: the component, its artifacts and the versions found in the build. */
    record Row(String component, List<String> artifacts, List<String> versions) {
    }

    /** A jar of the class path in a Maven repository layout: {@code .../artifact/version/artifact-version*.jar}. */
    record Jar(String artifact, String version) {
    }

    @Test
    void theSoftwareTablesListEveryJarOfTheBuildsClassPath() throws IOException {
        String credits = PublishedDocs.read("CREDITS.md");
        List<Row> rows = new ArrayList<>(table(credits, RUNTIME));
        rows.addAll(table(credits, TOOLS));
        Set<Jar> jars = mavenJars();
        assertTrue(jars.contains(new Jar("jme3-core", "3.9.0-stable")), "the class path was read: " + jars);

        Set<String> listed = new TreeSet<>();
        for (Row row : rows) {
            for (String artifact : row.artifacts()) {
                assertTrue(listed.add(artifact), artifact + " is listed once");
                assertTrue(jars.stream().anyMatch(jar -> jar.artifact().equals(artifact)),
                        artifact + " (" + row.component() + ") is on the class path; remove stale rows");
            }
        }
        for (Jar jar : jars) {
            Row row = rows.stream().filter(r -> r.artifacts().contains(jar.artifact())).findFirst().orElse(null);
            assertTrue(row != null, jar + " is not in CREDITS.md: add it to " + RUNTIME + " or " + TOOLS);
            assertTrue(row.versions().contains(jar.version()), jar + ": CREDITS.md says " + row.versions());
        }
    }

    @Test
    void theCreditsNameThePeopleAndTheLicencesButNeverQuoteTheMail() throws IOException {
        String credits = PublishedDocs.read("CREDITS.md");
        for (String required : List.of("**Pete Cooke**", "Rainbird / MicroProse",
                "| Chris Newcombe | 9 original towers", "| Tim Roberts | 2 original towers",
                "| Axeman Towers Ltd. | 2 original towers",
                "\"Legin Drawnek\"", "| Mario Latzig |", "| Marcus Pantel | Support and Analysis",
                "the contact to Pete Cooke", "**Dreamland**, **Improvi** and **Fadeout**", "free sample collections",
                "removed", "free retro bitmap font of unknown", "not affiliated with or endorsed by MicroProse",
                "covered by neither of this project's", "Towers you build in the Tower Designer are yours",
                "CC BY-NC-SA 4.0", "LGPL 2.1", "Tor-Einar Jarnbjo", "Copyright (c) 2009-2025 jMonkeyEngine")) {
            assertTrue(credits.contains(required), "CREDITS.md names: " + required);
        }
        assertFalse(credits.contains("sound code") || credits.contains("disc code"), "spec decision 3");
        assertNoMailText(credits, "CREDITS.md");
    }

    @Test
    void theInGameCreditsNameTheSameRuntimeComponents() throws IOException {
        List<String> inGame = new ArrayList<>();
        boolean inSection = false;
        for (String line : Texts.english().get("credits.text").split("\n", -1)) {
            if (line.equals("Third-party software:")) {
                inSection = true;
            } else if (line.isEmpty()) {
                inSection = false;
            } else if (inSection && !line.startsWith(" ")) {
                inGame.add(line.substring(0, line.indexOf(" - ")));
            }
        }
        List<String> documented = table(PublishedDocs.read("CREDITS.md"), RUNTIME).stream().map(Row::component)
                .toList();
        assertEquals(documented, inGame, "CREDITS.md's runtime table and the CREDITS tab, in the same order");
    }

    /** Spec decision 2 and the author's rule: the permission mail is summarised, never quoted; no e-mail address. */
    static void assertNoMailText(String text, String file) {
        for (String quoted : List.of("happy for you", "bothered", "I retained")) {
            assertFalse(text.contains(quoted), file + " quotes the permission mail: " + quoted);
        }
        assertFalse(Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").matcher(text).find(),
                file + " contains an e-mail address");
    }

    /** The rows of the table whose header starts with "| Component |" in the section with the given heading. */
    static List<Row> table(String markdown, String heading) {
        List<String> lines = List.of(markdown.split("\n"));
        int start = lines.indexOf(heading);
        assertTrue(start >= 0, "CREDITS.md has the section " + heading);
        List<Row> rows = new ArrayList<>();
        boolean inTable = false;
        for (int i = start + 1; i < lines.size() && !lines.get(i).startsWith("## "); i++) {
            String line = lines.get(i);
            if (line.startsWith("| Component |")) {
                inTable = true;
            } else if (inTable && line.startsWith("|---")) {
                continue;
            } else if (inTable && line.startsWith("| ")) {
                String[] cells = line.split("\\|");
                List<String> artifacts = new ArrayList<>();
                Matcher matcher = ARTIFACT.matcher(cells[2]);
                while (matcher.find()) {
                    artifacts.add(matcher.group(1));
                }
                rows.add(new Row(cells[1].strip(), artifacts, List.of(cells[3].strip().split(", "))));
            } else if (inTable) {
                break;
            }
        }
        assertFalse(rows.isEmpty(), "a software table under " + heading);
        return rows;
    }

    /** The Maven jars of the test class path (surefire's, else the JVM's), without the build's own folders. */
    static Set<Jar> mavenJars() {
        String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Set<Jar> jars = new LinkedHashSet<>();
        for (String entry : classPath.split(File.pathSeparator)) {
            Path jar = Path.of(entry);
            Path versionDir = jar.getParent();
            Path artifactDir = versionDir == null ? null : versionDir.getParent();
            if (artifactDir == null || !entry.endsWith(".jar")) {
                continue;
            }
            String artifact = artifactDir.getFileName().toString();
            String version = versionDir.getFileName().toString();
            if (jar.getFileName().toString().startsWith(artifact + "-" + version)) {
                jars.add(new Jar(artifact, version));
            }
        }
        return jars;
    }
}
