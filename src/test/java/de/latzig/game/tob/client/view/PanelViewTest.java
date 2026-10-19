package de.latzig.game.tob.client.view;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.jme3.bounding.BoundingBox;
import com.jme3.font.BitmapText;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer.Type;

import de.latzig.game.tob.client.logic.game.ConfirmDialogLayout;
import de.latzig.game.tob.client.logic.game.ConfirmDialogLayout.Answer;
import de.latzig.game.tob.client.logic.game.GameAction;
import de.latzig.game.tob.client.logic.game.PanelButton;
import de.latzig.game.tob.client.logic.game.PanelLayout;
import de.latzig.game.tob.client.logic.game.PanelState;
import de.latzig.game.tob.client.logic.game.PauseMenu;
import de.latzig.game.tob.client.logic.game.TextPlacement;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.sim.SimOptions;
import de.latzig.game.tob.core.sim.Simulation;
import de.latzig.game.tob.core.sim.SpiderId;
import de.latzig.game.tob.core.sim.TestTowers;

class PanelViewTest {

    private static final PanelLayout LAYOUT = PanelLayout.fullWidth(1280, 720);

    private static AssetLibrary assets() {
        assumeTrue(GeneratedAssets.available(), "generated assets not available");
        return new AssetLibrary(GeneratedAssets.assetManager(), GeneratedAssets.root());
    }

    private static Simulation zapperAndPusher() {
        return Simulation.start(TestTowers.tower().level(0, "FFF").object(0, 0, 0, ObjectType.ZAP)
                .object(2, 0, 0, ObjectType.PSH).build(), SimOptions.DEFAULT);
    }

    @Test
    void theButtonsShowTheRegionOfTheirStateAndGreyIsATint() {
        AssetLibrary assets = assets();
        PanelView view = new PanelView(assets, assets.font());
        Simulation sim = zapperAndPusher();

        view.update(LAYOUT, false, new PanelState(), sim, SpiderId.ZAPPER, false, new PauseMenu(), "001) Test",
                "KLONDIKES 0/2", 20f, 30f);

        assertEquals("panel.zapper1", view.shownRegion(PanelButton.ZAPPER));
        assertEquals("panel.pusher0", view.shownRegion(PanelButton.PUSHER));
        assertEquals("panel.grabber0", view.shownRegion(PanelButton.GRABBER));
        assertTrue(view.isGrey(PanelButton.GRABBER), "no grabber in this tower");
        assertTrue(view.isGrey(PanelButton.NORTH), "cameras disabled");
        assertFalse(view.isGrey(PanelButton.PUSHER));
        assertEquals("panel.prg_spc0", view.shownRegion(PanelButton.SLOT_8));
        assertEquals("001) Test", view.titleText());
        assertEquals("KLONDIKES 0/2", view.captionText());
        assertEquals(List.of(), view.menuTexts());
    }

    /**
     * Author's extra (2026-10-14): {@link PanelView#hideCursor()} hides the software cursor regardless of the last
     * {@code update}, and the next {@code update} shows it again (the game's picking and panel clicks keep working
     * either way, since they read the mouse position directly, not this sprite).
     */
    @Test
    void hideCursorHidesItAndTheNextUpdateShowsItAgain() {
        AssetLibrary assets = assets();
        PanelView view = new PanelView(assets, assets.font());
        Simulation sim = zapperAndPusher();
        view.update(LAYOUT, false, new PanelState(), sim, SpiderId.ZAPPER, false, new PauseMenu(), "", "", 20f, 30f);
        assertTrue(view.cursorShown());

        view.hideCursor();
        assertFalse(view.cursorShown());

        view.update(LAYOUT, false, new PanelState(), sim, SpiderId.ZAPPER, false, new PauseMenu(), "", "", 20f, 30f);
        assertTrue(view.cursorShown(), "the next update shows it again");
    }

    @Test
    void captionTextIsLeftAlignedFromVirtualX512NotCentredOverTheTransparentHalf() {
        AssetLibrary assets = assets();
        PanelView view = new PanelView(assets, assets.font());
        Simulation sim = zapperAndPusher();
        float scale = LAYOUT.scale();

        view.update(LAYOUT, false, new PanelState(), sim, SpiderId.ZAPPER, false, new PauseMenu(), "001) Test",
                "KLONDIKES 0/2  ROBOTS 0/1  TIME 1:58", 20f, 30f);
        PanelLayout.Rect expanded = LAYOUT.caption(false);
        assertEquals(expanded.x() + PanelLayout.CAPTION_TEXT_X * scale, view.captionTextX(), 1e-4f,
                "M1: left-aligned from virtual x = 512 (SJGL Label default BEGIN)");

        view.update(LAYOUT, true, new PanelState(), sim, SpiderId.ZAPPER, false, new PauseMenu(), "001) Test",
                "KLONDIKES 0/2", 20f, 30f);
        PanelLayout.Rect collapsed = LAYOUT.caption(true);
        assertEquals(collapsed.x() + PanelLayout.CAPTION_TEXT_X * scale, view.captionTextX(), 1e-4f,
                "M1 applies to the collapsed strip too");
    }

