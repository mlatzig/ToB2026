package de.latzig.game.tob.client.logic.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import de.latzig.game.tob.client.logic.Texts;
import de.latzig.game.tob.client.logic.editor.TowerDesigner.Command;
import de.latzig.game.tob.client.logic.editor.TowerDesigner.Menu;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Hit;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Widget;
import de.latzig.game.tob.client.logic.frontend.StartScreens;
import de.latzig.game.tob.client.logic.game.PanelLayout;
import de.latzig.game.tob.client.logic.ui.Drawing;
import de.latzig.game.tob.client.logic.ui.NineSlice;
import de.latzig.game.tob.client.logic.ui.StartLayout;
import de.latzig.game.tob.client.logic.ui.UiKey;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.TowerGrid;

/** Phase 6b spec decision 2: ToB 2009's editor layout, drawn engine-free, and where to click. */
public class EditorScreensTest {

    private static final Texts TEXTS = Texts.english();
    private static final StartLayout LAYOUT = new StartLayout(1024, 768);

    private static TowerDesigner designer(Path dir) {
        return new TowerDesigner(new TowerLibraries(dir.resolve("assets"), dir.resolve("user")), TEXTS);
    }

    private static StartScreens.Frame frame(TowerDesigner designer) {
        return EditorScreens.draw(designer, TEXTS, LAYOUT, -1f, -1f, 0.0);
    }

    private static List<String> texts(StartScreens.Frame frame) {
        return frame.drawing().labels().stream().map(Drawing.Label::text).toList();
    }

    private static Map<Widget, Long> areas(StartScreens.Frame frame) {
        return frame.areas().stream().collect(Collectors.groupingBy(a -> a.hit().widget(), Collectors.counting()));
    }

    private static PanelLayout.Rect centre(StartScreens.Frame frame, Hit hit) {
        return frame.areas().stream().filter(a -> a.hit().equals(hit)).findFirst().orElseThrow().rect();
    }

    @Test
    void theLayoutIsToB2009sWithThePalettesPackedAndRoomForThePreview() {
        assertEquals(1024f / 6f, EditorScreens.LEVEL_WIDTH, 1e-3f);
        assertEquals(508.333f, EditorScreens.TOWER_HEIGHT, 1e-3f);
        assertEquals(478.333f, EditorScreens.TOWER_WIDTH, 1e-3f);
        assertEquals(245.667f, EditorScreens.ELEMENTS_WIDTH, 1e-3f);
        assertEquals(7, EditorScreens.PALETTE_ROWS, "one row of base elements, six of objects, no blank row");
        PanelLayout.Rect preview = EditorScreens.PREVIEW;
        assertTrue(preview.y() >= EditorScreens.PALETTE_TOP + 7 * EditorScreens.ENTRY, "below the palette");
        assertEquals(EditorScreens.TOWER_TOP + EditorScreens.TOWER_HEIGHT - 2f * EditorScreens.BORDER,
                preview.y() + preview.height(), 1e-3f, "acceptance change 2: down to the palette group's bottom");
        assertTrue(preview.height() > 170f && preview.width() > 230f, preview.toString());
    }

    @Test
    void theScreenShowsTheMenuBarTheListTheGridThePaletteAndTheLevels(@TempDir Path dir) {
        StartScreens.Frame frame = frame(designer(dir));

        List<String> texts = texts(frame);
        for (String text : List.of("File", "Edit", "Options", "Toolbox", "(NOT SAVED)", "001: (NO TITLE)",
                "  1: ", "X: 1,Y: 1", "No spiders placed", "Floor", "Objects: 0/20", "Level 1",
                "Level 6")) {
            assertTrue(texts.contains(text), text + " in " + texts);
        }
        assertEquals(Map.of(Widget.EDITOR_MENU, 4L, Widget.EDITOR_LIST, 2L, Widget.EDITOR_TITLE, 1L,
                Widget.EDITOR_CELL, 256L, Widget.EDITOR_PALETTE, 40L, Widget.EDITOR_LEVEL, 6L,
                Widget.EDITOR_PREVIEW, 1L), areas(frame));
        Set<String> regions = frame.drawing().sprites().stream().map(Drawing.Sprite::region)
                .collect(Collectors.toSet());
        assertTrue(regions.containsAll(Set.of("gui.window_mid_menu", "editor.flr", "editor.box", "editor.lfu1",
                "editor.lfd1", "editor.zap", "editor.exc", "editor.cur")), regions.toString());
    }

