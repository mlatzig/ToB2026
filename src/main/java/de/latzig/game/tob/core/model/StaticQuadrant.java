package de.latzig.game.tob.core.model;

import java.util.Locale;

/**
 * The four quadrants of the STATIC texture atlas ({@code 1000-static-color.png} and its normal map, 1024² in ToB 2005
 * and ToB 2009). Phase 4b-1 spec §2 decision 8 splits it into one 512² texture per quadrant, so that mipmaps and
 * anisotropic filtering never reach into a neighbouring quadrant. The faces of ToB 2009's basis renderers
 * ({@code STATIC_POINTS}) use: {@link #TOP} the walkable tops (floors, boxes, lift platforms), {@link #BOTTOM} the
 * bottoms and the lift shafts, {@link #SIDE_A} the north and east box sides and floor edges, {@link #SIDE_B} the
 * south and west ones. Columns and rows count from the atlas's top-left corner (v grows downwards).
 */
public enum StaticQuadrant {

    TOP(0, 0),
    BOTTOM(1, 0),
    SIDE_A(0, 1),
    SIDE_B(1, 1);

    /** The base texture of ToB 2005's STATIC detail map ({@code elements.json}). */
    public static final String DETAIL_BASE = "static-detail";
    /** ToB 2005's STATIC detail map ({@code 1002_01M-STATIC.bmp}). */
    public static final String DETAIL_TEXTURE = "textures/1002-static-detail.png";

    private final int column;
    private final int row;

    StaticQuadrant(int column, int row) {
        this.column = column;
        this.row = row;
    }

    public int column() {
        return column;
    }

    public int row() {
        return row;
    }

    /** {@code static-top}, {@code static-bottom}, {@code static-side-a}, {@code static-side-b}. */
    public String baseName() {
        return "static-" + suffix();
    }

    /** E.g. {@code textures/1000-static-top-color.png}. */
    public String colourTexture() {
        return "textures/1000-static-" + suffix() + "-color.png";
    }

    /** E.g. {@code textures/1001-static-top-normal.png}. */
    public String normalMap() {
        return "textures/1001-static-" + suffix() + "-normal.png";
    }

    /**
     * The quadrant holding atlas UV ({@code u}, {@code v}); a value of exactly 0.5 counts to the right or lower half.
     */
    public static StaticQuadrant at(float u, float v) {
        int c = u < 0.5f ? 0 : 1;
        int r = v < 0.5f ? 0 : 1;
        for (StaticQuadrant quadrant : values()) {
            if (quadrant.column == c && quadrant.row == r) {
                return quadrant;
            }
        }
        throw new IllegalStateException("no quadrant " + c + "," + r);
    }

    /** Atlas u to this quadrant's own 0..1. */
    public float localU(float u) {
        return (u - column * 0.5f) * 2f;
    }

    /** Atlas v to this quadrant's own 0..1. */
    public float localV(float v) {
        return (v - row * 0.5f) * 2f;
    }

    private String suffix() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
