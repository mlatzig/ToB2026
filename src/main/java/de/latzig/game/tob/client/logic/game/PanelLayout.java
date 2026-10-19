package de.latzig.game.tob.client.logic.game;

import java.util.Optional;

/**
 * The ToB 2009 panel layout on the screen (Phase 3b-2 spec §3.1), in jME GUI coordinates: pixels, origin at the
 * bottom left, y upwards. The panel is {@value #VIRTUAL_WIDTH} virtual units wide and {@code 32 + 256 · 0.8} high:
 * the caption strip on top, then the button area, whose virtual heights are scaled by {@value #Y_SCALE}. Everything is
 * scaled by {@link #scale()} (spec §2: by default the window width / 1024, "Full Width") and centred horizontally.
 * The panel size option's other variant, "4:3" ({@link #fitHeight}, decision 4), scales by the window height /
 * {@value #VIRTUAL_HEIGHT} instead and is centred horizontally too. The title bar is a {@value #STRIP_HEIGHT}-unit
 * strip at the top of the window; while paused it carries the pause menu ({@link #menuTitle()}, {@link #menuRow}).
 * Collapsed (Tab), only the caption strip stays, at the bottom.
 */
public final class PanelLayout {

    public static final float VIRTUAL_WIDTH = 1024f;
    /** ToB 2009's virtual screen height: the "4:3" panel size scales by the window height / this. */
    public static final float VIRTUAL_HEIGHT = 768f;
    /** Height of the caption strip and of the title bar. */
    public static final float STRIP_HEIGHT = 32f;
    public static final float BUTTON_AREA_HEIGHT = 256f;
    /** ToB 2009 {@code AbstractGamePanelWindow.Y_SCALE}. */
    public static final float Y_SCALE = 0.8f;
    /** The title text starts this far from the left edge of the title bar (ToB 2009 {@code lblTitle} x = 124). */
    public static final float TITLE_TEXT_X = 124f;
    /**
     * The caption text starts this far from the left edge of the caption strip (final review M1): ToB 2009 places
     * {@code lblCaption} at (width/2, …) with SJGL's default {@code LabelAlignment.BEGIN}, so the text itself starts
     * at virtual x = 512, the mid-width of the 1024-wide strip, over its grey (opaque) half; the left half of
     * {@code panel.caption_max}/{@code caption_min} is transparent.
     */
    public static final float CAPTION_TEXT_X = 512f;
    /**
     * The first row of the visible grey face of the title bar and the caption strip ({@code panel.bar},
     * {@code panel.caption_max}/{@code caption_min}, 32 rows): row 0 is transparent, rows 1-19 are the flat face,
     * rows 20-21 its light lower lip, the dark bevel starts at row 22 (rows 22-29), rows 30-31 are transparent.
     * Panel and menu texts centre their cap band in their face (acceptance fix 1; round 2, item 1: the face is the
     * whole light area, rows 1-21, so the texts sit 1.5 units lower than with round 1's rows 0-19).
     */
    public static final float STRIP_FACE_TOP = 1f;
    /** One past the last row of the strip's visible face: the dark bevel starts here. */
    public static final float STRIP_FACE_BOTTOM = 22f;
    /** The height of the menu bar skin {@code gui.window_mid_menu} in atlas rows. */
    public static final float MENU_BAR_ROWS = 24f;
    /**
     * One past the last face row of {@code gui.window_mid_menu} (rows 0-19 flat, rows 20-23 its dark and light lower
     * edge), stretched over the 32-unit title bar.
     */
    public static final float MENU_BAR_FACE_BOTTOM = 20f;
    /**
     * The X icon at the left end of {@code panel.bar} is atlas columns 0-30 (column 31 is a transparent gap before the
     * ToB logo); acceptance item 6 makes it a button over the full bar height that acts like Esc.
     */
    public static final float EXIT_BUTTON_WIDTH = 31f;
    public static final float MENU_TITLE_WIDTH = 128f;
    public static final float MENU_ROW_WIDTH = 320f;

    /** A screen rectangle; contains its left and bottom edge, not its right and top edge. */
    public record Rect(float x, float y, float width, float height) {

        public boolean contains(float px, float py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }

    private final int width;
    private final int height;
    private final float scale;
    private final float left;

    /** @param scale screen pixels per virtual unit */
    public PanelLayout(int width, int height, float scale) {
        if (scale <= 0f) {
            throw new IllegalArgumentException("scale must be positive: " + scale);
        }
        this.width = width;
        this.height = height;
        this.scale = scale;
        this.left = (width - VIRTUAL_WIDTH * scale) / 2f;
    }

    /** The default: the panel spans the full window width (spec §2, variant B). */
    public static PanelLayout fullWidth(int width, int height) {
        return new PanelLayout(width, height, Math.max(1, width) / VIRTUAL_WIDTH);
    }

    /** "4:3" (Phase 5b, the 3b-2 spec's variant A): ToB 2009's panel scaled by the window height / 768, centred. */
    public static PanelLayout fitHeight(int width, int height) {
        return new PanelLayout(width, height, Math.max(1, height) / VIRTUAL_HEIGHT);
    }