    @Test
    void aCellIsClickedAtItsPlaceAndPlacedElementsAndMarksAreDrawn(@TempDir Path dir) {
        TowerDesigner designer = designer(dir);
        StartScreens.Frame empty = frame(designer);
        PanelLayout.Rect cell = centre(empty, new Hit(Widget.EDITOR_CELL, 3 * TowerGrid.WIDTH + 2));
        float x = cell.x() + cell.width() / 2f;
        float y = cell.y() + cell.height() / 2f;
        assertEquals(Optional.of(new Hit(Widget.EDITOR_CELL, 3 * TowerGrid.WIDTH + 2)), empty.hit(x, y));

        designer.press(empty.hit(x, y), false);
        designer.release();
        designer.model().tool(EditorElement.of(ObjectType.BFL));
        designer.press(Optional.of(new Hit(Widget.EDITOR_CELL, 5)), false);
        designer.release();
        StartScreens.Frame frame = frame(designer);

        List<Drawing.Sprite> sprites = frame.drawing().sprites();
        assertTrue(sprites.stream().anyMatch(s -> s.region().equals("editor.flr") && s.tint() == Drawing.WHITE));
        assertTrue(sprites.stream().anyMatch(s -> s.region().equals("editor.bfl")));
        assertTrue(sprites.stream().anyMatch(s -> s.region().equals("editor.cur") && s.tint() == EditorFindings.RED),
                "the bonus flag without floor is marked");
        assertTrue(texts(frame).contains("No floor for object"));
        assertTrue(sprites.stream().anyMatch(s -> s.region().equals("editor.flr") && s.tint() == Drawing.WHITE
                && s.rect().width() < 15f), "the level strip's map of the current level");
    }

    @Test
    void thePalettesCaptionNamesTheSelectedElement(@TempDir Path dir) {
        TowerDesigner designer = designer(dir);
        designer.model().tool(EditorElement.of(ObjectType.BFL));

        StartScreens.Frame frame = frame(designer);

        List<String> texts = texts(frame);
        String name = TEXTS.get(EditorElement.of(ObjectType.BFL).textKey());
        assertFalse(texts.contains("Elements"), "acceptance change 2: the name replaces the caption");
        assertEquals(1L, texts.stream().filter(name::equals).count(), "no second name line below: " + texts);
        Drawing.Label caption = frame.drawing().labels().stream().filter(l -> l.text().equals(name)).findFirst()
                .orElseThrow();
        assertEquals(EditorScreens.CAPTION - EditorScreens.BORDER, caption.rect().height(), 1e-3f, "the caption");
    }

    @Test
    void aLongElementNameIsFittedIntoTheCaption(@TempDir Path dir) {
        TowerDesigner designer = designer(dir);
        EditorElement longest = EditorElement.PALETTE.stream()
                .max((a, b) -> TEXTS.get(a.textKey()).length() - TEXTS.get(b.textKey()).length()).orElseThrow();
        designer.model().tool(longest);

        String name = TEXTS.get(longest.textKey());
        Drawing.Label caption = frame(designer).drawing().labels().stream().filter(l -> l.text().equals(name))
                .findFirst().orElseThrow();
        assertTrue(caption.size() <= EditorScreens.TEXT, name);
        assertTrue(StartScreens.maxChars(caption.rect().width(), caption.size()) >= name.length(), name);
    }

    @Test
    void anOpenMenuListsItsEntriesTheGreyedOnesGrey(@TempDir Path dir) {
        TowerDesigner designer = designer(dir);
        designer.press(Optional.of(new Hit(Widget.EDITOR_MENU, Menu.OPTIONS.ordinal())), false);

        StartScreens.Frame frame = frame(designer);

        Function<String, Drawing.Label> label = text -> frame.drawing().labels().stream()
                .filter(l -> l.text().equals(text)).findFirst().orElseThrow();
        assertEquals(Drawing.BLACK, label.apply("Settings").colour(), "Phase 6b-2: enabled");
        assertEquals(Drawing.BLACK, label.apply("Play Tower").colour());
        assertEquals(Drawing.WHITE, label.apply("Options").colour(), "the open title");
        assertEquals(4L, areas(frame).get(Widget.EDITOR_ITEM));
        PanelLayout.Rect play = centre(frame, new Hit(Widget.EDITOR_ITEM, Command.PLAY.ordinal()));
        assertEquals(Optional.of(new Hit(Widget.EDITOR_ITEM, Command.PLAY.ordinal())),
                frame.hit(play.x() + 5f, play.y() + 5f), "the menu lies over the grid");
    }

