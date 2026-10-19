package de.latzig.game.tob.client.logic;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import de.latzig.game.tob.core.model.TowerGrid;
import de.latzig.game.tob.core.sim.CellPos;
import de.latzig.game.tob.core.sim.Direction;
import de.latzig.game.tob.core.sim.Lift;
import de.latzig.game.tob.core.sim.SimEvent;
import de.latzig.game.tob.core.sim.WorldState;

/**
 * Generated floor, box and lift geometry (ToB 2009 basis renderers, {@code AbstractBasisElementRenderer} and
 * {@code BasisElement*Renderer}; 1 field = 32 units). A floor is a 2-unit slab at the bottom of its cell, a box a
 * full cube, a lift a column in the lower shaft cell whose top is the platform: 2 units above the cell bottom at the
 * bottom stop, one level higher at the top. Hidden faces are removed where a neighbour covers them completely: floor
 * edges against floors and boxes, box sides against boxes, a box top under a floor or box, a floor or box bottom over
 * a box. Lift cells take no part in the level meshes and are never hidden. UVs use the STATIC texture quadrants (top
 * and bottom rotated per cell like the sides, under the same south/north z-flip between ToB 2009's local coordinates
 * and this grid's z axis, {@code lz = 32} is grid north / {@code z0}, {@code lz = 0} is grid south / {@code z0 + 1});
 * bottoms are 25 % darker. Box sides pair U with height and V with the horizontal position along the wall, rotated
 * the same way ({@code rotate1} for north/south, {@code rotate2} for east/west), as {@code BasisElementBOXRenderer}'s
 * side calls do. Floor edges are a 1/32 strip (U horizontal, V height) shifted by {@code rotation/32} per cell, as
 * {@code BasisElementFLRRenderer}'s side calls do. A lift draws a cell-coloured shaft (fixed rotation 1, independent
 * of the cell's own rotation) up to the platform, capped by a lift-coloured 2-unit rim using the same geometry and
 * UVs as a floor edge, as {@code BasisElementLFTRenderer} does; its bottom is the darkened cell colour, not the lift
 * colour.
 */
public final class BaseGeometry {

    public static final float FLOOR_HEIGHT = 2f / 32f;
    private static final int[] ROTATION_ORDER = {0, 1, 2, 3, 1, 3, 2};
    private static final float STRIP = 1f / 32f;

    /** What a cell contributes to the level meshes. */
    public enum Kind {
        EMPTY, FLOOR, BOX, LIFT
    }

    private BaseGeometry() {
    }

    public static Kind kind(WorldState world, CellPos pos) {
        if (!pos.inGrid()) {
            return Kind.EMPTY;
        }
        if (world.isLift(pos)) {
            return Kind.LIFT;
        }
        if (world.isBlocked(pos)) {
            return Kind.BOX;
        }
        return world.hasFloor(pos) ? Kind.FLOOR : Kind.EMPTY;
    }

    /**
     * All floors and boxes of one level; Phase 5e: tops and sides in their own {@link CellColours}, the vertex
     * colours linear if {@link CellColours#linear} says so.
     */
    public static MeshArrays level(WorldState world, int level, CellColours colours) {
        Builder out = new Builder(colours.linear());
        for (int y = 0; y < TowerGrid.DEPTH; y++) {
            for (int x = 0; x < TowerGrid.WIDTH; x++) {
                CellPos pos = new CellPos(x, y, level);
                Kind kind = kind(world, pos);
                if (kind == Kind.FLOOR || kind == Kind.BOX) {
                    cell(world, pos, kind, colours.top(x, y, level), colours.side(x, y, level), out);
                }
            }
        }
        return out.build();
    }

    /**
     * The column of a lift at platform height {@code ratio} (0 bottom, 1 top), in the lower shaft cell
     * ({@code BasisElementLFTRenderer}): a shaft in {@code cellColour} from the cell bottom to the platform, capped
     * by a 2-unit platform rim in {@code liftColour}. The rim is geometrically and texture-wise a floor edge (the
     * same {@code floorEdgeUv}) shifted up to the platform height; the shaft always uses the source's fixed
     * rotation 1 (not {@code rotate1}/{@code rotate2}) with a ratio-scaled V, the same for all 4 sides. The bottom
     * (skipped over a box below) is the darkened cell colour, not the lift colour; the top is the undarkened lift
     * colour. Raw sRGB vertex colours, no gamma conversion ({@code linear = false}); tests only — scenes use
     * {@link #lift(Lift, float, boolean, CellColours)}, which honours {@link CellColours#linear()}.
     */
    static MeshArrays lift(Lift lift, float ratio, boolean boxBelow, int liftColour, int cellColour) {
        return lift(lift, ratio, boxBelow, liftColour, liftColour, cellColour, cellColour);
    }

