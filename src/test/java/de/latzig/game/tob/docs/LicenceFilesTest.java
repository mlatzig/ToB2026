package de.latzig.game.tob.docs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Phase 7b spec decision 1 and §3: {@code LICENSE} carries the official GPLv3 text and {@code LICENSE-ASSETS} the
 * official CC BY-NC-SA 4.0 legal code, each unchanged (SHA-256 of the texts fetched from gnu.org and
 * creativecommons.org). {@code LICENSE} is the bare text, so GitHub recognises the licence; what it covers and the
 * "or later" option are in {@code NOTICE} (Phase 7d). {@code LICENSE-ASSETS} keeps its short header above a
 * separator line.
 */
class LicenceFilesTest {

    static final String SEPARATOR = "-".repeat(80);

    @Test
    void theLicenceFilesCarryTheOfficialTextsBelowAShortHeader() throws Exception {
        String gpl = PublishedDocs.read("LICENSE");
        assertFalse(gpl.contains("\r"), "LICENSE: LF line endings");
        assertEquals("3972dc9744f6499f0f9b2dbf76696f2ae7ad8af9b23dde66d6af86c9dfb36986", sha256(gpl));
        assertTrue(gpl.startsWith("                    GNU GENERAL PUBLIC LICENSE\n"
                + "                       Version 3, 29 June 2007\n"), "the GPLv3 text");
        String notice = PublishedDocs.read("NOTICE");
        assertFalse(notice.contains("\r"), "NOTICE: LF line endings");
        assertTrue(notice.startsWith("ToB 2026 - licence notice\n"), "NOTICE: its first line");
        for (String part : List.of("either version 3 of the License, or", "(at your option) any later version",
                "LICENSE-ASSETS", "see the license in LICENSE")) {
            assertTrue(notice.contains(part), "NOTICE: " + part);
        }

        String cc = official("LICENSE-ASSETS", "ToB 2026 - own assets\n", "Attribution-NonCommercial-ShareAlike");
        assertEquals("e66c269d4819aaab34b49ef5220c4ddab6756f21bb5180761a4eb8561f2b7bbd", sha256(cc));
        assertTrue(cc.startsWith("Attribution-NonCommercial-ShareAlike 4.0 International\n"), "the CC legal code");
    }

    /** The official text below the separator, after checking the header's first line and coverage statement. */
    private static String official(String file, String firstLine, String coverage) throws IOException {
        String content = PublishedDocs.read(file);
        assertFalse(content.contains("\r"), file + ": LF line endings");
        assertTrue(content.startsWith(firstLine), file + ": the header names what the file covers");
        int separator = content.indexOf("\n\n" + SEPARATOR + "\n\n");
        assertTrue(separator > 0, file + ": header and licence text are separated by a line of 80 '-'");
        assertTrue(content.substring(0, separator).contains(coverage), file + ": " + coverage);
        return content.substring(separator + SEPARATOR.length() + 4);
    }

    private static String sha256(String text) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(StandardCharsets.UTF_8)));
    }
}