    @Test
    void aDialogIsTheOnlyThingToClick(@TempDir Path dir) throws IOException {
        TowerDesigner designer = designer(dir);
        designer.run(Command.SCROLL);

        StartScreens.Frame frame = frame(designer);

        assertEquals(Map.of(Widget.EDITOR_DIALOG, 7L), areas(frame), "4 directions, the switch, OK, Cancel");
        assertTrue(texts(frame).containsAll(List.of("Scroll Tower/Level", "Direction", "North", "West",
                "Only current Level?", "OK", "Cancel")));

        designer.key(UiKey.ESCAPE);
        designer.run(Command.SAVE_AS);
        StartScreens.Frame save = frame(designer);
        assertTrue(texts(save).containsAll(List.of("Save Library As", "Libraries", "Name", "Save", "Cancel")));
        assertFalse(areas(save).containsKey(Widget.EDITOR_CELL));
    }

    @Test
    void theEditorsVirtualWidthGrowsWithTheAspectRatioFrom4To3UpTo21To9() {
        float max = EditorScreens.MAX_WIDTH;
        assertEquals(1792f, max, 1e-3f, "21:9 at the reference height 768");
        assertEquals(1024f, new StartLayout(1280, 720).virtualWidth(), 1e-3f, "the start screens stay 4:3");
        assertEquals(1024f, StartLayout.wide(1024, 768, max).virtualWidth(), 1e-3f);
        assertEquals(1024f, StartLayout.wide(1280, 1024, max).virtualWidth(), 1e-3f, "narrower than 4:3: 4:3");
        assertEquals(1280f * 768f / 720f, StartLayout.wide(1280, 720, max).virtualWidth(), 1e-3f, "16:9");
        assertEquals(1792f, StartLayout.wide(2520, 1080, max).virtualWidth(), 1e-3f, "21:9");
        StartLayout clamped = StartLayout.wide(3840, 1080, max);
        assertEquals(1792f, clamped.virtualWidth(), 1e-3f, "32:9 is clamped to 21:9");
        assertEquals(new PanelLayout.Rect(660f, 0f, 2520f, 1080f), clamped.rect(0f, 0f, 1792f, 768f),
                "centred between bars");
        PanelLayout.Rect full = StartLayout.wide(1280, 720, max).rect(0f, 0f, 1280f * 768f / 720f, 768f);
        assertEquals(0f, full.x(), 1e-3f, "16:9 fills the window");
        assertEquals(1280f, full.width(), 1e-3f);
    }

    /** The editor's virtual screen in a window: its scale, its virtual width and its left edge in pixels. */
    private record Wide(float scale, float width, float left) {

        static Wide of(int w, int h) {
            float scale = h / 768f;
            float width = Math.min(1792f, Math.max(1024f, w / scale));
            return new Wide(scale, width, (w - width * scale) / 2f);
        }

        /** The screen x of the virtual x {@code virtual}. */
        float x(float virtual) {
            return left + virtual * scale;
        }
    }

    private static List<PanelLayout.Rect> rects(StartScreens.Frame frame, Widget widget) {
        return frame.areas().stream().filter(a -> a.hit().widget() == widget).map(StartScreens.Area::rect).toList();
    }

    private static PanelLayout.Rect bounds(List<PanelLayout.Rect> rects) {
        float x0 = (float) rects.stream().mapToDouble(PanelLayout.Rect::x).min().orElseThrow();
        float y0 = (float) rects.stream().mapToDouble(PanelLayout.Rect::y).min().orElseThrow();
        float x1 = (float) rects.stream().mapToDouble(r -> r.x() + r.width()).max().orElseThrow();
        float y1 = (float) rects.stream().mapToDouble(r -> r.y() + r.height()).max().orElseThrow();
        return new PanelLayout.Rect(x0, y0, x1 - x0, y1 - y0);
    }

    private static PanelLayout.Rect sprite(StartScreens.Frame frame, String region) {
        return frame.drawing().sprites().stream().filter(s -> s.region().equals(region)).findFirst().orElseThrow()
                .rect();
    }

