package de.latzig.game.tob.client.logic.game;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.latzig.game.tob.core.io.RecordingReader;
import de.latzig.game.tob.core.io.RecordingWriter;
import de.latzig.game.tob.core.sim.Recording;
import de.latzig.game.tob.core.sim.RecordingMeta;
import de.latzig.game.tob.core.sim.RecordingMeta.Outcome;

/**
 * The recordings in the user data folder (Phase 3c spec §2): {@code <userDir>/recordings/<tower-id>/} holds one file
 * per attempt, named {@code <yyyyMMdd-HHmmss>-<outcome>.json} after the local time of {@code recordedAt} (later files
 * of the same second get {@code -2}, {@code -3}, …, whatever their outcome, so the names sort by time). Since the
 * author's decision of 2026-10-09 (recordings are a development tool for regression tests) only {@code completed} and
 * {@code crash} recordings are saved, and none is ever deleted; files of other outcomes from earlier builds are still
 * listed and read. Plain {@code java.nio}, no engine.
 */
public final class RecordingStore {

    private static final Logger LOG = LoggerFactory.getLogger(RecordingStore.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);
    private static final Pattern NAME = Pattern.compile("(\\d{8}-\\d{6})-([a-z]+)(?:-(\\d+))?\\.json");
    private static final Comparator<Path> OLDEST_FIRST = Comparator.comparing((Path file) -> name(file).group(1))
            .thenComparingInt(RecordingStore::counter);

    /** A readable recording and its file. */
    public record Stored(Path file, Recording recording) {
    }

    private final Path recordings;
    private final ZoneId zone;
    private final RecordingWriter writer = new RecordingWriter();
    private final RecordingReader reader = new RecordingReader();

    /** A store in {@code userDir} that names files in the system time zone. */
    public RecordingStore(Path userDir) {
        this(userDir, ZoneId.systemDefault());
    }

    public RecordingStore(Path userDir, ZoneId zone) {
        this.recordings = userDir.resolve("recordings");
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    /** The folder of the recordings of {@code towerId}. */
    public Path directory(String towerId) {
        return recordings.resolve(towerId);
    }

    /**
     * Saves {@code recording}; it must carry metadata with the outcome {@code completed} or {@code crash}.
     *
     * @return the new file
     */
    public Path save(Recording recording) throws IOException {
        RecordingMeta meta = recording.meta()
                .orElseThrow(() -> new IllegalArgumentException("a stored recording needs metadata"));
        if (meta.outcome() != Outcome.COMPLETED && meta.outcome() != Outcome.CRASH) {
            throw new IllegalArgumentException("only completed and crash recordings are saved, not "
                    + meta.outcome().fileName());
        }
        Path directory = directory(recording.towerId());
        Files.createDirectories(directory);
        String stamp = STAMP.format(meta.recordedAt().atZone(zone));
        int counter = files(recording.towerId()).stream().filter(file -> name(file).group(1).equals(stamp))
                .mapToInt(RecordingStore::counter).max().orElse(0) + 1;
        Path file = directory.resolve(stamp + "-" + meta.outcome().fileName() + (counter == 1 ? "" : "-" + counter)
                + ".json");
        writer.write(recording, file);
        return file;
    }

    /** The recording files of {@code towerId}, oldest first; files not named by this store are ignored. */
    public List<Path> files(String towerId) throws IOException {
        Path directory = directory(towerId);
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> listing = Files.list(directory)) {
            return listing.filter(file -> NAME.matcher(file.getFileName().toString()).matches())
                    .sorted(OLDEST_FIRST).toList();
        }
    }

    /** The newest readable completed recording of {@code towerId}; unreadable files are logged and skipped. */
    public Optional<Stored> newestCompleted(String towerId) {
        List<Path> files;
        try {
            files = new ArrayList<>(files(towerId));
        } catch (IOException e) {
            LOG.warn("Cannot list the recordings of {}: {}", towerId, e.getMessage());
            return Optional.empty();
        }
        for (int i = files.size() - 1; i >= 0; i--) {
            Path file = files.get(i);
            if (outcome(file) != Outcome.COMPLETED) {
                continue;
            }
            try {
                return Optional.of(new Stored(file, reader.read(file)));
            } catch (IOException e) {
                LOG.warn("Skipping the unreadable recording {}: {}", file, e.getMessage());
            }
        }
        return Optional.empty();
    }

    /** The number of a file within its second: 1 without a suffix, else the suffix. */
    private static int counter(Path file) {
        String suffix = name(file).group(3);
        return suffix == null ? 1 : Integer.parseInt(suffix);
    }

    /** The outcome in a file name, or null for an unknown one. */
    private static Outcome outcome(Path file) {
        try {
            return Outcome.fromFileName(name(file).group(2));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Matcher name(Path file) {
        Matcher matcher = NAME.matcher(file.getFileName().toString());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("not a recording file name: " + file);
        }
        return matcher;
    }
}
