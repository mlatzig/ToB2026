package de.latzig.game.tob.client.logic;

import de.latzig.game.tob.core.model.Palette;
import de.latzig.game.tob.core.model.TowerDefinition;

/**
 * Phase 5e (Classic Look, decision 3): the base geometry and the ground in the original's colours. A floor square or
 * box uses colour set 1 (palette entries 1 side, 3 lit top) or set 2 (4 side, 6 lit top) as the original's colour
 * pattern table {@code L00BE1E} says (report 07 §3.3, {@code S:10199-10240}; the tower's {@code colourPattern} 0–4,
 * the ToB 2009 patterns 5–7 have no original and draw as 0): 32 bytes per pattern, 4 levels × 8 rows (z, from the
 * south), bit x (from the west) set = set 2; patterns 1, 2 and 4 change with the level, 0 and 3 do not (the table,
 * {@code S:10199-10218}; report 07 §3.3 calls pattern 2 level-independent, which the table contradicts). The
 * original's coordinates are the grid's (x − 4, 11 − y) and repeat every 8 cells and 4 levels beyond the original's
 * 8 × 8 × 4. The tops are the lit entries (decision 6: the scene's sun and shadows darken them). A lift platform is
 * the fixed greys 14 (top) and 13 (rim, report 07 §6); the ground is the lit floor, entry 16 (the reference
 * screenshot's yellow plane). The ToB 2009 look settings are not used. These colours are the tower's own palette
 * (the objects' static classic textures are not: rework, author 2026-10-16).
 *
 * @param palette the tower's palette
 * @param pattern the original colour pattern 0–4
 */
public record ClassicColours(Palette palette, int pattern) implements CellColours {

    /** {@code L00BE1E}: per pattern 4 levels × 8 rows, bit x = set 2. */
    private static final int[][] PATTERNS = {
        {0xAA, 0x55, 0xAA, 0x55, 0xAA, 0x55, 0xAA, 0x55, 0xAA, 0x55, 0xAA, 0x55, 0xAA, 0x55, 0xAA, 0x55,
            0xAA, 0x55, 0xAA, 0x55, 0xAA, 0x55, 0xAA, 0x55, 0xAA, 0x55, 0xAA, 0x55, 0xAA, 0x55, 0xAA, 0x55},
        {0xFF, 0x00, 0xFF, 0x00, 0xFF, 0x00, 0xFF, 0x00, 0x00, 0xFF, 0x00, 0xFF, 0x00, 0xFF, 0x00, 0xFF,
            0xFF, 0x00, 0xFF, 0x00, 0xFF, 0x00, 0xFF, 0x00, 0x00, 0xFF, 0x00, 0xFF, 0x00, 0xFF, 0x00, 0xFF},
        {0xF0, 0x0F, 0xF0, 0x0F, 0xF0, 0x0F, 0xF0, 0x0F, 0x0F, 0xF0, 0x0F, 0xF0, 0x0F, 0xF0, 0x0F, 0xF0,
            0xF0, 0x0F, 0xF0, 0x0F, 0xF0, 0x0F, 0xF0, 0x0F, 0x0F, 0xF0, 0x0F, 0xF0, 0x0F, 0xF0, 0x0F, 0xF0},
        {0xFF, 0xAA, 0xFF, 0xAA, 0xFF, 0xAA, 0xFF, 0xAA, 0xFF, 0xAA, 0xFF, 0xAA, 0xFF, 0xAA, 0xFF, 0xAA,
            0xFF, 0xAA, 0xFF, 0xAA, 0xFF, 0xAA, 0xFF, 0xAA, 0xFF, 0xAA, 0xFF, 0xAA, 0xFF, 0xAA, 0xFF, 0xAA},
        {0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00}};

    public ClassicColours {
        if (pattern < 0 || pattern >= PATTERNS.length) {
            throw new IllegalArgumentException("original colour pattern 0..4: " + pattern);
        }
    }

    public static ClassicColours of(TowerDefinition tower) {
        int pattern = tower.effects().colourPattern();
        return new ClassicColours(tower.palette(), pattern < PATTERNS.length ? pattern : 0);
    }

    /** Whether the cell at grid ({@code x}, {@code y}, {@code level}) uses colour set 2 in {@code pattern}. */
    public static boolean usesSet2(int pattern, int x, int y, int level) {
        int row = PATTERNS[pattern][Math.floorMod(level, 4) * 8 + Math.floorMod(11 - y, 8)];
        return (row >> Math.floorMod(x - 4, 8) & 1) != 0;
    }

    @Override
    public int top(int x, int y, int level) {
        return entry(usesSet2(pattern, x, y, level) ? 6 : 3);
    }

    @Override
    public int side(int x, int y, int level) {
        return entry(usesSet2(pattern, x, y, level) ? 4 : 1);
    }

    @Override
    public int liftTop() {
        return entry(14);
    }

    @Override
    public int liftSide() {
        return entry(13);
    }

    /** The palette's sRGB colours are drawn as linear vertex colours (controller gamma rule). */
    @Override
    public boolean linear() {
        return true;
    }

    /** The flat ground's colour: the lit floor, entry 16 (sRGB; as a plain material colour see {@link SrgbColours}). */
    public int ground() {
        return entry(16);
    }

    /** Palette entry {@code index} (0–17) as 0xRRGGBB. */
    private int entry(int index) {
        return TowerColours.rgb(palette.colours().get(index));
    }
}
