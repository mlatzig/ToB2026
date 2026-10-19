package de.latzig.game.tob.release;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;

/**
 * Phase 7c decisions 2, 4 and 6: builds a player package - the game's jar (without the assets) and the runtime
 * libraries (copied by Maven's {@code release} profile into {@code target/release/<platform>/lib}), the assets, a
 * jlink runtime of the JDK running this program, the {@code jpackage} app image with its launchers, the documents
 * and the third-party licence texts - zips it to {@code target/release/ToB2026-<version>-<platform>.zip} and checks
 * the ZIP ({@link PackageCheck}). Lives in the test sources: a build tool, never part of the game's jar.
 * <p>
 * Maven runs it for the running system ({@code mvn -Prelease package}, {@code exec:java}, the platform
 * {@code host}; Phase 7d). On Windows it then runs itself inside WSL with the Linux Temurin for the Linux package,
 * or skips that with a warning if WSL, the distribution or the Linux JDK is missing, or if
 * {@code -Dtob.release.linux=false}. On Linux (the GitHub Actions runner) it builds the Linux package natively.
 */
public final class ReleaseBuild {

    static final String MAIN_CLASS = "de.latzig.game.tob.client.TobApp";
    /** The runtime's modules (plan decision: the set of the working manual test of 2026-10-18). */
    static final String MODULES = "java.se,jdk.unsupported";
    /** {@code -Dtob.release.linux=false} skips the Linux package. */
    static final String LINUX_PROPERTY = "tob.release.linux";
    /** {@code -Dtob.release.wslDistro=NAME} chooses the WSL distribution; default {@value #DEFAULT_DISTRO}. */
    static final String DISTRO_PROPERTY = "tob.release.wslDistro";
    static final String DEFAULT_DISTRO = "Ubuntu";
    /** The Linux build's working folder inside WSL (a Linux file system, so the permissions are real). */
    static final String LINUX_STAGE = "/tmp/tob2026-release";
    static final String DESCRIPTION = "ToB 2026 - a remake of Tower of Babel by Pete Cooke";
    static final String VENDOR = "Mario Latzig";
    static final String COPYRIGHT = "ToB 2026 (c) Mario Latzig; Tower of Babel (c) Pete Cooke";

    /** Runs a command and returns its exit code (a seam for the tests of {@link #linuxMissing}). */
    @FunctionalInterface
    interface Probe {
        int exitCode(List<String> command) throws IOException, InterruptedException;
    }

    private ReleaseBuild() {
    }

    /** Arguments: {@code windows|linux|host PROJECT_ROOT VERSION JDK_VERSION [STAGE]}. */
    public static void main(String[] args) throws Exception {
        if (args.length < 4 || args.length > 5) {
            throw new IllegalArgumentException("usage: ReleaseBuild windows|linux|host PROJECT_ROOT VERSION"
                    + " JDK_VERSION [STAGE]");
        }
        Platform platform = Platform.of(args[0]);
        Path root = Path.of(args[1]).toAbsolutePath().normalize();
        String version = args[2];
        String jdk = args[3];
        Path stage = args.length == 5 ? Path.of(args[4]) : root.resolve("target/release").resolve(platform.id);
        List<String> problems = preconditions(platform, version, jdk, Platform.current(),
                System.getProperty("java.vendor.version"));
        if (!problems.isEmpty()) {
            throw new IllegalStateException("cannot build the " + platform.id + " package: "
                    + String.join("; ", problems));
        }
        Path zip = build(platform, root, version, stage);
        log("package: " + zip + " (" + Files.size(zip) / (1024 * 1024) + " MB)");
        if (platform == Platform.WINDOWS) {
            linux(root, version, jdk);
        }
    }

