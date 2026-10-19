package de.latzig.game.tob.client.logic.editor;

import java.util.List;
import java.util.Optional;

import de.latzig.game.tob.core.model.Behaviour;
import de.latzig.game.tob.core.model.Conditions;
import de.latzig.game.tob.core.model.Effects;
import de.latzig.game.tob.core.model.Lock;
import de.latzig.game.tob.core.model.Moon;
import de.latzig.game.tob.core.model.Palette;
import de.latzig.game.tob.core.model.RemakeVisuals;
import de.latzig.game.tob.core.model.Rgb12;
import de.latzig.game.tob.core.model.Sounds;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.model.TowerGrid;

/**
 * Towers of the Tower Designer (Phase 6b): a new tower's defaults, a tower with a new grid, a tower placed in its
 * library (group and index as the original's 13 groups of 9) and a tower under another id. Phase 6d decision 7: an
 * own tower keeps the id it got at creation ({@link TowerIds}); placing it never renumbers it. A new tower is
 * test-playable with these defaults (Phase 6b-1's interim defaults, kept in 6b-2, where the Settings and Effects
 * dialogs change them): no title, one klondike to collect, the time limit off (600 s kept), external cameras on,
 * medium time bombs, and the look of {@code original-000} (palette, moon, stars, sky and the ToB 2009 visuals).
 */
public final class EditorTowers {

    /** ToB 2009's library size (13 groups of 9, {@code SourceTowerLibrary.MAX_TOWERS}). */
    public static final int MAX_TOWERS = 117;
    /** The towers of a group (the original's 3 × 3). */
    public static final int GROUP = 9;

    /** {@code original-000}'s palette. */
    private static final List<String> PALETTE = List.of("000", "700", "900", "F00", "505", "707", "B0B", "000", "08C",
            "0B0", "0F0", "BB0", "FF0", "777", "BBB", "FFF", "077", "055");

    private EditorTowers() {
    }

    /** A new, empty tower with the interim defaults (see the class comment). */
    public static TowerDefinition blank(String id, int position) {
        return new TowerDefinition(id, position / GROUP, position % GROUP, "", "", new Conditions(600, false, 1, 0),
                new Behaviour(true, false, 1), new Effects(false, false, false, 3, 0, 1), new Moon(0, 0, 8, 28, 2),
                new Palette(PALETTE.stream().map(Rgb12::parseHex).toList()), new Sounds(0, true, true),
                new Lock(false, ""), TowerGrid.empty(), Optional.of(new RemakeVisuals(0xFFFFFF, 3, 0x004B4B,
                        0x006969, 6, 0xFFFFFF, false, false, 0, 0xA500A5, 0xE10000)));
    }

    /** {@code tower} with {@code grid}. */
    public static TowerDefinition withGrid(TowerDefinition tower, TowerGrid grid) {
        return new TowerDefinition(tower.id(), tower.group(), tower.index(), tower.title(), tower.author(),
                tower.conditions(), tower.behaviour(), tower.effects(), tower.moon(), tower.palette(), tower.sounds(),
                tower.lock(), grid, tower.remake());
    }

    /** {@code tower} at {@code position} of its library: its group and index; the id stays (Phase 6d). */
    public static TowerDefinition placed(TowerDefinition tower, int position) {
        return new TowerDefinition(tower.id(), position / GROUP, position % GROUP, tower.title(), tower.author(),
                tower.conditions(), tower.behaviour(), tower.effects(), tower.moon(), tower.palette(), tower.sounds(),
                tower.lock(), tower.grid(), tower.remake());
    }

    /** {@code tower} under the id {@code id} (Phase 6d decision 7: a copy, or a shipped tower becoming an own one). */
    public static TowerDefinition withId(TowerDefinition tower, String id) {
        return new TowerDefinition(id, tower.group(), tower.index(), tower.title(), tower.author(), tower.conditions(),
                tower.behaviour(), tower.effects(), tower.moon(), tower.palette(), tower.sounds(), tower.lock(),
                tower.grid(), tower.remake());
    }
}
