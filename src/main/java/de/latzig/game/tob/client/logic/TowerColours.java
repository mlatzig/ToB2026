package de.latzig.game.tob.client.logic;

import de.latzig.game.tob.core.model.Palette;
import de.latzig.game.tob.core.model.RemakeVisuals;
import de.latzig.game.tob.core.model.Rgb12;
import de.latzig.game.tob.core.model.TowerDefinition;

/**
 * The colours of a tower's base geometry, sky and ground (0xRRGGBB). The sky comes from the original palette, as do
 * the defaults for towers without ToB 2009 look settings: pattern colours from the lit square tops (entries 6 and 3),
 * ground colour 1 from the unlit floor (entry 17), ground colour 2 from the lit floor (entry 16). Phase 4a: the
 * zenith is palette entry 8 and the horizon entry 7, as ToB 2009 draws them (its importer stores entry 7 as sky colour
 * 2 and 8 as sky colour 1, report 07 §6; {@code SkyBox} and ToB 2005 {@code sky.cpp} put sky colour 2 at the horizon
 * and use it as the fog colour; reference screenshots 01/02 of {@code original-000}: black horizon, blue zenith). The
 * ToB 2009 look settings ({@link RemakeVisuals}, the 117 original towers) override pattern, pattern colours, ground
 * colours 1 and 2, lift and sun colour. The floor pattern follows ToB 2009
 * {@code AbstractBasisElementRenderer.getColor} for every tower (Phase 4a decision 2, variant (a), confirmed at the
 * acceptance on 2026-10-10; the original table {@code L00BE1E} is not used). Phase 5e: as the Remake look's
 * {@link CellColours}, a cell's sides take its top colour and a lift is one colour.
 */
public record TowerColours(int skyTop, int skyHorizon, int ground, int ground2, int pattern, int pattern1,
        int pattern2, int lift, int sun) implements CellColours {

    /** ToB 2009 {@code PatternType} order. */
    public static final int CROSSED = 0;
    public static final int LINE_BY_LINE = 1;
    public static final int CROSSED_IN_HALF = 2;
    public static final int ONE_IN_FOUR = 3;
    public static final int ALTERNATED = 4;
    public static final int CROSS_FADED_BY_LINE = 5;
    public static final int CROSS_FADED_BY_LEVEL = 6;
    public static final int CROSS_FADED_BY_RADIUS = 7;

    /** Lift colour for towers without ToB 2009 look settings. */
    public static final int DEFAULT_LIFT = 0xC0C0C0;

    /** Colours drawn with the ToB 2009 pattern semantics; ground colour 2 equals ground colour 1. */
    public TowerColours(int skyTop, int skyHorizon, int ground, int pattern, int pattern1, int pattern2, int lift,
            int sun) {
        this(skyTop, skyHorizon, ground, ground, pattern, pattern1, pattern2, lift, sun);
    }

    public static TowerColours of(TowerDefinition tower) {
        Palette palette = tower.palette();
        int skyTop = rgb(palette.colours().get(8));
        int skyHorizon = rgb(palette.colours().get(7));
        if (tower.remake().isPresent()) {
            RemakeVisuals remake = tower.remake().get();
            return new TowerColours(skyTop, skyHorizon, remake.groundColor1(), remake.groundColor2(),
                    remake.pattern(), remake.patternColor1(), remake.patternColor2(), remake.liftColor(),
                    remake.sunColor());
        }
        return new TowerColours(skyTop, skyHorizon, rgb(palette.colours().get(17)), rgb(palette.colours().get(16)),
                CROSSED, rgb(palette.colours().get(6)), rgb(palette.colours().get(3)), DEFAULT_LIFT, 0xFFFFFF);
    }

    /** Colour of the floor or box in a cell (ToB 2009 grid of 16 × 16 × 6). */
    public int cellColour(int x, int y, int level) {
        return switch (pattern) {
            case LINE_BY_LINE -> pick((1 + y * 17 + level * 257) % 2);
            case CROSSED_IN_HALF -> pick((x / 8 + y * 17 + level * 257) % 2);
            case ONE_IN_FOUR -> ((x % 2) + (y % 2) == 0) ^ (level % 2 == 0) ? pattern1 : pattern2;
            case ALTERNATED -> level % 2 == 0 ? pattern1 : pattern2;
            case CROSS_FADED_BY_LINE -> mix(pattern1, pattern2, level % 2 == 0 ? 1f - y / 15f : y / 15f);
            case CROSS_FADED_BY_LEVEL -> mix(pattern1, pattern2, level / 5f);
            case CROSS_FADED_BY_RADIUS -> {
                float alpha = (float) Math.sqrt((x - 8) * (x - 8) + (y - 8) * (y - 8)) / 8f;
                yield mix(pattern1, pattern2, level % 2 == 0 ? 1f - alpha : alpha);
            }
            default -> pick((x + y * 17 + level * 257) % 2);
        };
    }

    /** Phase 5e: the Remake look's top colour is {@link #cellColour}. */
    @Override
    public int top(int x, int y, int level) {
        return cellColour(x, y, level);
    }

    @Override
    public int liftTop() {
        return lift;
    }

    private int pick(int index) {
        return index == 0 ? pattern1 : pattern2;
    }

    /** A 12-bit Amiga colour as 0xRRGGBB (each channel × 17). */
    public static int rgb(Rgb12 colour) {
        return (colour.r() * 17 << 16) | (colour.g() * 17 << 8) | colour.b() * 17;
    }

    /** Linear mix, {@code t} clamped to 0..1. */
    public static int mix(int a, int b, float t) {
        float f = Math.clamp(t, 0f, 1f);
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * f);
        int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * f);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * f);
        return (r << 16) | (g << 8) | bl;
    }
}
