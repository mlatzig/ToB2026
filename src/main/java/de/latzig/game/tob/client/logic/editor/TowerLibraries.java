package de.latzig.game.tob.client.logic.editor;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import de.latzig.game.tob.client.logic.game.PickerModel;
import de.latzig.game.tob.core.io.AtomicFiles;
import de.latzig.game.tob.core.io.TowerLibraryLoader;
import de.latzig.game.tob.core.io.TowerLibraryWriter;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.model.TowerLibrary;

/**
 * The Tower Designer's libraries (Phase 6b spec decision 5): the shipped libraries ({@code original}, {@code tutorial},
 * {@code demo} in the assets) open read-only; own libraries are JSON files in the format of {@code original.json} in
 * {@code <userdir>/towers/<name>.json}, written atomically ({@link AtomicFiles}). An own library's name is its id,
 * lower case {@code a-z 0-9 _}, at most {@value #MAX_NAME} characters, never a shipped library's name (or
 * {@code large}, the 6a test library). Phase 6d decision 7: its towers keep the ids they got at creation (random
 * UUIDs, {@link TowerIds}; towers saved before 6d keep their {@code <name>-<nnn>}); a shipped library saved as an own
 * one gets new ids first ({@link EditorModel#towersToSave}), so no own tower ever has an {@code original-} id and
 * joins the campaign (6a's open point M6). Loading is lenient
 * ({@link TowerLibraryLoader}): towers that cannot be read are skipped and counted; towers with check errors open
 * (the rule pass marks them).
 * <p>
 * Final review M6 (controller verified on Windows 11, 2026-10-14): a Windows device name ({@code con}, {@code nul},
 * ...) is a valid library name - with the {@code .json} extension the file ({@code con.json}, ...) is an ordinary
 * file, not the reserved device name itself, so no file-name prefix or device-name check is needed. Only
 * {@link #RESERVED} (a shipped library's name, or {@code large}) is refused.
 */
public final class TowerLibraries {

    /** The own libraries' folder in the user data folder. */
    public static final String FOLDER = "towers";
    public static final int MAX_NAME = 16;
    /** The {@code source} of a library the designer writes. */
    public static final String SOURCE = "ToB 2026 Tower Designer";
    private static final List<String> RESERVED = List.of("original", "tutorial", "demo", "large");
    private static final String SUFFIX = ".json";

    /** A library of the Open dialog. */
    public record Entry(String name, boolean readOnly) {

        public Entry {
            Objects.requireNonNull(name, "name");
        }
    }

    /** A library as the designer opened it; {@code skipped} towers could not be read. */
    public record Opened(Entry entry, List<TowerDefinition> towers, int skipped) {

        public Opened {
            Objects.requireNonNull(entry, "entry");
            towers = List.copyOf(towers);
        }
    }

    private final Path shipped;
    private final Path own;
    private final TowerLibraryLoader loader = new TowerLibraryLoader();
    private final TowerLibraryWriter writer = new TowerLibraryWriter();

    /**
     * @param shipped the assets' towers folder ({@code AppConfig.towersDirectory()})
     * @param userDir the user data folder ({@code tob.userdir}); own libraries go to its {@value #FOLDER} folder
     */
    public TowerLibraries(Path shipped, Path userDir) {
        this.shipped = Objects.requireNonNull(shipped, "shipped");
        this.own = userDir.resolve(FOLDER);
    }

    /** The own libraries' folder. */
    public Path folder() {
        return own;
    }

    /** Whether {@code name} may name an own library (already in lower case). */
    public static boolean validName(String name) {
        return name.length() <= MAX_NAME && name.matches("[a-z0-9][a-z0-9_]*") && !reserved(name);
    }

    /**
     * Whether {@code name} is refused as reserved rather than for its characters: a shipped library's name (or
     * {@code large}, the 6a test library), so {@code TowerDesigner} can give it its own error text (final review
     * M6). A Windows device name is not reserved (controller verified on Windows 11, see the class javadoc).
     */
    public static boolean reserved(String name) {
        return RESERVED.contains(name);
    }

    /** A typed name as the designer stores it: trimmed, lower case. */
    public static String normalized(String typed) {
        return typed.strip().toLowerCase(Locale.ROOT);
    }

    /** The shipped libraries present (read-only, in the catalogue's order), then the own ones by name. */
    public List<Entry> list() throws IOException {
        List<Entry> entries = new ArrayList<>();
        for (String name : PickerModel.LIBRARIES) {
            if (Files.isRegularFile(shipped.resolve(name + SUFFIX))) {
                entries.add(new Entry(name, true));
            }
        }
        List<String> names = new ArrayList<>();
        if (Files.isDirectory(own)) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(own, "*" + SUFFIX)) {
                for (Path file : files) {
                    String name = file.getFileName().toString();
                    name = name.substring(0, name.length() - SUFFIX.length());
                    if (validName(name)) {
                        names.add(name);
                    }
                }
            }
        }
        names.sort(null);
        names.forEach(name -> entries.add(new Entry(name, false)));
        return entries;
    }

    /** Whether an own library {@code name} exists. */
    public boolean exists(String name) {
        return Files.isRegularFile(own.resolve(name + SUFFIX));
    }

    /**
     * The library {@code name} as the designer opens it (Phase 6d decision 8: the name {@code settings.json}
     * remembers): a shipped one read-only, an own one if its file exists; empty for anything else.
     */
    public Optional<Entry> entry(String name) {
        if (PickerModel.LIBRARIES.contains(name)) {
            return Files.isRegularFile(shipped.resolve(name + SUFFIX)) ? Optional.of(new Entry(name, true))
                    : Optional.empty();
        }
        return validName(name) && exists(name) ? Optional.of(new Entry(name, false)) : Optional.empty();
    }

    /** Reads {@code entry}'s towers leniently. */
    public Opened open(Entry entry) throws IOException {
        Path file = (entry.readOnly() ? shipped : own).resolve(entry.name() + SUFFIX);
        List<TowerDefinition> towers = new ArrayList<>();
        int skipped = 0;
        for (TowerLibraryLoader.LoadedTower loaded : loader.load(file).towers()) {
            Optional<TowerDefinition> tower = loaded.tower();
            if (tower.isPresent() && towers.size() < EditorTowers.MAX_TOWERS) {
                towers.add(tower.get());
            } else {
                skipped++;
            }
        }
        return new Opened(entry, towers, skipped);
    }

    /**
     * Writes {@code towers} as the own library {@code name}: each keeps its id (Phase 6d decision 7: never
     * renumbered), its group and index follow its position (groups of 9); returns the towers as written.
     *
     * @throws IllegalArgumentException if the name is not {@link #validName valid}
     */
    public List<TowerDefinition> save(String name, List<TowerDefinition> towers) throws IOException {
        if (!validName(name)) {
            throw new IllegalArgumentException("not a library name: " + name);
        }
        List<TowerDefinition> placed = new ArrayList<>();
        for (int i = 0; i < towers.size(); i++) {
            placed.add(EditorTowers.placed(towers.get(i), i));
        }
        TowerLibrary library = new TowerLibrary(name, name, SOURCE, placed);
        AtomicFiles.writeString(own.resolve(name + SUFFIX), writer.writeToString(library));
        return List.copyOf(placed);
    }
}
