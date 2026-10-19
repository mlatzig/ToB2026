package de.latzig.game.tob.core.progress;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A player profile (Phase 5a spec decision 10): a named savegame with the library it plays, the current group of the
 * tower screen, the progress per tower id and the total score. Immutable; every change returns a new profile. A
 * completed tower is played again only as practice (decision 8): {@link #started} and {@link #ended} then return the
 * profile unchanged. Phase 5b decision 4: Backwards Allowed is a global option, no longer part of the profile.
 * <p>
 * Phase 6d (spec decision 5): the player's own towers are marked solved per own library ({@code customSolved}:
 * library name → tower ids), without score, attempts or practice; {@code customLibrary} is the own library the
 * Overview showed last (empty: the original towers). Neither touches the campaign's progress.
 */
public record Profile(String name, String library, int group, Map<String, TowerProgress> towers, long score,
        Map<String, Set<String>> customSolved, Optional<String> customLibrary) {

    /** The longest name (ToB 2009 {@code SavedGameConstants}: 16). */
    public static final int NAME_LENGTH = 16;

    /** The result of an attempt: the new profile and, for a first completion, its score. */
    public record Ended(Profile profile, Optional<TowerScore> score) {
    }

    public Profile {
        if (!isValidName(name)) {
            throw new IllegalArgumentException("invalid profile name: '" + name + "'");
        }
        Objects.requireNonNull(library, "library");
        if (group < 0 || group > Progression.LAST_GROUP) {
            throw new IllegalArgumentException("group out of range 0..12: " + group);
        }
        towers = Collections.unmodifiableMap(new TreeMap<>(towers));
        if (score < 0) {
            throw new IllegalArgumentException("negative score");
        }
        Map<String, Set<String>> solved = new TreeMap<>();
        customSolved.forEach((libraryName, ids) -> {
            if (!ids.isEmpty()) {
                solved.put(libraryName, Collections.unmodifiableSet(new TreeSet<>(ids)));
            }
        });
        customSolved = Collections.unmodifiableMap(solved);
        Objects.requireNonNull(customLibrary, "customLibrary");
    }

    /** A profile without own towers solved, showing the original towers. */
    public Profile(String name, String library, int group, Map<String, TowerProgress> towers, long score) {
        this(name, library, group, towers, score, Map.of(), Optional.empty());
    }

    /** Printable ASCII (spec §5: the bitmap font is ASCII only; umlauts and other characters are ignored). */
    public static boolean isNameChar(char c) {
        return c >= 0x20 && c <= 0x7E;
    }

    /** 1–16 printable ASCII characters, not starting with a space. */
    public static boolean isValidName(String name) {
        if (name == null || name.isEmpty() || name.length() > NAME_LENGTH || name.charAt(0) == ' ') {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            if (!isNameChar(name.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** A new profile: the first group, nothing done. */
    public static Profile create(String name, String library) {
        return new Profile(name, library, 0, Map.of(), 0L);
    }

    public TowerProgress progress(String towerId) {
        return towers.getOrDefault(towerId, TowerProgress.NONE);
    }

    /** A completed tower is replayed as practice. */
    public boolean isPractice(String towerId) {
        return progress(towerId).completed();
    }

    public int completedCount() {
        return (int) towers.values().stream().filter(TowerProgress::completed).count();
    }

    public Profile withGroup(int newGroup) {
        return new Profile(name, library, newGroup, towers, score, customSolved, customLibrary);
    }

    /** Phase 6d decision 5: whether the own tower {@code towerId} of the library {@code libraryName} is solved. */
    public boolean customSolved(String libraryName, String towerId) {
        return customSolved.getOrDefault(libraryName, Set.of()).contains(towerId);
    }

    /** Phase 6d decision 5: the own tower {@code towerId} of {@code libraryName} marked solved (no score). */
    public Profile withCustomSolved(String libraryName, String towerId) {
        Map<String, Set<String>> changed = new TreeMap<>(customSolved);
        Set<String> ids = new TreeSet<>(changed.getOrDefault(libraryName, Set.of()));
        ids.add(Objects.requireNonNull(towerId, "towerId"));
        changed.put(Objects.requireNonNull(libraryName, "libraryName"), ids);
        return new Profile(name, library, group, towers, score, changed, customLibrary);
    }

    /** Phase 6d decision 5: the Overview's library, an own library's name or empty for the original towers. */
    public Profile withCustomLibrary(Optional<String> libraryName) {
        return new Profile(name, library, group, towers, score, customSolved, libraryName);
    }

    /** An attempt starts (autosave point 1, ToB 2009): one more attempt; nothing in practice. */
    public Profile started(String towerId) {
        TowerProgress p = progress(towerId);
        if (p.completed()) {
            return this;
        }
        return with(towerId, new TowerProgress(p.attempts() + 1, p.counter(), false, 0, Optional.empty(),
                OptionalInt.empty()));
    }

    /**
     * An attempt ended (autosave point 2): a completion scores ({@link TowerScore#completed}) and adds to the total,
     * a restart adds 1 and a failure or abandon 2 to the retry counter; nothing in practice.
     *
     * @param survivors the player spiders present and alive at the end
     * @param frames    the simulation frames of the attempt
     */
    public Ended ended(String towerId, Outcome outcome, int survivors, int frames, Instant now) {
        TowerProgress p = progress(towerId);
        if (p.completed()) {
            return new Ended(this, Optional.empty());
        }
        return switch (outcome) {
            case COMPLETED -> {
                TowerScore towerScore = TowerScore.completed(p.counter(), survivors);
                Profile done = with(towerId, new TowerProgress(p.attempts(), p.counter(), true, towerScore.total(),
                        Optional.of(now), OptionalInt.of(frames)));
                yield new Ended(new Profile(name, library, group, done.towers, score + towerScore.total(),
                        customSolved, customLibrary), Optional.of(towerScore));
            }
            case RESTARTED -> new Ended(with(towerId, new TowerProgress(p.attempts(),
                    TowerScore.afterRestart(p.counter()), false, 0, Optional.empty(), OptionalInt.empty())),
                    Optional.empty());
            case FAILED -> new Ended(with(towerId, new TowerProgress(p.attempts(),
                    TowerScore.afterFailure(p.counter()), false, 0, Optional.empty(), OptionalInt.empty())),
                    Optional.empty());
        };
    }

    private Profile with(String towerId, TowerProgress progress) {
        Map<String, TowerProgress> changed = new TreeMap<>(towers);
        changed.put(towerId, progress);
        return new Profile(name, library, group, changed, score, customSolved, customLibrary);
    }
}
