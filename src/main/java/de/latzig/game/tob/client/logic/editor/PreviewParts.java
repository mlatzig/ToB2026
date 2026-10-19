package de.latzig.game.tob.client.logic.editor;

import java.util.EnumSet;
import java.util.Optional;
import java.util.OptionalInt;

import de.latzig.game.tob.client.logic.ClassicColours;
import de.latzig.game.tob.client.logic.Socket;
import de.latzig.game.tob.client.logic.TowerColours;
import de.latzig.game.tob.client.logic.effects.CloudTexture;
import de.latzig.game.tob.client.logic.effects.Terrain;
import de.latzig.game.tob.core.model.BaseType;
import de.latzig.game.tob.core.model.Cell;
import de.latzig.game.tob.core.model.Moon;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.Palette;
import de.latzig.game.tob.core.model.RemakeVisuals;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.model.TowerGrid;

/**
 * What the Tower Designer's 3D live preview builds from a tower, split into the three parts it rebuilds separately
 * (Phase 6b spec decision 3: every edit shows at once, rebuilt only where it changed): the sky (dome colours, stars,
 * moon, clouds), the terrain (ground, relief, the socket under the tower's footprint) and the world (the base
 * elements, the objects and the tower's lightning, meteors and haze). A part is rebuilt when its key differs from the
 * one shown. Every part depends on the tower id, the seed of the tower's random look. Title, author, the finishing
 * conditions and the behaviour change nothing that is shown; the sun's colour is set every frame.
 * <p>
 * The sky's key carries the whole {@link Moon} (heading, type, position, rotation and the Task 0 optional stored
 * {@link Moon#size()}), so a Moon Height or a stored Moon Size edit (Effects dialog) rebuilds the sky exactly like a
 * moon direction or type edit does, and touches no other part.
 * <p>
 * Final review M1: the ground and world keys carry only the {@link TowerColours} fields {@link Terrain}, {@link
 * Socket}, {@code BaseGeometry} and {@code BaseView} actually read from ({@link Ground#ground()}/
 * {@link Ground#ground2()}; {@link World#pattern()}/{@link World#pattern1()}/{@link World#pattern2()}/
 * {@link World#lift()}) — not the whole {@link TowerColours}, which also carries the sky and sun colours the
 * terrain and the world never use (the sun's colour is set every frame regardless of a rebuild) — so a Sky Color or
 * Sun Color edit (colour dialog) rebuilds only the sky.
 * <p>
 * Measured while planning (headless, the scene graph only; the first build of an element type also loads its assets,
 * up to about 250 ms once): the world 3-7 ms, the terrain 0.2 ms, the sky 1-2 ms plus 25-70 ms to generate a cloud
 * texture (towers with clouds only); the moon's textures are generated in the background.
 * <p>
 * The world is built from a world state as the game's ({@code WorldState.load}), which refuses a lift start without
 * its stop and a spider placed twice; the designer can produce both without the editor refusing: a lift placed and
 * marked ("Can't place lift here"), or a spider doubled by the Toolbox's Copy Level (it copies cells verbatim,
 * objects included) or by loading a library file written that way. {@link #viewable} turns the lift into a floor and
 * keeps only the first copy of a doubled spider (final review I1), in {@code WorldState.load}'s own scan order
 * (level ascending, row y descending, column x ascending), so the world can always be built from what the designer
 * shows.
 */
public record PreviewParts(Sky sky, Ground ground, World world) {

    /** The sky's key: the seed, the sky colours, the stars, the moon and the cloud cover. */
    public record Sky(String id, int skyTop, int skyHorizon, int stars, Moon moon, OptionalInt clouds) {
    }

    /** The terrain's key: the seed, the ground colours ({@link Terrain}, {@link Socket}), the relief and the
     * socket. */
    public record Ground(String id, int ground, int ground2, int height, Optional<Socket> socket) {
    }

    /** The world's key: the seed, the grid, the floor-pattern and lift colours ({@code BaseGeometry},
     * {@code BaseView}), the ambient effects and the relief meteors strike; Phase 5e: the palette in the Classic
     * Look (the floors' colours), null in the Remake look. */
    public record World(String id, TowerGrid grid, int pattern, int pattern1, int pattern2, int lift,
            boolean lightning, boolean meteor, boolean pulse, int height, Palette classicPalette) {
    }