    @Test
    void theExpandedButtonAreaGetsTheSemiTransparentGroupFill() {
        AssetLibrary assets = assets();
        PanelView view = new PanelView(assets, assets.font());
        Simulation sim = zapperAndPusher();

        view.update(LAYOUT, false, new PanelState(), sim, SpiderId.ZAPPER, false, new PauseMenu(), "", "", 0f, 0f);
        assertEquals("gui.group_outset_left_top", view.fillRegion(0));
        assertEquals("gui.group_outset_background", view.fillRegion(4));
        assertEquals("gui.group_outset_right_bottom", view.fillRegion(8));

        view.update(LAYOUT, true, new PanelState(), sim, SpiderId.ZAPPER, false, new PauseMenu(), "", "", 0f, 0f);
        for (int i = 0; i < 9; i++) {
            assertNull(view.fillRegion(i), "no fill while collapsed");
        }
    }

    @Test
    void collapsedHidesTheButtonsAndPausedShowsTheMenuInTheTitleBar() {
        AssetLibrary assets = assets();
        PanelView view = new PanelView(assets, assets.font());
        Simulation sim = zapperAndPusher();
        PauseMenu menu = new PauseMenu();
        menu.open();

        view.update(LAYOUT, true, new PanelState(), sim, SpiderId.ZAPPER, true, menu, "001) Test", "", 0f, 0f);

        assertNull(view.shownRegion(PanelButton.ZAPPER), "collapsed");
        assertEquals("EXIT", view.titleText());
        assertEquals(List.of("ABANDON", "RESTART"), view.menuTexts());
    }

    @Test
    void anAtlasSpriteMapsItsRegionTopRowFirstAndRejectsAnUnknownRegion() {
        AssetLibrary assets = assets();
        AtlasSprite sprite = new AtlasSprite("test", assets.guiAtlas(), assets.guiMaterial());

        sprite.show("panel.zapper0", new PanelLayout.Rect(15f, 188f, 250f, 60f), 0f,
                sprite.geometry().getMaterial());

        // panel.zapper0 = (12, 520, 200 x 60) in the 1024 x 1024 atlas; bottom vertices take the region's bottom row
        FloatBuffer uv = (FloatBuffer) sprite.geometry().getMesh().getBuffer(Type.TexCoord).getData();
        float[] actual = new float[8];
        uv.rewind();
        uv.get(actual);
        float u0 = 12f / 1024f;
        float u1 = 212f / 1024f;
        float top = 520f / 1024f;
        float bottom = 580f / 1024f;
        assertArrayEquals(new float[] {u0, bottom, u1, bottom, u1, top, u0, top}, actual, 1e-6f);
        assertEquals(250f, sprite.geometry().getLocalScale().x, 1e-6f);
        assertTrue(sprite.isShown());
        assertThrows(AssetLoadException.class, () -> sprite.show("panel.nope", new PanelLayout.Rect(0, 0, 1, 1), 0f,
                sprite.geometry().getMaterial()));
    }

    /**
     * The cap band of the ToB 2009 font, measured in the font data (round 2, item 1): {@code tob-font.fnt} gives every
     * glyph {@code yoffset} 0 and the full cell height, so the band is measured in the glyph cells of the capitals
     * T, E and H in {@code tob-font_0.png}: the first and one past the last inked row (top of the capitals to the
     * baseline). Returns {top, bottom, cell height}.
     */
    private static float[] capBand() throws IOException {
        Path dir = GeneratedAssets.root().resolve("gui");
        BufferedImage image = ImageIO.read(dir.resolve("tob-font_0.png").toFile());
        int cell = -1;
        int top = Integer.MAX_VALUE;
        int bottom = -1;
        for (String line : Files.readAllLines(dir.resolve("tob-font.fnt"))) {
            if (line.startsWith("common ")) {
                cell = fields(line).get("lineHeight");
            }
            if (!line.startsWith("char ")) {
                continue;
            }
            Map<String, Integer> c = fields(line);
            char ch = (char) c.get("id").intValue();
            if ("TEH".indexOf(ch) < 0) {
                continue;
            }
            for (int row = 0; row < c.get("height"); row++) {
                for (int col = 0; col < c.get("width"); col++) {
                    if ((image.getRGB(c.get("x") + col, c.get("y") + row) >>> 24) > 40) {
                        top = Math.min(top, c.get("yoffset") + row);
                        bottom = Math.max(bottom, c.get("yoffset") + row + 1);
                    }
                }
            }
        }
        return new float[] {top, bottom, cell};
    }

