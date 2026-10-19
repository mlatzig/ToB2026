package de.latzig.game.tob.client.logic.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import de.latzig.game.tob.client.logic.game.PanelLayout;
import de.latzig.game.tob.client.logic.game.TextPlacement;

/**
 * One frame of a start screen as plain data (Phase 5a spec §3: the atlas sprite path, not Lemur): GUI atlas sprites
 * and bitmap-font labels, each with its place in the drawing order ({@code order}: the later in front, sprites and
 * labels counted together, so a dialog's window covers the texts of the screen below it). Engine-free; the view only
 * draws it, the tests check it.
 *
 * <p>The Overview's 3×3 letter grid (author decision, 2026-10-12) needs no primitive of its own: a cell is a
 * coloured background sprite (its state: solved green, open white, locked grey, selection highlighted) stacked with
 * a centred {@code Label} carrying the cell's letter (A-I), both added through the ordinary {@link Builder#sprite}
 * and {@link Builder#label} calls over the cell's {@link PanelLayout.Rect} (see {@code StartScreens}, Task 11).
 *
 * @param sprites   atlas regions stretched over screen rectangles, with an RGB tint (white = none)
 * @param labels    one-line texts with their size in pixels, colour and alignment in their rectangle
 * @param viewports Phase 6b-2: where a 3D view shows (the Tower Designer's preview), in the drawing order too
 */
public record Drawing(List<Sprite> sprites, List<Label> labels, List<Viewport> viewports) {

    /** Plain white: an untinted sprite or white text. */
    public static final int WHITE = 0xFFFFFF;
    /** ToB 2009 {@code Colors.GRAY}: greyed buttons, dialog labels. */
    public static final int GREY = 0x808080;
    public static final int BLACK = 0x000000;
    /** ToB 2009 {@code Colors.ORANGE}: text boxes and the selected list row. */
    public static final int ORANGE = 0xFFC800;
    /** ToB 2009 {@code Colors.GREEN}: solved towers, 100 %. */
    public static final int GREEN = 0x00FF00;

    /** How a label sits in its rectangle horizontally; vertically its cap band is centred ({@link TextPlacement}). */
    public enum Align {
        LEFT, CENTRE, RIGHT
    }

    public record Sprite(String region, PanelLayout.Rect rect, int tint, int order) {

        public Sprite {
            Objects.requireNonNull(region, "region");
            Objects.requireNonNull(rect, "rect");
        }
    }

    public record Label(String text, PanelLayout.Rect rect, float size, int colour, Align align, int order) {

        public Label {
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(rect, "rect");
            Objects.requireNonNull(align, "align");
        }
    }

    /** Phase 6b-2: a screen rectangle that a 3D view fills, in front of what was drawn before it. */
    public record Viewport(PanelLayout.Rect rect, int order) {

        public Viewport {
            Objects.requireNonNull(rect, "rect");
        }
    }

    public Drawing {
        sprites = List.copyOf(sprites);
        labels = List.copyOf(labels);
        viewports = List.copyOf(viewports);
    }

    /** A drawing without a 3D view. */
    public Drawing(List<Sprite> sprites, List<Label> labels) {
        this(sprites, labels, List.of());
    }

    /** The number of items (sprites and labels). */
    public int size() {
        return sprites.size() + labels.size();
    }

    /** Collects sprites and labels in drawing order. */
    public static final class Builder {

        private final List<Sprite> sprites = new ArrayList<>();
        private final List<Label> labels = new ArrayList<>();
        private final List<Viewport> viewports = new ArrayList<>();
        private int order;

        public Builder sprite(String region, PanelLayout.Rect rect, int tint) {
            sprites.add(new Sprite(region, rect, tint, order++));
            return this;
        }

        /** The nine pieces of {@code skin} over {@code rect}, its borders {@code scale} pixels per virtual unit. */
        public Builder nineSlice(NineSlice skin, PanelLayout.Rect rect, float scale, int tint) {
            List<PanelLayout.Rect> pieces = skin.pieces(rect, scale);
            for (int i = 0; i < pieces.size(); i++) {
                sprites.add(new Sprite(skin.regions().get(i), pieces.get(i), tint, order++));
            }
            return this;
        }

        public Builder label(String text, PanelLayout.Rect rect, float size, int colour, Align align) {
            labels.add(new Label(text, rect, size, colour, align, order++));
            return this;
        }

        /** Phase 6b-2: the place of a 3D view over {@code rect}, in front of everything drawn so far. */
        public Builder viewport(PanelLayout.Rect rect) {
            viewports.add(new Viewport(rect, order++));
            return this;
        }

        public Drawing build() {
            return new Drawing(sprites, labels, viewports);
        }
    }
}
