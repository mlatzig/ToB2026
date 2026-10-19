package de.latzig.game.tob.client.logic.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.client.logic.game.ConfirmDialogLayout.Answer;
import de.latzig.game.tob.client.logic.ui.NineSlice;

/** Acceptance round 2, item 2: the ARE YOU SURE? dialog as a centred modal window. */
class ConfirmDialogLayoutTest {

    private static final float EPS = 1e-3f;

    private static float centreX(PanelLayout.Rect rect) {
        return rect.x() + rect.width() / 2f;
    }

    private static float centreY(PanelLayout.Rect rect) {
        return rect.y() + rect.height() / 2f;
    }

    private static boolean inside(PanelLayout.Rect inner, PanelLayout.Rect outer) {
        return inner.x() >= outer.x() - EPS && inner.y() >= outer.y() - EPS
                && inner.x() + inner.width() <= outer.x() + outer.width() + EPS
                && inner.y() + inner.height() <= outer.y() + outer.height() + EPS;
    }

    @Test
    void theDialogIsCentredInTheWindowWidthAndIn3dAreaAt1280x720() {
        PanelLayout layout = PanelLayout.fullWidth(1280, 720);
        ConfirmDialogLayout dialog = ConfirmDialogLayout.of(layout, false);
        PanelLayout.Rect window = dialog.window();

        assertEquals(320f * 1.25f, window.width(), EPS, "320 virtual units wide");
        assertEquals(143f * 1.25f, window.height(), EPS, "caption 24 + message 72 + buttons 42 + border 5");
        assertEquals(640f, centreX(window), EPS, "horizontally centred in the window");
        assertEquals((296f + 680f) / 2f, centreY(window), EPS, "vertically centred between panel and title bar");

        ConfirmDialogLayout collapsed = ConfirmDialogLayout.of(layout, true);
        assertEquals((40f + 680f) / 2f, centreY(collapsed.window()), EPS, "collapsed: above the caption strip");
    }

    @Test
    void theDialogIsCentredAtAnotherSizeToo() {
        PanelLayout layout = PanelLayout.fullWidth(1680, 980);
        float scale = 1680f / 1024f;
        ConfirmDialogLayout dialog = ConfirmDialogLayout.of(layout, false);
        PanelLayout.Rect window = dialog.window();

        assertEquals(320f * scale, window.width(), EPS);
        assertEquals(840f, centreX(window), EPS);
        assertEquals((layout.panelHeight(false) + layout.titleBar().y()) / 2f, centreY(window), EPS);
    }

    @Test
    void aTooLowWindowKeepsTheDialogBelowTheTitleBar() {
        PanelLayout layout = PanelLayout.fullWidth(1280, 400);
        PanelLayout.Rect window = ConfirmDialogLayout.of(layout, false).window();

        assertEquals(layout.titleBar().y(), window.y() + window.height(), EPS, "top edge at the title bar");
    }

    @Test
    void aMinimizedWindowKeepsTheLastDialog() {
        PanelLayout last = PanelLayout.fullWidth(1280, 720);
        PanelLayout minimized = PanelLayout.resized(last, 0, 0);

        assertEquals(ConfirmDialogLayout.of(last, false).window(), ConfirmDialogLayout.of(minimized, false).window());
        PanelLayout startedMinimized = PanelLayout.resized(null, 0, 0);
        PanelLayout.Rect window = ConfirmDialogLayout.of(startedMinimized, false).window();
        assertTrue(window.width() > 0f && window.height() > 0f, "a usable fallback, no zero or negative size");
    }

    /** ToB 2009 {@code AbstractDialog}: the buttons side by side at the bottom right, OK (YES) left of Cancel (NO). */
    @Test
    void yesAndNoSitSideBySideAtTheBottomRight() {
        PanelLayout layout = PanelLayout.fullWidth(1280, 720);
        ConfirmDialogLayout dialog = ConfirmDialogLayout.of(layout, false);
        PanelLayout.Rect yes = dialog.button(Answer.YES);
        PanelLayout.Rect no = dialog.button(Answer.NO);
        PanelLayout.Rect area = dialog.buttonArea();

        assertEquals(96f * 1.25f, yes.width(), EPS);
        assertEquals(32f * 1.25f, yes.height(), EPS, "geometry button_height");
        assertEquals(yes.width(), no.width(), EPS);
        assertEquals(yes.y(), no.y(), EPS, "side by side");
        assertEquals(yes.x() + yes.width(), no.x(), EPS, "YES directly left of NO");
        assertEquals(area.x() + area.width() - 5f * 1.25f, no.x() + no.width(), EPS, "right edge inside the group");
        assertEquals(centreY(area), centreY(yes), EPS, "vertically centred in the button group");
        assertTrue(inside(area, dialog.window()));
        assertTrue(inside(dialog.messageGroup(), dialog.messageArea()));
        assertTrue(inside(dialog.messageTextFace(), dialog.messageGroup()));
        assertTrue(dialog.messageArea().y() >= area.y() + area.height() - EPS, "the question above the buttons");
        assertTrue(inside(dialog.caption(), dialog.window()));
        assertEquals(dialog.window().y() + dialog.window().height(), dialog.caption().y() + dialog.caption().height(),
                EPS, "the caption bar on top");
    }

