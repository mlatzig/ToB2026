package de.latzig.game.tob.client.logic.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.client.logic.game.PanelLayout;

class StartBuildingBlocksTest {

    @Test
    void theVirtualScreenScalesByHeightAndIsCentred() {
        StartLayout layout = new StartLayout(1280, 720);

        assertEquals(720f / 768f, layout.scale(), 1e-6f);
        PanelLayout.Rect title = layout.rect(128f, 32f, 768f, 128f);
        assertEquals(160f + 128f * layout.scale(), title.x(), 1e-3f, "960 px wide, centred: 160 px left");
        assertEquals(720f - 160f * layout.scale(), title.y(), 1e-3f, "y upwards from the bottom");
        assertEquals(768f * layout.scale(), title.width(), 1e-3f);
        assertEquals(new PanelLayout.Rect(0f, 0f, 1280f, 720f), layout.window());
        assertEquals(1, new StartLayout(0, 0).height(), "a minimized window keeps a layout");
    }

    @Test
    void aDrawingKeepsTheOrderAndANineSliceAddsNinePieces() {
        StartLayout layout = new StartLayout(1024, 768);
        Drawing drawing = new Drawing.Builder()
                .sprite("start.title", layout.rect(128f, 32f, 768f, 128f), Drawing.WHITE)
                .nineSlice(NineSlice.GROUP_OUTSET, layout.rect(128f, 192f, 768f, 60f), 1f, Drawing.WHITE)
                .label("Menu", layout.rect(133f, 197f, 758f, 50f), 40f, Drawing.WHITE, Drawing.Align.CENTRE)
                .build();

        assertEquals(10, drawing.sprites().size());
        assertEquals("start.title", drawing.sprites().get(0).region());
        assertEquals("gui.group_outset_left_top", drawing.sprites().get(1).region());
        assertEquals("Menu", drawing.labels().get(0).text());
        assertEquals(10, drawing.labels().get(0).order(), "after the ten sprites");
        assertEquals(11, drawing.size());
    }

    @Test
    void theNameFieldTakesPrintableAsciiOnlyUpToSixteen() {
        TextField field = new TextField();

        for (char c : " Müßller ~?".toCharArray()) {
            field.type(c);
        }
        assertEquals("Mller ~?", field.text(), "no leading space, no umlaut, no sharp s");
        assertFalse(field.type('\t'));
        assertFalse(field.type('€'), "no euro sign");
        for (int i = 0; i < 20; i++) {
            field.type('x');
        }
        assertEquals(16, field.text().length());
        field.backspace();
        assertEquals(15, field.text().length());
        field.clear();
        field.type('A');
        field.type(' ');
        assertEquals("A", field.name(), "trailing spaces are not part of the name");
    }

    @Test
    void theFadeGoesToBlackChangesAndComesBack() {
        ScreenFade fade = new ScreenFade();
        List<String> changes = new ArrayList<>();
        assertEquals(1f, fade.alpha(), "a new screen starts black");
        fade.update(0.25);
        assertEquals(0.5f, fade.alpha(), 1e-6f);
        fade.update(0.25);
        assertEquals(0f, fade.alpha());

        fade.fadeOut(() -> changes.add("next"));
        fade.fadeOut(() -> changes.add("twice"));
        assertTrue(fade.isLeaving());
        fade.update(0.4);
        assertEquals(List.of(), changes);
        fade.update(0.1);

        assertEquals(List.of("next"), changes, "the change runs once, at black");
        assertEquals(1f, fade.alpha());
        assertFalse(fade.isLeaving());
    }
}
