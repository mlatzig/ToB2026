package de.latzig.game.tob.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Phase 7c: the release build's checks, tool arguments and WSL commands - without running jlink, jpackage or WSL -
 * and the package inputs in {@code src/package} (read from the project root, Maven's working directory).
 */
class ReleaseBuildTest {

    private static final Path ROOT = Path.of("D:\\work\\tob");

    @Test
    void aTemurinReleaseBuildOnItsOwnSystemMayRun() {
        assertEquals(List.of(), ReleaseBuild.preconditions(Platform.WINDOWS, "1.0.0", "21.0.8+9", Platform.WINDOWS,
                "Temurin-21.0.8+9"));
        assertEquals(List.of(), ReleaseBuild.preconditions(Platform.LINUX, "1.2", "21.0.9+10", Platform.LINUX,
                "Temurin-21.0.9+10"));
    }

    @Test
    void aSnapshotAnotherSystemOrAnotherJdkStopTheBuild() {
        List<String> problems = ReleaseBuild.preconditions(Platform.LINUX, "1.0.1-SNAPSHOT", "21.0.8+9",
                Platform.WINDOWS, "Corretto-21.0.8.9.1");
        assertEquals(3, problems.size(), problems.toString());
        assertTrue(problems.get(0).contains("1.0.1-SNAPSHOT is no release version"), problems.get(0));
        assertTrue(problems.get(1).contains("a linux package must be built on linux"), problems.get(1));
        assertTrue(problems.get(2).contains("Eclipse Temurin 21.0.8+9"), problems.get(2));
    }

    @Test
    void jlinkAndJpackageGetTheProvenOptions() {
        assertEquals(List.of("--add-modules", "java.se,jdk.unsupported", "--strip-debug", "--no-header-files",
                "--no-man-pages", "--strip-native-commands", "--output", Path.of("rt").toString()),
                ReleaseBuild.jlinkArguments(Path.of("rt")));

        List<String> windows = ReleaseBuild.jpackageArguments(Platform.WINDOWS, ROOT, "1.0.0", Path.of("in"),
                Path.of("rt"), Path.of("img"));
        assertEquals(List.of("--type", "app-image", "--name", "ToB2026", "--app-version", "1.0.0"),
                windows.subList(0, 6));
        assertEquals("tob2026-1.0.0.jar", after(windows, "--main-jar"));
        assertEquals("de.latzig.game.tob.client.TobApp", after(windows, "--main-class"));
        assertEquals(ROOT.resolve("src/package/ToB2026.ico").toString(), after(windows, "--icon"));
        assertFalse(windows.contains("--win-console"), "the launcher opens no console window");
        assertFalse(windows.contains("--add-launcher"), "one launcher only (question 2: the log is a file)");

        List<String> linux = ReleaseBuild.jpackageArguments(Platform.LINUX, ROOT, "1.0.0", Path.of("in"),
                Path.of("rt"), Path.of("img"));
        assertEquals("tob2026", after(linux, "--name"));
        assertEquals(ROOT.resolve("src/package/ToB2026.png").toString(), after(linux, "--icon"));
        assertFalse(linux.contains("--add-launcher"));
    }

    @Test
    void theLinuxBuildRunsThisProgramInWsl() {
        assertEquals("/mnt/d/work/tob/target", ReleaseBuild.wslPath("D:\\work\\tob\\target"));
        assertEquals("/mnt/c/x", ReleaseBuild.wslPath("C:/x"));
        assertEquals(List.of("wsl.exe", "-d", "Ubuntu", "--exec", "sh", "-c", "true"),
                ReleaseBuild.wslCommand("Ubuntu", "true"));
        assertEquals("exec $HOME/jdks/jdk-21.0.8+9/bin/java -cp '/mnt/d/work/tob/target/test-classes'"
                + " de.latzig.game.tob.release.ReleaseBuild linux '/mnt/d/work/tob' 1.0.0 21.0.8+9"
                + " /tmp/tob2026-release",
                ReleaseBuild.linuxScript(ROOT, "1.0.0", "21.0.8+9"));
    }

    @Test
    void missingWslADistributionOrTheLinuxJdkSkipWithAMessage() throws InterruptedException {
        assertEquals(Optional.empty(), ReleaseBuild.linuxMissing("Ubuntu", "21.0.8+9", ROOT, command -> 0));

        Optional<String> noWsl = ReleaseBuild.linuxMissing("Ubuntu", "21.0.8+9", ROOT, command -> {
            throw new IOException("Cannot run program \"wsl.exe\"");
        });
        assertTrue(noWsl.orElseThrow().startsWith("WSL is not available"), noWsl.get());

        Optional<String> noDistro = ReleaseBuild.linuxMissing("Debian", "21.0.8+9", ROOT, command -> 1);
        assertTrue(noDistro.orElseThrow().contains("the WSL distribution Debian does not start"), noDistro.get());

        List<List<String>> asked = new ArrayList<>();
        Optional<String> noJdk = ReleaseBuild.linuxMissing("Ubuntu", "21.0.8+9", ROOT, command -> {
            asked.add(command);
            return asked.size() == 1 ? 0 : 1;
        });
        assertEquals("no Linux JDK $HOME/jdks/jdk-21.0.8+9 in WSL Ubuntu; install it once with: wsl -d Ubuntu --exec sh"
                + " /mnt/d/work/tob/src/package/wsl-jdk.sh 21.0.8+9", noJdk.orElseThrow());
        assertEquals("test -x $HOME/jdks/jdk-21.0.8+9/bin/jpackage", asked.get(1).get(6));
    }

    /**
     * Decision 6 and the fix wave (final review I2): the licence texts the packages ship, each pinned to the official
     * text (SHA-256), listed with its source in {@code src/package/licenses/SOURCES.md} and named in CREDITS.md.
     */
    @Test
    void thePackageInputsAreInPlace() throws IOException, NoSuchAlgorithmException {
        Map<String, String> pinned = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        pinned.putAll(Map.ofEntries(
                Map.entry("LGPL-2.0.txt", "cc535c21133c895b56b374c8a1dc1eb948d99003ed2b47372069456b62f42b24"),
                Map.entry("LGPL-2.1.txt", "20e50fe7aae3e56378ebf0417d9de904f55a0e61e4df315333e632a4d3555d95"),
                Map.entry("Apache-2.0.txt", "cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30"),
                Map.entry("LWJGL-BSD-3-Clause.txt", "4c2f770897d0cfda4d9a65bb4d6d6983dccfd57d4c7eb74ea5ded0762cbc0e75"),
                Map.entry("libffi-MIT.txt", "d66f6a020ebb611afe64b93270d447aa32a9345d2d3a43d296493e9806b944f8"),
                Map.entry("liburing-MIT.txt", "1ec30e534aecb8eef3439fe3e36b20be20461bb758ca85107602fdaa3e23d274"),
                Map.entry("Khronos-MIT.txt", "f3857759226191860119a14e6cc0420df506c10a5c1b6b2ff8abc8f8d31920fb"),
                Map.entry("jemalloc-BSD-2-Clause.txt",
                        "94aa2caa98c25d942f58b956c71dba6a99ff98fc3a31cbc669fe2a4cd0268b53"),
                Map.entry("OpenAL-Soft-BSD-3-Clause.txt",
                        "b5a08cf98996dd83aa906a72c163600f6cb17a65ce546a553815a1267abd43ff"),
                Map.entry("OpenAL-Soft-PFFFT.txt", "64056328b3e7bc104e24ef96accc1a7abead156f0c02d1b87c97e3f3a28030de"),
                Map.entry("Lemur-BSD-3-Clause.txt", "92b5a09573223ac5ec4b56049afeeae1b127a5fdb6157f8b2fcbe9108a6725b7"),
                Map.entry("Lemur-Simsilica-BSD-3-Clause.txt",
                        "cc9c1ce4a4b4113f4de5eb0026b3870f1bc3920eb8a62c444cfc81a3a6d95f05")));
        assertEquals(pinned.keySet().stream().map(name -> "licenses/" + name).toList(),
                Platform.LICENCE_TEXTS.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList(),
                "the packages ship exactly the pinned texts");
        String manifest = Files.readString(Path.of("src/package/licenses/SOURCES.md"), StandardCharsets.UTF_8);
        String credits = Files.readString(Path.of("CREDITS.md"), StandardCharsets.UTF_8);
        for (Map.Entry<String, String> text : pinned.entrySet()) {
            Path file = Path.of("src/package/licenses").resolve(text.getKey());
            assertEquals(text.getValue(), sha256(file), "the official text: " + text.getKey());
            assertTrue(manifest.contains("| `" + text.getKey() + "` |") && manifest.contains("`" + text.getValue()
                    + "`"), "SOURCES.md lists " + text.getKey() + " with its source and SHA-256");
            assertTrue(credits.contains("`" + text.getKey() + "`"), "CREDITS.md names " + text.getKey());
        }
        assertTrue(Files.isRegularFile(Path.of(Platform.WINDOWS.icon)));
        assertTrue(Files.isRegularFile(Path.of(Platform.LINUX.icon)));
    }

    /** Fix wave (final review I1): a skipped Linux step removes the Linux ZIP of an earlier run. */
    @Test
    void aSkippedLinuxPackageLeavesNoStaleZip(@TempDir Path root) throws IOException {
        Path stale = root.resolve("target/release").resolve(Platform.LINUX.zipName("1.0.0"));
        Files.createDirectories(stale.getParent());
        Files.writeString(stale, "an earlier build");
        Path windows = Files.writeString(stale.resolveSibling(Platform.WINDOWS.zipName("1.0.0")), "this build");

        ReleaseBuild.skipLinux(root, "1.0.0", "a test");
        ReleaseBuild.skipLinux(root, "1.0.0", "nothing left to remove");

        assertFalse(Files.exists(stale), "the stale Linux ZIP is removed");
        assertTrue(Files.exists(windows), "the new Windows ZIP stays");
    }

    /** Fix wave (final review M2): development files below the assets (the towers' cross-check) stay at home. */
    @Test
    void developmentFilesOfTheAssetsAreNotShipped() {
        assertFalse(ReleaseBuild.shipsAsset(Path.of("towers", "towers-crosscheck.md")));
        assertFalse(ReleaseBuild.shipsAsset(Path.of("NOTES.MD")));
        assertTrue(ReleaseBuild.shipsAsset(Path.of("towers", "original.json")));
        assertTrue(ReleaseBuild.shipsAsset(Path.of("gui", "tob-font.fnt")));
    }

    /**
     * Fix wave (final review M7): the ZIP entries carry the time of the POM's
     * {@code project.build.outputTimestamp} (which also fixes the jar's entries), so a rebuild of the same sources
     * gives the same bytes.
     */
    @Test
    void theZipEntryTimeIsThePomsOutputTimestamp() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"), StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("<project.build.outputTimestamp>([^<]+)</project.build.outputTimestamp>")
                .matcher(pom);
        assertTrue(matcher.find(), "pom.xml sets project.build.outputTimestamp");
        assertEquals(Instant.parse(matcher.group(1)), Zips.ENTRY_TIME.toInstant());
    }

    /** Decision 4: the WSL setup script - a POSIX shell script with LF only (a CR breaks {@code sh}). */
    @Test
    void theWslJdkScriptInstallsTheCheckedJdkWhereTheBuildLooks() throws IOException {
        String script = Files.readString(Path.of("src/package/wsl-jdk.sh"), StandardCharsets.UTF_8);
        assertTrue(script.startsWith("#!/bin/sh\n"));
        assertFalse(script.contains("\r"), "LF line endings");
        assertTrue(script.contains("\nset -eu\n"), "stops at the first error");
        assertTrue(script.contains("sha256sum -c"), "the download is checked");
        assertTrue(script.contains("target=\"$HOME/jdks/jdk-$version\""), "the folder of ReleaseBuild.linuxJdk");
        assertEquals("$HOME/jdks/jdk-21.0.8+9", ReleaseBuild.linuxJdk("21.0.8+9"));
    }

    /** Phase 7d: the POM builds the running system's package - Windows (then Linux in WSL) or Linux natively. */
    @Test
    void thePomBuildsThePackageOfTheRunningSystem() throws IOException {
        assertEquals(Platform.current(), Platform.of("host"));
        assertEquals(Platform.WINDOWS, Platform.of("windows"));
        assertEquals(Platform.LINUX, Platform.of("linux"));
        assertThrows(IllegalArgumentException.class, () -> Platform.of("macos"));
        String pom = Files.readString(Path.of("pom.xml"), StandardCharsets.UTF_8);
        assertTrue(pom.contains("<argument>host</argument>"), "pom.xml passes host to ReleaseBuild");
        assertFalse(pom.contains("<argument>windows</argument>"), "the Linux runner must not be asked for Windows");
    }

    /**
     * Phase 7d: the Linux package's desktop entry helper - a POSIX shell script with LF only, shipped executable in
     * the package's top folder; it writes the absolute paths a desktop entry needs, to the launcher and the icon.
     */
    @Test
    void theDesktopEntryHelperWritesTheUnpackedFoldersAbsolutePaths() throws IOException {
        String script = Files.readString(Path.of("src/package", Platform.DESKTOP_SCRIPT), StandardCharsets.UTF_8);
        assertTrue(script.startsWith("#!/bin/sh\n"));
        assertFalse(script.contains("\r"), "LF line endings");
        assertTrue(script.contains("\nset -eu\n"), "stops at the first error");
        assertTrue(script.contains("dir=\"$(cd \"$(dirname \"$0\")\" && pwd -P)\""), "the folder it is in, absolute");
        for (String line : List.of("Exec=\"$dir/" + Platform.LINUX.launchers.get(0) + "\"",
                "Icon=$dir/" + Platform.LINUX_ICON, "Type=Application", "Categories=Game;LogicGame;")) {
            assertTrue(script.contains("\n" + line + "\n"), "the entry has " + line);
        }
        assertTrue(script.contains("\napps=\"$data/applications\"\n"), "the user's applications folder");
        assertTrue(script.contains("\nentry=\"$apps/tob2026.desktop\"\n"));
        assertTrue(script.contains("--remove"));
        assertEquals(List.of("bin/tob2026", Platform.DESKTOP_SCRIPT), Platform.LINUX.launchers);
    }

    /**
     * Phase 7d fix wave (carried Minor, Tasks 1-3 review): {@code install-desktop-entry.sh} refuses a folder path
     * holding a character a desktop entry's {@code Exec}/{@code Icon} line cannot escape (plan decision 3), with
     * no automated coverage so far. Where a real POSIX file system is reachable - a bare {@code sh} (the Linux CI
     * runner's own file system) or WSL from Windows (its own Linux file system, as the plan's own verification
     * used) - this drives the actual script from a folder named with each forbidden character and checks the
     * refusal (exit 1, the message on stderr). A bare {@code sh} on Windows itself (e.g. Git's) is not used for
     * this: it still runs on the Windows (NTFS) file system, which cannot hold a folder name with {@code "} or
     * {@code \} to begin with. If neither a POSIX file system nor WSL is reachable, this falls back to a static
     * check of the refusal's source instead of skipping the regression coverage outright.
     */
    @Test
    void installDesktopEntryRefusesFolderPathsWithForbiddenCharacters() throws IOException, InterruptedException {
        String script = Files.readString(Path.of("src/package", Platform.DESKTOP_SCRIPT), StandardCharsets.UTF_8);
        List<String> forbidden = List.of("\"", "`", "$", "\\", "%");
        Optional<List<String>> shell = posixShellPrefix();
        if (shell.isEmpty()) {
            staticallyChecksTheRefusal(script, forbidden);
            return;
        }
        boolean viaWsl = shell.get().get(0).equals("wsl.exe");
        String absolute = Path.of("src/package", Platform.DESKTOP_SCRIPT).toAbsolutePath().toString();
        String scriptPath = viaWsl ? ReleaseBuild.wslPath(absolute) : absolute.replace('\\', '/');
        // the character travels as its decimal ordinal (plain digits), not as itself: Windows' own process
        // argument quoting (ProcessBuilder -> CreateProcess, then wsl.exe's own argv translation) does not
        // round-trip a lone '"' correctly, so the script reconstructs the character from "$1" via octal.
        String body = "set -eu\n"
                + "oct=$(printf '%03o' \"$1\")\n"
                + "char=$(printf \"\\\\${oct}\")\n"
                + "base=/tmp/tob7d-desktop-$$\n"
                + "dir=\"$base/tob${char}folder\"\n"
                + "mkdir -p \"$dir/bin\"\n"
                + "cp '" + scriptPath + "' \"$dir/install-desktop-entry.sh\"\n"
                + "printf '#!/bin/sh\\n' > \"$dir/bin/tob2026\"\n"
                + "chmod +x \"$dir/bin/tob2026\" \"$dir/install-desktop-entry.sh\"\n"
                + "export HOME=\"$base/home\"\n"
                + "mkdir -p \"$HOME\"\n"
                + "if sh \"$dir/install-desktop-entry.sh\" 2>\"$base/err.txt\"; then status=0; else status=$?; fi\n"
                + "cat \"$base/err.txt\" >&2\n"
                + "rm -rf \"$base\"\n"
                + "exit \"$status\"\n";
        for (String ch : forbidden) {
            List<String> command = new ArrayList<>(shell.get());
            command.add(body);
            command.add("sh");
            command.add(Integer.toString(ch.charAt(0)));
            Process process = new ProcessBuilder(command).start();
            String err = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            int code = process.waitFor();
            assertEquals(1, code, "refuses the character " + ch + "; stderr: " + err);
            assertTrue(err.contains("cannot hold"), "the message for " + ch + ": " + err);
        }
    }

    /** No POSIX file system was reachable to run the script for real; checks the refusal's source instead. */
    private static void staticallyChecksTheRefusal(String script, List<String> forbidden) {
        List<String> lines = script.lines().toList();
        int caseIndex = lines.indexOf("case \"$dir\" in");
        assertTrue(caseIndex >= 0, "the refusal's case statement is present");
        String pattern = lines.get(caseIndex + 1).strip();
        assertTrue(pattern.startsWith("*[") && pattern.endsWith("]*)"), "a glob bracket pattern: " + pattern);
        for (String ch : forbidden) {
            assertTrue(pattern.contains("\\" + ch), "the pattern refuses " + ch + ": " + pattern);
        }
        assertTrue(lines.get(caseIndex + 2).strip().contains(">&2"), "the refusal message goes to stderr");
        assertEquals("exit 1 ;;", lines.get(caseIndex + 3).strip(), "refuses with exit 1");
    }

    /** The first reachable POSIX file system this machine offers: a bare {@code sh}, else WSL; else empty. */
    private static Optional<List<String>> posixShellPrefix() {
        if (File.separatorChar == '/' && processSucceeds(List.of("sh", "-c", "true"))) {
            return Optional.of(List.of("sh", "-c"));
        }
        if (processSucceeds(List.of("wsl.exe", "-d", ReleaseBuild.DEFAULT_DISTRO, "--exec", "true"))) {
            return Optional.of(List.of("wsl.exe", "-d", ReleaseBuild.DEFAULT_DISTRO, "--exec", "sh", "-c"));
        }
        return Optional.empty();
    }

    private static boolean processSucceeds(List<String> command) {
        try {
            return new ProcessBuilder(command).redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private static String after(List<String> arguments, String option) {
        return arguments.get(arguments.indexOf(option) + 1);
    }

    private static String sha256(Path file) throws IOException, NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }
}