    /**
     * Phase 5e: the lift column with the platform's top in {@code liftTop} and its rim in {@code liftSide}, the shaft
     * in {@code cellSide} and the bottom the darkened {@code cellTop}. Raw sRGB vertex colours, no gamma conversion
     * ({@code linear = false}); tests only — scenes use {@link #lift(Lift, float, boolean, CellColours)}, which
     * honours {@link CellColours#linear()}.
     */
    static MeshArrays lift(Lift lift, float ratio, boolean boxBelow, int liftTop, int liftSide, int cellTop,
            int cellSide) {
        return lift(lift, ratio, boxBelow, liftTop, liftSide, cellTop, cellSide, false);
    }

    /**
     * Phase 5e: the lift column in {@code colours} (the platform's top and rim, the lower shaft cell's top and side
     * colours), the vertex colours linear if {@link CellColours#linear} says so (controller gamma rule).
     */
    public static MeshArrays lift(Lift lift, float ratio, boolean boxBelow, CellColours colours) {
        CellPos cell = lift.lower();
        return lift(lift, ratio, boxBelow, colours.liftTop(), colours.liftSide(),
                colours.top(cell.x(), cell.y(), cell.level()), colours.side(cell.x(), cell.y(), cell.level()),
                colours.linear());
    }

    private static MeshArrays lift(Lift lift, float ratio, boolean boxBelow, int liftTop, int liftSide, int cellTop,
            int cellSide, boolean linear) {
        CellPos cell = lift.lower();
        Builder out = new Builder(linear);
        float x0 = cell.x() - 0.5f;
        float z0 = cell.y() - 0.5f;
        float bottom = cell.level();
        float clampedRatio = Math.clamp(ratio, 0f, 1f);
        float shaftTop = bottom + clampedRatio;
        float platformTop = shaftTop + FLOOR_HEIGHT;
        int rotate1 = rotation(cell, 0);
        int rotate2 = rotation(cell, 2);
        out.top(x0, z0, platformTop, liftTop, rotate1);
        if (!boxBelow) {
            out.bottom(x0, z0, bottom, darker(cellTop), rotate1);
        }
        float[] shaft = side(0.5f, 1f, 0f, 0.5f * clampedRatio);
        out.northSide(x0, z0, bottom, shaftTop, cellSide, shaft);
        out.eastSide(x0, z0, bottom, shaftTop, cellSide, shaft);
        out.southSide(x0, z0, bottom, shaftTop, cellSide, shaft);
        out.westSide(x0, z0, bottom, shaftTop, cellSide, shaft);
        out.northSide(x0, z0, shaftTop, platformTop, liftSide, floorEdgeUv(0.5f, 0f, 0.5f + STRIP, 0.5f, rotate1));
        out.eastSide(x0, z0, shaftTop, platformTop, liftSide, floorEdgeUv(0f, 0.5f, 0.5f, 0.5f + STRIP, rotate2));
        out.southSide(x0, z0, shaftTop, platformTop, liftSide, floorEdgeUv(1f, 0.5f, 0.5f + STRIP, 0.5f, rotate1));
        out.westSide(x0, z0, shaftTop, platformTop, liftSide, floorEdgeUv(0.5f, 1f, 0.5f, 0.5f + STRIP, rotate2));
        return out.build();
    }

    /** Levels whose meshes a step's {@link SimEvent.FloorEaten} events change (the cell's level and its neighbours). */
    public static Set<Integer> dirtyLevels(List<SimEvent> events) {
        Set<Integer> levels = new TreeSet<>();
        for (SimEvent event : events) {
            if (event instanceof SimEvent.FloorEaten eaten) {
                int level = eaten.cell().level();
                for (int l = Math.max(0, level - 1); l <= Math.min(TowerGrid.LEVELS - 1, level + 1); l++) {
                    levels.add(l);
                }
            }
        }
        return levels;
    }