    @ParameterizedTest(name = "{0} x {1}")
    @CsvSource({"1024,768", "1280,720", "1920,1080", "2520,1080", "3840,1080"})
    void theColumnsKeepTheirWidthsTheGridStaysSquareAndCentredAndTheLevelsSpanTheWidth(int w, int h,
            @TempDir Path dir) {
        Wide wide = Wide.of(w, h);
        float s = wide.scale();
        float b = EditorScreens.BORDER;
        StartScreens.Frame frame = EditorScreens.draw(designer(dir), TEXTS, new StartLayout(w, h), -1f, -1f, 0.0);

        List<PanelLayout.Rect> list = rects(frame, Widget.EDITOR_LIST);
        assertFalse(list.isEmpty());
        for (PanelLayout.Rect row : list) {
            assertEquals(wide.x(2f * b), row.x(), 1e-2f, "the tower list stays at the left");
            assertEquals((EditorScreens.LIBRARY_WIDTH - 4f * b) * s, row.width(), 1e-2f, "and 300 wide");
        }
        List<PanelLayout.Rect> palette = rects(frame, Widget.EDITOR_PALETTE);
        float paletteLeft = wide.width() - EditorScreens.ELEMENTS_WIDTH;
        assertEquals(wide.x(paletteLeft + b), palette.get(0).x(), 1e-2f, "the palette column at the right");
        assertEquals(EditorScreens.ENTRY * s, palette.get(0).width(), 1e-2f, "with ToB 2009's entries");
        PanelLayout.Rect last = palette.get(EditorElement.BASE_COUNT + EditorScreens.PER_ROW - 1);
        assertEquals(wide.x(wide.width() - b), last.x() + last.width(), 1e-2f);
        PanelLayout.Rect preview = EditorScreens.preview(new StartLayout(w, h));
        assertEquals(palette.get(0).x(), preview.x(), 1e-2f, "the 3D preview stays in the palette column");
        assertEquals(EditorScreens.PREVIEW.width() * s, preview.width(), 1e-2f);

        PanelLayout.Rect grid = bounds(rects(frame, Widget.EDITOR_CELL));
        assertEquals(grid.width(), grid.height(), 1e-2f, "the grid is square");
        float size = (EditorScreens.TOWER_WIDTH - 2f * b) * s;
        assertEquals(size, grid.width() * 16.2f / 16f, 1e-2f, "at ToB 2009's size");
        float centre = wide.x((EditorScreens.LIBRARY_WIDTH + paletteLeft) / 2f);
        assertEquals(centre, grid.x() + size / 2f, 1e-2f, "and centred between the list and the palette");

        List<PanelLayout.Rect> levels = rects(frame, Widget.EDITOR_LEVEL);
        assertEquals(6, levels.size());
        for (int level = 0; level < levels.size(); level++) {
            assertEquals(wide.x(level * wide.width() / 6f), levels.get(level).x(), 1e-2f, "level " + level);
            assertEquals(wide.width() / 6f * s, levels.get(level).width(), 1e-2f, "the six share the width");
        }
        PanelLayout.Rect menuLeft = sprite(frame, "gui.window_left_menu");
        PanelLayout.Rect menuRight = sprite(frame, "gui.window_right_menu");
        assertEquals(wide.x(0f), menuLeft.x(), 1e-2f, "the menu bar spans the width");
        assertEquals(wide.x(wide.width()), menuRight.x() + menuRight.width(), 1e-2f);
        PanelLayout.Rect title = centre(frame, Hit.of(Widget.EDITOR_TITLE));
        assertEquals(wide.x(EditorScreens.LIBRARY_WIDTH), title.x(), 1e-2f, "the title strip spans the rest");
        assertEquals(wide.x(wide.width()), title.x() + title.width(), 1e-2f);

        // hit-tests at the corners (GUI pixels, y upwards)
        float top = grid.y() + grid.height();
        assertEquals(Optional.of(new Hit(Widget.EDITOR_CELL, 0)), frame.hit(grid.x() + 1f, top - 1f));
        assertEquals(Optional.of(new Hit(Widget.EDITOR_CELL, TowerGrid.WIDTH - 1)),
                frame.hit(grid.x() + grid.width() - 1f, top - 1f));
        assertEquals(Optional.of(new Hit(Widget.EDITOR_CELL, TowerGrid.WIDTH * TowerGrid.DEPTH - 1)),
                frame.hit(grid.x() + grid.width() - 1f, grid.y() + 1f));
        assertEquals(Optional.of(new Hit(Widget.EDITOR_LEVEL, 0)), frame.hit(wide.x(0f) + 1f, 1f));
        assertEquals(Optional.of(new Hit(Widget.EDITOR_LEVEL, 5)), frame.hit(wide.x(wide.width()) - 1f, 1f));
        assertEquals(Optional.of(new Hit(Widget.EDITOR_PALETTE, EditorElement.BASE_COUNT + EditorScreens.PER_ROW
                - 1)), frame.hit(last.x() + last.width() - 1f, last.y() + last.height() - 1f));
        assertEquals(Optional.of(new Hit(Widget.EDITOR_MENU, 0)), frame.hit(wide.x(b) + 1f, h - 1f));
        if (wide.left() > 1f) {
            assertEquals(Optional.empty(), frame.hit(wide.left() - 1f, 1f), "nothing in the bars");
            assertEquals(Optional.empty(), frame.hit(w - wide.left() + 1f, h - 1f));
        }
    }

