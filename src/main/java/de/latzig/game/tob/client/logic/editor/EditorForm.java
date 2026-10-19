package de.latzig.game.tob.client.logic.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import de.latzig.game.tob.client.logic.ui.UiKey;
import de.latzig.game.tob.core.progress.Profile;

/**
 * A dialog form of the Tower Designer in ToB 2009's look (Phase 6b-2; the SJGL controls of
 * {@code J:editor/dialogs/OptionsSettingsDialog.java} and {@code OptionsEffectsDialog.java}): fields in captioned
 * groups, one or two per row, one of them with the focus. A field is a text box ({@code TextBox}: printable ASCII,
 * at most its length), a number box ({@code NumberBox}: − and + by its step, between its limits, digits typed in), a
 * choice ({@code EnumBox}: − and +, no wrap), a switch ({@code CheckBox}), a colour button (it opens the colour
 * dialog) or a line of information. Tab and Down move the focus on, Up back; Left and Right are − and +; Space flips a
 * switch or opens a colour; Enter is OK, Esc Cancel. Every field remembers its first value, so a dialog writes back
 * only what was changed ({@link Field#changed()}) and keeps the tower's other raw data as it was.
 */
public final class EditorForm {

    /** The kinds of fields. */
    public enum Kind {
        TEXT, NUMBER, CHOICE, CHECK, COLOUR, INFO
    }

    /** What a key or a click asks of the dialog. */
    public enum Action {
        NONE, OK, CANCEL, COLOUR
    }

    /** The parts of a field ({@code EDITOR_FIELD} hit row = field index × {@link #PARTS} + part). */
    public static final int PARTS = 3;
    public static final int BODY = 0;
    public static final int MINUS = 1;
    public static final int PLUS = 2;

    /** One field: its kind, label, place, limits and value. */
    public static final class Field {

        private final Kind kind;
        private final String labelKey;
        private final int min;
        private final int max;
        private final int step;
        private final List<String> choices;
        private final boolean twelveBit;
        private final int initial;
        private final String initialText;
        private final StringBuilder text;
        private int value;
        private String rowKey = "";
        private int group;
        private int row;
        private int column;

        private Field(Kind kind, String labelKey, int value, int min, int max, int step, List<String> choices,
                boolean twelveBit, String text) {
            this.kind = kind;
            this.labelKey = Objects.requireNonNull(labelKey, "labelKey");
            this.min = min;
            this.max = max;
            this.step = step;
            this.choices = List.copyOf(choices);
            this.twelveBit = twelveBit;
            this.value = Math.clamp(value, min, max);
            this.initial = this.value;
            this.initialText = text;
            this.text = new StringBuilder(text);
        }

        /** A text box with {@code text}, at most {@code length} printable ASCII characters. */
        public static Field text(String labelKey, String text, int length) {
            return new Field(Kind.TEXT, labelKey, 0, 0, length, 1, List.of(), false, text);
        }

        /** A number box between {@code min} and {@code max}, − and + by {@code step}. */
        public static Field number(String labelKey, int value, int min, int max, int step) {
            return new Field(Kind.NUMBER, labelKey, value, min, max, step, List.of(), false, "");
        }

        /** A choice of {@code choices} (their texts), {@code value} the chosen one's index. */
        public static Field choice(String labelKey, int value, List<String> choices) {
            return new Field(Kind.CHOICE, labelKey, value, 0, choices.size() - 1, 1, choices, false, "");
        }

        /** A switch, on or off. */
        public static Field check(String labelKey, boolean on) {
            return new Field(Kind.CHECK, labelKey, on ? 1 : 0, 0, 1, 1, List.of(), false, "");
        }

        /** A colour button (0xRRGGBB); a {@code twelveBit} colour keeps 4 bits per channel (the original palette). */
        public static Field colour(String labelKey, int rgb, boolean twelveBit) {
            return new Field(Kind.COLOUR, labelKey, rgb, 0, 0xFFFFFF, 1, List.of(), twelveBit, "");
        }

        /** A line of information the dialog keeps up to date ({@link #info(String)}). */
        public static Field info(String labelKey, String text) {
            return new Field(Kind.INFO, labelKey, 0, 0, 0, 1, List.of(), false, text);
        }

        public Kind kind() {
            return kind;
        }

        /** The field's own name (a bundle key; a colour's is the colour dialog's caption, a switch's its text). */
        public String labelKey() {
            return labelKey;
        }

        /** The row's label (a bundle key) on the row's first field, else empty. */
        public String rowKey() {
            return rowKey;
        }

        /** The index of the field's group in {@link EditorForm#groups()}. */
        public int group() {
            return group;
        }

        /** The field's row, counted over the whole form. */
        public int row() {
            return row;
        }

        /** 0 for the row's first field, 1 for the second. */
        public int column() {
            return column;
        }

        public int value() {
            return value;
        }

        public int min() {
            return min;
        }

        public int max() {
            return max;
        }

        public boolean on() {
            return value != 0;
        }

        public boolean twelveBit() {
            return twelveBit;
        }

        public List<String> choices() {
            return choices;
        }

        public String text() {
            return text.toString();
        }

        /** What the field's box shows: the text, the number or the chosen choice. */
        public String display() {
            return switch (kind) {
                case TEXT, INFO -> text.toString();
                case NUMBER -> Integer.toString(value);
                case CHOICE -> choices.get(value);
                case CHECK, COLOUR -> "";
            };
        }