    private static void cell(WorldState world, CellPos pos, Kind kind, int colour, int sideColour, Builder out) {
        float x0 = pos.x() - 0.5f;
        float z0 = pos.y() - 0.5f;
        float bottom = pos.level();
        boolean box = kind == Kind.BOX;
        float top = bottom + (box ? 1f : FLOOR_HEIGHT);
        int rotate1 = rotation(pos, 0);
        int rotate2 = rotation(pos, 2);
        Kind above = kind(world, pos.above());
        if (!box || (above != Kind.FLOOR && above != Kind.BOX)) {
            out.top(x0, z0, top, colour, rotate1);
        }
        if (kind(world, pos.below()) != Kind.BOX) {
            out.bottom(x0, z0, bottom, darker(colour), rotate1);
        }
        // a floor edge is a strip shifted by rotation/32 per cell; north/south use rotate1, east/west use rotate2
        // (BasisElementFLRRenderer south/north/east/west, vShift = rotate1/rotate2 * 1/32). A box side keeps the
        // whole quadrant band 0.5..1 (height, now on U) and is rotated and axis-paired like BasisElementBOXRenderer's
        // side calls (see quadrantSideUv).
        float[] north = box ? quadrantSideUv(0f, 0.5f, 1f, rotate1)
                : floorEdgeUv(0.5f, 0f, 0.5f + STRIP, 0.5f, rotate1);
        float[] east = box ? quadrantSideUv(0f, 0.5f, 1f, rotate2)
                : floorEdgeUv(0f, 0.5f, 0.5f, 0.5f + STRIP, rotate2);
        float[] south = box ? quadrantSideUv(0.5f, 1f, 1f, rotate1)
                : floorEdgeUv(1f, 0.5f, 0.5f + STRIP, 0.5f, rotate1);
        float[] west = box ? quadrantSideUv(0.5f, 1f, 1f, rotate2)
                : floorEdgeUv(0.5f, 1f, 0.5f, 0.5f + STRIP, rotate2);
        if (visible(world, pos.step(Direction.NORTH), box)) {
            out.northSide(x0, z0, bottom, top, sideColour, north);
        }
        if (visible(world, pos.step(Direction.EAST), box)) {
            out.eastSide(x0, z0, bottom, top, sideColour, east);
        }
        if (visible(world, pos.step(Direction.SOUTH), box)) {
            out.southSide(x0, z0, bottom, top, sideColour, south);
        }
        if (visible(world, pos.step(Direction.WEST), box)) {
            out.westSide(x0, z0, bottom, top, sideColour, west);
        }
    }

    /** A box side is hidden by a box; a floor edge by a floor or a box. */
    private static boolean visible(WorldState world, CellPos neighbour, boolean box) {
        Kind kind = kind(world, neighbour);
        return box ? kind != Kind.BOX : kind != Kind.BOX && kind != Kind.FLOOR;
    }

    /** {@code offset} 0 is ToB 2009's {@code rotate1} (top/bottom, north/south sides), 2 is {@code rotate2} (east/west
     * sides); {@code AbstractBasisElementRenderer.getRotationLevel}. */
    private static int rotation(CellPos pos, int offset) {
        return ROTATION_ORDER[(pos.x() + pos.y() * 16 + pos.level() * 256 + offset) % ROTATION_ORDER.length];
    }

    /**
     * A quarter darker, in sRGB (a perceptual quarter), before the single sRGB→linear conversion in
     * {@code Builder.quad}.
     */
    static int darker(int colour) {
        return TowerColours.mix(colour, 0x000000, 0.25f);
    }

    /** Bottom-left, bottom-right, top-right, top-left as seen from outside; v grows downwards. */
    private static float[] side(float u0, float u1, float v0, float v1) {
        return new float[] {u0, v1, u1, v1, u1, v0, u0, v0};
    }

    /**
     * A floor edge's (or a lift rim's) strip: {@code side(u0, u1, v0, v1)} shifted down the texture by
     * {@code rotation / 32} ({@code BasisElementFLRRenderer}'s {@code vShift = rotate1/rotate2 * 0.03125f}); the
     * quad itself is not corner-rotated, only shifted in V.
     */
    private static float[] floorEdgeUv(float u0, float u1, float v0, float v1, int rotation) {
        float shift = rotation * STRIP;
        return side(u0, u1, v0 + shift, v1 + shift);
    }

    /**
     * ToB 2009's {@code addQuad} rotates the quad's vertex positions forward by {@code steps} while keeping the UV
     * slots fixed ({@code AbstractBasisElementRenderer.addQuad}), which for {@code BaseGeometry}'s own corner order
     * (top, bottom and every side) works out to each physical corner receiving the UV {@code (-(corner + steps))
     * mod 4} positions behind it in the unrotated order (verified numerically for all 4 compass directions, the top
     * and the bottom, at all 4 rotation levels, against {@code BasisElementBOXRenderer}'s and
     * {@code AbstractBasisElementRenderer}'s top/bottom calls).
     */
    private static float[] rotateSide(float[] uv, int steps) {
        float[] out = new float[8];
        for (int corner = 0; corner < 4; corner++) {
            int from = (((-(corner + steps)) % 4) + 4) % 4;
            out[corner * 2] = uv[from * 2];
            out[corner * 2 + 1] = uv[from * 2 + 1];
        }
        return out;
    }

