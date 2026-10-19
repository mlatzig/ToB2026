package de.latzig.game.tob.client.logic.editor;

import java.util.Random;

import de.latzig.game.tob.core.model.TowerDefinition;

/**
 * A Tower Designer dialog that edits the selected tower's data in a form (Phase 6b-2: {@link SettingsDialog},
 * {@link EffectsDialog}): its caption, its {@link EditorForm}, and the tower it makes of the form's values. While it is
 * open the 3D preview shows {@link #apply} of the edited tower; OK applies it as one undo step, Cancel drops it.
 */
public interface TowerDialog {

    /** The dialog's caption (a bundle key). */
    String captionKey();

    EditorForm form();

    /** {@code tower} with the form's changed fields; the same tower if nothing was changed. */
    TowerDefinition apply(TowerDefinition tower);

    /** Brings the form's information lines up to date after an input (nothing by default). */
    default void refresh() {
    }

    /** Whether the dialog has ToB 2009's "Randomize Colors" button. */
    default boolean randomizable() {
        return false;
    }

    /** "Randomize Colors" with {@code random} (nothing by default). */
    default void randomize(Random random) {
    }
}
