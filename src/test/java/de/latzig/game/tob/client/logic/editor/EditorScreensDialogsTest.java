package de.latzig.game.tob.client.logic.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import de.latzig.game.tob.client.logic.Texts;
import de.latzig.game.tob.client.logic.editor.TowerDesigner.Command;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Hit;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Widget;
import de.latzig.game.tob.client.logic.frontend.StartScreens;
import de.latzig.game.tob.client.logic.game.PanelLayout;
import de.latzig.game.tob.client.logic.ui.Drawing;
import de.latzig.game.tob.client.logic.ui.StartLayout;
import de.latzig.game.tob.client.logic.ui.UiKey;

/**
 * Phase 6b spec decision 8: the Settings, Effects and colour dialogs drawn in ToB 2009's look. The Effects dialog's
 * moon fields follow the author's changed decision (Task 3 result): no Moon X, Moon Height instead of Moon Position
 * (the new tower's moon y 28 shows as height 55 - 28 = 27), Moon Size editable and stored.
 */
class EditorScreensDialogsTest {

    private static final Texts TEXTS = Texts.english();

    private static TowerDesigner designer(Path dir) {
        return new TowerDesigner(new TowerLibraries(dir.resolve("assets"), dir.resolve("user")), TEXTS);
    }

    private static StartScreens.Frame frame(TowerDesigner designer, int w, int h) {
        return EditorScreens.draw(designer, TEXTS, new StartLayout(w, h), -1f, -1f, 0.0);
    }

    private static List<String> texts(StartScreens.Frame frame) {
        return frame.drawing().labels().stream().map(Drawing.Label::text).toList();
    }

    private static Map<Widget, Long> areas(StartScreens.Frame frame) {
        return frame.areas().stream().collect(Collectors.groupingBy(a -> a.hit().widget(), Collectors.counting()));
    }

    private static PanelLayout.Rect area(StartScreens.Frame frame, Hit hit) {
        return frame.areas().stream().filter(a -> a.hit().equals(hit)).findFirst().orElseThrow().rect();
    }

    @Test
    void settingsShowsToB2009sGroupsLabelsAndControls(@TempDir Path dir) {
        TowerDesigner designer = designer(dir);
        designer.run(Command.SETTINGS);

        StartScreens.Frame frame = frame(designer, 1024, 768);

        assertTrue(texts(frame).containsAll(List.of("Settings", "General", "Title", "Author", "Finishing Conditions",
                "Klondikes", "Robots", "TimeLimit", "Behaviour", "External cams enabled?", "Mine destroys floor?",
                "Timebomb Speed", "Medium", "1", "-", "+", "*", "OK", "Cancel")), texts(frame).toString());
        assertEquals(Map.of(Widget.EDITOR_FIELD, 16L, Widget.EDITOR_DIALOG, 2L), areas(frame),
                "2 text boxes, 3 number boxes and a choice with - and +, 2 switches; OK, Cancel");
        assertEquals(StartScreens.HOVER, frame.drawing().labels().stream().filter(l -> l.text().equals("Title"))
                .findFirst().orElseThrow().colour(), "the focused row's label");
        EditorScreensTest.assertCovered(frame);
    }

    @Test
    void effectsShowsTheMoonFieldsAndTheColoursAndNoSunSize(@TempDir Path dir) {
        TowerDesigner designer = designer(dir);
        designer.run(Command.EFFECTS);

        StartScreens.Frame frame = frame(designer, 1024, 768);

        List<String> texts = texts(frame);
        // Author's changed decision 3 (Task 3 result): Moon Height replaces Moon Position; the new tower's moon
        // y 28 shows as height 55 - 28 = 27; Moon X is no longer shown.
        assertTrue(texts.containsAll(List.of("Effects", "Terrain", "Sky Colors", "Sun Color", "Moon", "North",
                "Grey Moon", "Moon Height", "27", "Moon Size", "Ground Colors", "Ground Height", "Stars", "100",
                "Tower", "Pattern", "Crossed", "Pattern Colors", "Lift Color", "Additional FX", "Lightning?",
                "Meteor?", "Pulse?", "Clouds", "None", "Randomize Colors", "OK", "Cancel")), texts.toString());
        assertFalse(texts.contains("Sun Size"));
        assertFalse(texts.contains("Moon Position"));
        List<Integer> swatches = frame.drawing().sprites().stream().filter(s -> s.region().equals("editor.col"))
                .map(Drawing.Sprite::tint).toList();
        assertEquals(List.of(0x0088CC, 0x000000, 0xFFFFFF, 0x004B4B, 0x006969, 0xA500A5, 0xE10000, 0xFFFFFF),
                swatches, "the eight colour buttons in the tower's colours");
        assertEquals(Map.of(Widget.EDITOR_FIELD, 35L, Widget.EDITOR_DIALOG, 3L), areas(frame),
                "8 colours, 3 switches, 2 number boxes (Moon Height, Ground Height) and 6 choices (Moon, Moon Type, "
                        + "Moon Size, Stars, Pattern, Clouds) with - and +; Randomize Colors, OK, Cancel");
        EditorScreensTest.assertCovered(frame);
    }

