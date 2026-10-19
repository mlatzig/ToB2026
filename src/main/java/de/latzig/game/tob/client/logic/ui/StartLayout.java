package de.latzig.game.tob.client.logic.ui;

import de.latzig.game.tob.client.logic.game.PanelLayout;

/**
 * The start screens' virtual layout (Phase 5a spec §3): ToB 2009's 1024 × 768 screen ({@code general.screen.*}),
 * scaled by the window height and centred horizontally, as {@link PanelLayout} scales the panel. Virtual coordinates
 * have their origin at the top left with y downwards (as ToB 2009's windows); {@link #rect} turns them into jME GUI
 * rectangles (pixels, origin bottom left, y upwards). The Tower Designer's layout ({@link #wide}) keeps the height
 * but lets the virtual width grow with the window's aspect ratio (acceptance change 1 of Phase 6b-1).
 */
public final class StartLayout {

    public static final float WIDTH = 1024f;
    public static final float HEIGHT = 768f;

    private final int width;
    private final int height;
    private final float scale;
    private final float virtualWidth;
    private final float left;

    /** A layout for a {@code width} × {@code height} window; a window without area keeps a 1 × 1 layout. */
    public StartLayout(int width, int height) {
        this(width, height, WIDTH);
    }

    private StartLayout(int width, int height, float maxWidth) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.scale = this.height / HEIGHT;
        this.virtualWidth = Math.min(maxWidth, Math.max(WIDTH, this.width / scale));
        this.left = (this.width - virtualWidth * scale) / 2f;
    }

    /**
     * A layout for a {@code width} × {@code height} window whose virtual width follows the window's aspect ratio:
     * {@link #WIDTH} (4:3) for a window of 4:3 or narrower, up to {@code maxWidth} for a wider one; beyond that the
     * virtual screen is centred between bars, as the 4:3 layout is.
     */
    public static StartLayout wide(int width, int height, float maxWidth) {
        return new StartLayout(width, height, maxWidth);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** The virtual screen's width: {@link #WIDTH}, or more for {@link #wide} on a wide window. */
    public float virtualWidth() {
        return virtualWidth;
    }

    /** Screen pixels per virtual unit. */
    public float scale() {
        return scale;
    }

    /** The screen rectangle of the virtual rectangle with its top left at ({@code x}, {@code y}). */
    public PanelLayout.Rect rect(float x, float y, float w, float h) {
        return new PanelLayout.Rect(left + x * scale, height - (y + h) * scale, w * scale, h * scale);
    }

    /** The whole window (the fade covers it). */
    public PanelLayout.Rect window() {
        return new PanelLayout.Rect(0f, 0f, width, height);
    }
}
