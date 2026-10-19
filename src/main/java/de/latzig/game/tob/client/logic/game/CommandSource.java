package de.latzig.game.tob.client.logic.game;

import java.util.Optional;

import de.latzig.game.tob.core.sim.Command;
import de.latzig.game.tob.core.sim.Simulation;

/** Supplies at most one command per simulation frame; asked once per frame, before the step. */
@FunctionalInterface
public interface CommandSource {

    /** The command for the next frame, mapped against the simulation state before that frame, or empty. */
    Optional<Command> next(Simulation simulation);

    /**
     * Task 16 (walk look-ahead): the command {@link #next} would most likely return for the next frame if nothing
     * changed until then, without any side effect; empty if unknown. Only the drawing uses it
     * ({@link de.latzig.game.tob.client.logic.WalkLookAhead}).
     */
    default Optional<Command> peek(Simulation simulation) {
        return Optional.empty();
    }

    /** {@code source}'s commands with {@code lookAhead}'s {@link #peek} (a method reference cannot peek). */
    static CommandSource peeking(CommandSource source, CommandSource lookAhead) {
        return new CommandSource() {
            @Override
            public Optional<Command> next(Simulation simulation) {
                return source.next(simulation);
            }

            @Override
            public Optional<Command> peek(Simulation simulation) {
                return lookAhead.peek(simulation);
            }
        };
    }
}
