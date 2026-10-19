package de.latzig.game.tob.core.sim;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The 8-slot program of one spider ({@code W001C1E + 16·n}: state {@code $4}, cursor {@code $6}, slots {@code $8-$F}).
 * The cursor is also the run index, as in the original. Editing follows {@code S:8454-8584}, execution
 * {@code L00BC9A} ({@code S:10105-10178}). The public methods are read-only.
 */
public final class SpiderProgram {

    public static final int SLOTS = 8;

    private final ProgramStep[] slots = new ProgramStep[SLOTS];
    private int cursor;
    private ProgramState state = ProgramState.IDLE;

    SpiderProgram() {
        Arrays.fill(slots, ProgramStep.EMPTY);
    }

    public ProgramStep slot(int index) {
        return slots[index];
    }

    public List<ProgramStep> steps() {
        return List.of(slots);
    }

    /** Write position, or the index of the last executed step while running (−1 right after Run, 8 after the end). */
    public int cursor() {
        return cursor;
    }

    public ProgramState state() {
        return state;
    }

    /** The write button ({@code S:8539-8547}): enters write mode with cursor 0; no effect in write mode. */
    void enterWriteMode() {
        if (state != ProgramState.WRITING) {
            cursor = 0;
            state = ProgramState.WRITING;
        }
    }

    /** Inserts at the cursor (clamped to 0..7), shifts the rest and drops the last slot ({@code S:8454-8477}). */
    void insert(ProgramStep step) {
        int at = Math.max(0, Math.min(cursor, SLOTS - 1));
        System.arraycopy(slots, at, slots, at + 1, SLOTS - 1 - at);
        slots[at] = step;
        cursor = at < SLOTS - 1 ? at + 1 : at;
    }

    /**
     * Removes the step before the cursor and leaves write mode, stopping a running program ({@code S:8548-8573}).
     *
     * @return false if the cursor is 0 or below (nothing happens)
     */
    boolean delete() {
        int at = cursor - 1;
        if (at < 0) {
            return false;
        }
        at = Math.min(at, SLOTS - 1);
        System.arraycopy(slots, at + 1, slots, at, SLOTS - 1 - at);
        slots[SLOTS - 1] = ProgramStep.EMPTY;
        cursor = at;
        state = ProgramState.IDLE;
        return true;
    }

    /** Empties every slot, resets the cursor and stops ({@code S:8574-8581}). */
    void clear() {
        Arrays.fill(slots, ProgramStep.EMPTY);
        cursor = 0;
        state = ProgramState.IDLE;
    }

    /** Run / Run All ({@code S:8515-8538}): the next step is slot 0. */
    void run() {
        state = ProgramState.RUNNING;
        cursor = -1;
    }

    void stop() {
        state = ProgramState.IDLE;
    }

    /** The program position buttons ({@code S:8582-8584}); allowed in every state. */
    void setCursor(int index) {
        if (index < 0 || index >= SLOTS) {
            throw new IllegalArgumentException("cursor out of range 0..7: " + index);
        }
        cursor = index;
    }

    void copySlotsFrom(SpiderProgram source) {
        System.arraycopy(source.slots, 0, slots, 0, SLOTS);
    }

    /**
     * Advances to the next step; past slot 7 the program stops and nothing is returned ({@code S:10105-10122}).
     * Self-guarded like the original ({@code S:10105-10108}, {@code L00BC9A} checks {@code $4(a5)==1} first): a
     * call while not {@link ProgramState#RUNNING} leaves the cursor and the slots untouched and returns empty.
     */
    Optional<ProgramStep> next() {
        if (state != ProgramState.RUNNING) {
            return Optional.empty();
        }
        cursor++;
        if (cursor < 0 || cursor >= SLOTS) {
            state = ProgramState.IDLE;
            return Optional.empty();
        }
        return Optional.of(slots[cursor]);
    }
}
