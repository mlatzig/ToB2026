package de.latzig.game.tob.client.logic.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.client.logic.editor.EditorForm.Action;
import de.latzig.game.tob.client.logic.editor.EditorForm.Field;
import de.latzig.game.tob.client.logic.ui.UiKey;

/** Phase 6b-2: the Tower Designer's dialog forms in ToB 2009's look (text, number, choice, switch, colour, info). */
class EditorFormTest {

    private static EditorForm form() {
        return new EditorForm.Builder()
                .group("g.one")
                .row("r.title", Field.text("f.title", "Tower", 8))
                .row("r.count", Field.number("f.count", 3, 0, 7, 1))
                .row("", Field.info("f.info", "Normal"))
                .group("g.two")
                .row("r.speed", Field.choice("f.speed", 1, List.of("Slow", "Medium", "Fast")))
                .row("", Field.check("f.cams", true))
                .row("r.colours", Field.colour("f.sky1", 0x0088CC, true), Field.colour("f.sky2", 0x000000, true))
                .build();
    }

    @Test
    void theFieldsLieInTheirGroupsAndRowsAndTheFirstFieldHasTheFocus() {
        EditorForm form = form();

        assertEquals(List.of("g.one", "g.two"), form.groups());
        assertEquals(7, form.fields().size());
        assertEquals(6, form.rows());
        assertEquals(0, form.focus());
        assertEquals(1, form.field(3).group());
        assertEquals(5, form.field(6).row(), "the second colour shares the row");
        assertEquals(1, form.field(6).column());
        assertEquals("r.colours", form.field(5).rowKey());
        assertEquals("", form.field(6).rowKey());
        assertEquals("Medium", form.field(3).display());
        assertEquals("3", form.field(1).display());
        assertThrows(IllegalStateException.class, () -> new EditorForm.Builder().row("x", Field.check("c", true)));
    }

    @Test
    void tabAndTheArrowsMoveTheFocusPastTheInformationLine() {
        EditorForm form = form();

        form.key(UiKey.TAB);
        assertEquals(1, form.focus());
        form.key(UiKey.DOWN);
        assertEquals(3, form.focus(), "the information line cannot have the focus");
        form.key(UiKey.UP);
        assertEquals(1, form.focus());
        form.key(UiKey.UP);
        form.key(UiKey.UP);
        assertEquals(6, form.focus(), "round from the first to the last");
        form.focus(2);
        assertEquals(6, form.focus(), "not the information line");
    }

    @Test
    void numbersAndChoicesStepWithinTheirLimitsAndNumbersTakeDigits() {
        EditorForm form = form();
        form.focus(1);

        form.key(UiKey.RIGHT);
        assertEquals(4, form.focused().value());
        for (int i = 0; i < 9; i++) {
            form.key(UiKey.RIGHT);
        }
        assertEquals(7, form.focused().value(), "at most 7");
        form.key(UiKey.BACKSPACE);
        assertEquals(0, form.focused().value());
        form.type('5');
        assertEquals(5, form.focused().value());
        form.type('9');
        assertEquals(7, form.focused().value(), "59 > 7: the digit alone, kept within the limit");
        form.type('x');
        assertEquals(7, form.focused().value());

        assertEquals(Action.NONE, form.click(3, EditorForm.MINUS));
        assertEquals(3, form.focus());
        assertEquals("Slow", form.focused().display());
        form.click(3, EditorForm.MINUS);
        assertEquals(0, form.focused().value(), "a choice does not wrap");
        form.click(3, EditorForm.PLUS);
        assertEquals("Medium", form.focused().display());
    }

    @Test
    void aTextBoxTakesPrintableAsciiUpToItsLength() {
        EditorForm form = form();

        form.type(' ');
        form.type('ä');
        form.type('1');
        form.type('2');
        form.type('3');
        assertEquals("Tower 12", form.focused().text(), "at most 8, umlauts ignored");
        form.key(UiKey.BACKSPACE);
        assertEquals("Tower 1", form.focused().display());
        form.key(UiKey.SPACE);
        assertEquals("Tower 1", form.focused().text(), "Space comes as a typed character, not as a key");
    }

    @Test
    void aSwitchFlipsAndAColourAsksForTheColourDialog() {
        EditorForm form = form();

        assertEquals(Action.NONE, form.click(4, EditorForm.BODY));
        assertFalse(form.field(4).on());
        form.key(UiKey.SPACE);
        assertTrue(form.field(4).on());
        assertEquals(Action.COLOUR, form.click(6, EditorForm.BODY));
        assertEquals(6, form.focus());
        assertEquals(Action.COLOUR, form.key(UiKey.SPACE));
        assertEquals(Action.NONE, form.click(2, EditorForm.BODY), "the information line is not clickable");
        assertEquals(Action.OK, form.key(UiKey.ENTER));
        assertEquals(Action.CANCEL, form.key(UiKey.ESCAPE));
        assertEquals(20, EditorForm.hitRow(6, EditorForm.PLUS));
    }

    @Test
    void aFieldKnowsWhetherItWasChanged() {
        EditorForm form = form();
        Field count = form.field(1);
        Field title = form.field(0);

        assertFalse(count.changed());
        count.value(4);
        assertTrue(count.changed());
        count.value(3);
        assertFalse(count.changed(), "back to the first value");
        title.type('!');
        assertTrue(title.changed());
        title.backspace();
        assertFalse(title.changed());
        Field clamped = Field.number("f.x", 128, 0, 17, 1);
        assertEquals(17, clamped.value(), "shown within the limits");
        assertFalse(clamped.changed(), "unchanged: the dialog keeps the raw 128");
    }
}
