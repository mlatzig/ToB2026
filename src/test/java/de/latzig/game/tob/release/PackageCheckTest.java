package de.latzig.game.tob.release;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Phase 7c §4: {@link PackageCheck} on small hand-made packages, and {@link Zips} writing a folder. */
class PackageCheckTest {

    private static final String VERSION = "1.0.0";

    @Test
    void completeWindowsAndLinuxPackagesPass(@TempDir Path dir) throws IOException {
        for (Platform platform : Platform.values()) {
            Path zip = dir.resolve(platform.zipName(VERSION));
            write(zip, platform, files(platform), "rwxr-xr-x", false);
            assertEquals(List.of(), PackageCheck.problems(zip, platform, VERSION), platform.id);
        }
    }

    @Test
    void missingFilesAndForeignNativesAreReported(@TempDir Path dir) throws IOException {
        List<String> files = files(Platform.WINDOWS);
        files.remove("runtime/legal/java.base/LICENSE");
        files.remove("app/lwjgl-openal-3.3.6-natives-windows.jar");
        files.add("app/lwjgl-openal-3.3.6-natives-linux.jar");
        files.add("app/lwjgl-3.3.6-natives-windows-x86.jar");
        Path zip = dir.resolve("p.zip");
        write(zip, Platform.WINDOWS, files, "rw-r--r--", false);

        assertEquals(List.of("missing: ToB2026/runtime/legal/java.base/LICENSE",
                "missing: the lwjgl-openal natives (*-natives-windows.jar) in ToB2026/app/",
                "natives of another platform: ToB2026/app/lwjgl-3.3.6-natives-windows-x86.jar",
                "natives of another platform: ToB2026/app/lwjgl-openal-3.3.6-natives-linux.jar"),
                PackageCheck.problems(zip, Platform.WINDOWS, VERSION));
    }

    @Test
    void assetsInTheGameJarAndFilesOutsideTheTopFolderAreReported(@TempDir Path dir) throws IOException {
        Path zip = dir.resolve("p.zip");
        write(zip, Platform.WINDOWS, files(Platform.WINDOWS), "rw-r--r--", true);
        try (FileSystem fs = FileSystems.newFileSystem(URI.create("jar:" + zip.toUri()), Map.of())) {
            Files.writeString(fs.getPath("/stray.txt"), "x");
        }

        assertEquals(List.of("outside the top folder ToB2026/: stray.txt",
                "the game's jar contains the assets: assets/towers/original.json",
                "the game's jar contains Maven's descriptor: META-INF/maven/de.latzig.game/tob2026/pom.xml"),
                PackageCheck.problems(zip, Platform.WINDOWS, VERSION));
    }

    @Test
    void aLinuxLauncherWithoutTheExecuteBitIsReported(@TempDir Path dir) throws IOException {
        Path zip = dir.resolve("p.zip");
        write(zip, Platform.LINUX, files(Platform.LINUX), "rw-r--r--", false);

        List<String> problems = PackageCheck.problems(zip, Platform.LINUX, VERSION);
        assertEquals(2, problems.size(), problems.toString());
        assertTrue(problems.get(0).startsWith("not executable: tob2026/bin/tob2026"), problems.get(0));
        assertTrue(problems.get(1).startsWith("not executable: tob2026/install-desktop-entry.sh"), problems.get(1));
    }

    /** Phase 7d: the Linux package's desktop entry helper and the icon it names belong in the package. */
    @Test
    void aLinuxPackageWithoutTheDesktopEntryHelperOrItsIconIsReported(@TempDir Path dir) throws IOException {
        List<String> files = files(Platform.LINUX);
        files.remove(Platform.DESKTOP_SCRIPT);
        files.remove(Platform.LINUX_ICON);
        Path zip = dir.resolve("p.zip");
        write(zip, Platform.LINUX, files, "rwxr-xr-x", false);

        assertEquals(List.of("missing: tob2026/install-desktop-entry.sh", "missing: tob2026/lib/tob2026.png"),
                PackageCheck.problems(zip, Platform.LINUX, VERSION));
    }

    /** Fix wave (final review M2): a development note below the assets does not belong in a package. */
    @Test
    void markdownFilesBelowTheAssetsAreReported(@TempDir Path dir) throws IOException {
        List<String> files = files(Platform.LINUX);
        files.add("lib/app/assets/towers/towers-crosscheck.md");
        Path zip = dir.resolve("p.zip");
        write(zip, Platform.LINUX, files, "rwxr-xr-x", false);

        assertEquals(List.of("a development file in the assets: tob2026/lib/app/assets/towers/towers-crosscheck.md"),
                PackageCheck.problems(zip, Platform.LINUX, VERSION));
    }

