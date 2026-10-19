package de.latzig.game.tob.release;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Phase 7c: writing and reading the packages' ZIP files with the JDK's ZIP file system, which keeps the Unix file
 * permissions (the Linux launcher's execute bit) when the source has them.
 */
final class Zips {

    /**
     * The time of every entry: the POM's {@code project.build.outputTimestamp} (checked by {@code ReleaseBuildTest}),
     * which also fixes the times in the game's jar - so a rebuild of the same sources gives the same ZIP (fix wave,
     * final review M7). Change both together for a new release.
     */
    static final FileTime ENTRY_TIME = FileTime.from(Instant.parse("2026-10-19T00:00:00Z"));

    /** A file of a ZIP: its permissions (empty if the ZIP has none for it). */
    record Entry(Set<PosixFilePermission> permissions) {
    }

    private Zips() {
    }

    /**
     * Writes {@code folder} with everything below it into the new ZIP {@code zip}, as the top folder of the ZIP
     * (named like {@code folder}); POSIX permissions are copied where the source file system has them, and every
     * entry gets the time {@link #ENTRY_TIME}.
     */
    static void zip(Path folder, Path zip) throws IOException {
        Files.deleteIfExists(zip);
        boolean posix = Files.getFileAttributeView(folder, PosixFileAttributeView.class) != null;
        try (FileSystem fs = open(zip, true); Stream<Path> files = Files.walk(folder)) {
            Path base = folder.getParent();
            for (Path source : (Iterable<Path>) files.sorted()::iterator) {
                StringBuilder name = new StringBuilder();
                base.relativize(source).forEach(part -> name.append('/').append(part));
                Path target = fs.getPath(name.toString());
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(source, target);
                    if (posix) {
                        Files.setPosixFilePermissions(target, Files.getPosixFilePermissions(source));
                    }
                }
                // all three times: the ZIP file system stores the access and creation time as well
                Files.getFileAttributeView(target, BasicFileAttributeView.class).setTimes(ENTRY_TIME, ENTRY_TIME,
                        ENTRY_TIME);
            }
        }
    }

    /** The files of {@code zip} by name (no leading '/'), sorted; folders are left out. */
    static Map<String, Entry> files(Path zip) throws IOException {
        Map<String, Entry> files = new TreeMap<>();
        try (FileSystem fs = open(zip, false); Stream<Path> paths = Files.walk(fs.getPath("/"))) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                if (Files.isRegularFile(path)) {
                    PosixFileAttributes attributes = Files.readAttributes(path, PosixFileAttributes.class);
                    files.put(path.toString().substring(1), new Entry(attributes.permissions()));
                }
            }
        }
        return files;
    }

    /** The entry names of the ZIP or jar {@code name} inside {@code zip}. */
    static List<String> entriesOfInnerZip(Path zip, String name) throws IOException {
        try (FileSystem fs = open(zip, false); InputStream in = Files.newInputStream(fs.getPath("/" + name));
                ZipInputStream inner = new ZipInputStream(in)) {
            List<String> names = new ArrayList<>();
            for (ZipEntry entry = inner.getNextEntry(); entry != null; entry = inner.getNextEntry()) {
                names.add(entry.getName());
            }
            return names;
        }
    }

    private static FileSystem open(Path zip, boolean create) throws IOException {
        Map<String, String> env = Map.of("create", Boolean.toString(create), "enablePosixFileAttributes", "true");
        return FileSystems.newFileSystem(URI.create("jar:" + zip.toUri()), env);
    }
}
