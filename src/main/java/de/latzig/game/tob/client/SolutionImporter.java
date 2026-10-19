package de.latzig.game.tob.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.latzig.game.tob.client.logic.game.PickerModel;
import de.latzig.game.tob.client.logic.game.RecordingStore;
import de.latzig.game.tob.client.logic.game.SolutionCheck;
import de.latzig.game.tob.client.logic.game.TowerEntry;
import de.latzig.game.tob.core.model.TowerDefinition;

/**
 * The solution import tool (Phase 3c spec §2): copies the newest completed recording of every tower from the user
 * data folder ({@code tob.userdir}, the same folder the game records into) to
 * {@code src/test/resources/solutions/<tower-id>.json}, where {@code SolutionReplayTest} replays it. A tower that
 * already has a solution there is skipped unless it is named with {@code --replace}. Every recording is checked
 * headless first ({@link SolutionCheck}); one that is not a solution is reported as failed and not copied. Unreadable
 * recording files are skipped with a warning in the log ({@link RecordingStore#newestCompleted}). Run from the project
 * root:
 *
 * <pre>
 * mvn compile exec:java -Dexec.mainClass=de.latzig.game.tob.client.SolutionImporter
 * </pre>
 *
 * and, to replace existing solutions, add {@code -Dexec.args="--replace original-001,original-002"}. With
 * {@code -Dtob.towerLibrary=src/test/resources/towers/large.json} it also imports the solutions of that library's
 * towers (Phase 6a).
 */
public final class SolutionImporter {

    /** The committed solutions, relative to the project root. */
    public static final Path TARGET = Path.of("src/test/resources/solutions");
    static final String USAGE = "usage: SolutionImporter [--replace <tower-id>[,<tower-id>...]]";

    private static final Logger LOG = LoggerFactory.getLogger(SolutionImporter.class);

    public enum Result {
        COPIED,
        SKIPPED,
        FAILED
    }

    /** The result for one tower with a completed recording; {@code detail} names the file or the problem. */
    public record Line(String towerId, Result result, String detail) {
        @Override
        public String toString() {
            return result + " " + towerId + ": " + detail;
        }
    }

    private SolutionImporter() {
    }

    public static void main(String[] args) throws IOException {
        Set<String> replace;
        try {
            replace = replaceSet(args);
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
            System.out.println(USAGE);
            return;
        }
        AppConfig config = AppConfig.fromSystemProperties();
        List<TowerDefinition> towers = PickerModel.load(config.towersDirectory(), config.towerLibrary()).entries()
                .stream().map(TowerEntry::tower).flatMap(Optional::stream).toList();
        RecordingStore store = new RecordingStore(config.userDir());
        LOG.info("Importing from {} to {}", config.userDir().resolve("recordings").toAbsolutePath(),
                TARGET.toAbsolutePath());
        List<Line> lines = run(store, TARGET, towers, replace);
        lines.forEach(line -> System.out.println(line));
        System.out.println(count(lines, Result.COPIED) + " copied, " + count(lines, Result.SKIPPED) + " skipped, "
                + count(lines, Result.FAILED) + " failed");
    }

    /**
     * Imports the newest readable completed recording of each of {@code towers} (in their order) into {@code target}.
     *
     * @param replace the ids of the towers whose existing solution is replaced
     * @return one line per tower that has a completed recording
     */
    public static List<Line> run(RecordingStore store, Path target, List<TowerDefinition> towers,
            Set<String> replace) throws IOException {
        List<Line> lines = new ArrayList<>();
        for (TowerDefinition tower : towers) {
            Optional<RecordingStore.Stored> newest = store.newestCompleted(tower.id());
            if (newest.isEmpty()) {
                continue;
            }
            Path source = newest.get().file();
            Path destination = target.resolve(tower.id() + ".json");
            if (Files.exists(destination) && !replace.contains(tower.id())) {
                lines.add(new Line(tower.id(), Result.SKIPPED, "has a solution already (use --replace)"));
                continue;
            }
            Optional<String> problem = SolutionCheck.problem(newest.get().recording(), tower);
            if (problem.isPresent()) {
                lines.add(new Line(tower.id(), Result.FAILED, source + ": " + problem.get()));
                continue;
            }
            Files.createDirectories(target);
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            lines.add(new Line(tower.id(), Result.COPIED, source + " -> " + destination));
        }
        return lines;
    }

    /** The tower ids after {@code --replace} (comma or space separated). */
    static Set<String> replaceSet(String[] args) {
        Set<String> replace = new LinkedHashSet<>();
        boolean replacing = false;
        for (String arg : args) {
            if (arg.equals("--replace")) {
                replacing = true;
            } else if (replacing && !arg.startsWith("--")) {
                Arrays.stream(arg.split(",")).map(String::strip).filter(id -> !id.isEmpty()).forEach(replace::add);
            } else {
                throw new IllegalArgumentException("unknown argument " + arg);
            }
        }
        if (replacing && replace.isEmpty()) {
            throw new IllegalArgumentException("--replace needs at least one tower id");
        }
        return replace;
    }

    private static long count(List<Line> lines, Result result) {
        return lines.stream().filter(line -> line.result() == result).count();
    }
}
