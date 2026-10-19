package de.latzig.game.tob.release;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phase 7c §4: the content check of a built package - the launchers (the Linux one executable), the game's jar
 * without the assets, the assets next to it, the LWJGL natives of this platform only, the jlink runtime with its
 * {@code legal} folder, the documents and the licence texts, everything below one top folder; no development notes
 * below the assets and no two versions of one library (stale jars of an earlier build; fix wave, I1 and M2).
 */
final class PackageCheck {

    /** A jar directly in the app folder: artifact, version (e.g. {@code 3.9.0-stable}), natives classifier. */
    private static final Pattern JAR = Pattern.compile("([^/]+?)-([0-9]+(?:\\.[0-9]+)*(?:-[A-Za-z]+)?)"
            + "(-natives-[^/]+)?\\.jar");

    private PackageCheck() {
    }

    /** The problems of the package {@code zip} for {@code platform}; empty if it is complete. */
    static List<String> problems(Path zip, Platform platform, String version) throws IOException {
        Map<String, Zips.Entry> files = Zips.files(zip);
        String top = platform.imageName + "/";
        List<String> problems = new ArrayList<>();
        files.keySet().stream().filter(name -> !name.startsWith(top))
                .forEach(name -> problems.add("outside the top folder " + top + ": " + name));

        List<String> required = new ArrayList<>(platform.launchers);
        String jar = platform.appDir + "/" + Platform.gameJar(version);
        required.addAll(List.of(platform.launcherConfig(), jar, platform.appDir + "/assets/towers/original.json",
                platform.appDir + "/assets/gui/tob-font.fnt", platform.runtimeDir + "/release",
                platform.runtimeDir + "/" + platform.jvmLibrary, platform.runtimeDir + "/legal/java.base/LICENSE",
                platform.runtimeDir + "/legal/java.desktop/LICENSE"));
        required.addAll(Platform.DOCUMENTS);
        required.addAll(Platform.LICENCE_TEXTS);
        if (platform == Platform.LINUX) {
            required.add(Platform.LINUX_ICON);
        }
        for (String file : required) {
            if (!files.containsKey(top + file)) {
                problems.add("missing: " + top + file);
            }
        }

        String app = top + platform.appDir + "/";
        for (String module : Platform.NATIVE_MODULES) {
            String prefix = app + module + "-";
            String versioned = "[0-9.]+" + Pattern.quote(platform.nativesSuffix);
            boolean found = files.keySet().stream()
                    .anyMatch(name -> name.startsWith(prefix) && name.substring(prefix.length()).matches(versioned));
            if (!found) {
                problems.add("missing: the " + module + " natives (*" + platform.nativesSuffix + ") in " + app);
            }
        }
        files.keySet().stream().filter(name -> name.contains("-natives-") && !name.endsWith(platform.nativesSuffix))
                .forEach(name -> problems.add("natives of another platform: " + name));
        files.keySet().stream().filter(name -> name.startsWith(app + "assets/")
                && name.toLowerCase(Locale.ROOT).endsWith(".md"))
                .forEach(name -> problems.add("a development file in the assets: " + name));
        Map<String, List<String>> versions = new TreeMap<>();
        for (String name : files.keySet()) {
            Matcher matcher = name.startsWith(app) ? JAR.matcher(name.substring(app.length())) : null;
            if (matcher != null && matcher.matches()) {
                String key = matcher.group(1) + (matcher.group(3) == null ? "" : matcher.group(3));
                versions.computeIfAbsent(key, k -> new ArrayList<>()).add(matcher.group(0));
            }
        }
        versions.forEach((library, jars) -> {
            if (jars.size() > 1) {
                problems.add("two versions of " + library + " in " + app + ": " + String.join(", ", jars));
            }
        });

        if (files.containsKey(top + jar)) {
            List<String> entries = Zips.entriesOfInnerZip(zip, top + jar);
            entries.stream().filter(name -> name.startsWith("assets/")).findFirst()
                    .ifPresent(name -> problems.add("the game's jar contains the assets: " + name));
            // Phase 7d: the jar's copy of pom.xml would carry the author's development folder
            entries.stream().filter(name -> name.startsWith("META-INF/maven/")).findFirst()
                    .ifPresent(name -> problems.add("the game's jar contains Maven's descriptor: " + name));
        }
        if (platform == Platform.LINUX) {
            for (String launcher : platform.launchers) {
                Zips.Entry entry = files.get(top + launcher);
                if (entry != null && !entry.permissions().contains(PosixFilePermission.OWNER_EXECUTE)) {
                    problems.add("not executable: " + top + launcher + " " + entry.permissions());
                }
            }
        }
        return problems;
    }
}
