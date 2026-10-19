package de.latzig.game.tob.client.logic.ui;

import java.util.ArrayList;
import java.util.List;

import de.latzig.game.tob.client.logic.game.PanelLayout;

/**
 * A 9-slice skin of the GUI atlas as the ToB 2009 GUI toolkit draws it (SJGL {@code Window}, {@code Button},
 * {@code Group} paint listeners): fixed corners, edges stretched along one axis, the centre stretched along both.
 * The nine region names are listed top row first, left to right; the edge thicknesses are virtual units (the
 * {@code geometry} section of {@code gui-atlas.json}), scaled by the caller.
 *
 * @param regions the nine atlas regions: left top, mid top, right top, left mid, centre, right mid, left bottom, mid
 *                bottom, right bottom
 */
public record NineSlice(List<String> regions, float left, float right, float top, float bottom) {

    /** A ToB 2009 button, raised ({@code gui.button_outset_*}, border {@code button_border_width} = 5). */
    public static final NineSlice BUTTON = box("gui.button_outset_", 5f);
    /** A ToB 2009 button, pressed ({@code gui.button_inset_*}): Phase 5b's active tab, as ToB 2005's tab bar. */
    public static final NineSlice BUTTON_INSET = box("gui.button_inset_", 5f);
    /** A ToB 2009 group, sunken ({@code gui.group_inset_*}, border {@code group_border_width} = 5). */
    public static final NineSlice GROUP_INSET = box("gui.group_inset_", 5f);
    /** A ToB 2009 group, raised ({@code gui.group_outset_*}, border {@code group_border_width} = 5). */
    public static final NineSlice GROUP_OUTSET = box("gui.group_outset_", 5f);
    /** A ToB 2009 text box ({@code gui.textbox_*}, border {@code textbox_border_width} = 5), Phase 5a. */
    public static final NineSlice TEXTBOX = box("gui.textbox_", 5f);
    /** A ToB 2009 list box ({@code gui.listbox_*}, border {@code listbox_border_width} = 5), Phase 5a. */
    public static final NineSlice LISTBOX = box("gui.listbox_", 5f);
    /** A ToB 2009 progress bar ({@code gui.progress_*}, border {@code progress_border_width} = 5), Phase 5a. */
    public static final NineSlice PROGRESS = box("gui.progress_", 5f);
    /**
     * A ToB 2009 window with an active caption: the caption bar ({@code window_caption_height} = 24) on top, the
     * border ({@code window_border_width} = 5) left, right and below, the semi-transparent dark background inside.
     */
    public static final NineSlice WINDOW = new NineSlice(List.of("gui.window_left_active_caption",
            "gui.window_mid_active_caption", "gui.window_right_active_caption", "gui.window_left_border",
            "gui.window_background", "gui.window_right_border", "gui.window_left_bottom_border",
            "gui.window_mid_bottom_border", "gui.window_right_bottom_border"), 5f, 5f, 24f, 5f);

    public NineSlice {
        if (regions.size() != 9) {
            throw new IllegalArgumentException("a 9-slice skin needs 9 regions: " + regions);
        }
        regions = List.copyOf(regions);
    }

    private static NineSlice box(String prefix, float border) {
        List<String> names = new ArrayList<>();
        for (String row : List.of("top", "mid", "bottom")) {
            for (String column : List.of("left", "mid", "right")) {
                names.add(row.equals("mid") && column.equals("mid") ? prefix + "background"
                        : prefix + column + "_" + row);
            }
        }
        return new NineSlice(names, border, border, border, border);
    }

    /**
     * The nine screen rectangles of {@code rect} (GUI coordinates, y upwards), in the order of {@link #regions()};
     * the edges are {@code scale} pixels per virtual unit thick.
     */
    public List<PanelLayout.Rect> pieces(PanelLayout.Rect rect, float scale) {
        float l = left * scale;
        float r = right * scale;
        float t = top * scale;
        float b = bottom * scale;
        float[] xs = {rect.x(), rect.x() + l, rect.x() + rect.width() - r};
        float[] ws = {l, rect.width() - l - r, r};
        float[] ys = {rect.y() + rect.height() - t, rect.y() + b, rect.y()};
        float[] hs = {t, rect.height() - t - b, b};
        List<PanelLayout.Rect> pieces = new ArrayList<>(9);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                pieces.add(new PanelLayout.Rect(xs[column], ys[row], ws[column], hs[row]));
            }
        }
        return pieces;
    }
}
