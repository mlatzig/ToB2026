package de.latzig.game.tob.client.logic.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.function.IntUnaryOperator;

import de.latzig.game.tob.client.logic.Texts;
import de.latzig.game.tob.client.logic.TowerColours;
import de.latzig.game.tob.client.logic.editor.EditorForm.Field;
import de.latzig.game.tob.client.logic.effects.MoonDesign;
import de.latzig.game.tob.client.logic.effects.MoonPlacement;
import de.latzig.game.tob.client.logic.effects.StarField;
import de.latzig.game.tob.client.logic.effects.Terrain;
import de.latzig.game.tob.core.model.Effects;
import de.latzig.game.tob.core.model.Moon;
import de.latzig.game.tob.core.model.Palette;
import de.latzig.game.tob.core.model.RemakeVisuals;
import de.latzig.game.tob.core.model.Rgb12;
import de.latzig.game.tob.core.model.TowerDefinition;

/**
 * The Tower Designer's "Effects" dialog (Phase 6b spec decision 8; ToB 2009's {@code OptionsEffectsDialog},
 * {@code J:editor/dialogs/OptionsEffectsDialog.java:196-442}, applied by {@code OptionsEffectsAction}): Terrain (Sky
 * Colors, Sun Color, the moon, Ground Colors, Ground Height, Stars), Tower (Pattern, Pattern Colors, Lift Color) and
 * Additional FX (Lightning?, Meteor?, Pulse?, Clouds), Randomize Colors, OK and Cancel. ToB 2026's changes (spec
 * decision 8, the author's answer to the plan's question 3): Sun Size is gone (no visible sun; the tower keeps its
 * value), its row and ToB 2009's "Moons" count became the moon fields of the tower model: Moon, the direction (None,
 * North, East, South, West: {@code moon.heading}); Moon Type, the original type 0-5 ({@code moon.type}, 4b-3's
 * looks); Moon Height, the height of a normal moon above the horizon ({@value #MAX_MOON_HEIGHT} − {@code moon.y},
 * {@code moon.y} 0-55 the range {@link MoonPlacement} uses, so + raises the moon); and Moon Size, the size class
 * ({@link MoonDesign.SizeClass}, stored in {@code moon.size}). Without a stored size the field shows the class 4b-3
 * draws for the chosen type from the tower's seed and follows the type; a size the author chooses is stored and
 * stays. The horizontal position {@code moon.x} is not shown and kept. The sky colours are the original palette's
 * entries 8 (zenith) and 7 (horizon), 4 bits per channel ({@link TowerColours}); the stars are ToB 2009's star
 * values of the original's densities 0-3 ({@link StarField}). Unlike ToB 2009's OK, which forgot the stars and the
 * moons, OK writes every changed field, and only those.
 */
public final class EffectsDialog implements TowerDialog {

    public static final int SKY_1 = 0;
    public static final int SKY_2 = 1;
    public static final int SUN = 2;
    public static final int MOON = 3;
    public static final int MOON_TYPE = 4;
    public static final int MOON_HEIGHT = 5;
    public static final int MOON_SIZE = 6;
    public static final int GROUND_1 = 7;
    public static final int GROUND_2 = 8;
    public static final int GROUND_HEIGHT = 9;
    public static final int STARS = 10;
    public static final int PATTERN = 11;
    public static final int PATTERN_1 = 12;
    public static final int PATTERN_2 = 13;
    public static final int LIFT = 14;
    public static final int LIGHTNING = 15;
    public static final int METEOR = 16;
    public static final int PULSE = 17;
    public static final int CLOUDS = 18;
    /** The palette entries of sky colours 1 (zenith) and 2 (horizon). */
    public static final int SKY_1_ENTRY = 8;
    public static final int SKY_2_ENTRY = 7;
    /** The moon types that occur (6 and 7 never do and look like type 0). */
    public static final int MOON_TYPES = 6;
    /** The largest moon y {@link MoonPlacement} uses; Moon Height is this minus {@code moon.y}. */
    public static final int MAX_MOON_HEIGHT = 55;
    /** ToB 2009's {@code SourceTowerConstants.MAX_GROUNDHEIGHT}. */
    public static final int MAX_GROUND_HEIGHT = 10;
    /** The sun size a tower without ToB 2009 look settings gets when they are written (ToB 2009's default). */
    public static final int DEFAULT_SUN_SIZE = 3;

