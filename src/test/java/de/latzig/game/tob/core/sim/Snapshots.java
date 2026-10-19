package de.latzig.game.tob.core.sim;

/** Text dump of the complete simulation state, for "same final state" comparisons. */
public final class Snapshots {

    private Snapshots() {
    }

    public static String of(Simulation sim) {
        WorldState world = sim.world();
        StringBuilder text = new StringBuilder();
        text.append("frame=").append(world.frame()).append(" phase=").append(world.clockPhase())
                .append(" time=").append(world.timeLeft()).append(" ready=").append(world.isReady())
                .append(" frozen=").append(world.isFrozen()).append(" freeze=").append(world.freezeCounter())
                .append(" exit=").append(world.exitCode())
                .append('/').append(world.exitDelay()).append(" finished=").append(world.isFinished())
                .append(" klondikes=").append(world.counters().klondikesCollected())
                .append(" robots=").append(world.counters().robotsDestroyed())
                .append(" selected=").append(sim.selected()).append('\n');
        for (SimObject o : world.objects()) {
            text.append(o.slot()).append(' ').append(o.type()).append(' ').append(o.status()).append(' ')
                    .append(o.cell()).append(' ').append(o.secondCell()).append(' ').append(o.facing()).append(' ')
                    .append(o.moveKind()).append(' ').append(o.moveDirection()).append(' ').append(o.moveCounter())
                    .append(' ').append(o.destroyCounter()).append(' ').append(o.objectFlags()).append(' ')
                    .append(o.classTimer()).append(' ').append(o.isDeactivated()).append(' ').append(o.markedCell())
                    .append(' ').append(o.worldX())
                    .append(',')
                    .append(o.worldY()).append(',').append(o.worldZ()).append('\n');
        }
        for (Lift lift : world.lifts()) {
            text.append("lift ").append(lift.lower()).append(' ').append(lift.state()).append(' ')
                    .append(lift.rider()).append('\n');
        }
        ForcerLifts forcer = world.forcerLifts();
        text.append("forcer ").append(forcer.timer()).append(' ').append(forcer.isUp()).append(' ')
                .append(forcer.platforms()).append('\n');
        text.append(world.occupancy().heldCells()).append('\n');
        for (int index = 0; index < CellPos.COUNT; index++) {
            CellPos pos = CellPos.ofIndex(index);
            if (world.flags(pos) != 0) {
                text.append(pos).append('=').append(world.flags(pos)).append(' ');
            }
        }
        text.append('\n');
        for (SpiderId id : SpiderId.values()) {
            SpiderProgram program = sim.program(id);
            text.append(id).append(' ').append(program.steps()).append(' ').append(program.cursor()).append(' ')
                    .append(program.state()).append('\n');
        }
        for (CameraId id : CameraId.values()) {
            SpiderProgram program = sim.cameraProgram(id);
            CameraPose pose = sim.camera(id);
            text.append("camera ").append(id).append(' ').append(program.steps()).append(' ').append(program.cursor())
                    .append(' ').append(program.state()).append(' ').append(pose.height()).append(',')
                    .append(pose.offset()).append(',').append(pose.distance()).append('\n');
        }
        return text.toString();
    }
}