    @ParameterizedTest(name = "{0} x {1}")
    @CsvSource({"1024,768", "1280,720", "1680,720"})
    void theDialogsStandLeftOfThePaletteSoThePreviewStaysInView(int w, int h, @TempDir Path dir) {
        TowerDesigner designer = designer(dir);
        PanelLayout.Rect preview = EditorScreens.preview(new StartLayout(w, h));
        for (Command command : List.of(Command.SETTINGS, Command.EFFECTS)) {
            designer.run(command);
            StartScreens.Frame frame = frame(designer, w, h);
            PanelLayout.Rect cancel = area(frame, new Hit(Widget.EDITOR_DIALOG, TowerDesigner.DIALOG_CANCEL));
            float s = h / 768f;
            assertTrue(cancel.x() + cancel.width() + 2f * EditorScreens.BORDER * s <= preview.x(),
                    command + ": the dialog ends left of the preview");
            PanelLayout.Rect first = area(frame, new Hit(Widget.EDITOR_FIELD, EditorForm.hitRow(0, EditorForm.BODY)));
            assertTrue(first.y() + first.height() < h - EditorScreens.MENU_HEIGHT * s, "below the menu bar");
            assertTrue(cancel.y() > 0f, "above the window's bottom");
            designer.key(UiKey.ESCAPE);
        }
    }

    @Test
    void aClickOnADrawnControlReachesItsField(@TempDir Path dir) {
        TowerDesigner designer = designer(dir);
        designer.run(Command.EFFECTS);
        StartScreens.Frame frame = frame(designer, 1280, 720);

        PanelLayout.Rect lightning = area(frame, new Hit(Widget.EDITOR_FIELD,
                EditorForm.hitRow(EffectsDialog.LIGHTNING, EditorForm.BODY)));
        designer.press(frame.hit(lightning.x() + 5f, lightning.y() + 5f), false);
        PanelLayout.Rect plus = area(frame, new Hit(Widget.EDITOR_FIELD,
                EditorForm.hitRow(EffectsDialog.GROUND_HEIGHT, EditorForm.PLUS)));
        designer.press(frame.hit(plus.x() + 5f, plus.y() + 5f), false);

        assertTrue(designer.shown().effects().lightning());
        assertEquals(7, designer.shown().remake().orElseThrow().groundHeight());
        assertEquals("*", texts(frame(designer, 1280, 720)).stream().filter(t -> t.equals("*")).findFirst()
                .orElseThrow(), "the switch shows its star");
    }

    @Test
    void theColourDialogOpensOverEffectsWithItsChannelsAndTheColour(@TempDir Path dir) {
        TowerDesigner designer = designer(dir);
        designer.run(Command.EFFECTS);
        designer.press(Optional.of(new Hit(Widget.EDITOR_FIELD, EditorForm.hitRow(EffectsDialog.GROUND_1,
                EditorForm.BODY))), false);

        StartScreens.Frame frame = frame(designer, 1024, 768);

        assertTrue(texts(frame).containsAll(List.of("Ground Color 1", "Red", "Green", "Blue", "0", "75")),
                texts(frame).toString());
        assertEquals(Map.of(Widget.EDITOR_FIELD, 9L, Widget.EDITOR_DIALOG, 2L), areas(frame),
                "only the colour dialog: three number boxes, OK, Cancel");
        List<Drawing.Sprite> sprites = frame.drawing().sprites();
        Drawing.Sprite last = sprites.stream().filter(s -> s.region().equals("editor.col"))
                .reduce((a, b) -> b).orElseThrow();
        assertEquals(0x004B4B, last.tint(), "the colour beside the channels");
        assertTrue(last.rect().height() > 2f * EditorScreens.FORM_CONTROL, "beside all three rows");
        EditorScreensTest.assertCovered(frame);
    }
}
