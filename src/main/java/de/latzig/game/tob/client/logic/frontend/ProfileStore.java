package de.latzig.game.tob.client.logic.frontend;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.latzig.game.tob.core.io.ProfileReader;
import de.latzig.game.tob.core.io.ProfileWriter;
import de.latzig.game.tob.core.progress.Profile;

/**
 * The profiles in the user data folder (Phase 5a spec decision 10): {@code <userDir>/savegames/} holds one
 * {@link #fileName} per profile; {@code <userDir>/settings.json} ({@link SettingsFile}, shared with the options since
 * Phase 5b) remembers the last profile. Names are unique ignoring case. Unreadable files are logged and skipped. Plain
 * {@code java.nio}, no engine. Phase 5c-1 (spec decision 2): at most {@link #MAX_PROFILES} profiles can be created;
 * more from older data are never deleted automatically and stay listed and playable.
 * <p>
 * Not {@code final}: {@link GameFlowTest} subclasses it to stub a save failure (its own injected, controllable
 * {@link IOException}) without touching the real file system.
 */
public class ProfileStore {

    /** Phase 5c-1 decision 2: no new profile beyond this many. */
    public static final int MAX_PROFILES = 5;

    private static final Logger LOG = LoggerFactory.getLogger(ProfileStore.class);
    private static final String PREFIX = "profile-";
    private static final String SUFFIX = ".json";

    private final Path directory;
    private final SettingsFile settings;
    private final ProfileReader reader = new ProfileReader();
    private final ProfileWriter writer = new ProfileWriter();

    public ProfileStore(Path userDir) {
        this.directory = userDir.resolve("savegames");
        this.settings = new SettingsFile(userDir.resolve("settings.json"));
    }

    public Path directory() {
        return directory;
    }

    /** The settings file of the user data folder (Phase 5b: the last profile and the options). */
    public SettingsFile settings() {
        return settings;
    }

    /**
     * {@code profile-} + the lower-cased name, every character outside {@code a-z 0-9 _ -} as {@code %XX} (two
     * upper-case hex digits), + {@code .json}: legal on every file system, one file per name ignoring case.
     */
    public static String fileName(String name) {
        StringBuilder file = new StringBuilder(PREFIX);
        for (char c : name.toLowerCase(Locale.ROOT).toCharArray()) {
            if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '-') {
                file.append(c);
            } else {
                file.append(String.format(Locale.ROOT, "%%%02X", (int) c));
            }
        }
        return file.append(SUFFIX).toString();
    }

    /** Every readable profile, sorted by name ignoring case. */
    public List<Profile> list() {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        List<Path> files;
        try (Stream<Path> listing = Files.list(directory)) {
            files = listing.filter(file -> {
                String name = file.getFileName().toString();
                return name.startsWith(PREFIX) && name.endsWith(SUFFIX);
            }).toList();
        } catch (IOException e) {
            LOG.warn("Cannot list the profiles in {}: {}", directory, e.getMessage());
            return List.of();
        }
        List<Profile> profiles = new ArrayList<>();
        for (Path file : files) {
            try {
                profiles.add(reader.read(file));
            } catch (IOException e) {
                LOG.warn("Skipping the unreadable profile {}: {}", file, e.getMessage());
            }
        }
        profiles.sort(Comparator.comparing(Profile::name, String.CASE_INSENSITIVE_ORDER));
        return profiles;
    }

    /** The profile named {@code name}, ignoring case. */
    public Optional<Profile> find(String name) {
        return list().stream().filter(profile -> profile.name().equalsIgnoreCase(name)).findFirst();
    }

    public boolean exists(String name) {
        return find(name).isPresent();
    }

    /** Whether there are {@link #MAX_PROFILES} or more profiles: no new one can be created (decision 2). */
    public boolean full() {
        return list().size() >= MAX_PROFILES;
    }

    /** Saves {@code profile} atomically (autosave at every tower start and completion). */
    public void save(Profile profile) throws IOException {
        writer.write(profile, directory.resolve(fileName(profile.name())));
    }

    public void delete(String name) throws IOException {
        Files.deleteIfExists(directory.resolve(fileName(name)));
    }

    /** The profile played last, if the settings file names one (an unreadable file is logged by it). */
    public Optional<String> lastProfile() {
        return settings.read().lastProfile();
    }

    /** Remembers {@code name} as the last profile; the options in the same file stay (Phase 5b decision 10). */
    public void rememberLast(String name) throws IOException {
        settings.update(current -> current.withLastProfile(name));
    }
}
