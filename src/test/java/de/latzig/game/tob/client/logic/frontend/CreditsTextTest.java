package de.latzig.game.tob.client.logic.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.client.logic.Texts;

/**
 * Phase 7b: the final in-game credits ({@code credits.text}, the Hall of Fame's CREDITS tab) and the menu ticker
 * ({@code menu.ticker}) name the people with their documented roles (spec decisions 2-5), the licences (decision 1)
 * and the third-party software, within the widths the screens allow. {@code CREDITS.md} holds the long form; the
 * test {@code de.latzig.game.tob.docs.CreditsDocTest} checks that both list the same software.
 */
class CreditsTextTest {

    /** The credits tab's line width in characters (the bundle's comment on {@code credits.text}). */
    static final int CREDITS_LINE = 46;
    /** The menu ticker group's line width in characters ({@code FrontEndTextsTest}). */
    static final int TICKER_LINE = 48;

    private static final Texts TEXTS = Texts.english();
    private static final List<String> CREDITS = List.of(TEXTS.get("credits.text").split("\n", -1));

    @Test
    void theCreditsNameThePeopleWithTheirDocumentedRoles() {
        assertEquals("Tower of Babel - the 2026 remake", CREDITS.get(0));
        assertEquals(List.of("Pete Cooke", "Rainbird / MicroProse 1989", "remade with his kind permission"),
                block("Original game:"));
        assertEquals("Pete Cooke, Chris Newcombe, Legin Drawnek, Tim Roberts, Axeman Towers Ltd.",
                String.join(" ", block("Original tower designs:")));
        assertEquals(List.of("Mario Latzig"), block("Remake:"));
        assertEquals(List.of("Dreamland, Improvi, Fadeout", "by Mario Latzig"), block("Music:"));
        assertEquals(List.of("from free sample collections,", "heavily edited by Mario Latzig"), block("Sounds:"));
        assertEquals(List.of("Marcus Pantel"), block("Support and Analysis:"));
        assertEquals(List.of("Chris Newcombe, for the contact to Pete Cooke"), block("Thanks:"));
        String all = String.join("\n", CREDITS);
        assertFalse(all.contains("sound code") || all.contains("disc code"), "spec decision 3: documented roles only");
    }

    @Test
    void theCreditsCarryTheLicenceLines() {
        assertEquals(List.of("Code - GNU GPL 3.0 or later", "Own assets - CC BY-NC-SA 4.0",
                "Original towers - (c) their authors,", "included with Pete Cooke's permission,",
                "non-commercial use only"), block("Licences:"));
        assertTrue(CREDITS.contains("Not affiliated with or endorsed by MicroProse"), "spec decision 9");
        assertEquals(List.of("(c) 2026 Mario Latzig", "Tower of Babel (c) Pete Cooke"), block("Copyright:"));
    }

    @Test
    void theCreditsListTheThirdPartySoftwareWithItsLicences() {
        assertEquals(List.of(
                "jMonkeyEngine - BSD 3-Clause License",
                "LWJGL - BSD 3-Clause License",
                "  with GLFW - zlib License",
                "  with OpenAL Soft - LGPL 2.0",
                "  with jemalloc - BSD 2-Clause License",
                "  with libffi - MIT License",
                "lwjgl3-awt - MIT License",
                "j-ogg-vorbis - J-Ogg by Tor-Einar Jarnbjo",
                "Lemur - BSD 3-Clause License",
                "Guava - Apache License 2.0",
                "Jackson - Apache License 2.0",
                "SLF4J - MIT License",
                "Logback - LGPL 2.1"), block("Third-party software:"));
    }

    @Test
    void everyLineFitsAndTheTickerNamesTheDocumentedRolesOnly() {
        List<String> headings = new ArrayList<>();
        for (String line : CREDITS) {
            assertTrue(line.length() <= CREDITS_LINE, "a credits line fits the tab: " + line);
            assertEquals(line.stripTrailing(), line, "no trailing blanks: '" + line + "'");
            if (line.endsWith(":")) {
                headings.add(line);
            }
        }
        assertEquals(List.of("Original game:", "Original tower designs:", "Remake:", "Music:", "Sounds:",
                "Support and Analysis:", "Thanks:", "Licences:", "Third-party software:", "Copyright:"), headings);

        String ticker = TEXTS.get("menu.ticker");
        assertTrue(ticker.endsWith("\n\t"), "the ticker clears its last line");
        List<String> lines = List.of(ticker.substring(0, ticker.length() - 2).split("\n\t"));
        assertEquals(List.of(
                "Based on \"Tower of Babel\" by Pete Cooke (1989),",
                "remade with his kind permission",
                "Rainbird/MicroProse 1989/90",
                "Original towers: Pete Cooke, Chris Newcombe,",
                "Legin Drawnek, Tim Roberts, Axeman Towers Ltd.",
                "Remake: Mario Latzig",
                "Support and Analysis: Marcus Pantel",
                "Thanks to Chris Newcombe for the contact"), lines);
        for (String line : lines) {
            assertTrue(line.length() <= TICKER_LINE, "a ticker line fits the ticker group: " + line);
        }
    }

    /** The lines after {@code heading} up to the next empty line (or the end). */
    private static List<String> block(String heading) {
        int start = CREDITS.indexOf(heading);
        assertTrue(start >= 0, "heading missing: " + heading);
        List<String> lines = new ArrayList<>();
        for (int i = start + 1; i < CREDITS.size() && !CREDITS.get(i).isEmpty(); i++) {
            lines.add(CREDITS.get(i));
        }
        return lines;
    }
}
