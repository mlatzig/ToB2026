package de.latzig.game.tob.client.logic.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.client.logic.Texts;
import de.latzig.game.tob.client.logic.ui.UiKey;

/** Phase 6b-2: ToB 2009's colour dialog behind the Effects dialog's colour buttons. */
class ColourDialogTest {

    private static final Texts TEXTS = Texts.english();

    @Test
    void redGreenAndBlueStepBy16AsInToB2009() {
        ColourDialog dialog = new ColourDialog("editor.effects.sun", 0xFFEE00, false);
        EditorForm form = dialog.form();

        assertEquals("Sun Color", TEXTS.get(dialog.captionKey()));
        assertEquals(List.of("Red", "Green", "Blue"), form.fields().stream().map(f -> TEXTS.get(f.labelKey()))
                .toList());
        assertEquals(List.of(255, 238, 0), form.fields().stream().map(EditorForm.Field::value).toList());
        form.key(UiKey.LEFT);
        form.key(UiKey.TAB);
        form.key(UiKey.RIGHT);
        form.key(UiKey.TAB);
        form.key(UiKey.RIGHT);
        assertEquals(0xEFFE10, dialog.colour(), "255 - 16, 238 + 16, 0 + 16");
        form.key(UiKey.BACKSPACE);
        form.key(UiKey.BACKSPACE);
        form.type('9');
        assertEquals(0xEFFE09, dialog.colour(), "digits typed in");
        assertEquals(0xFFEE00, dialog.initial());
        assertEquals(EditorForm.Action.OK, form.key(UiKey.ENTER));
    }

    @Test
    void aTwelveBitColourStepsThroughItsSixteenLevels() {
        ColourDialog dialog = new ColourDialog("editor.effects.sky1", 0x0088CC, true);
        EditorForm form = dialog.form();

        form.key(UiKey.RIGHT);
        assertEquals(0x1188CC, dialog.colour(), "0 + 17");
        form.key(UiKey.TAB);
        form.type('0');
        assertEquals(0x1100CC, dialog.colour(), "136 typed on to 1360 > 255: the digit 0 alone");
        form.key(UiKey.BACKSPACE);
        form.type('9');
        form.type('0');
        assertEquals(0x1155CC, dialog.colour(), "90 rounds to 85 = 5 × 17");
    }
}