    private final long seed;
    private final int rawType;
    private final boolean sizeStored;
    private final EditorForm form;
    /** The size the Moon Size field shows unless one is chosen: the stored one, else the seed's for the type. */
    private int shownSize;

    public EffectsDialog(TowerDefinition tower, Texts texts) {
        this.seed = tower.id().hashCode();
        TowerColours colours = TowerColours.of(tower);
        Moon moon = tower.moon();
        this.rawType = moon.type();
        this.sizeStored = moon.size().isPresent();
        this.shownSize = moon.size().orElseGet(() -> seedSize(moon.type()));
        Effects effects = tower.effects();
        int height = tower.remake().map(RemakeVisuals::groundHeight).orElse(Terrain.DEFAULT_GROUND_HEIGHT);
        int clouds = tower.remake().map(r -> r.heavyClouds() ? 2 : r.clouds() ? 1 : 0).orElse(0);
        List<String> types = new ArrayList<>();
        for (int type = 0; type < MOON_TYPES; type++) {
            types.add(texts.get("editor.effects.moon.type" + type));
        }
        List<String> sizes = new ArrayList<>();
        for (MoonDesign.SizeClass size : MoonDesign.SizeClass.values()) {
            sizes.add(texts.get("editor.effects.size." + size.name().toLowerCase(Locale.ROOT)));
        }
        List<String> patterns = new ArrayList<>();
        for (int pattern = 0; pattern <= TowerColours.CROSS_FADED_BY_RADIUS; pattern++) {
            patterns.add(texts.get("editor.effects.pattern.type" + pattern));
        }
        form = new EditorForm.Builder()
                .group("editor.effects.terrain")
                .row("editor.effects.sky", Field.colour("editor.effects.sky1", colours.skyTop(), true),
                        Field.colour("editor.effects.sky2", colours.skyHorizon(), true))
                .row("editor.effects.sun", Field.colour("editor.effects.sun", colours.sun(), false))
                .row("editor.effects.moon", Field.choice("editor.effects.moon", moon.present() && moon.heading() < 4
                        ? moon.heading() + 1 : 0, List.of(texts.get("editor.effects.moon.none"),
                                texts.get("editor.toolbox.north"), texts.get("editor.toolbox.east"),
                                texts.get("editor.toolbox.south"), texts.get("editor.toolbox.west"))))
                .row("editor.effects.moon_type", Field.choice("editor.effects.moon_type", moon.type(), types))
                .row("editor.effects.moon_height", Field.number("editor.effects.moon_height",
                        MAX_MOON_HEIGHT - Math.min(moon.y(), MAX_MOON_HEIGHT), 0, MAX_MOON_HEIGHT, 1))
                .row("editor.effects.moon_size", Field.choice("editor.effects.moon_size", shownSize, sizes))
                .row("editor.effects.ground", Field.colour("editor.effects.ground1", colours.ground(), false),
                        Field.colour("editor.effects.ground2", colours.ground2(), false))
                .row("editor.effects.ground_height", Field.number("editor.effects.ground_height", height, 0,
                        MAX_GROUND_HEIGHT, 1))
                .row("editor.effects.stars", Field.choice("editor.effects.stars", effects.starDensity(),
                        List.of("0", "34", "67", "100")))
                .group("editor.effects.tower")
                .row("editor.effects.pattern", Field.choice("editor.effects.pattern", colours.pattern(), patterns))
                .row("editor.effects.pattern_colours", Field.colour("editor.effects.pattern1", colours.pattern1(),
                        false), Field.colour("editor.effects.pattern2", colours.pattern2(), false))
                .row("editor.effects.lift", Field.colour("editor.effects.lift", colours.lift(), false))
                .group("editor.effects.fx")
                .row("", Field.check("editor.effects.lightning", effects.lightning()))
                .row("", Field.check("editor.effects.meteor", effects.meteor()))
                .row("", Field.check("editor.effects.pulse", effects.bluePulse()))
                .row("editor.effects.clouds", Field.choice("editor.effects.clouds", clouds,
                        List.of(texts.get("editor.effects.clouds.none"), texts.get("editor.effects.clouds.normal"),
                                texts.get("editor.effects.clouds.heavy"))))
                .build();
        refresh();
    }