    @ParameterizedTest(name = "{0} x {1}")
    @CsvSource({"1024,768", "1280,720", "2520,1080", "3840,1080"})
    void theDialogsAndMenusKeepTheirSizeTheDialogsCentred(int w, int h, @TempDir Path dir) {
        Wide wide = Wide.of(w, h);
        float s = wide.scale();
        float b = EditorScreens.BORDER;
        TowerDesigner designer = designer(dir);
        designer.run(Command.SAVE_AS);
        StartScreens.Frame frame = EditorScreens.draw(designer, TEXTS, new StartLayout(w, h), -1f, -1f, 0.0);

        PanelLayout.Rect cancel = centre(frame, new Hit(Widget.EDITOR_DIALOG, TowerDesigner.DIALOG_CANCEL));
        float dialogRight = w / 2f + EditorScreens.LIBRARY_DIALOG_WIDTH / 2f * s;
        assertEquals(dialogRight - 2f * b * s, cancel.x() + cancel.width(), 1e-2f, "centred on the window");
        assertEquals(EditorScreens.DIALOG_BUTTON * s, cancel.width(), 1e-2f, "at ToB 2009's size");
        float dialogBottom = h / 2f - EditorScreens.LIBRARY_DIALOG_HEIGHT / 2f * s;
        assertEquals(dialogBottom + 3f * b * s, cancel.y(), 1e-2f);

        designer.key(UiKey.ESCAPE);
        designer.press(Optional.of(new Hit(Widget.EDITOR_MENU, Menu.EDIT.ordinal())), false);
        StartScreens.Frame menu = EditorScreens.draw(designer, TEXTS, new StartLayout(w, h), -1f, -1f, 0.0);
        PanelLayout.Rect item = rects(menu, Widget.EDITOR_ITEM).get(0);
        assertEquals(wide.x(b + Menu.EDIT.ordinal() * EditorScreens.MENU_TITLE_WIDTH), item.x(), 1e-2f,
                "below its title");
        assertEquals(EditorScreens.MENU_WIDTH * s, item.width(), 1e-2f);
    }

    private static boolean near(PanelLayout.Rect a, PanelLayout.Rect b) {
        return Math.abs(a.x() - b.x()) < 1e-2f && Math.abs(a.y() - b.y()) < 1e-2f
                && Math.abs(a.width() - b.width()) < 1e-2f && Math.abs(a.height() - b.height()) < 1e-2f;
    }

    /** The opaque black rectangles drawn after the screen's own black background. */
    private static List<PanelLayout.Rect> black(StartScreens.Frame frame) {
        return frame.drawing().sprites().stream().skip(1)
                .filter(s -> s.region().equals(EditorScreens.OPAQUE) && s.tint() == Drawing.BLACK)
                .map(Drawing.Sprite::rect).toList();
    }

