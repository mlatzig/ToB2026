package de.latzig.game.tob.core.sim;

import static de.latzig.game.tob.core.sim.ProgramStep.BACK;
import static de.latzig.game.tob.core.sim.ProgramStep.EMPTY;
import static de.latzig.game.tob.core.sim.ProgramStep.FIRE;
import static de.latzig.game.tob.core.sim.ProgramStep.FORWARD;
import static de.latzig.game.tob.core.sim.ProgramStep.LEFT;
import static de.latzig.game.tob.core.sim.ProgramStep.RIGHT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class SpiderProgramTest {

    private static final List<ProgramStep> EMPTY_PROGRAM = List.of(EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY,
            EMPTY);

    private static SpiderProgram written(ProgramStep... steps) {
        SpiderProgram program = new SpiderProgram();
        program.enterWriteMode();
        for (ProgramStep step : steps) {
            program.insert(step);
        }
        return program;
    }

    @Test
    void aNewProgramIsEmptyAndIdle() {
        SpiderProgram program = new SpiderProgram();

        assertEquals(EMPTY_PROGRAM, program.steps());
        assertEquals(0, program.cursor());
        assertEquals(ProgramState.IDLE, program.state());
    }

    @Test
    void stepCodesAreTheOriginalBytes() {
        assertEquals(List.of(0, 1, 2, 3, 4, 5), Arrays.stream(ProgramStep.values()).map(ProgramStep::code).toList());
    }

    @Test
    void writeInsertsAtTheCursorAndShiftsTheRest() {
        SpiderProgram program = written(FORWARD, LEFT, RIGHT);
        assertEquals(List.of(FORWARD, LEFT, RIGHT, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY), program.steps());
        assertEquals(3, program.cursor());

        program.setCursor(1);
        program.insert(FIRE);

        assertEquals(List.of(FORWARD, FIRE, LEFT, RIGHT, EMPTY, EMPTY, EMPTY, EMPTY), program.steps());
        assertEquals(2, program.cursor());
        assertEquals(ProgramState.WRITING, program.state());
        assertThrows(IllegalArgumentException.class, () -> program.setCursor(8));
    }

    @Test
    void aFullProgramDropsItsLastStepAndTheLastSlotIsOverwritten() {
        SpiderProgram program = written(FORWARD, FORWARD, FORWARD, FORWARD, FORWARD, FORWARD, FORWARD, BACK);
        assertEquals(List.of(FORWARD, FORWARD, FORWARD, FORWARD, FORWARD, FORWARD, FORWARD, BACK), program.steps());
        assertEquals(7, program.cursor());

        program.insert(FIRE);
        assertEquals(List.of(FORWARD, FORWARD, FORWARD, FORWARD, FORWARD, FORWARD, FORWARD, FIRE), program.steps());
        assertEquals(7, program.cursor());

        program.setCursor(0);
        program.insert(LEFT);
        assertEquals(List.of(LEFT, FORWARD, FORWARD, FORWARD, FORWARD, FORWARD, FORWARD, FORWARD), program.steps());
    }

    @Test
    void enteringWriteModeResetsTheCursorOnlyOnEntry() {
        SpiderProgram program = written(FORWARD, FORWARD);
        program.enterWriteMode();
        assertEquals(2, program.cursor());

        program.stop();
        program.enterWriteMode();
        assertEquals(0, program.cursor());
    }

    @Test
    void deleteRemovesTheStepBeforeTheCursorAndLeavesWriteMode() {
        SpiderProgram program = written(FORWARD, LEFT, RIGHT);

        assertTrue(program.delete());
        assertEquals(List.of(FORWARD, LEFT, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY), program.steps());
        assertEquals(2, program.cursor());
        assertEquals(ProgramState.IDLE, program.state());

        program.setCursor(1);
        assertTrue(program.delete());
        assertEquals(List.of(LEFT, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY), program.steps());
        assertEquals(0, program.cursor());
    }

    @Test
    void deleteAtCursorZeroDoesNothing() {
        SpiderProgram program = written(FORWARD);
        program.setCursor(0);

        assertFalse(program.delete());
        assertEquals(List.of(FORWARD, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY), program.steps());
        assertEquals(ProgramState.WRITING, program.state());
    }

    @Test
    void clearEmptiesEverySlotAndStops() {
        SpiderProgram program = written(FORWARD, FIRE);
        program.run();

        program.clear();

        assertEquals(EMPTY_PROGRAM, program.steps());
        assertEquals(0, program.cursor());
        assertEquals(ProgramState.IDLE, program.state());
    }

    @Test
    void aRunStartsBeforeSlotZeroAndStopsAfterSlotSeven() {
        SpiderProgram program = written(FORWARD, LEFT);
        program.run();
        assertEquals(ProgramState.RUNNING, program.state());
        assertEquals(-1, program.cursor());

        List<ProgramStep> executed = new ArrayList<>();
        for (int i = 0; i < SpiderProgram.SLOTS; i++) {
            executed.add(program.next().orElseThrow());
        }
        assertEquals(List.of(FORWARD, LEFT, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY), executed);
        assertEquals(ProgramState.RUNNING, program.state());

        assertTrue(program.next().isEmpty());
        assertEquals(ProgramState.IDLE, program.state());
        assertEquals(8, program.cursor());
    }

    @Test
    void nextWhileWritingLeavesCursorAndSlotsUnchanged() {
        SpiderProgram program = written(FORWARD, LEFT, RIGHT);
        program.setCursor(1);

        assertTrue(program.next().isEmpty());

        assertEquals(List.of(FORWARD, LEFT, RIGHT, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY), program.steps());
        assertEquals(1, program.cursor());
        assertEquals(ProgramState.WRITING, program.state());
    }

    @Test
    void nextAfterTheProgramStoppedKeepsCursorAtEight() {
        SpiderProgram program = written(FORWARD, LEFT);
        program.run();
        for (int i = 0; i < SpiderProgram.SLOTS; i++) {
            program.next();
        }
        assertTrue(program.next().isEmpty());
        assertEquals(8, program.cursor());
        assertEquals(ProgramState.IDLE, program.state());

        assertTrue(program.next().isEmpty());
        assertEquals(8, program.cursor());
        assertEquals(ProgramState.IDLE, program.state());
    }

    @Test
    void deleteAtCursorSeven() {
        SpiderProgram program = written(FORWARD, FORWARD, FORWARD, FORWARD, FORWARD, FORWARD, FORWARD, BACK);
        assertEquals(7, program.cursor());

        assertTrue(program.delete());
        assertEquals(List.of(FORWARD, FORWARD, FORWARD, FORWARD, FORWARD, FORWARD, BACK, EMPTY), program.steps());
        assertEquals(6, program.cursor());
    }

    @Test
    void deleteAtCursorEightAfterARunCompleted() {
        SpiderProgram program = written(FORWARD, LEFT, RIGHT, FIRE, FORWARD, LEFT, RIGHT, FIRE);
        program.run();
        for (int i = 0; i < SpiderProgram.SLOTS; i++) {
            program.next();
        }
        program.next();
        assertEquals(8, program.cursor());

        assertTrue(program.delete());
        assertEquals(List.of(FORWARD, LEFT, RIGHT, FIRE, FORWARD, LEFT, RIGHT, EMPTY), program.steps());
        assertEquals(7, program.cursor());
        assertEquals(ProgramState.IDLE, program.state());
    }

    @Test
    void setCursorWhileRunningMovesTheRunIndex() {
        SpiderProgram program = written(FORWARD, LEFT, RIGHT);
        program.run();

        program.setCursor(1);

        assertEquals(1, program.cursor());
        assertEquals(ProgramState.RUNNING, program.state());
        assertEquals(RIGHT, program.next().orElseThrow());
    }

    @Test
    void copyingTakesTheStepsOnly() {
        SpiderProgram source = written(FORWARD, FIRE);
        SpiderProgram target = new SpiderProgram();

        target.copySlotsFrom(source);

        assertEquals(source.steps(), target.steps());
        assertEquals(0, target.cursor());
        assertEquals(ProgramState.IDLE, target.state());
    }
}