    @Override
    public String captionKey() {
        return "editor.command.effects";
    }

    @Override
    public EditorForm form() {
        return form;
    }

    /** The size class 4b-3 draws for {@code type} from the tower's seed ({@link MoonDesign.SizeClass} ordinal). */
    private int seedSize(int type) {
        return MoonDesign.design(type, seed, 0f, 0f).size().ordinal();
    }

    /** The type the moon shows: the chosen one, or the tower's raw type (6 and 7 included) while unchanged. */
    private int moonType() {
        return form.field(MOON_TYPE).changed() ? form.field(MOON_TYPE).value() : rawType;
    }

    /** Whether the author chose a size other than the one shown by default (only then is it stored). */
    private boolean sizeChosen() {
        return form.field(MOON_SIZE).value() != shownSize;
    }

    /**
     * The Moon Size field follows the type: without a stored size and while no size is chosen, it shows what 4b-3
     * draws for the chosen type from the tower's seed.
     */
    @Override
    public void refresh() {
        if (!sizeStored && !sizeChosen()) {
            shownSize = seedSize(moonType());
            form.field(MOON_SIZE).value(shownSize);
        }
    }

    @Override
    public boolean randomizable() {
        return true;
    }

    /**
     * ToB 2009's "Randomize Colors" ({@code OptionsEffectsDialog.RandomizeColorsAction},
     * {@code J:editor/dialogs/OptionsEffectsDialog.java:580-657}): a light sky colour 1 and sky colour 2 around it, a
     * bright sun (each channel 191 or 255, not all 191), ground colour 1 and colour 2 around it, pattern colour 1 and
     * colour 2 around it with two channels swapped, and a lift colour of 63 or 255 per channel (not all 63). The sky
     * colours are kept to 4 bits per channel (the original palette).
     */
    @Override
    public void randomize(Random random) {
        int r = random.nextInt(128) + 128;
        int g = random.nextInt(128) + 128;
        int b = random.nextInt(128) + 128;
        colour(SKY_1, r, g, b);
        colour(SKY_2, random.nextInt(128) + r - 128, random.nextInt(128) + g - 128, random.nextInt(128) + b - 128);
        do {
            r = random.nextInt(2) * 64 + 191;
            g = random.nextInt(2) * 64 + 191;
            b = random.nextInt(2) * 64 + 191;
        } while (r == 191 && g == 191 && b == 191);
        colour(SUN, r, g, b);
        r = random.nextInt(192) + 32;
        g = random.nextInt(192) + 32;
        b = random.nextInt(192) + 32;
        colour(GROUND_1, r, g, b);
        colour(GROUND_2, random.nextInt(64) + r - 32, random.nextInt(64) + g - 32, random.nextInt(64) + b - 32);
        r = random.nextInt(192) + 32;
        g = random.nextInt(192) + 32;
        b = random.nextInt(192) + 32;
        colour(PATTERN_1, r, g, b);
        r = random.nextInt(64) + r - 32;
        g = random.nextInt(64) + g - 32;
        b = random.nextInt(64) + b - 32;
        int[] c = {r, g, b};
        switch (random.nextInt(3)) {
            case 0 -> swap(c, 0, 2);
            case 1 -> swap(c, 0, 1);
            default -> swap(c, 1, 2);
        }
        colour(PATTERN_2, c[0], c[1], c[2]);
        do {
            r = random.nextInt(2) * 192 + 63;
            g = random.nextInt(2) * 192 + 63;
            b = random.nextInt(2) * 192 + 63;
        } while (r == 63 && g == 63 && b == 63);
        colour(LIFT, r, g, b);
    }