    /** Fix wave (final review I1): stale jars of an earlier build (another version of a library) are reported. */
    @Test
    void twoVersionsOfALibraryAreReported(@TempDir Path dir) throws IOException {
        List<String> files = files(Platform.WINDOWS);
        files.add("app/lwjgl-3.3.5.jar");
        files.add("app/lwjgl-3.3.5-natives-windows.jar");
        files.add("app/jackson-core-2.21.0.jar");
        Path zip = dir.resolve("p.zip");
        write(zip, Platform.WINDOWS, files, "rw-r--r--", false);

        assertEquals(List.of(
                "two versions of jackson-core in ToB2026/app/: jackson-core-2.21.0.jar, jackson-core-2.22.3.jar",
                "two versions of lwjgl in ToB2026/app/: lwjgl-3.3.5.jar, lwjgl-3.3.6.jar",
                "two versions of lwjgl-natives-windows in ToB2026/app/: lwjgl-3.3.5-natives-windows.jar,"
                        + " lwjgl-3.3.6-natives-windows.jar"),
                PackageCheck.problems(zip, Platform.WINDOWS, VERSION));
    }

    /** Fix wave (final review M7): every entry carries the same fixed time, so a rebuild gives the same bytes. */
    @Test
    void zipGivesEveryEntryTheFixedTime(@TempDir Path dir) throws IOException {
        Path image = dir.resolve("ToB2026");
        Files.createDirectories(image.resolve("app/assets"));
        Files.writeString(image.resolve("README.md"), "readme");
        Files.writeString(image.resolve("app/assets/a.txt"), "a");
        Path first = dir.resolve("first.zip");
        Path second = dir.resolve("second.zip");

        Zips.zip(image, first);
        Files.setLastModifiedTime(image.resolve("README.md"), FileTime.from(Instant.parse("2001-02-03T04:05:06Z")));
        Zips.zip(image, second);

        try (FileSystem fs = FileSystems.newFileSystem(URI.create("jar:" + first.toUri()), Map.of());
                Stream<Path> paths = Files.walk(fs.getPath("/"))) {
            for (Path path : (Iterable<Path>) paths.filter(p -> !p.toString().equals("/"))::iterator) {
                assertEquals(Zips.ENTRY_TIME.toInstant(), Files.getLastModifiedTime(path).toInstant(), path.toString());
            }
        }
        assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second), "the same bytes");
    }

    @Test
    void zipWritesTheFolderAsTheTopFolder(@TempDir Path dir) throws IOException {
        Path image = dir.resolve("ToB2026");
        Files.createDirectories(image.resolve("app/assets"));
        Files.writeString(image.resolve("README.md"), "readme");
        Files.writeString(image.resolve("app/assets/a.txt"), "a");
        Path zip = dir.resolve("out.zip");

        Zips.zip(image, zip);

        assertEquals(List.of("ToB2026/README.md", "ToB2026/app/assets/a.txt"), List.copyOf(Zips.files(zip).keySet()));
    }

    /** The files of a complete package of {@code platform}, relative to its top folder. */
    private static List<String> files(Platform platform) {
        List<String> files = new ArrayList<>(platform.launchers);
        files.addAll(List.of(platform.launcherConfig(), platform.appDir + "/" + Platform.gameJar(VERSION),
                platform.appDir + "/assets/towers/original.json", platform.appDir + "/assets/gui/tob-font.fnt",
                platform.appDir + "/jackson-core-2.22.3.jar", platform.runtimeDir + "/release",
                platform.runtimeDir + "/" + platform.jvmLibrary, platform.runtimeDir + "/legal/java.base/LICENSE",
                platform.runtimeDir + "/legal/java.desktop/LICENSE"));
        for (String module : Platform.NATIVE_MODULES) {
            files.add(platform.appDir + "/" + module + "-3.3.6.jar");
            files.add(platform.appDir + "/" + module + "-3.3.6" + platform.nativesSuffix);
        }
        files.addAll(Platform.DOCUMENTS);
        files.addAll(Platform.LICENCE_TEXTS);
        if (platform == Platform.LINUX) {
            files.add(Platform.LINUX_ICON);
        }
        return files;
    }

    /** A package with the given files (the game's jar a real jar, with the assets if asked) and permissions. */
    private static void write(Path zip, Platform platform, List<String> files, String permissions,
            boolean assetsInJar) throws IOException {
        Map<String, String> env = Map.of("create", "true", "enablePosixFileAttributes", "true");
        try (FileSystem fs = FileSystems.newFileSystem(URI.create("jar:" + zip.toUri()), env)) {
            for (String file : files) {
                Path path = fs.getPath("/" + platform.imageName + "/" + file);
                Files.createDirectories(path.getParent());
                byte[] content = file.endsWith(Platform.gameJar(VERSION)) ? gameJar(assetsInJar) : new byte[] {1};
                Files.write(path, content);
                Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions));
            }
        }
    }

    private static byte[] gameJar(boolean withAssets) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream jar = new ZipOutputStream(bytes)) {
            jar.putNextEntry(new ZipEntry("de/latzig/game/tob/client/TobApp.class"));
            jar.closeEntry();
            if (withAssets) {
                jar.putNextEntry(new ZipEntry("assets/towers/original.json"));
                jar.closeEntry();
                // Phase 7d: the stale jar of a normal build also carries Maven's descriptor
                jar.putNextEntry(new ZipEntry("META-INF/maven/de.latzig.game/tob2026/pom.xml"));
                jar.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
