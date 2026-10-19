package de.latzig.game.tob.docs;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Phase 7b spec §3 and decision 9: {@code README.md} says what the game is, where it comes from (the permission in
 * summary), how to build and run it, the controls, the licences, the MicroProse disclaimer and removal on request;
 * Phase 7c: how to play from the packages and how to build them. Phase 7d: the repository's address for problem
 * reports and removal requests (also in the packages), the Linux desktop entry, the offscreen tests, the release
 * workflow and the README pictures.
 */
class ReadmeTest {

    @Test
    void theReadmeStatesOriginPermissionDisclaimerLicencesAndHowToRun() throws IOException {
        String readme = PublishedDocs.read("README.md");
        for (String required : List.of("*Tower of Babel*", "Pete Cooke", "free PC version", "non-commercial",
                "not affiliated with or endorsed by MicroProse", "will be removed", "mvn compile exec:java",
                "-Dtob.userdir", "Java 21", "| F9, F10 |", "GNU General Public License 3.0 or later (`LICENSE`, the scope in `NOTICE`)",
                "Creative Commons BY-NC-SA 4.0 (`LICENSE-ASSETS`)", "covered by neither licence above",
                "| Towers you build with the Tower Designer | yours |", "Marcus Pantel", "Chris Newcombe",
                "[CREDITS.md](CREDITS.md)", "[docs/formats/README.md](docs/formats/README.md)",
                // Phase 7c: the packages, where they keep the user data, how they are built
                "## Playing", "`ToB2026-1.0.0-windows.zip`", "`ToB2026-1.0.0-linux.zip`", "`tob2026/bin/tob2026`",
                "## Reporting problems", "`%APPDATA%\\ToB2026\\logs\\tob2026.log`",
                "`~/.local/share/tob2026/logs/tob2026.log`", "`~/.local/share/tob2026`",
                // fix wave (final review I1, M1): a release build after a normal one needs clean; the build tool
                // lives in the test classes; the unsigned launcher makes Windows SmartScreen warn
                "`mvn -Prelease clean package`", "`-DskipTests`", "`-Dmaven.test.skip=true`", "More info",
                "Run anyway", "`src/package/wsl-jdk.sh`", "`-Dtob.release.linux=false`",
                // Phase 7d
                "<https://github.com/mlatzig/ToB2026/issues>", "`tob2026/install-desktop-entry.sh`", "`--remove`",
                "`-DexcludedGroups=offscreen`", "`.github/workflows/release.yml`", "ReadmeScreenshots")) {
            assertTrue(readme.contains(required), "README.md says: " + required);
        }
        assertFalse(readme.contains("in this repository"), "the packaged README names the repository's address");
        assertFalse(readme.contains("`mvn -Prelease package`"), "a release build without clean may reuse a stale jar");
        CreditsDocTest.assertNoMailText(readme, "README.md");
    }
}
