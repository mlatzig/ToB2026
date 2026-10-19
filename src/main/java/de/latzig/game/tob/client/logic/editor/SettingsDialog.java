package de.latzig.game.tob.client.logic.editor;

import java.util.List;

import de.latzig.game.tob.client.logic.Texts;
import de.latzig.game.tob.client.logic.editor.EditorForm.Field;
import de.latzig.game.tob.core.model.Behaviour;
import de.latzig.game.tob.core.model.Conditions;
import de.latzig.game.tob.core.model.TowerDefinition;

/**
 * The Tower Designer's "Settings" dialog (Phase 6b spec decision 8; ToB 2009's {@code OptionsSettingsDialog},
 * {@code J:editor/dialogs/OptionsSettingsDialog.java:110-260}, applied by {@code OptionsSettingsAction}): General
 * (Title, Author), Finishing Conditions (Klondikes, Robots, TimeLimit) and Behaviour (External cams enabled?, Mine
 * destroys floor?, Timebomb Speed), OK and Cancel. The limits are ToB 2026's tower model's (title and author 62
 * characters, klondikes and robots 0-7, the original's four time bomb speeds) instead of ToB 2009's (20 characters,
 * 0-20, three speeds); TimeLimit is 0-999 seconds as in ToB 2009, 0 for none (the tower keeps its raw seconds then).
 * OK writes only the fields that were changed.
 */
public final class SettingsDialog implements TowerDialog {

    public static final int TITLE = 0;
    public static final int AUTHOR = 1;
    public static final int KLONDIKES = 2;
    public static final int ROBOTS = 3;
    public static final int TIME_LIMIT = 4;
    public static final int CAMERAS = 5;
    public static final int MINES = 6;
    public static final int TIME_BOMB = 7;
    /** ToB 2009's {@code SourceTowerConstants.MAX_TIMELIMIT}. */
    public static final int MAX_TIME_LIMIT = 999;
    /** The time limit's − and + step in seconds (digits can be typed). */
    public static final int TIME_LIMIT_STEP = 10;
    /** The model's limit of klondikes and robots ({@link Conditions}). */
    public static final int MAX_COUNT = 7;

    private final EditorForm form;

    public SettingsDialog(TowerDefinition tower, Texts texts) {
        Conditions conditions = tower.conditions();
        Behaviour behaviour = tower.behaviour();
        List<String> speeds = List.of(texts.get("editor.settings.slow"), texts.get("editor.settings.medium"),
                texts.get("editor.settings.fast"), texts.get("editor.settings.faster"));
        form = new EditorForm.Builder()
                .group("editor.settings.general")
                .row("editor.settings.title", Field.text("editor.settings.title", tower.title(),
                        TowerDefinition.MAX_TEXT_LENGTH))
                .row("editor.settings.author", Field.text("editor.settings.author", tower.author(),
                        TowerDefinition.MAX_TEXT_LENGTH))
                .group("editor.settings.conditions")
                .row("editor.settings.klondikes", Field.number("editor.settings.klondikes", conditions.klondikes(), 0,
                        MAX_COUNT, 1))
                .row("editor.settings.robots", Field.number("editor.settings.robots", conditions.robots(), 0,
                        MAX_COUNT, 1))
                .row("editor.settings.time_limit", Field.number("editor.settings.time_limit",
                        conditions.timeLimitEnabled() ? conditions.timeLimitSeconds() : 0, 0, MAX_TIME_LIMIT,
                        TIME_LIMIT_STEP))
                .group("editor.settings.behaviour")
                .row("", Field.check("editor.settings.cameras", behaviour.camerasEnabled()))
                .row("", Field.check("editor.settings.mines", behaviour.minesDestroyFloor()))
                .row("editor.settings.time_bomb", Field.choice("editor.settings.time_bomb",
                        behaviour.timeBombSpeed(), speeds))
                .build();
    }

    /** The dialog's caption (a bundle key: the menu entry's). */
    @Override
    public String captionKey() {
        return "editor.command.settings";
    }

    @Override
    public EditorForm form() {
        return form;
    }

    /** {@code tower} with the changed fields (OK); the same tower if nothing was changed. */
    @Override
    public TowerDefinition apply(TowerDefinition tower) {
        if (form.fields().stream().noneMatch(Field::changed)) {
            return tower;
        }
        Conditions c = tower.conditions();
        int seconds = c.timeLimitSeconds();
        boolean limited = c.timeLimitEnabled();
        if (form.field(TIME_LIMIT).changed()) {
            int typed = form.field(TIME_LIMIT).value();
            limited = typed > 0;
            seconds = limited ? typed : seconds;
        }
        Conditions conditions = new Conditions(seconds, limited, value(KLONDIKES, c.klondikes()),
                value(ROBOTS, c.robots()));
        Behaviour b = tower.behaviour();
        Behaviour behaviour = new Behaviour(value(CAMERAS, b.camerasEnabled() ? 1 : 0) != 0,
                value(MINES, b.minesDestroyFloor() ? 1 : 0) != 0, value(TIME_BOMB, b.timeBombSpeed()));
        return new TowerDefinition(tower.id(), tower.group(), tower.index(), text(TITLE, tower.title()),
                text(AUTHOR, tower.author()), conditions, behaviour, tower.effects(), tower.moon(), tower.palette(),
                tower.sounds(), tower.lock(), tower.grid(), tower.remake());
    }

    private int value(int field, int unchanged) {
        return form.field(field).changed() ? form.field(field).value() : unchanged;
    }

    private String text(int field, String unchanged) {
        return form.field(field).changed() ? form.field(field).text().stripTrailing() : unchanged;
    }
}
