package de.latzig.game.tob.core.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.sim.TowerCheck;

/**
 * Lenient library loading for the game (Phase 1a carry-over 4): every tower comes back with its problems (parse
 * errors and {@link TowerCheck} ERROR findings); the game skips towers with problems but can list them. A broken
 * library header still throws. The strict {@link TowerLibraryReader} remains for the converter.
 */
public final class TowerLibraryLoader {

    public record LoadedTower(String label, Optional<TowerDefinition> tower, List<String> problems) {
        public LoadedTower {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(tower, "tower");
            problems = List.copyOf(problems);
        }

        public boolean playable() {
            return tower.isPresent() && problems.isEmpty();
        }
    }

    public record LoadedLibrary(String id, String name, String source, List<LoadedTower> towers) {
        public LoadedLibrary {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(source, "source");
            towers = List.copyOf(towers);
        }

        public List<TowerDefinition> playableTowers() {
            return towers.stream().filter(LoadedTower::playable).map(t -> t.tower().orElseThrow()).toList();
        }

        public List<LoadedTower> skipped() {
            return towers.stream().filter(t -> !t.playable()).toList();
        }
    }

    private final ObjectMapper mapper = new ObjectMapper();

    public LoadedLibrary load(Path file) throws IOException {
        return loadFromString(Files.readString(file, StandardCharsets.UTF_8));
    }

    public LoadedLibrary loadFromString(String json) throws IOException {
        JsonNode root = mapper.readTree(json);
        JsonNode towerNodes;
        String id;
        String name;
        String source;
        try {
            String format = TowerLibraryReader.text(root, "format");
            int version = TowerLibraryReader.integer(root, "version");
            if (!TowerLibraryWriter.FORMAT.equals(format) || version != TowerLibraryWriter.VERSION) {
                throw new IllegalArgumentException("unsupported format " + format + " version " + version
                        + " (expected " + TowerLibraryWriter.FORMAT + " " + TowerLibraryWriter.VERSION + ")");
            }
            towerNodes = TowerLibraryReader.array(root, "towers");
            id = TowerLibraryReader.text(root, "id");
            name = TowerLibraryReader.text(root, "name");
            source = TowerLibraryReader.text(root, "source");
        } catch (IllegalArgumentException e) {
            throw new TowerFormatException(List.of("library: " + e.getMessage()));
        }

        List<LoadedTower> towers = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < towerNodes.size(); i++) {
            JsonNode node = towerNodes.get(i);
            String label = node != null && node.path("id").isTextual() ? node.path("id").asText() : "#" + i;
            if (node == null || !node.isObject()) {
                towers.add(new LoadedTower(label, Optional.empty(), List.of("tower must be an object")));
                continue;
            }
            TowerDefinition tower;
            try {
                tower = TowerLibraryReader.tower(node);
            } catch (IllegalArgumentException | NullPointerException e) {
                towers.add(new LoadedTower(label, Optional.empty(), List.of(String.valueOf(e.getMessage()))));
                continue;
            }
            List<String> problems = new ArrayList<>();
            if (!ids.add(tower.id())) {
                problems.add("duplicate tower id " + tower.id());
            }
            for (TowerCheck.Finding finding : TowerCheck.check(tower)) {
                if (finding.severity() == TowerCheck.Severity.ERROR) {
                    problems.add(finding.code() + ": " + finding.message()
                            + finding.location().map(pos -> " at " + pos).orElse(""));
                }
            }
            towers.add(new LoadedTower(label, Optional.of(tower), problems));
        }
        return new LoadedLibrary(id, name, source, towers);
    }
}
