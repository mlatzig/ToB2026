package de.latzig.game.tob.client.logic.editor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

import de.latzig.game.tob.core.model.TowerDefinition;

/**
 * Undo and redo of one tower (Phase 6b spec decision 7): at most {@value #DEPTH} steps back, the oldest dropped first.
 * Every change is a snapshot of the immutable tower; a painting stroke (the left button held over the grid) is one
 * step however many cells it paints. A change equal to the current tower is no step. A new change after an undo
 * drops the steps that could have been redone. The editor keeps the history through a test play and starts a new one
 * when another tower is opened.
 */
public final class EditHistory {

    /** Steps that can be undone (spec decision 7). */
    public static final int DEPTH = 100;

    private final Deque<TowerDefinition> undo = new ArrayDeque<>();
    private final Deque<TowerDefinition> redo = new ArrayDeque<>();
    private TowerDefinition current;
    private boolean stroke;
    private boolean strokeRecorded;

    public EditHistory(TowerDefinition tower) {
        this.current = Objects.requireNonNull(tower, "tower");
    }

    public TowerDefinition current() {
        return current;
    }

    /** A change: one step, or part of the running stroke's step. Returns whether the tower changed. */
    public boolean apply(TowerDefinition next) {
        Objects.requireNonNull(next, "next");
        if (next.equals(current)) {
            return false;
        }
        if (!stroke || !strokeRecorded) {
            undo.push(current);
            if (undo.size() > DEPTH) {
                undo.removeLast();
            }
            strokeRecorded = stroke;
        }
        redo.clear();
        current = next;
        return true;
    }

    /** A painting stroke begins: its changes up to {@link #endStroke} make one step. */
    public void beginStroke() {
        stroke = true;
        strokeRecorded = false;
    }

    public void endStroke() {
        stroke = false;
        strokeRecorded = false;
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    /** Steps back; returns the tower now current. Ends a running stroke. */
    public TowerDefinition undo() {
        endStroke();
        if (canUndo()) {
            redo.push(current);
            current = undo.pop();
        }
        return current;
    }

    /** Steps forward again; returns the tower now current. */
    public TowerDefinition redo() {
        endStroke();
        if (canRedo()) {
            undo.push(current);
            current = redo.pop();
        }
        return current;
    }

    /** The steps that can be undone. */
    public int undoSteps() {
        return undo.size();
    }
}
