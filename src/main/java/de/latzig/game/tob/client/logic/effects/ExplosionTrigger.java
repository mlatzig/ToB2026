package de.latzig.game.tob.client.logic.effects;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.latzig.game.tob.core.sim.CellPos;
import de.latzig.game.tob.core.sim.SimEvent;

/**
 * Which simulation events start an explosion (Phase 4b-1 spec §2 decisions 1, 2 and 5; {@code core.sim} is
 * unchanged and only read). The first {@link SimEvent.Destroyed} of an object breaks it apart; a mine or bomb blast
 * that re-arms an object already being destroyed emits a second one ({@code Rules.blast}), which starts nothing.
 * Every {@link SimEvent.FloorEaten} (lizard, wiper, mines that destroy the floor; ToB 2005's three
 * {@code TWR_destroyFloor} callers) breaks the floor apart. Destroyed spiders are objects like any other.
 */
public final class ExplosionTrigger {

    /** Something that breaks apart. */
    public sealed interface Blast permits ObjectBlast, FloorBlast {
    }

    /** The object in {@code slot} began its destruction. */
    public record ObjectBlast(int slot) implements Blast {
    }

    /** The floor of {@code cell} was eaten. */
    public record FloorBlast(CellPos cell) implements Blast {
    }

    private final Set<Integer> exploded = new HashSet<>();

    /** The blasts of one step's events, in event order. */
    public List<Blast> onStep(List<SimEvent> events) {
        List<Blast> blasts = new ArrayList<>();
        for (SimEvent event : events) {
            switch (event) {
                case SimEvent.Destroyed destroyed -> {
                    if (exploded.add(destroyed.slot())) {
                        blasts.add(new ObjectBlast(destroyed.slot()));
                    }
                }
                case SimEvent.FloorEaten eaten -> blasts.add(new FloorBlast(eaten.cell()));
                default -> {
                }
            }
        }
        return blasts;
    }
}
