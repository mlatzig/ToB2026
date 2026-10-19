package de.latzig.game.tob.release;

import java.util.List;

/**
 * Phase 7c decision 2: the two player packages and the layout {@code jpackage} gives their app images. Paths are
 * relative to the image's top folder ({@link #imageName}) and use '/'.
 */
enum Platform {

    WINDOWS("windows", "ToB2026", List.of("ToB2026.exe"), "app", "runtime",
            "bin/server/jvm.dll", "src/package/ToB2026.ico", "-natives-windows.jar"),
    LINUX("linux", "tob2026", List.of("bin/tob2026", "install-desktop-entry.sh"), "lib/app", "lib/runtime",
            "lib/server/libjvm.so",
            "src/package/ToB2026.png", "-natives-linux.jar");

    /** The LWJGL modules whose natives every package needs. */
    static final List<String> NATIVE_MODULES = List.of("lwjgl", "lwjgl-glfw", "lwjgl-jemalloc", "lwjgl-openal",
            "lwjgl-opengl");
    /** Phase 7d: the Linux package's helper that adds the game to the applications menu (top folder). */
    static final String DESKTOP_SCRIPT = "install-desktop-entry.sh";
    /** Phase 7d: the Linux launcher's icon where {@code jpackage} puts it; the desktop entry names it. */
    static final String LINUX_ICON = "lib/tob2026.png";
    /** The documents copied into the image's top folder. */
    static final List<String> DOCUMENTS = List.of("README.md", "CREDITS.md", "LICENSE", "LICENSE-ASSETS");
    /**
     * The licence texts, from {@code src/package/licenses} (sources and SHA-256 in its {@code SOURCES.md}), in the
     * image's {@code licenses}: the LGPL texts (spec decision 6) and the texts the permissive licences of the
     * shipped libraries and natives ask a binary distribution to include (fix wave, final review I2).
     */
    static final List<String> LICENCE_TEXTS = List.of("licenses/Apache-2.0.txt", "licenses/jemalloc-BSD-2-Clause.txt",
            "licenses/Khronos-MIT.txt", "licenses/Lemur-BSD-3-Clause.txt", "licenses/Lemur-Simsilica-BSD-3-Clause.txt",
            "licenses/LGPL-2.0.txt", "licenses/LGPL-2.1.txt", "licenses/libffi-MIT.txt", "licenses/liburing-MIT.txt",
            "licenses/LWJGL-BSD-3-Clause.txt", "licenses/OpenAL-Soft-BSD-3-Clause.txt",
            "licenses/OpenAL-Soft-PFFFT.txt");

    /** The platform's name in the ZIP name and in {@code target/release/<id>}. */
    final String id;
    /** The app image's top folder and main launcher name ({@code jpackage --name}). */
    final String imageName;
    /** The launchers and helper scripts a player starts, the main launcher first (on Linux all executable). */
    final List<String> launchers;
    /** The folder with the game's jars, its assets and the launcher configuration. */
    final String appDir;
    /** The jlink runtime. */
    final String runtimeDir;
    /** The JVM's native library, relative to {@link #runtimeDir}. */
    final String jvmLibrary;
    /** The launcher icon, relative to the project root. */
    final String icon;
    /** The file name ending of the LWJGL natives jars this platform needs. */
    final String nativesSuffix;

    Platform(String id, String imageName, List<String> launchers, String appDir, String runtimeDir,
            String jvmLibrary, String icon, String nativesSuffix) {
        this.id = id;
        this.imageName = imageName;
        this.launchers = launchers;
        this.appDir = appDir;
        this.runtimeDir = runtimeDir;
        this.jvmLibrary = jvmLibrary;
        this.icon = icon;
        this.nativesSuffix = nativesSuffix;
    }

    /** The package's file name, e.g. {@code ToB2026-1.0.0-windows.zip} (decision 7). */
    String zipName(String version) {
        return "ToB2026-" + version + "-" + id + ".zip";
    }

    /** The game's own jar in the package (Maven's {@code finalName}). */
    static String gameJar(String version) {
        return "tob2026-" + version + ".jar";
    }

    /** The launcher configuration {@code jpackage} writes into {@link #appDir}. */
    String launcherConfig() {
        return appDir + "/" + imageName + ".cfg";
    }

    /** The platform of the running JVM, from {@code os.name}. */
    static Platform current() {
        return System.getProperty("os.name", "").startsWith("Windows") ? WINDOWS : LINUX;
    }

    /** The platform {@code windows} or {@code linux}; {@code host} is the running system's (Phase 7d, the POM's). */
    static Platform of(String id) {
        if ("host".equals(id)) {
            return current();
        }
        for (Platform platform : values()) {
            if (platform.id.equals(id)) {
                return platform;
            }
        }
        throw new IllegalArgumentException("unknown platform " + id + " (windows, linux or host)");
    }
}