    /**
     * A box/lift side's quadrant band: U (not V) carries height, from {@code uLo} (the cell bottom) to {@code uHi}
     * (a full field above), scaled by {@code heightFraction} for a lift column shorter than a full field; V carries
     * the horizontal position along the wall, fixed at the quadrant's own 0.5..1 band. Matches
     * {@code BasisElementBOXRenderer}'s north/east calls (quadrant {@code STATIC_POINTS[8-11]}, {@code uLo}=0,
     * {@code uHi}=0.5) and south/west calls (quadrant {@code STATIC_POINTS[12-15]}, {@code uLo}=0.5, {@code uHi}=1),
     * rotated the same way ToB 2009 rotates the quad.
     */
    private static float[] quadrantSideUv(float uLo, float uHi, float heightFraction, int rotation) {
        float u = uLo + (uHi - uLo) * Math.clamp(heightFraction, 0f, 1f);
        return rotateSide(new float[] {uLo, 0.5f, u, 0.5f, u, 1f, uLo, 1f}, rotation);
    }

    /**
     * Collects quads as two counter-clockwise triangles each; Phase 5e: the vertex colours converted from sRGB to
     * linear if {@code linear}.
     */
    private static final class Builder {

        private final boolean linear;

        private float[] positions = new float[18 * 64];
        private float[] uvs = new float[12 * 64];
        private float[] colors = new float[24 * 64];
        private int quads;

        Builder(boolean linear) {
            this.linear = linear;
        }

        void top(float x0, float z0, float y, int colour, int rotation) {
            float[] corners = {x0, y, z0, x0, y, z0 + 1, x0 + 1, y, z0 + 1, x0 + 1, y, z0};
            // STATIC_POINTS 1,2,3,0, under the z-flip (AbstractBasisElementRenderer/BasisElementFLRRenderer top).
            quad(corners, rotateSide(new float[] {0.5f, 0f, 0.5f, 0.5f, 0f, 0.5f, 0f, 0f}, rotation), colour);
        }

        void bottom(float x0, float z0, float y, int colour, int rotation) {
            float[] corners = {x0, y, z0, x0 + 1, y, z0, x0 + 1, y, z0 + 1, x0, y, z0 + 1};
            // STATIC_POINTS 6,7,4,5, under the z-flip.
            quad(corners, rotateSide(new float[] {1f, 0.5f, 0.5f, 0.5f, 0.5f, 0f, 1f, 0f}, rotation), colour);
        }

        void northSide(float x0, float z0, float y0, float y1, int colour, float[] uv) {
            quad(new float[] {x0 + 1, y0, z0, x0, y0, z0, x0, y1, z0, x0 + 1, y1, z0}, uv, colour);
        }

        void southSide(float x0, float z0, float y0, float y1, int colour, float[] uv) {
            float z = z0 + 1;
            quad(new float[] {x0, y0, z, x0 + 1, y0, z, x0 + 1, y1, z, x0, y1, z}, uv, colour);
        }

        void eastSide(float x0, float z0, float y0, float y1, int colour, float[] uv) {
            float x = x0 + 1;
            quad(new float[] {x, y0, z0 + 1, x, y0, z0, x, y1, z0, x, y1, z0 + 1}, uv, colour);
        }

        void westSide(float x0, float z0, float y0, float y1, int colour, float[] uv) {
            quad(new float[] {x0, y0, z0, x0, y0, z0 + 1, x0, y1, z0 + 1, x0, y1, z0}, uv, colour);
        }

        private void quad(float[] corners, float[] uv, int colour) {
            int[] order = {0, 1, 2, 0, 2, 3};
            int p = quads * 18;
            int u = quads * 12;
            int c = quads * 24;
            if (p + 18 > positions.length) {
                positions = Arrays.copyOf(positions, positions.length * 2);
                uvs = Arrays.copyOf(uvs, uvs.length * 2);
                colors = Arrays.copyOf(colors, colors.length * 2);
            }
            float[] rgb = SrgbColours.rgb(colour, linear);
            float r = rgb[0];
            float g = rgb[1];
            float b = rgb[2];
            for (int i = 0; i < 6; i++) {
                int corner = order[i];
                System.arraycopy(corners, corner * 3, positions, p + i * 3, 3);
                System.arraycopy(uv, corner * 2, uvs, u + i * 2, 2);
                colors[c + i * 4] = r;
                colors[c + i * 4 + 1] = g;
                colors[c + i * 4 + 2] = b;
                colors[c + i * 4 + 3] = 1f;
            }
            quads++;
        }

        MeshArrays build() {
            return new MeshArrays(Arrays.copyOf(positions, quads * 18), Arrays.copyOf(uvs, quads * 12),
                    Arrays.copyOf(colors, quads * 24));
        }
    }
}