    /** The parts of {@code tower} in the Remake look. */
    public static PreviewParts of(TowerDefinition tower) {
        return of(tower, false);
    }

    /**
     * The parts of {@code tower} in the Remake look or (Phase 5e, {@code classic}) the Classic Look: no clouds, the
     * flat ground in the palette's floor colour (no relief, no socket), the world in the palette's colours with the
     * original colour pattern, the meteors striking the flat ground.
     */
    public static PreviewParts of(TowerDefinition tower, boolean classic) {
        if (classic) {
            TowerColours colours = TowerColours.of(tower);
            ClassicColours original = ClassicColours.of(tower);
            return new PreviewParts(new Sky(tower.id(), colours.skyTop(), colours.skyHorizon(),
                    tower.effects().starDensity(), tower.moon(), OptionalInt.empty()),
                    new Ground(tower.id(), original.ground(), original.ground(), 0, Optional.empty()),
                    new World(tower.id(), tower.grid(), original.pattern(), 0, 0, 0, tower.effects().lightning(),
                            tower.effects().meteor(), tower.effects().bluePulse(), 0, tower.palette()));
        }
        TowerColours colours = TowerColours.of(tower);
        int height = tower.remake().map(RemakeVisuals::groundHeight).orElse(Terrain.DEFAULT_GROUND_HEIGHT);
        return new PreviewParts(new Sky(tower.id(), colours.skyTop(), colours.skyHorizon(),
                tower.effects().starDensity(), tower.moon(), CloudTexture.cover(tower)),
                new Ground(tower.id(), colours.ground(), colours.ground2(), height, Socket.of(tower.grid())),
                new World(tower.id(), tower.grid(), colours.pattern(), colours.pattern1(), colours.pattern2(),
                        colours.lift(), tower.effects().lightning(), tower.effects().meteor(),
                        tower.effects().bluePulse(), height, null));
    }

    /**
     * {@code tower} as the preview can load it: a lift start without its stop shows as a floor (with its object),
     * and only the first copy of a spider placed more than once keeps its object (the later copies lose it; their
     * base stays). The scan is {@code WorldState.load}'s own order (level ascending, row y descending, column x
     * ascending), so "first" means the same cell {@code WorldState.load} itself would have kept.
     */
    public static TowerDefinition viewable(TowerDefinition tower) {
        TowerGrid grid = tower.grid();
        TowerGrid.Builder builder = null;
        EnumSet<ObjectType> seenSpiders = EnumSet.noneOf(ObjectType.class);
        for (int level = 0; level < TowerGrid.LEVELS; level++) {
            for (int y = TowerGrid.DEPTH - 1; y >= 0; y--) {
                for (int x = 0; x < TowerGrid.WIDTH; x++) {
                    Cell cell = grid.cell(x, y, level);
                    boolean orphan = cell.base() == BaseType.LIFT_UP_START
                            && base(grid, x, y, level + 1) != BaseType.LIFT_UP_STOP
                            || cell.base() == BaseType.LIFT_DOWN_START
                                    && base(grid, x, y, level - 1) != BaseType.LIFT_DOWN_STOP;
                    boolean laterSpider = cell.hasObject() && cell.object().isSpider()
                            && !seenSpiders.add(cell.object());
                    if (orphan || laterSpider) {
                        builder = builder == null ? grid.toBuilder() : builder;
                        BaseType base = orphan ? BaseType.FLOOR : cell.base();
                        ObjectType object = laterSpider ? null : cell.object();
                        builder.set(x, y, level, new Cell(base, object));
                    }
                }
            }
        }
        return builder == null ? tower : EditorTowers.withGrid(tower, builder.build());
    }

    private static BaseType base(TowerGrid grid, int x, int y, int level) {
        return level < 0 || level >= TowerGrid.LEVELS ? BaseType.EMPTY : grid.cell(x, y, level).base();
    }
}
