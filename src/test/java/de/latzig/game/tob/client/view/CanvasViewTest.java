package de.latzig.game.tob.client.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.client.logic.ui.Drawing;
import de.latzig.game.tob.client.logic.ui.NineSlice;
import de.latzig.game.tob.client.logic.ui.StartLayout;

class CanvasViewTest {

    @Test
    void drawsSpritesAndLabelsInTheDrawingsOrderAndTheFadeInFront() {
        assumeTrue(GeneratedAssets.available(), "generated assets not available");
        AssetLibrary assets = new AssetLibrary(GeneratedAssets.assetManager(), GeneratedAssets.root());
        CanvasView view = new CanvasView(assets, assets.font());
        StartLayout layout = new StartLayout(1280, 720);
        Drawing drawing = new Drawing.Builder()
                .sprite("start.title", layout.rect(128f, 32f, 768f, 128f), Drawing.WHITE)
                .label("Below", layout.rect(128f, 200f, 768f, 50f), 20f, Drawing.WHITE, Drawing.Align.CENTRE)
                .nineSlice(NineSlice.BUTTON, layout.rect(300f, 200f, 256f, 50f), layout.scale(), Drawing.GREY)
                .label("Above", layout.rect(300f, 200f, 256f, 50f), 20f, Drawing.BLACK, Drawing.Align.LEFT)
                .build();

        view.show(drawing, 0.5f, 1280, 720);

        assertEquals(10, view.shownRegions().size());
        assertEquals("start.title", view.shownRegions().get(0));
        assertEquals(List.of("Below", "Above"), view.shownTexts());
        assertTrue(view.spriteZ(1) > view.textZ(0), "the button covers the earlier label");
        assertTrue(view.textZ(1) > view.spriteZ(9), "the later label is on the button");
        assertEquals(0.5f, view.fadeAlpha());

        view.show(new Drawing.Builder().sprite("gui.cursor_normal", layout.rect(0f, 0f, 24f, 24f), Drawing.WHITE)
                .build(), 0f, 1280, 720);

        assertEquals(List.of("gui.cursor_normal"), view.shownRegions(), "surplus sprites are hidden");
        assertEquals(List.of(), view.shownTexts());
        assertEquals(0f, view.fadeAlpha());
    }
}