    /**
     * The reasons this build cannot run: a version {@code jpackage} does not accept (a release needs one such as
     * {@code 1.0.0}, no {@code -SNAPSHOT}), a package for another system than the running one, or a JDK other than
     * the Temurin {@code jdk} that {@code CREDITS.md} names ({@code java.vendor.version} {@code Temurin-<jdk>}).
     */
    static List<String> preconditions(Platform platform, String version, String jdk, Platform running,
            String vendorVersion) {
        List<String> problems = new ArrayList<>();
        if (!version.matches("[0-9]+(\\.[0-9]+){0,2}")) {
            problems.add("the version " + version + " is no release version such as 1.0.0 (set it in pom.xml)");
        }
        if (platform != running) {
            problems.add("a " + platform.id + " package must be built on " + platform.id + ", not on " + running.id);
        }
        if (!("Temurin-" + jdk).equals(vendorVersion)) {
            problems.add("the JDK must be Eclipse Temurin " + jdk + " (tob.release.jdk in pom.xml), not "
                    + vendorVersion);
        }
        return problems;
    }

    /** Builds the package of {@code platform} in {@code stage}; returns the checked ZIP in target/release. */
    static Path build(Platform platform, Path root, String version, Path stage) throws IOException {
        long start = System.nanoTime();
        Path input = stage.resolve("input");
        Path runtime = stage.resolve("runtime");
        Path images = stage.resolve("image");
        for (Path old : List.of(input, runtime, images)) {
            delete(old);
        }
        Files.createDirectories(input);
        Path libs = root.resolve("target/release").resolve(platform.id).resolve("lib");
        try (Stream<Path> jars = Files.list(libs)) {
            for (Path jar : (Iterable<Path>) jars.filter(p -> p.toString().endsWith(".jar")).sorted()::iterator) {
                Files.copy(jar, input.resolve(jar.getFileName().toString()));
            }
        }
        String gameJar = Platform.gameJar(version);
        Files.copy(root.resolve("target").resolve(gameJar), input.resolve(gameJar));
        copyAssets(root.resolve("src/main/resources/assets"), input.resolve("assets"));
        plainFiles(input);
        log("input ready: " + input + seconds(start));

        run("jlink", jlinkArguments(runtime));
        log("runtime ready: " + runtime + seconds(start));
        run("jpackage", jpackageArguments(platform, root, version, input, runtime, images));
        Path image = images.resolve(platform.imageName);
        for (String document : Platform.DOCUMENTS) {
            copyDocument(root.resolve(document), image.resolve(document));
        }
        for (String text : Platform.LICENCE_TEXTS) {
            Files.createDirectories(image.resolve(text).getParent());
            Files.copy(root.resolve("src/package").resolve(text), image.resolve(text));
        }
        plainFiles(image.resolve("licenses"));
        for (String document : Platform.DOCUMENTS) {
            plainFiles(image.resolve(document));
        }
        if (platform == Platform.LINUX) {
            executable(root.resolve("src/package").resolve(Platform.DESKTOP_SCRIPT),
                    image.resolve(Platform.DESKTOP_SCRIPT));
        }
        log("app image ready: " + image + seconds(start));

        Path zip = stage.resolve(platform.zipName(version));
        Zips.zip(image, zip);
        List<String> problems = PackageCheck.problems(zip, platform, version);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("the package " + zip + " is incomplete:\n  "
                    + String.join("\n  ", problems));
        }
        Path out = root.resolve("target/release").resolve(platform.zipName(version));
        Files.createDirectories(out.getParent());
        Files.move(zip, out, StandardCopyOption.REPLACE_EXISTING);
        log("zipped and checked" + seconds(start));
        return out;
    }

    static List<String> jlinkArguments(Path runtime) {
        return List.of("--add-modules", MODULES, "--strip-debug", "--no-header-files", "--no-man-pages",
                "--strip-native-commands", "--output", runtime.toString());
    }

    static List<String> jpackageArguments(Platform platform, Path root, String version, Path input, Path runtime,
            Path images) {
        List<String> arguments = new ArrayList<>(List.of("--type", "app-image", "--name", platform.imageName,
                "--app-version", version, "--input", input.toString(), "--main-jar", Platform.gameJar(version),
                "--main-class", MAIN_CLASS, "--runtime-image", runtime.toString(), "--icon",
                root.resolve(platform.icon).toString(), "--dest", images.toString(), "--description", DESCRIPTION,
                "--vendor", VENDOR, "--copyright", COPYRIGHT));
        return arguments;
    }

    /** Runs the Linux build inside WSL, or logs why it is skipped. */
    static void linux(Path root, String version, String jdk) throws IOException, InterruptedException {
        if ("false".equals(System.getProperty(LINUX_PROPERTY))) {
            skipLinux(root, version, "Linux package skipped (-D" + LINUX_PROPERTY + "=false)");
            return;
        }
        String distro = System.getProperty(DISTRO_PROPERTY, DEFAULT_DISTRO);
        Optional<String> missing = linuxMissing(distro, jdk, root, ReleaseBuild::exitCode);
        if (missing.isPresent()) {
            skipLinux(root, version, "WARNING: Linux package skipped: " + missing.get());
            return;
        }
        log("Linux package: running the build in WSL " + distro);
        List<String> command = wslCommand(distro, linuxScript(root, version, jdk));
        int code = new ProcessBuilder(command).inheritIO().start().waitFor();
        if (code != 0) {
            throw new IllegalStateException("the Linux package failed in WSL " + distro + " (exit code " + code
                    + ")");
        }
    }

    /**
     * Logs why the Linux package is skipped and removes the Linux ZIP of an earlier run from {@code target/release},
     * so that no stale package lies next to the new Windows one (fix wave, final review I1).
     */
    static void skipLinux(Path root, String version, String reason) throws IOException {
        log(reason);
        Path stale = root.resolve("target/release").resolve(Platform.LINUX.zipName(version));
        if (Files.deleteIfExists(stale)) {
            log("removed the Linux package of an earlier run: " + stale);
        }
    }

    /**
     * Whether the asset {@code relative} (to the assets folder) belongs in a package: not the Markdown notes of the
     * development, such as the tower converter's {@code towers/towers-crosscheck.md} (fix wave, final review M2).
     */
    static boolean shipsAsset(Path relative) {
        return !relative.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".md");
    }

    /** Why the Linux build cannot run: WSL, the distribution or the Linux JDK missing; empty if it can. */
    static Optional<String> linuxMissing(String distro, String jdk, Path root, Probe probe)
            throws InterruptedException {
        try {
            if (probe.exitCode(List.of("wsl.exe", "-d", distro, "--exec", "true")) != 0) {
                return Optional.of("the WSL distribution " + distro + " does not start (another one: -D"
                        + DISTRO_PROPERTY + "=NAME)");
            }
            if (probe.exitCode(wslCommand(distro, "test -x " + linuxJdk(jdk) + "/bin/jpackage")) != 0) {
                String script = wslPath(root.resolve("src/package/wsl-jdk.sh").toString());
                return Optional.of("no Linux JDK " + linuxJdk(jdk) + " in WSL " + distro
                        + "; install it once with: wsl -d " + distro + " --exec sh " + script + " " + jdk);
            }
            return Optional.empty();
        } catch (IOException e) {
            return Optional.of("WSL is not available (" + e.getMessage() + ")");
        }
    }

    /** The Linux Temurin {@code jdk} in the WSL user's home, as {@code wsl-jdk.sh} installs it. */
    static String linuxJdk(String jdk) {
        return "$HOME/jdks/jdk-" + jdk;
    }

    static List<String> wslCommand(String distro, String script) {
        return List.of("wsl.exe", "-d", distro, "--exec", "sh", "-c", script);
    }

    /** The shell command inside WSL: this program with the Linux JDK, for the project as WSL sees it. */
    static String linuxScript(Path root, String version, String jdk) {
        return "exec " + linuxJdk(jdk) + "/bin/java -cp " + quote(wslPath(root.resolve("target/test-classes")
                .toString())) + " " + ReleaseBuild.class.getName() + " linux " + quote(wslPath(root.toString())) + " "
                + version + " " + jdk + " " + LINUX_STAGE;
    }

    /** A Windows path as WSL mounts it: {@code D:\a\b} becomes {@code /mnt/d/a/b}. */
    static String wslPath(String windowsPath) {
        String path = windowsPath.replace('\\', '/');
        if (path.length() < 2 || path.charAt(1) != ':') {
            throw new IllegalArgumentException("not a Windows path with a drive: " + windowsPath);
        }
        return "/mnt/" + Character.toLowerCase(path.charAt(0)) + path.substring(2);
    }

    private static String quote(String text) {
        return "'" + text.replace("'", "'\\''") + "'";
    }

    private static int exitCode(List<String> command) throws IOException, InterruptedException {
        return new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start().waitFor();
    }

    private static void run(String name, List<String> arguments) {
        ToolProvider tool = ToolProvider.findFirst(name)
                .orElseThrow(() -> new IllegalStateException(name + " is missing: build the release with a JDK"));
        int code = tool.run(System.out, System.err, arguments.toArray(String[]::new));
        if (code != 0) {
            throw new IllegalStateException(name + " failed (exit code " + code + "): " + arguments);
        }
    }

    /**
     * Phase 7d: copies a helper script into the package, executable for everyone ({@code rwxr-xr-x}; the Linux
     * package is always built on a Linux file system, which has POSIX permissions).
     */
    private static void executable(Path source, Path target) throws IOException {
        Files.copy(source, target);
        if (Files.getFileAttributeView(target, PosixFileAttributeView.class) != null) {
            Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
    }

    /**
     * Copies a document into the package: a Markdown one as {@link PackageReadme#forPackage} adapts it (no block for
     * GitHub, links to files outside the package into the repository; Phase 7d), the licences unchanged.
     */
    private static void copyDocument(Path source, Path target) throws IOException {
        if (source.getFileName().toString().endsWith(".md")) {
            String text = Files.readString(source, StandardCharsets.UTF_8);
            Files.writeString(target, PackageReadme.forPackage(text, Platform.DOCUMENTS), StandardCharsets.UTF_8);
        } else {
            Files.copy(source, target);
        }
    }

    /** Copies the assets below {@code from} to {@code to}, without the files {@link #shipsAsset} leaves out. */
    private static void copyAssets(Path from, Path to) throws IOException {
        try (Stream<Path> paths = Files.walk(from)) {
            for (Path path : (Iterable<Path>) paths.sorted()::iterator) {
                Path relative = from.relativize(path);
                Path target = to.resolve(relative.toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else if (shipsAsset(relative)) {
                    Files.copy(path, target);
                }
            }
        }
    }

    /**
     * The Linux build reads its inputs from the Windows drive, where WSL shows every file as executable: there the
     * copied files (a folder's or a single one) become {@code rw-r--r--}. Does nothing without POSIX permissions.
     */
    private static void plainFiles(Path path) throws IOException {
        if (Files.getFileAttributeView(path, PosixFileAttributeView.class) == null) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            for (Path file : (Iterable<Path>) paths.filter(Files::isRegularFile)::iterator) {
                Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));
            }
        }
    }

    /** Deletes a folder of an earlier run; on Windows first clears the read-only flag jpackage and jlink set. */
    private static void delete(Path folder) throws IOException {
        if (!Files.exists(folder)) {
            return;
        }
        boolean windows = Platform.current() == Platform.WINDOWS;
        try (Stream<Path> paths = Files.walk(folder)) {
            for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) {
                if (windows) {
                    Files.getFileAttributeView(path, DosFileAttributeView.class).setReadOnly(false);
                }
                Files.delete(path);
            }
        }
    }

    private static String seconds(long start) {
        return String.format(Locale.ROOT, " (%.1f s)", (System.nanoTime() - start) / 1e9);
    }

    private static void log(String message) {
        System.out.println("[release] " + message);
    }
}
