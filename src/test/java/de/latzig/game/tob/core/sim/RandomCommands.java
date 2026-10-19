package de.latzig.game.tob.core.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Seeded pseudo-random player input (abandon and restart excluded). */
public final class RandomCommands {

    private final Random random;

    public RandomCommands(long seed) {
        this.random = new Random(seed);
    }

    /** For each of the three spiders a one-in-three chance of one random command, then a 1-in-10 camera command. */
    public List<Command> next() {
        List<Command> commands = new ArrayList<>();
        for (SpiderId spider : SpiderId.values()) {
            if (random.nextInt(3) == 0) {
                commands.add(command(spider));
            }
        }
        if (random.nextInt(10) == 0) {
            commands.add(cameraCommand(CameraId.values()[random.nextInt(4)]));
        }
        return commands;
    }

    private Command cameraCommand(CameraId camera) {
        return switch (random.nextInt(13)) {
            case 0 -> new Command.SelectCamera(camera);
            case 1 -> new Command.CameraEnterWriteMode(camera);
            case 2 -> new Command.CameraWrite(camera, ProgramStep.values()[1 + random.nextInt(5)]);
            case 3 -> new Command.CameraForward(camera);
            case 4 -> new Command.CameraBack(camera);
            case 5 -> new Command.CameraTurnLeft(camera);
            case 6 -> new Command.CameraTurnRight(camera);
            case 7 -> new Command.CameraFire(camera);
            case 8 -> new Command.CameraSetCursor(camera, random.nextInt(SpiderProgram.SLOTS));
            case 9 -> new Command.CameraDelete(camera);
            case 10 -> new Command.CameraClear(camera);
            case 11 -> new Command.CameraZoomIn(camera);
            default -> new Command.CameraZoomOut(camera);
        };
    }

    private Command command(SpiderId spider) {
        int pick = random.nextInt(100);
        if (pick < 30) {
            return new Command.Forward(spider);
        }
        if (pick < 45) {
            return new Command.Back(spider);
        }
        if (pick < 55) {
            return new Command.TurnLeft(spider);
        }
        if (pick < 65) {
            return new Command.TurnRight(spider);
        }
        if (pick < 72) {
            return new Command.Fire(spider);
        }
        if (pick < 76) {
            return new Command.EnterWriteMode(spider);
        }
        if (pick < 82) {
            return new Command.Write(spider, ProgramStep.values()[1 + random.nextInt(5)]);
        }
        if (pick < 85) {
            return new Command.SetCursor(spider, random.nextInt(SpiderProgram.SLOTS));
        }
        if (pick < 87) {
            return new Command.Delete(spider);
        }
        if (pick < 88) {
            return new Command.Clear(spider);
        }
        if (pick < 93) {
            return new Command.Run(spider);
        }
        if (pick < 95) {
            return new Command.Stop(spider);
        }
        if (pick < 97) {
            return new Command.RunAll();
        }
        return new Command.SelectSpider(spider);
    }
}
