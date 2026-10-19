package de.latzig.game.tob.core.sim;

import de.latzig.game.tob.core.model.ObjectType;

/** The three player spiders with their fixed slots 0–2 (original slots 4–6, always first in the list). */
public enum SpiderId implements Viewpoint {
    ZAPPER(ObjectType.ZAP), PUSHER(ObjectType.PSH), GRABBER(ObjectType.GRB);

    private static final SpiderId[] VALUES = values();

    private final ObjectType type;

    SpiderId(ObjectType type) {
        this.type = type;
    }

    public ObjectType type() {
        return type;
    }

    public int slot() {
        return ordinal();
    }

    public static SpiderId of(ObjectType type) {
        for (SpiderId id : VALUES) {
            if (id.type == type) {
                return id;
            }
        }
        throw new IllegalArgumentException(type.code() + " is not a spider");
    }

    public static SpiderId ofSlot(int slot) {
        if (slot < 0 || slot >= VALUES.length) {
            throw new IllegalArgumentException("not a spider slot: " + slot);
        }
        return VALUES[slot];
    }
}
