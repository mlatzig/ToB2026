package de.latzig.game.tob.client.logic.frontend;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.progress.Profile;
import de.latzig.game.tob.core.progress.Progression;

/**
 * The standard game (Phase 5a spec decision 7): the 117 original towers in 13 groups of 9 by their {@code group}
 * (0–12) and {@code index} (0–8), and the original's access rules ({@link Progression}) for a profile. A completed
 * tower stays playable as practice (decision 8). Tower lock and password flags are designer data and ignored.
 */
public final class Campaign {

    /** Where a tower is: its group and its index A–I. */
    public record Location(int group, int index) {
    }

    private final TowerDefinition[][] towers;
    private final Map<String, Location> locations = new HashMap<>();

    private Campaign(TowerDefinition[][] towers) {
        this.towers = towers;
        for (int g = 0; g < Progression.GROUPS; g++) {
            for (int i = 0; i < Progression.TOWERS_PER_GROUP; i++) {
                locations.put(towers[g][i].id(), new Location(g, i));
            }
        }
    }

    /**
     * @throws IllegalArgumentException unless every group/index slot holds exactly one tower
     */
    public static Campaign of(List<TowerDefinition> library) {
        TowerDefinition[][] slots = new TowerDefinition[Progression.GROUPS][Progression.TOWERS_PER_GROUP];
        for (TowerDefinition tower : library) {
            if (tower.group() >= Progression.GROUPS || tower.index() >= Progression.TOWERS_PER_GROUP) {
                throw new IllegalArgumentException(tower.id() + " is outside 13 x 9: " + tower.group() + "/"
                        + tower.index());
            }
            if (slots[tower.group()][tower.index()] != null) {
                throw new IllegalArgumentException("two towers at " + tower.group() + "/" + tower.index());
            }
            slots[tower.group()][tower.index()] = tower;
        }
        for (int g = 0; g < Progression.GROUPS; g++) {
            for (int i = 0; i < Progression.TOWERS_PER_GROUP; i++) {
                if (slots[g][i] == null) {
                    throw new IllegalArgumentException("no tower at " + g + "/" + i);
                }
            }
        }
        return new Campaign(slots);
    }

    public TowerDefinition tower(int group, int index) {
        return towers[group][index];
    }

    /** The tower's 1-based number (1–117), as ToB 2009's Overview list shows it ({@code ordinal + 1}). */
    public int number(int group, int index) {
        return group * Progression.TOWERS_PER_GROUP + index + 1;
    }

    public Optional<Location> locate(String towerId) {
        return Optional.ofNullable(locations.get(towerId));
    }

    public int doneMask(Profile profile, int group) {
        boolean[] completed = new boolean[Progression.TOWERS_PER_GROUP];
        for (int i = 0; i < completed.length; i++) {
            completed[i] = profile.progress(towers[group][i].id()).completed();
        }
        return Progression.doneMask(completed);
    }

    public int accessibleMask(Profile profile, int group) {
        return Progression.accessibleMask(doneMask(profile, group));
    }

    /** Accessible, or completed and replayed as practice. */
    public boolean isPlayable(Profile profile, int group, int index) {
        int bit = 1 << index;
        return ((accessibleMask(profile, group) | doneMask(profile, group)) & bit) != 0;
    }

    /** The first accessible (not completed) tower of the group in A–I order, or −1. */
    public int nextIndex(Profile profile, int group) {
        int open = accessibleMask(profile, group);
        return open == 0 ? -1 : Integer.numberOfTrailingZeros(open);
    }

    public int completedCount(Profile profile) {
        int count = 0;
        for (Location location : locations.values()) {
            if (profile.progress(towers[location.group()][location.index()].id()).completed()) {
                count++;
            }
        }
        return count;
    }

    public boolean allSolved(Profile profile) {
        return completedCount(profile) == Progression.TOWERS;
    }
}
