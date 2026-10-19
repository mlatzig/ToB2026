package de.latzig.game.tob.client.logic.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import de.latzig.game.tob.client.logic.Texts;
import de.latzig.game.tob.client.logic.frontend.Campaign;
import de.latzig.game.tob.client.logic.frontend.CampaignTest;
import de.latzig.game.tob.client.logic.frontend.DoubleClick;
import de.latzig.game.tob.client.logic.frontend.GameFlow;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Hit;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Widget;
import de.latzig.game.tob.client.logic.frontend.ProfileStore;
import de.latzig.game.tob.client.logic.frontend.StartScreens;
import de.latzig.game.tob.client.logic.game.PanelLayout;
import de.latzig.game.tob.client.logic.ui.Drawing;
import de.latzig.game.tob.client.logic.ui.ScreenFade;
import de.latzig.game.tob.client.logic.ui.StartLayout;
import de.latzig.game.tob.client.logic.ui.UiKey;
import de.latzig.game.tob.core.io.TowerLibraryWriter;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.model.TowerLibrary;

/** Phase 6b spec decision 3: the 3D preview's place on the designer's screen and its mouse and keys. */
class EditorPreviewInputTest {

    private static final Texts TEXTS = Texts.english();
    private static final Optional<Hit> PREVIEW = Optional.of(Hit.of(Widget.EDITOR_PREVIEW));

    private static TowerDesigner designer(Path dir) {
        return new TowerDesigner(new TowerLibraries(dir.resolve("assets"), dir.resolve("user")), TEXTS);
    }

    private static StartScreens.Frame frame(TowerDesigner designer, int w, int h) {
        return EditorScreens.draw(designer, TEXTS, new StartLayout(w, h), -1f, -1f, 0.0);
    }

    private static boolean near(PanelLayout.Rect a, PanelLayout.Rect b) {
        return Math.abs(a.x() - b.x()) < 1e-2f && Math.abs(a.y() - b.y()) < 1e-2f
                && Math.abs(a.width() - b.width()) < 1e-2f && Math.abs(a.height() - b.height()) < 1e-2f;
    }

    @ParameterizedTest(name = "{0} x {1}")
    @CsvSource({"1024,768", "1280,720", "1680,720"})
    void thePreviewFillsTheReservedAreaAboveThePaletteGroupAndUnderTheMenus(int w, int h, @TempDir Path dir) {
        TowerDesigner designer = designer(dir);
        StartScreens.Frame frame = frame(designer, w, h);
        PanelLayout.Rect reserved = EditorScreens.preview(new StartLayout(w, h));

        List<Drawing.Viewport> viewports = frame.drawing().viewports();
        assertEquals(1, viewports.size());
        assertTrue(near(reserved, viewports.get(0).rect()), viewports.get(0) + " at " + reserved);
        assertEquals(PREVIEW, frame.hit(reserved.x() + 5f, reserved.y() + 5f));
        float cx = reserved.x() + reserved.width() / 2f;
        float cy = reserved.y() + reserved.height() / 2f;
        int lastPanel = frame.drawing().sprites().stream()
                .filter(s -> s.region().startsWith("gui.group") && s.rect().contains(cx, cy))
                .mapToInt(Drawing.Sprite::order).max().orElseThrow();
        assertTrue(viewports.get(0).order() > lastPanel, "in front of the palette's group");

        designer.press(Optional.of(new Hit(Widget.EDITOR_MENU, TowerDesigner.Menu.OPTIONS.ordinal())), false);
        StartScreens.Frame menu = frame(designer, w, h);
        int firstMenu = menu.drawing().sprites().stream().filter(s -> s.region().startsWith("gui.menu_"))
                .mapToInt(Drawing.Sprite::order).min().orElseThrow();
        assertTrue(menu.drawing().viewports().get(0).order() < firstMenu, "an open menu lies over the preview");
    }