    @ParameterizedTest(name = "{0} x {1}")
    @CsvSource({"1024,768", "1280,720", "1680,720"})
    void theCellAreasAreBlackAndTheirMarginsKeepThePanelsGrey(int w, int h, @TempDir Path dir) {
        float s = h / 768f;
        float b = EditorScreens.BORDER;
        StartScreens.Frame frame = EditorScreens.draw(designer(dir), TEXTS, new StartLayout(w, h), -1f, -1f, 0.0);
        List<PanelLayout.Rect> black = black(frame);

        PanelLayout.Rect grid = bounds(rects(frame, Widget.EDITOR_CELL));
        float size = (EditorScreens.TOWER_WIDTH - 2f * b) * s;
        float top = grid.y() + grid.height();
        PanelLayout.Rect cells = new PanelLayout.Rect(grid.x(), top - size, size, size);
        assertTrue(black.stream().anyMatch(r -> near(r, cells)), cells + " in " + black);
        List<PanelLayout.Rect> levels = rects(frame, Widget.EDITOR_LEVEL);
        float map = (EditorScreens.LEVEL_WIDTH - 2f * b) * s;
        for (PanelLayout.Rect level : levels) {
            float levelTop = level.y() + level.height() - (b + EditorScreens.CAPTION) * s;
            PanelLayout.Rect square = new PanelLayout.Rect(level.x() + (level.width() - map) / 2f, levelTop - map,
                    map, map);
            assertTrue(black.stream().anyMatch(r -> near(r, square)), square + " in " + black);
        }
        assertEquals(1 + levels.size(), black.size(), "only the cell areas: the margins stay grey");
        int firstCell = frame.drawing().sprites().stream().filter(sp -> sp.region().startsWith("editor."))
                .mapToInt(Drawing.Sprite::order).min().orElseThrow();
        int lastBlack = frame.drawing().sprites().stream().skip(1)
                .filter(sp -> sp.region().equals(EditorScreens.OPAQUE) && sp.tint() == Drawing.BLACK
                        && near(sp.rect(), cells))
                .mapToInt(Drawing.Sprite::order).max().orElseThrow();
        assertTrue(lastBlack < firstCell, "the black area lies under the grid's elements");
    }

    /** Asserts that {@code frame}'s dialog window has an opaque black backing above everything drawn before it. */
    public static void assertCovered(StartScreens.Frame frame) {
        List<Drawing.Sprite> sprites = frame.drawing().sprites();
        int window = -1;
        for (int i = 0; i < sprites.size(); i++) {
            if (sprites.get(i).region().equals(NineSlice.WINDOW.regions().get(0))) {
                window = i;
            }
        }
        assertTrue(window > 0, "a dialog window");
        List<PanelLayout.Rect> pieces = sprites.subList(window, window + 9).stream().map(Drawing.Sprite::rect)
                .toList();
        Drawing.Sprite backing = sprites.get(window - 1);
        assertEquals(EditorScreens.OPAQUE, backing.region(), "an opaque backing right before the window");
        assertEquals(Drawing.BLACK, backing.tint());
        assertTrue(near(bounds(pieces), backing.rect()), backing.rect() + " covers " + bounds(pieces));
        for (Drawing.Sprite sprite : sprites.subList(0, window - 1)) {
            assertTrue(sprite.order() < backing.order(), "drawn (z) under the backing: " + sprite);
        }
    }

    @ParameterizedTest(name = "{0} x {1}")
    @CsvSource({"1024,768", "1280,720", "1680,720"})
    void theDialogsCoverWhatIsUnderThem(int w, int h, @TempDir Path dir) {
        TowerDesigner designer = designer(dir);
        designer.model().tool(EditorElement.LIFT_UP);
        designer.press(Optional.of(new Hit(Widget.EDITOR_CELL, 8 * TowerGrid.WIDTH + 8)), false);
        designer.release();
        designer.run(Command.SAVE_AS);
        assertCovered(EditorScreens.draw(designer, TEXTS, new StartLayout(w, h), -1f, -1f, 0.0));
        designer.key(UiKey.ESCAPE);
        designer.run(Command.SCROLL);
        assertCovered(EditorScreens.draw(designer, TEXTS, new StartLayout(w, h), -1f, -1f, 0.0));
    }

    @Test
    void aLongTitleIsCutInsteadOfShrunk() {
        String cut = EditorScreens.cut("  6: But Concrete Gives The Game Away (Mode 1)", 270f, EditorScreens.LIST_TEXT);

        assertEquals("  6: But Concrete...", cut);
        assertEquals(StartScreens.maxChars(270f, EditorScreens.LIST_TEXT), cut.length());
        assertEquals("  1: Softly Softly!", EditorScreens.cut("  1: Softly Softly!", 270f, EditorScreens.LIST_TEXT));
    }

    @Test
    void theCursorPulsesInEightStepsFromDarkRedToWhite() {
        Set<Integer> tints = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            tints.add(EditorScreens.pulse(i * 0.013));
        }
        assertEquals(9, tints.size(), "a bounded number of tints (one material each)");
        assertTrue(tints.contains(EditorScreens.DARK_RED));
        assertTrue(tints.contains(Drawing.WHITE));
    }
}
