package de.latzig.game.tob.client.logic.frontend;

import java.util.List;
import java.util.Optional;

import de.latzig.game.tob.client.logic.ui.ScrollBox;
import de.latzig.game.tob.core.progress.Profile;

/**
 * The Play window's list (Phase 5c-1 spec decision 2): row {@value #NEW_PROFILE} is "&lt; New Profile &gt;", rows 1
 * and below the profiles in the store's order (by name), each with its total score and group. New profiles are limited
 * to {@link ProfileStore#MAX_PROFILES}: with that many the first row is greyed and cannot be selected; more profiles
 * from older data stay listed and playable. The profile played last is preselected, else "&lt; New Profile &gt;" (or,
 * greyed, the first profile). Up and Down move without wrapping and skip a greyed row; {@link #ROWS} rows are shown
 * at a time ({@link #box}). Engine-free.
 */
public final class PlayModel {

    /** The rows shown at a time: "&lt; New Profile &gt;" and five profiles. */
    public static final int ROWS = ProfileStore.MAX_PROFILES + 1;
    /** The row of "&lt; New Profile &gt;". */
    public static final int NEW_PROFILE = 0;

    private final List<Profile> profiles;
    private final ScrollBox box = new ScrollBox(ROWS);
    private int selected;

    /**
     * @param profiles the store's profiles, in the order to show
     * @param last     the name of the profile played last, if any (ignoring case)
     */
    public PlayModel(List<Profile> profiles, Optional<String> last) {
        this.profiles = List.copyOf(profiles);
        box.size(rows());
        selected = newProfileEnabled() ? NEW_PROFILE : 1;
        for (int i = 0; i < this.profiles.size(); i++) {
            if (last.isPresent() && this.profiles.get(i).name().equalsIgnoreCase(last.get())) {
                selected = i + 1;
            }
        }
        box.show(selected);
    }

    public List<Profile> profiles() {
        return profiles;
    }

    /** The rows: "&lt; New Profile &gt;" and one per profile. */
    public int rows() {
        return profiles.size() + 1;
    }

    /** The selected row: {@link #NEW_PROFILE} or a profile's index + 1. */
    public int selected() {
        return selected;
    }

    /** Fewer than {@link ProfileStore#MAX_PROFILES} profiles: "&lt; New Profile &gt;" can be chosen. */
    public boolean newProfileEnabled() {
        return profiles.size() < ProfileStore.MAX_PROFILES;
    }

    /** Whether {@code row} exists and is not greyed. */
    public boolean selectable(int row) {
        return row == NEW_PROFILE ? newProfileEnabled() : row > NEW_PROFILE && row < rows();
    }

    /** Selects {@code row} (a click) and scrolls it into view; false if it is greyed or outside the list. */
    public boolean select(int row) {
        if (!selectable(row)) {
            return false;
        }
        selected = row;
        box.show(row);
        return true;
    }

    /** Moves the selection by {@code step} rows (Up −1, Down +1), over a greyed row; false at the ends. */
    public boolean move(int step) {
        for (int row = selected + step; row >= 0 && row < rows(); row += step) {
            if (selectable(row)) {
                return select(row);
            }
        }
        return false;
    }

    /** The selected profile; empty on "&lt; New Profile &gt;". */
    public Optional<Profile> selectedProfile() {
        return selected == NEW_PROFILE ? Optional.empty() : Optional.of(profiles.get(selected - 1));
    }

    /** The visible rows and the scroll bar (more than {@link #ROWS} rows only with older data). */
    public ScrollBox box() {
        return box;
    }
}