        /** Whether the value differs from the one the field started with. */
        public boolean changed() {
            return kind == Kind.TEXT ? !initialText.contentEquals(text) : value != initial;
        }

        /** Sets the value, kept within the limits. */
        public void value(int newValue) {
            value = Math.clamp(newValue, min, max);
        }

        /** An information line's new text. */
        public void info(String newText) {
            text.setLength(0);
            text.append(newText);
        }

        /** Whether the field can have the focus (every kind but information). */
        public boolean focusable() {
            return kind != Kind.INFO;
        }

        void place(String key, int newGroup, int newRow, int newColumn) {
            rowKey = key;
            group = newGroup;
            row = newRow;
            column = newColumn;
        }

        void minus() {
            if (kind == Kind.NUMBER || kind == Kind.CHOICE) {
                value(value - step);
            }
        }

        void plus() {
            if (kind == Kind.NUMBER || kind == Kind.CHOICE) {
                value(value + step);
            }
        }

        void type(char c) {
            if (kind == Kind.TEXT && Profile.isNameChar(c) && text.length() < max) {
                text.append(c);
            } else if (kind == Kind.NUMBER && c >= '0' && c <= '9') {
                int digit = c - '0';
                long typed = (long) value * 10 + digit;
                value(typed <= max ? (int) typed : digit);
            }
        }

        void backspace() {
            if (kind == Kind.TEXT && !text.isEmpty()) {
                text.setLength(text.length() - 1);
            } else if (kind == Kind.NUMBER) {
                value(value / 10);
            }
        }
    }

    /** Builds a form group by group and row by row. */
    public static final class Builder {

        private final List<String> groups = new ArrayList<>();
        private final List<Field> fields = new ArrayList<>();
        private int rows;

        /** Starts a group with the caption {@code captionKey} (a bundle key). */
        public Builder group(String captionKey) {
            groups.add(captionKey);
            return this;
        }

        /** One row of the current group: its label ({@code ""}: none) and one or two fields. */
        public Builder row(String rowKey, Field... onRow) {
            if (groups.isEmpty() || onRow.length < 1 || onRow.length > 2) {
                throw new IllegalStateException("a row needs a group and one or two fields");
            }
            for (int c = 0; c < onRow.length; c++) {
                onRow[c].place(c == 0 ? rowKey : "", groups.size() - 1, rows, c);
                fields.add(onRow[c]);
            }
            rows++;
            return this;
        }

        public EditorForm build() {
            return new EditorForm(groups, fields, rows);
        }
    }

    private final List<String> groups;
    private final List<Field> fields;
    private final int rows;
    private int focus;

    private EditorForm(List<String> groups, List<Field> fields, int rows) {
        this.groups = List.copyOf(groups);
        this.fields = List.copyOf(fields);
        this.rows = rows;
        this.focus = next(-1, 1);
    }

    /** The groups' captions (bundle keys) in order. */
    public List<String> groups() {
        return groups;
    }

    public List<Field> fields() {
        return fields;
    }

    public Field field(int index) {
        return fields.get(index);
    }

    /** The number of rows over every group. */
    public int rows() {
        return rows;
    }

    /** The index of the field with the focus. */
    public int focus() {
        return focus;
    }

    /** The field with the focus. */
    public Field focused() {
        return fields.get(focus);
    }

    /** Gives {@code index} the focus, if it can have it. */
    public void focus(int index) {
        if (index >= 0 && index < fields.size() && fields.get(index).focusable()) {
            focus = index;
        }
    }

    private int next(int from, int direction) {
        for (int i = 1; i <= fields.size(); i++) {
            int candidate = Math.floorMod(from + direction * i, fields.size());
            if (fields.get(candidate).focusable()) {
                return candidate;
            }
        }
        return 0;
    }

    public Action key(UiKey key) {
        Field field = focused();
        switch (key) {
            case TAB, DOWN -> focus = next(focus, 1);
            case UP -> focus = next(focus, -1);
            case LEFT -> field.minus();
            case RIGHT -> field.plus();
            case SPACE -> {
                if (field.kind() == Kind.CHECK) {
                    field.value(1 - field.value());
                } else if (field.kind() == Kind.COLOUR) {
                    return Action.COLOUR;
                }
            }
            case BACKSPACE -> field.backspace();
            case ENTER -> {
                return Action.OK;
            }
            case ESCAPE -> {
                return Action.CANCEL;
            }
            default -> {
            }
        }
        return Action.NONE;
    }

    /** A typed character for the field with the focus (a text box takes text, a number box digits). */
    public void type(char c) {
        focused().type(c);
    }

    /** A click on {@code part} of field {@code index}: it gets the focus; − and + step, a switch flips. */
    public Action click(int index, int part) {
        if (index < 0 || index >= fields.size() || !fields.get(index).focusable()) {
            return Action.NONE;
        }
        focus = index;
        Field field = fields.get(index);
        if (part == MINUS) {
            field.minus();
        } else if (part == PLUS) {
            field.plus();
        } else if (field.kind() == Kind.CHECK) {
            field.value(1 - field.value());
        } else if (field.kind() == Kind.COLOUR) {
            return Action.COLOUR;
        }
        return Action.NONE;
    }

    /** The hit row of {@code part} of field {@code index} ({@code EDITOR_FIELD}). */
    public static int hitRow(int index, int part) {
        return index * PARTS + part;
    }
}