    private static Map<String, Integer> fields(String line) {
        Map<String, Integer> c = new HashMap<>();
        for (String pair : line.substring(line.indexOf(' ')).trim().split(" +")) {
            String[] kv = pair.split("=");
            if (kv.length == 2 && kv[1].matches("-?\\d+")) {
                c.put(kv[0], Integer.parseInt(kv[1]));
            }
        }
        return c;
    }

    /**
     * The vertical centre of a one-line text's cap band (round 2, item 1): jME's {@link BitmapText} spans one font
     * cell per line from its local translation (the top) downwards; the band's rows come from the font data
     * ({@link #capBand()}), not from the ink of the text itself, so descenders or a missing capital do not move it.
     * Measured from the text's world bound, so the test does not trust the top-left convention it checks.
     */
    private static float capCentre(BitmapText text, float[] band) {
        Spatial root = text;
        while (root.getParent() != null) {
            root = root.getParent();
        }
        root.updateLogicalState(0f);
        root.updateGeometricState();
        BoundingBox bound = (BoundingBox) text.getWorldBound();
        float top = bound.getCenter().y + bound.getYExtent();
        float cell = 2f * bound.getYExtent();
        assertEquals(text.getLineHeight(), cell, 0.01f, text.getText() + ": one line, one font cell high");
        return top - cell * (band[0] + band[1]) / 2f / band[2];
    }

    private static float centre(PanelLayout.Rect rect) {
        return rect.y() + rect.height() / 2f;
    }

    /**
     * Acceptance fix 1 (author's screenshot RToB-Screenshot-03.png): every panel and menu text is centred vertically
     * in its face – the title bar's and caption strip's grey face (from row {@value PanelLayout#STRIP_FACE_TOP} of
     * the strip down to the bevel at row {@value PanelLayout#STRIP_FACE_BOTTOM}), the menu bar's face, a menu row –
     * within a pixel, as in ToB 2009
     * (RToB-Screenshot-01/-02.png).
     */
    @Test
    void everyPanelMenuAndDialogTextHasItsCapBandCentredInItsFace() throws IOException {
        AssetLibrary assets = assets();
        float[] band = capBand();
        PanelView view = new PanelView(assets, assets.font());
        Simulation sim = zapperAndPusher();
        for (PanelLayout layout : List.of(PanelLayout.fullWidth(1280, 720), PanelLayout.fullWidth(1680, 980),
                PanelLayout.fullWidth(1024, 768))) {
            for (boolean collapsed : new boolean[] {false, true}) {
                view.update(layout, collapsed, new PanelState(), sim, SpiderId.ZAPPER, false, new PauseMenu(),
                        "009) Tutorial 9 - Puzzle B (Pete Cooke)", "ROBOTS 0/4", 20f, 30f);
                String at = layout.width() + "x" + layout.height() + (collapsed ? " collapsed" : "");
                assertEquals(centre(layout.titleTextFace()), capCentre(view.titleLabel(), band), 1f, "title " + at);
                assertEquals(centre(layout.captionTextFace(collapsed)), capCentre(view.captionLabel(), band), 1f,
                        "caption " + at);
            }
            PauseMenu menu = new PauseMenu();
            menu.open();
            view.update(layout, false, new PanelState(), sim, SpiderId.ZAPPER, true, menu, "", "", 0f, 0f);
            assertEquals(centre(layout.menuTitleFace()), capCentre(view.titleLabel(), band), 1f, "EXIT");
            for (int row = 0; row < 2; row++) {
                assertEquals(centre(layout.menuRow(row)), capCentre(view.menuLabel(row), band), 1f,
                        view.menuLabel(row).getText());
            }
            menu.key(GameAction.BACK);
            menu.key(GameAction.MENU_CONFIRM);
            assertEquals(PauseMenu.Level.CONFIRM, menu.level());
            for (boolean collapsed : new boolean[] {false, true}) {
                view.update(layout, collapsed, new PanelState(), sim, SpiderId.ZAPPER, true, menu, "", "", 0f, 0f);
                assertEquals(List.of(), view.menuTexts(), "the drop-down closes behind the dialog");
                ConfirmDialogLayout dialog = ConfirmDialogLayout.of(layout, collapsed);
                String at = layout.width() + "x" + layout.height() + (collapsed ? " collapsed" : "");
                assertEquals(centre(dialog.captionTextFace()), capCentre(view.dialogCaptionLabel(), band), 1f,
                        "dialog caption " + at);
                assertEquals(centre(dialog.messageTextFace()), capCentre(view.dialogQuestionLabel(), band), 1f,
                        "question " + at);
                for (Answer answer : Answer.values()) {
                    assertEquals(centre(dialog.buttonTextFace(answer)), capCentre(view.dialogButtonLabel(answer),
                            band), 1f, answer + " " + at);
                }
            }
        }
    }

