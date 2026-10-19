package de.latzig.game.tob.release;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * Phase 7d decision 5: the GitHub Actions workflow that builds the packages of a version tag - on a Windows and a
 * Linux runner, with the Temurin of {@code tob.release.jdk}, without the offscreen tests (no graphics card), into a
 * draft release - and the Git files: every file kept byte for byte (the tower checksums, the recorded solutions and
 * the LF-only scripts depend on it), the build folder ignored. Read from the project root.
 */
class ReleaseWorkflowTest {

    @Test
    void aVersionTagBuildsBothPackagesWithThePinnedJdkIntoADraftRelease() throws IOException {
        String workflow = Files.readString(Path.of(".github/workflows/release.yml"), StandardCharsets.UTF_8);
        Matcher jdk = Pattern.compile("<tob.release.jdk>([^<]+)</tob.release.jdk>")
                .matcher(Files.readString(Path.of("pom.xml"), StandardCharsets.UTF_8));
        assertTrue(jdk.find(), "pom.xml has tob.release.jdk");
        for (String required : List.of("tags:\n      - \"v*\"", "os: windows-latest", "os: ubuntu-22.04",
                "distribution: temurin", "java-version: \"" + jdk.group(1) + ".0.LTS\"",
                "mvn -B -Prelease clean package -DexcludedGroups=offscreen", "options: -Dtob.release.linux=false",
                "contents: write", "--draft", "ToB2026-$version-$platform.zip")) {
            assertTrue(workflow.contains(required), "release.yml: " + required);
        }
        assertFalse(workflow.contains("\r") || workflow.contains("\t"), "LF and no tabs");
    }

    @Test
    void gitKeepsEveryFileByteForByteAndIgnoresTheBuild() throws IOException {
        assertTrue(Files.readAllLines(Path.of(".gitattributes"), StandardCharsets.UTF_8).contains("* -text"),
                "no line-ending conversion, also on Windows");
        assertTrue(Files.readAllLines(Path.of(".gitignore"), StandardCharsets.UTF_8).contains("/target/"),
                "the build folder");
    }
}
