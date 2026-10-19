package de.latzig.game.tob.client.logic.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class PanelLayoutTest {

    private static final float EPS = 1e-3f;

    /**
     * Every button at ToB 2009's 1024 × 768 (scale 1), as x, y (bottom edge, from the window bottom), width, height:
     * the panel is 236.8 high, the button area starts 32 below its top, virtual heights times 0.8.
     */
    private static final Object[][] AT_1024 = {
            {PanelButton.ZAPPER, 12, 150.4, 200, 48}, {PanelButton.PUSHER, 12, 102.4, 200, 48},
            {PanelButton.GRABBER, 12, 54.4, 200, 48}, {PanelButton.NORTH, 12, 6.4, 50, 48},
            {PanelButton.EAST, 62, 6.4, 50, 48}, {PanelButton.SOUTH, 112, 6.4, 50, 48},
            {PanelButton.WEST, 162, 6.4, 50, 48}, {PanelButton.FIRE, 212, 150.4, 400, 48},
            {PanelButton.LEFT, 212, 54.4, 100, 96}, {PanelButton.UP, 312, 102.4, 200, 48},
            {PanelButton.DOWN, 312, 54.4, 200, 48}, {PanelButton.RIGHT, 512, 54.4, 100, 96},
            {PanelButton.PAUSE, 212, 6.4, 400, 48}, {PanelButton.STOP, 612, 150.4, 200, 48},
            {PanelButton.RUN, 612, 102.4, 200, 48}, {PanelButton.RUN_ALL, 612, 54.4, 200, 48},
            {PanelButton.WRITE, 812, 150.4, 200, 48}, {PanelButton.DELETE, 812, 102.4, 200, 48},
            {PanelButton.CLEAR, 812, 54.4, 200, 48}, {PanelButton.SLOT_1, 612, 6.4, 50, 48},
            {PanelButton.SLOT_2, 662, 6.4, 50, 48}, {PanelButton.SLOT_3, 712, 6.4, 50, 48},
            {PanelButton.SLOT_4, 762, 6.4, 50, 48}, {PanelButton.SLOT_5, 812, 6.4, 50, 48},
            {PanelButton.SLOT_6, 862, 6.4, 50, 48}, {PanelButton.SLOT_7, 912, 6.4, 50, 48},
            {PanelButton.SLOT_8, 962, 6.4, 50, 48}};

    private static void assertRect(float x, float y, float w, float h, PanelLayout.Rect rect, String message) {
        assertEquals(x, rect.x(), EPS, message + " x");
        assertEquals(y, rect.y(), EPS, message + " y");
        assertEquals(w, rect.width(), EPS, message + " width");
        assertEquals(h, rect.height(), EPS, message + " height");
    }

    private static float f(Object value) {
        return ((Number) value).floatValue();
    }

    @Test
    void theButtonAreaIsTheToB2009GroupBelowTheCaptionStrip() {
        assertRect(0f, 0f, 1024f, 204.8f, PanelLayout.fullWidth(1024, 768).buttonArea(), "at 1024");
        assertRect(0f, 0f, 1280f, 256f, PanelLayout.fullWidth(1280, 720).buttonArea(), "at 1280");
        PanelLayout layout = PanelLayout.fullWidth(1280, 720);
        assertEquals(layout.caption(false).y(), layout.buttonArea().height(), EPS, "it ends at the caption strip");
    }

    @Test
    void allTwentySevenButtonsSitWhereToB2009DrawsThemAt1024And1280() {
        PanelLayout tob2009 = PanelLayout.fullWidth(1024, 768);
        PanelLayout window = PanelLayout.fullWidth(1280, 720);
        assertEquals(PanelButton.values().length, AT_1024.length);
        assertEquals(27, AT_1024.length);
        assertEquals(1f, tob2009.scale(), EPS);
        assertEquals(1.25f, window.scale(), EPS);

        for (Object[] row : AT_1024) {
            PanelButton button = (PanelButton) row[0];
            assertRect(f(row[1]), f(row[2]), f(row[3]), f(row[4]), tob2009.button(button), button + " at 1024");
            assertRect(1.25f * f(row[1]), 1.25f * f(row[2]), 1.25f * f(row[3]), 1.25f * f(row[4]),
                    window.button(button), button + " at 1280");
        }
        assertEquals(236.8f, tob2009.panelHeight(false), EPS);
        assertEquals(296f, window.panelHeight(false), EPS, "41 % of 720");
        assertRect(0, 204.8f, 1024, 32, tob2009.caption(false), "caption strip");
        assertRect(0, 680, 1280, 40, window.titleBar(), "title bar at the window top");
    }

    @Test
    void hitTestsIncludeTheLeftAndBottomEdgeButNotTheGaps() {
        PanelLayout layout = PanelLayout.fullWidth(1024, 768);

        assertEquals(Optional.of(PanelButton.ZAPPER), layout.buttonAt(12f, 150.4f, false), "bottom-left corner");
        assertEquals(Optional.of(PanelButton.ZAPPER), layout.buttonAt(211.9f, 198.3f, false));
        assertEquals(Optional.of(PanelButton.FIRE), layout.buttonAt(212f, 160f, false), "the right edge is FIRE's");
        assertEquals(Optional.of(PanelButton.LEFT), layout.buttonAt(250f, 100f, false), "the tall turn button");
        assertEquals(Optional.of(PanelButton.SLOT_8), layout.buttonAt(1011.9f, 10f, false));
        assertEquals(Optional.empty(), layout.buttonAt(5f, 100f, false), "left margin");
        assertEquals(Optional.empty(), layout.buttonAt(100f, 200f, false), "gap above the first row");
        assertEquals(Optional.empty(), layout.buttonAt(1015f, 100f, false), "right margin");
        assertTrue(layout.covers(5f, 100f, false), "a gap still belongs to the panel: no 3D pick");
        assertTrue(layout.onCaption(500f, 220f, false));
        assertFalse(layout.covers(500f, 240f, false), "above the panel: the 3D view");
        assertTrue(layout.covers(500f, 760f, false), "the title bar");
    }

    @Test
    void collapsedOnlyTheCaptionStripStaysAtTheBottom() {
        PanelLayout layout = PanelLayout.fullWidth(1280, 720);

        assertEquals(40f, layout.panelHeight(true), EPS);
        assertRect(0, 0, 1280, 40, layout.caption(true), "caption strip");
        assertEquals(Optional.empty(), layout.buttonAt(20f, 200f, true), "no buttons while collapsed");
        assertTrue(layout.onCaption(640f, 39.9f, true));
        assertFalse(layout.covers(640f, 41f, true), "the 3D view above the strip");
    }

    @Test
    void thePauseMenuHangsBelowItsTitleAtTheLeftOfTheTitleBar() {
        PanelLayout layout = PanelLayout.fullWidth(1280, 720);

        assertRect(0, 680, 160, 40, layout.menuTitle(), "EXIT");
        assertRect(0, 640, 400, 40, layout.menuRow(0), "first row");
        assertRect(0, 600, 400, 40, layout.menuRow(1), "second row");
    }

    @Test
    void aSmallerScaleCentresThePanel() {
        PanelLayout layout = new PanelLayout(1280, 720, 0.9375f);

        assertRect(160f + 12f * 0.9375f, 150.4f * 0.9375f, 200f * 0.9375f, 48f * 0.9375f,
                layout.button(PanelButton.ZAPPER), "4:3 panel centred (Phase 5 option A)");
    }

    /** Phase 5b decision 6: "4:3" fits the panel to the window height and centres it; the option switches live. */
    @Test
    void thePanelSizeOptionSwitchesBetweenFullWidthAndFourToThree() {
        PanelLayout full = PanelLayout.of(PanelSize.FULL_WIDTH, 1280, 720);
        PanelLayout fit = PanelLayout.of(PanelSize.FIT_HEIGHT, 1280, 720);

        assertEquals(1.25f, full.scale(), 1e-6f);
        assertEquals(0.9375f, fit.scale(), 1e-6f);
        assertRect(160f, 0f, 960f, 256f * 0.8f * 0.9375f, fit.buttonArea(), "960 units wide, centred");
        assertSame(fit, PanelLayout.resized(fit, 1280, 720, PanelSize.FIT_HEIGHT), "unchanged");
        assertEquals(1.25f, PanelLayout.resized(fit, 1280, 720, PanelSize.FULL_WIDTH).scale(), 1e-6f, "switched");
        assertSame(full, PanelLayout.resized(full, 0, 0, PanelSize.FIT_HEIGHT), "no area: the last layout stays");
        assertEquals(PanelLayout.fullWidth(1280, 720).scale(), PanelLayout.resized(null, 1280, 720).scale(), 0f);
    }

    /** Acceptance fix 2: a window without area (minimized, 0 × 0) keeps the last valid layout. */
    @Test
    void aWindowWithoutAreaKeepsTheLastValidLayout() {
        PanelLayout last = PanelLayout.fullWidth(1280, 720);
        assertSame(last, PanelLayout.resized(last, 0, 0));
        assertSame(last, PanelLayout.resized(last, 1280, 0));
        assertSame(last, PanelLayout.resized(last, 0, 720));
        assertSame(last, PanelLayout.resized(last, 1280, 720), "unchanged size: the same layout");
        PanelLayout bigger = PanelLayout.resized(last, 1600, 900);
        assertEquals(1600, bigger.width());
        assertEquals(900, bigger.height());
        assertFalse(PanelLayout.hasArea(0, 720));
        assertTrue(PanelLayout.hasArea(1, 1));
        PanelLayout first = PanelLayout.resized(null, 0, 0);
        assertTrue(first.width() > 0 && first.height() > 0, "started minimized: a usable fallback layout");
    }

    /**
     * Acceptance item 6: the X icon at the left end of {@code panel.bar} (atlas columns 0-30, a transparent gap at 31)
     * is a button over the full bar height; at 1280 × 720 (scale 1.25) it spans x 0 to 38.75 and y 680 to 720.
     */
    @Test
    void theExitButtonIsTheXAtTheLeftEndOfTheTitleBar() {
        PanelLayout layout = PanelLayout.fullWidth(1280, 720);
        assertRect(0f, 680f, 38.75f, 40f, layout.exitButton(), "X");
        assertTrue(layout.onExitButton(0f, 680f), "left and bottom edge");
        assertTrue(layout.onExitButton(38.7f, 719.9f), "just inside the right and top edge");
        assertFalse(layout.onExitButton(38.75f, 700f), "right edge: the gap before the ToB logo");
        assertFalse(layout.onExitButton(20f, 679.9f), "below the bar");
        assertFalse(layout.onExitButton(20f, 720f), "top edge");
        assertTrue(layout.covers(20f, 700f, false), "part of the title bar: never picks");

        PanelLayout narrow = new PanelLayout(1280, 960, 0.9375f);
        assertRect(160f, 960f - 30f, 31f * 0.9375f, 30f, narrow.exitButton(), "4:3 panel centred");
    }

    /**
     * Acceptance round 2, item 1: the text faces are the visible light face of each skin, measured in the atlas.
     * {@code panel.bar} and {@code panel.caption_max}/{@code caption_min}: row 0 is transparent, rows 1-19 the flat
     * face, rows 20-21 its light lower lip, the dark bevel starts at row 22: face rows 1-21 (21 units, centre 11.5
     * below the strip's top, 1.5 units lower than round 1's rows 0-19). {@code gui.window_mid_menu} stretched over the
     * 32-unit bar: rows 0-19 of 24 (unchanged).
     */
    @Test
    void theTextFacesAreTheVisibleFacesOfTheSkins() {
        PanelLayout layout = PanelLayout.fullWidth(1024, 768);
        assertRect(124f, 746f, 900f, 21f, layout.titleTextFace(), "title: rows 1-21 of panel.bar");
        assertRect(512f, 214.8f, 512f, 21f, layout.captionTextFace(false), "caption: rows 1-21 of caption_max");
        assertRect(512f, 10f, 512f, 21f, layout.captionTextFace(true), "collapsed caption");
        assertRect(0f, 768f - 32f * 20f / 24f, 128f, 32f * 20f / 24f, layout.menuTitleFace(), "EXIT: rows 0-19 of 24");

        PanelLayout window = PanelLayout.fullWidth(1280, 720);
        assertEquals(720f - 11.5f * 1.25f, window.titleTextFace().y() + window.titleTextFace().height() / 2f, EPS,
                "the title's centre 11.5 units below the window top");
    }
}
