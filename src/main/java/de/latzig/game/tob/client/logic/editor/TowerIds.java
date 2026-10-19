package de.latzig.game.tob.client.logic.editor;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The ids of new own towers (Phase 6d spec decision 7): a random {@link UUID} each ({@link UUID#randomUUID()}), given
 * once when the Tower Designer creates a tower and never renumbered, stored only as the tower's {@code id} in the
 * library JSON. Tests inject a predictable source. Engine-free.
 */
public final class TowerIds {

    private final Supplier<UUID> source;

    /** Random UUIDs. */
    public TowerIds() {
        this(UUID::randomUUID);
    }

    /** @param source where the UUIDs come from (tests: a predictable one) */
    public TowerIds(Supplier<UUID> source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    /** A new tower's id: the UUID's 36-character text form (lower case hex digits and four dashes). */
    public String next() {
        return source.get().toString();
    }
}