    @Test
    void onTheFullScreenThePreviewCoversEverythingAndIsTheOnlyThingToClick(@TempDir Path dir) {
        TowerDesigner designer = designer(dir);
        designer.press(PREVIEW, false);
        designer.release();
        designer.press(PREVIEW, false);
        assertTrue(designer.preview().maximized(), "a double click");

        StartScreens.Frame frame = frame(designer, 1280, 720);

        Drawing.Viewport viewport = frame.drawing().viewports().get(0);
        assertTrue(near(new PanelLayout.Rect(0f, 0f, 1280f, 720f), viewport.rect()));
        int last = frame.drawing().sprites().stream().mapToInt(Drawing.Sprite::order).max().orElseThrow();
        assertTrue(viewport.order() > last, "drawn after everything of the screen");
        assertEquals(1, frame.areas().size());
        assertEquals(PREVIEW, frame.hit(640f, 360f));
    }

    @Test
    void dragTurnsRightClickResetsAndNothingIsPlaced(@TempDir Path dir) {
        TowerDesigner designer = designer(dir);
        TowerDefinition before = designer.model().tower();

        designer.mouse(900f, 100f);
        designer.press(PREVIEW, false);
        designer.mouse(950f, 90f);
        designer.pointer(PREVIEW, true);
        assertEquals(50 * PreviewCamera.RADIANS_PER_PIXEL, designer.preview().yaw(), 1e-5f);
        assertTrue(designer.preview().pitch() > Math.toRadians(PreviewCamera.RESET_PITCH_DEGREES));
        designer.release();
        assertFalse(designer.preview().grabbed());
        designer.press(PREVIEW, true);
        assertEquals(0f, designer.preview().yaw(), 0f, "the right click resets the view");
        assertEquals(before, designer.model().tower(), "nothing placed or removed");
        assertFalse(designer.model().canUndo());
    }

    @Test
    void onTheFullScreenEscReturnsUndoStillWorksOtherKeysDoNothing(@TempDir Path dir) {
        TowerDesigner designer = designer(dir);
        designer.press(Optional.of(new Hit(Widget.EDITOR_CELL, 3)), false);
        designer.release();
        designer.preview().toggle();

        designer.key(UiKey.RIGHT);
        designer.key(UiKey.SPACE);
        assertEquals(3, designer.model().x(), "the arrows and Space do nothing on the full screen");
        designer.key(UiKey.UNDO);
        assertFalse(designer.model().canUndo(), "Ctrl+Z undid the placement");
        designer.key(UiKey.REDO);
        assertTrue(designer.model().canUndo());
        designer.key(UiKey.ESCAPE);
        assertFalse(designer.preview().maximized());
        designer.key(UiKey.LEFT);
        assertEquals(2, designer.model().x(), "back on the small preview: the keys edit again");
    }

    @Test
    void theFlowPassesTheMouseAndTheClockForTheDoubleClick(@TempDir Path dir) throws IOException {
        Path shipped = Files.createDirectories(dir.resolve("assets-towers"));
        new TowerLibraryWriter().write(new TowerLibrary("original", "Original", "test",
                List.of(EditorModelTest.playable("original-000"))), shipped.resolve("original.json"));
        GameFlow flow = new GameFlow(TEXTS, new ProfileStore(dir.resolve("user")));
        flow.designer(new TowerLibraries(shipped, dir.resolve("user")));
        flow.loaded(Campaign.of(CampaignTest.towers()));
        for (int i = 0; i < 4; i++) {
            flow.update(ScreenFade.SECONDS);
        }
        flow.click(Hit.of(Widget.MENU_DESIGNER));
        for (int i = 0; i < 4; i++) {
            flow.update(ScreenFade.SECONDS);
        }
        TowerDesigner designer = flow.designer();

        flow.editorMouse(800f, 100f);
        flow.editorPress(PREVIEW, false);
        flow.editorMouse(820f, 100f);
        assertEquals(20 * PreviewCamera.RADIANS_PER_PIXEL, designer.preview().yaw(), 1e-5f);
        flow.editorRelease();
        flow.update(DoubleClick.INTERVAL + 0.1);
        flow.click(Hit.of(Widget.EDITOR_PREVIEW));
        assertFalse(designer.preview().maximized(), "the first press was too long ago");
        flow.update(0.1);
        flow.click(Hit.of(Widget.EDITOR_PREVIEW));
        assertTrue(designer.preview().maximized());
        flow.key(UiKey.ESCAPE);
        assertFalse(designer.preview().maximized());
        assertEquals(GameFlow.Screen.EDITOR, flow.screen(), "Esc on the full screen does not leave the designer");
    }
}