    private static void swap(int[] c, int i, int j) {
        int t = c[i];
        c[i] = c[j];
        c[j] = t;
    }

    private void colour(int field, int r, int g, int b) {
        int rgb = r << 16 | g << 8 | b;
        form.field(field).value(form.field(field).twelveBit() ? twelveBit(rgb) : rgb);
    }

    /** {@code rgb} with each channel rounded to the nearest of the 16 levels of a 12-bit colour (× 17). */
    public static int twelveBit(int rgb) {
        return TowerColours.rgb(rgb12(rgb));
    }

    /** {@code rgb} as a 12-bit colour, each channel rounded. */
    public static Rgb12 rgb12(int rgb) {
        return new Rgb12(Math.round(((rgb >> 16) & 0xFF) / 17f), Math.round(((rgb >> 8) & 0xFF) / 17f),
                Math.round((rgb & 0xFF) / 17f));
    }

    @Override
    public TowerDefinition apply(TowerDefinition tower) {
        if (form.fields().stream().noneMatch(Field::changed)) {
            return tower;
        }
        Palette palette = tower.palette();
        if (changed(SKY_1, SKY_2)) {
            List<Rgb12> colours = new ArrayList<>(palette.colours());
            colours.set(SKY_1_ENTRY, rgb12(form.field(SKY_1).value()));
            colours.set(SKY_2_ENTRY, rgb12(form.field(SKY_2).value()));
            palette = new Palette(colours);
        }
        Moon m = tower.moon();
        Moon moon = changed(MOON, MOON_TYPE, MOON_HEIGHT) || sizeChosen() ? new Moon(
                value(MOON, m.heading(), v -> v == 0 ? Moon.NO_MOON : v - 1), value(MOON_TYPE, m.type(), v -> v),
                m.x(), value(MOON_HEIGHT, m.y(), v -> MAX_MOON_HEIGHT - v), m.rotation(),
                sizeChosen() ? Optional.of(form.field(MOON_SIZE).value()) : m.size()) : m;
        Effects e = tower.effects();
        Effects effects = changed(LIGHTNING, METEOR, PULSE, STARS) ? new Effects(form.field(LIGHTNING).on(),
                form.field(METEOR).on(), form.field(PULSE).on(), form.field(STARS).value(), e.colourPattern(),
                e.skyStyle()) : e;
        Optional<RemakeVisuals> remake = tower.remake();
        if (changed(SUN, GROUND_1, GROUND_2, GROUND_HEIGHT, PATTERN, PATTERN_1, PATTERN_2, LIFT, CLOUDS)) {
            int clouds = form.field(CLOUDS).value();
            remake = Optional.of(new RemakeVisuals(form.field(SUN).value(),
                    remake.map(RemakeVisuals::sunSize).orElse(DEFAULT_SUN_SIZE), form.field(GROUND_1).value(),
                    form.field(GROUND_2).value(), form.field(GROUND_HEIGHT).value(), form.field(LIFT).value(),
                    clouds == 1, clouds == 2, form.field(PATTERN).value(), form.field(PATTERN_1).value(),
                    form.field(PATTERN_2).value()));
        }
        return new TowerDefinition(tower.id(), tower.group(), tower.index(), tower.title(), tower.author(),
                tower.conditions(), tower.behaviour(), effects, moon, palette, tower.sounds(), tower.lock(),
                tower.grid(), remake);
    }

    private boolean changed(int... fields) {
        for (int field : fields) {
            if (form.field(field).changed()) {
                return true;
            }
        }
        return false;
    }

    private int value(int field, int unchanged, IntUnaryOperator map) {
        return form.field(field).changed() ? map.applyAsInt(form.field(field).value()) : unchanged;
    }
}