    /**
     * Real data: the cap band of T, E and H is rows 8 to 28 of the 32-row cell (what {@link TextPlacement} centres);
     * every capital and digit lies in it.
     */
    @Test
    void theFontInksCapitalsAndDigitsInTheCapBandTextPlacementCentres() throws IOException {
        assumeTrue(GeneratedAssets.available(), "generated assets not available");
        assertArrayEquals(new float[] {TextPlacement.CAP_TOP, TextPlacement.BASELINE, TextPlacement.CELL}, capBand());
        Path dir = GeneratedAssets.root().resolve("gui");
        BufferedImage image = ImageIO.read(dir.resolve("tob-font_0.png").toFile());
        int checked = 0;
        for (String line : Files.readAllLines(dir.resolve("tob-font.fnt"))) {
            if (!line.startsWith("char ")) {
                continue;
            }
            Map<String, Integer> c = new HashMap<>();
            for (String pair : line.substring(5).trim().split(" +")) {
                String[] kv = pair.split("=");
                c.put(kv[0], Integer.parseInt(kv[1]));
            }
            char ch = (char) c.get("id").intValue();
            if (!(ch >= 'A' && ch <= 'Z' || ch >= '0' && ch <= '9')) {
                continue;
            }
            assertEquals((int) TextPlacement.CELL, c.get("height"), ch + " height");
            assertEquals(0, c.get("yoffset"), ch + " yoffset");
            int first = -1;
            int last = -1;
            for (int row = 0; row < c.get("height"); row++) {
                for (int col = 0; col < c.get("width"); col++) {
                    if ((image.getRGB(c.get("x") + col, c.get("y") + row) >>> 24) > 40) {
                        first = first < 0 ? row : first;
                        last = row;
                        break;
                    }
                }
            }
            assertEquals((int) TextPlacement.CAP_TOP, first, ch + " first inked row");
            assertEquals((int) TextPlacement.BASELINE - 1, last, ch + " last inked row");
            checked++;
        }
        assertEquals(36, checked, "A-Z and 0-9");
    }

    /**
     * Round 2, item 2: ARE YOU SURE? is a ToB 2009 window (caption, frame, dark face) with YES and NO buttons; the
     * caption names the choice, the highlighted button shows ToB 2009's focus marks {@code <...>}, and the drop-down
     * menu is closed behind it.
     */
    @Test
    void theConfirmationIsADialogWithTheHighlightedButtonMarked() {
        AssetLibrary assets = assets();
        PanelView view = new PanelView(assets, assets.font());
        Simulation sim = zapperAndPusher();
        PauseMenu menu = new PauseMenu();
        menu.open();
        view.update(LAYOUT, false, new PanelState(), sim, SpiderId.ZAPPER, true, menu, "", "", 0f, 0f);
        assertFalse(view.dialogShown(), "EXIT level: no dialog");

        menu.key(GameAction.FORWARD);
        menu.key(GameAction.MENU_CONFIRM);
        view.update(LAYOUT, false, new PanelState(), sim, SpiderId.ZAPPER, true, menu, "", "", 0f, 0f);
        assertTrue(view.dialogShown());
        assertEquals("RESTART", view.dialogCaptionLabel().getText());
        assertEquals(PauseMenu.QUESTION, view.dialogQuestionLabel().getText());
        assertEquals("YES", view.dialogButtonLabel(Answer.YES).getText());
        assertEquals("<NO>", view.dialogButtonLabel(Answer.NO).getText(), "NO preselected");
        assertEquals(List.of(), view.menuTexts());
        assertEquals("EXIT", view.titleText(), "the menu bar keeps its title");
        assertEquals("gui.window_background", view.dialogRegion(4), "the window face");
        assertEquals("gui.button_outset_background", view.dialogRegion(9 * 4 + 4), "the YES button");

        menu.key(GameAction.TURN_LEFT);
        view.update(LAYOUT, false, new PanelState(), sim, SpiderId.ZAPPER, true, menu, "", "", 0f, 0f);
        assertEquals("<YES>", view.dialogButtonLabel(Answer.YES).getText());
        assertEquals("NO", view.dialogButtonLabel(Answer.NO).getText());

        menu.escape();
        view.update(LAYOUT, false, new PanelState(), sim, SpiderId.ZAPPER, true, menu, "", "", 0f, 0f);
        assertFalse(view.dialogShown(), "NO/Esc: gone");
        assertEquals(List.of("ABANDON", "RESTART"), view.menuTexts());
    }
}