    /** The layout of the Panel size option {@code size}. */
    public static PanelLayout of(PanelSize size, int width, int height) {
        return size == PanelSize.FIT_HEIGHT ? fitHeight(width, height) : fullWidth(width, height);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public float scale() {
        return scale;
    }

    /** The height the panel covers at the bottom of the window. */
    public float panelHeight(boolean collapsed) {
        return (collapsed ? STRIP_HEIGHT : STRIP_HEIGHT + BUTTON_AREA_HEIGHT * Y_SCALE) * scale;
    }

    public Rect button(PanelButton button) {
        float top = panelHeight(false) - (STRIP_HEIGHT + Y_SCALE * button.y()) * scale;
        float h = Y_SCALE * button.height() * scale;
        return new Rect(left + button.x() * scale, top - h, button.width() * scale, h);
    }

    /** The caption strip: the top of the panel, or alone at the bottom while collapsed. */
    public Rect caption(boolean collapsed) {
        return new Rect(left, panelHeight(collapsed) - STRIP_HEIGHT * scale, VIRTUAL_WIDTH * scale,
                STRIP_HEIGHT * scale);
    }

    /** The whole panel (caption strip and button area, including the gaps between the buttons). */
    public Rect panel(boolean collapsed) {
        return new Rect(left, 0f, VIRTUAL_WIDTH * scale, panelHeight(collapsed));
    }

    /**
     * The button area below the caption strip, which ToB 2009 fills with a raised group (Phase 4a spec §2 decision 7:
     * {@code GamePanelWindowNormal.initControls} adds the group 0, 0, 1024, 256, i.e. virtual y 32, height 256 · 0.8).
     */
    public Rect buttonArea() {
        return new Rect(left, 0f, VIRTUAL_WIDTH * scale, BUTTON_AREA_HEIGHT * Y_SCALE * scale);
    }

    public Rect titleBar() {
        return new Rect(left, height - STRIP_HEIGHT * scale, VIRTUAL_WIDTH * scale, STRIP_HEIGHT * scale);
    }

    /** A window without area (minimized: 0 × 0) gets no new layout (acceptance fix 2). */
    public static boolean hasArea(int width, int height) {
        return width > 0 && height > 0;
    }

    /**
     * The layout for a window of {@code width} × {@code height}: {@code last} itself if the size is unchanged or the
     * window has no area (minimized), so the last valid layout stays (acceptance fix 2); started without area, a
     * 1024 × 768 layout until the first valid size.
     */
    public static PanelLayout resized(PanelLayout last, int width, int height) {
        return resized(last, width, height, PanelSize.FULL_WIDTH);
    }

    /** As {@link #resized(PanelLayout, int, int)} for the Panel size option {@code size} (Phase 5b). */
    public static PanelLayout resized(PanelLayout last, int width, int height, PanelSize size) {
        if (!hasArea(width, height)) {
            return last != null ? last : of(size, (int) VIRTUAL_WIDTH, (int) VIRTUAL_HEIGHT);
        }
        PanelLayout next = of(size, width, height);
        if (last != null && last.width == width && last.height == height && last.scale == next.scale) {
            return last;
        }
        return next;
    }

    /** The text area of the title: from {@link #TITLE_TEXT_X}, the bar's visible face. */
    public Rect titleTextFace() {
        Rect bar = titleBar();
        return face(new Rect(bar.x() + TITLE_TEXT_X * scale, bar.y(), bar.width() - TITLE_TEXT_X * scale,
                bar.height()), STRIP_FACE_TOP, STRIP_FACE_BOTTOM, STRIP_HEIGHT);
    }

    /** The text area of the caption: from {@link #CAPTION_TEXT_X}, the strip's visible face. */
    public Rect captionTextFace(boolean collapsed) {
        Rect strip = caption(collapsed);
        return face(new Rect(strip.x() + CAPTION_TEXT_X * scale, strip.y(), strip.width() - CAPTION_TEXT_X * scale,
                strip.height()), STRIP_FACE_TOP, STRIP_FACE_BOTTOM, STRIP_HEIGHT);
    }

    /** The face of the menu title "EXIT" while paused. */
    public Rect menuTitleFace() {
        return face(menuTitle(), 0f, MENU_BAR_FACE_BOTTOM, MENU_BAR_ROWS);
    }

    /** The X at the left end of the title bar (acceptance item 6): acts like Esc. */
    public Rect exitButton() {
        Rect bar = titleBar();
        return new Rect(bar.x(), bar.y(), EXIT_BUTTON_WIDTH * scale, bar.height());
    }

    public boolean onExitButton(float x, float y) {
        return exitButton().contains(x, y);
    }

    /** Rows {@code top} to {@code bottom} (exclusive) of a {@code rows}-row skin stretched over {@code strip}. */
    private static Rect face(Rect strip, float top, float bottom, float rows) {
        float row = strip.height() / rows;
        return new Rect(strip.x(), strip.y() + strip.height() - bottom * row, strip.width(), (bottom - top) * row);
    }

    /** The "EXIT" menu title at the left end of the title bar (while paused). */
    public Rect menuTitle() {
        return new Rect(left, height - STRIP_HEIGHT * scale, MENU_TITLE_WIDTH * scale, STRIP_HEIGHT * scale);
    }

    /** Row {@code row} (0 = first) of the open pause menu, below the menu title. */
    public Rect menuRow(int row) {
        return new Rect(left, height - (row + 2) * STRIP_HEIGHT * scale, MENU_ROW_WIDTH * scale,
                STRIP_HEIGHT * scale);
    }

    /** The button at a screen point; collapsed, there is none. */
    public Optional<PanelButton> buttonAt(float x, float y, boolean collapsed) {
        if (collapsed) {
            return Optional.empty();
        }
        for (PanelButton button : PanelButton.values()) {
            if (button(button).contains(x, y)) {
                return Optional.of(button);
            }
        }
        return Optional.empty();
    }

    public boolean onCaption(float x, float y, boolean collapsed) {
        return caption(collapsed).contains(x, y);
    }

    /**
     * Whether a click at the point belongs to the panel or the title bar, including the gaps between the buttons,
     * so it never picks in the 3D view (spec §4.1: panel clicks take priority).
     */
    public boolean covers(float x, float y, boolean collapsed) {
        return panel(collapsed).contains(x, y) || titleBar().contains(x, y);
    }
}