    @Test
    void hitTestsFindYesNoAndTheDialogArea() {
        for (PanelLayout layout : List.of(PanelLayout.fullWidth(1280, 720), PanelLayout.fullWidth(1024, 768))) {
            ConfirmDialogLayout dialog = ConfirmDialogLayout.of(layout, false);
            PanelLayout.Rect yes = dialog.button(Answer.YES);
            PanelLayout.Rect no = dialog.button(Answer.NO);
            PanelLayout.Rect window = dialog.window();

            assertEquals(Optional.of(Answer.YES), dialog.answerAt(centreX(yes), centreY(yes)));
            assertEquals(Optional.of(Answer.NO), dialog.answerAt(centreX(no), centreY(no)));
            assertEquals(Optional.of(Answer.NO), dialog.answerAt(no.x(), no.y()), "left/bottom edge belongs");
            assertEquals(Optional.empty(), dialog.answerAt(centreX(window), centreY(dialog.messageGroup())));
            assertTrue(dialog.contains(centreX(window), centreY(dialog.messageGroup())), "the question: inside");
            assertTrue(dialog.contains(window.x(), window.y()));
            assertFalse(dialog.contains(window.x() - 1f, centreY(window)), "left of the dialog");
            assertFalse(dialog.contains(centreX(window), window.y() + window.height() + 1f), "above the dialog");
            assertEquals(Optional.empty(), dialog.answerAt(10f, 10f), "the panel");
        }
    }

    @Test
    void theTextFacesFollowTheSkins() {
        PanelLayout layout = PanelLayout.fullWidth(1024, 768);
        ConfirmDialogLayout dialog = ConfirmDialogLayout.of(layout, false);
        PanelLayout.Rect caption = dialog.caption();
        PanelLayout.Rect face = dialog.captionTextFace();

        assertEquals(caption.x() + 5f, face.x(), EPS, "caption text starts after the left border (ToB 2009)");
        assertEquals(caption.y() + caption.height() - 2f, face.y() + face.height(), EPS, "blue face from row 2");
        assertEquals(18f, face.height(), EPS, "rows 2-19 of window_mid_active_caption");
        PanelLayout.Rect yes = dialog.button(Answer.YES);
        PanelLayout.Rect yesFace = dialog.buttonTextFace(Answer.YES);
        assertEquals(centreY(yes), centreY(yesFace), EPS, "inside the 5-unit button border");
        assertEquals(22f, yesFace.height(), EPS);
        assertEquals(centreY(dialog.messageGroup()), centreY(dialog.messageTextFace()), EPS);
    }

    @Test
    void theNineSlicePiecesTileTheRectangle() {
        PanelLayout.Rect rect = new PanelLayout.Rect(100f, 50f, 400f, 178.75f);
        List<PanelLayout.Rect> pieces = NineSlice.WINDOW.pieces(rect, 1.25f);

        assertEquals(9, pieces.size());
        assertEquals(new PanelLayout.Rect(100f, 50f + 178.75f - 30f, 6.25f, 30f), pieces.get(0), "caption left");
        assertEquals(new PanelLayout.Rect(106.25f, 56.25f, 387.5f, 178.75f - 30f - 6.25f), pieces.get(4), "face");
        assertEquals(new PanelLayout.Rect(493.75f, 50f, 6.25f, 6.25f), pieces.get(8), "bottom right");
        float area = 0f;
        for (PanelLayout.Rect piece : pieces) {
            area += piece.width() * piece.height();
        }
        assertEquals(rect.width() * rect.height(), area, 0.5f, "no gaps, no overlaps");
        assertEquals("gui.button_outset_background", NineSlice.BUTTON.regions().get(4));
        assertEquals("gui.button_outset_left_top", NineSlice.BUTTON.regions().get(0));
        assertEquals("gui.button_outset_right_bottom", NineSlice.BUTTON.regions().get(8));
    }
}
