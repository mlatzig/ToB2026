package de.latzig.game.tob.client.logic.game;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import de.latzig.game.tob.core.io.TowerLibraryLoader;
import de.latzig.game.tob.core.model.TowerDefinition;

/**
 * The tower catalogue (Phase 3a's picker model; Phase 5a replaced the picker by the start screens and keeps this as
 * the catalogue, without the picker's cursor and pages): the 144 towers of the original, tutorial and demo libraries
 * in that order, with their numbers in their library (the title bar), and, for a developer start, the towers of an
 * extra library after them (Phase 6a, {@code -Dtob.towerLibrary}). Loading is lenient ({@link TowerLibraryLoader}):
 * towers with problems stay in the list and cannot be started.
 */
public final class PickerModel {

    public static final List<String> LIBRARIES = List.of("original", "tutorial", "demo");

    private final List<TowerEntry> entries;

    public PickerModel(List<TowerEntry> entries) {
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("no towers");
        }
        this.entries = List.copyOf(entries);
    }

    /** Reads {@code original.json}, {@code tutorial.json} and {@code demo.json} from the towers directory. */
    public static PickerModel load(Path towersDirectory) throws IOException {
        return load(towersDirectory, Optional.empty());
    }

    /**
     * Reads the three libraries from the towers directory, then {@code extraLibrary} if present (Phase 6a's developer
     * start), labelled with its file name without {@code .json}.
     */
    public static PickerModel load(Path towersDirectory, Optional<Path> extraLibrary) throws IOException {
        TowerLibraryLoader loader = new TowerLibraryLoader();
        List<TowerEntry> entries = new ArrayList<>();
        for (String library : LIBRARIES) {
            add(entries, loader, library, towersDirectory.resolve(library + ".json"));
        }
        if (extraLibrary.isPresent()) {
            String name = extraLibrary.get().getFileName().toString();
            add(entries, loader, name.endsWith(".json") ? name.substring(0, name.length() - 5) : name,
                    extraLibrary.get());
        }
        return new PickerModel(entries);
    }

    private static void add(List<TowerEntry> entries, TowerLibraryLoader loader, String library, Path file)
            throws IOException {
        List<TowerLibraryLoader.LoadedTower> towers = loader.load(file).towers();
        for (int i = 0; i < towers.size(); i++) {
            TowerLibraryLoader.LoadedTower loaded = towers.get(i);
            String title = loaded.tower().map(TowerDefinition::title).orElse(loaded.label());
            String label = String.format(Locale.ROOT, "%-8s %03d  %s", library.toUpperCase(Locale.ROOT), i + 1,
                    title);
            entries.add(new TowerEntry(label, i + 1, loaded.tower(), loaded.problems()));
        }
    }

    public List<TowerEntry> entries() {
        return entries;
    }

    /** The 1-based number of the tower with {@code id} in its library, or 0 if it is not listed. */
    public int number(String id) {
        int index = indexOf(id);
        return index < 0 ? 0 : entries.get(index).number();
    }

    /** Index of the tower with {@code id}, or −1. */
    public int indexOf(String id) {
        for (int i = 0; i < entries.size(); i++) {
            Optional<TowerDefinition> tower = entries.get(i).tower();
            if (tower.isPresent() && tower.get().id().equals(id)) {
                return i;
            }
        }
        return -1;
    }
}
