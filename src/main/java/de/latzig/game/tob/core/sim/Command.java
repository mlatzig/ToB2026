package de.latzig.game.tob.core.sim;

import java.util.Objects;

/** One player input for one frame. Every spider command names its spider, every camera command its camera. */
public sealed interface Command {

    /** Commands addressed to one spider; ignored while that spider is not alive ({@code S:8345-8346}). */
    sealed interface SpiderCommand extends Command {
        SpiderId spider();
    }

    /**
     * Commands addressed to one camera; all of them are ignored while the tower has cameras disabled
     * ({@code S:8340-8343}). In write mode the five action buttons write their step instead ({@code S:8350-8352}).
     */
    sealed interface CameraCommand extends Command {
        CameraId camera();
    }

    record Forward(SpiderId spider) implements SpiderCommand {
        public Forward {
            Objects.requireNonNull(spider, "spider");
        }
    }

    /** Back: lift ride, backwards walk (option), or reversal while moving. */
    record Back(SpiderId spider) implements SpiderCommand {
        public Back {
            Objects.requireNonNull(spider, "spider");
        }
    }

    record TurnLeft(SpiderId spider) implements SpiderCommand {
        public TurnLeft {
            Objects.requireNonNull(spider, "spider");
        }
    }

    record TurnRight(SpiderId spider) implements SpiderCommand {
        public TurnRight {
            Objects.requireNonNull(spider, "spider");
        }
    }

    record Fire(SpiderId spider) implements SpiderCommand {
        public Fire {
            Objects.requireNonNull(spider, "spider");
        }
    }

    /** The write button: enters write mode (no toggle; {@code S:8539-8547}). */
    record EnterWriteMode(SpiderId spider) implements SpiderCommand {
        public EnterWriteMode {
            Objects.requireNonNull(spider, "spider");
        }
    }

    /** Writes a step in write mode (the same as pressing the action button in write mode). */
    record Write(SpiderId spider, ProgramStep step) implements SpiderCommand {
        public Write {
            Objects.requireNonNull(spider, "spider");
            Objects.requireNonNull(step, "step");
            if (step == ProgramStep.EMPTY) {
                throw new IllegalArgumentException("EMPTY cannot be written");
            }
        }
    }

    record SetCursor(SpiderId spider, int cursor) implements SpiderCommand {
        public SetCursor {
            Objects.requireNonNull(spider, "spider");
            if (cursor < 0 || cursor >= SpiderProgram.SLOTS) {
                throw new IllegalArgumentException("cursor out of range 0..7: " + cursor);
            }
        }
    }

    record Delete(SpiderId spider) implements SpiderCommand {
        public Delete {
            Objects.requireNonNull(spider, "spider");
        }
    }

    record Clear(SpiderId spider) implements SpiderCommand {
        public Clear {
            Objects.requireNonNull(spider, "spider");
        }
    }

    record Run(SpiderId spider) implements SpiderCommand {
        public Run {
            Objects.requireNonNull(spider, "spider");
        }
    }

    record Stop(SpiderId spider) implements SpiderCommand {
        public Stop {
            Objects.requireNonNull(spider, "spider");
        }
    }

    /** Forward on a camera: height + $80 ({@code S:8408-8418}). */
    record CameraForward(CameraId camera) implements CameraCommand {
        public CameraForward {
            Objects.requireNonNull(camera, "camera");
        }
    }

    /** Back on a camera: height − $80 ({@code S:8419-8429}). */
    record CameraBack(CameraId camera) implements CameraCommand {
        public CameraBack {
            Objects.requireNonNull(camera, "camera");
        }
    }

    /** Left on a camera: sideways offset − $80 ({@code S:8430-8441}). */
    record CameraTurnLeft(CameraId camera) implements CameraCommand {
        public CameraTurnLeft {
            Objects.requireNonNull(camera, "camera");
        }
    }

    /** Right on a camera: sideways offset + $80 ({@code S:8442-8453}). */
    record CameraTurnRight(CameraId camera) implements CameraCommand {
        public CameraTurnRight {
            Objects.requireNonNull(camera, "camera");
        }
    }

    /** Fire on a camera: nothing ({@code S:8442-8443}), except a FIRE step in write mode. */
    record CameraFire(CameraId camera) implements CameraCommand {
        public CameraFire {
            Objects.requireNonNull(camera, "camera");
        }
    }

    /** The write button for a camera program. */
    record CameraEnterWriteMode(CameraId camera) implements CameraCommand {
        public CameraEnterWriteMode {
            Objects.requireNonNull(camera, "camera");
        }
    }

    /** Writes a step into a camera program in write mode. */
    record CameraWrite(CameraId camera, ProgramStep step) implements CameraCommand {
        public CameraWrite {
            Objects.requireNonNull(camera, "camera");
            Objects.requireNonNull(step, "step");
            if (step == ProgramStep.EMPTY) {
                throw new IllegalArgumentException("EMPTY cannot be written");
            }
        }
    }

    /** A program position button with a camera selected ({@code S:8582-8584}). */
    record CameraSetCursor(CameraId camera, int cursor) implements CameraCommand {
        public CameraSetCursor {
            Objects.requireNonNull(camera, "camera");
            if (cursor < 0 || cursor >= SpiderProgram.SLOTS) {
                throw new IllegalArgumentException("cursor out of range 0..7: " + cursor);
            }
        }
    }

    /** Delete on a camera program ({@code S:8548-8573}). */
    record CameraDelete(CameraId camera) implements CameraCommand {
        public CameraDelete {
            Objects.requireNonNull(camera, "camera");
        }
    }

    /** Clear on a camera program ({@code S:8574-8581}). */
    record CameraClear(CameraId camera) implements CameraCommand {
        public CameraClear {
            Objects.requireNonNull(camera, "camera");
        }
    }

    /** The Stop/In button on a camera: distance + $100, ends write mode, program throttle 8 ({@code S:8478-8495}). */
    record CameraZoomIn(CameraId camera) implements CameraCommand {
        public CameraZoomIn {
            Objects.requireNonNull(camera, "camera");
        }
    }

    /** The Run/Out button on a camera: distance − $100, ends write mode, program throttle 8 ({@code S:8500-8514}). */
    record CameraZoomOut(CameraId camera) implements CameraCommand {
        public CameraZoomOut {
            Objects.requireNonNull(camera, "camera");
        }
    }

    /** Starts the three spider programs; ignored while the selected spider is not alive (camera: cameras disabled). */
    record RunAll() implements Command {
    }

    /** Selects a spider; a camera in write mode copies its program to it instead (see {@link SpiderController}). */
    record SelectSpider(SpiderId spider) implements Command {
        public SelectSpider {
            Objects.requireNonNull(spider, "spider");
        }
    }

    /** Selects a camera; a spider in write mode copies its program to it instead (see {@link SpiderController}). */
    record SelectCamera(CameraId camera) implements Command {
        public SelectCamera {
            Objects.requireNonNull(camera, "camera");
        }
    }

    /**
     * Exit 5; ignored while an exit is pending (the pause menu is refused then). The frame that accepts it still runs
     * input, movement and handlers; then the attempt ends ({@link Simulation#step}).
     */
    record Abandon() implements Command {
    }

    /** Exit 6; ignored while an exit is pending. Like {@link Abandon}, the frame still runs before the attempt ends. */
    record Restart() implements Command {
    }
}
