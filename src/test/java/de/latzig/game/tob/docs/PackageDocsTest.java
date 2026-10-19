package de.latzig.game.tob.docs;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Phase 7c decisions 6 and 7: {@code CREDITS.md} names the version of the packages (the POM's) and the Java runtime
 * they ship (the POM's {@code tob.release.jdk}, which the release build enforces) with its licence and source, and
 * the LGPL texts in the packages. Line breaks do not matter (the text is compared with single spaces).
 */
class PackageDocsTest {

    @Test
    void theCreditsNameTheRuntimeOfThePackagesAndItsSource() throws IOException {
        String jdk = pom("<tob.release.jdk>([^<]+)</tob.release.jdk>");
        String credits = credits();
        for (String required : List.of("## Java runtime (in the player packages)", "**Eclipse Temurin " + jdk + "**",
                "GNU General Public License version 2 with the Classpath Exception",
                "<https://github.com/adoptium/jdk21u/tree/jdk-" + jdk + "_adopt>", "`legal/`",
                "`LGPL-2.0.txt` (OpenAL Soft)", "`LGPL-2.1.txt` (Logback)")) {
            assertTrue(credits.contains(required), "CREDITS.md names: " + required);
        }
    }

    @Test
    void theCreditsNameTheVersionOfThePom() throws IOException {
        String version = pom("<artifactId>tob2026</artifactId>\\s*<version>([^<]+)</version>").replace("-SNAPSHOT",
                "");
        assertTrue(credits().contains("credits of ToB 2026 version " + version + ";"),
                "CREDITS.md names the version " + version);
    }

    /** Fix wave (final review M3): the README's package names follow the POM's version. */
    @Test
    void theReadmeNamesThePackagesOfThePomsVersion() throws IOException {
        String version = pom("<artifactId>tob2026</artifactId>\\s*<version>([^<]+)</version>").replace("-SNAPSHOT",
                "");
        String readme = PublishedDocs.read("README.md");
        for (String platform : List.of("windows", "linux")) {
            assertTrue(readme.contains("`ToB2026-" + version + "-" + platform + ".zip`"),
                    "README.md names the " + platform + " package of version " + version);
        }
    }

    /**
     * Fix wave (final review M4, I2): version-exact sources of the LGPL libraries, the natives of both platforms, and
     * Guava's licence text in the packages (its jar carries none).
     */
    @Test
    void theCreditsGiveVersionExactSourcesAndTheLicenceTexts() throws IOException {
        String credits = credits();
        for (String required : List.of("<https://github.com/kcat/openal-soft/tree/1.24.1>",
                "<https://github.com/qos-ch/logback/tree/v_1.6.5>", "LWJGL 3.3.6's Windows and Linux natives",
                "Guava's licence text is `licenses/Apache-2.0.txt`")) {
            assertTrue(credits.contains(required), "CREDITS.md names: " + required);
        }
        assertFalse(credits.contains("Jackson** and **Guava**"), "Guava's jar carries no licence file");
    }

    private static String credits() throws IOException {
        return PublishedDocs.read("CREDITS.md").replaceAll("\\s+", " ");
    }

    private static String pom(String regex) throws IOException {
        Matcher matcher = Pattern.compile(regex).matcher(PublishedDocs.read("pom.xml"));
        assertTrue(matcher.find(), "pom.xml has " + regex);
        return matcher.group(1);
    }
}
