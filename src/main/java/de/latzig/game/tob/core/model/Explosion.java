package de.latzig.game.tob.core.model;

import java.util.List;

/**
 * How an element breaks apart when it is destroyed (Phase 4b-1 spec §2 decision 1; ToB 2005
 * {@code TWR_destroyElement}, {@code twr.cpp:3592-3770}, and {@code TWR_destroyPlayerCamera},
 * {@code twr.cpp:2945-2970}). The 12 explosion sprites start {@code spriteOffset} units (32 per field) above the
 * element's origin. {@code meshes} are ToB 2005's pre-broken debris meshes; they replace the parts of the element's
 * first group in order, and parts beyond them do not explode (the exchanger's sparkle, the converter's glow, the
 * freezer's crystal). Empty: the parts of the group shown at the moment of destruction explode at the key shown.
 */
public record Explosion(int spriteOffset, List<String> meshes) {

    /** ToB 2005's sprite offset of most elements: 6 units. */
    public static final int LOW_OFFSET = 6;
    /** ToB 2005's sprite offset of the spiders, the enemies, the hopper and the converters: 8 units. */
    public static final int HIGH_OFFSET = 8;
    /** Own parts, sprites at 6 units: how {@code elements.json} before version 4 is read. */
    public static final Explosion DEFAULT = new Explosion(LOW_OFFSET, List.of());

    public Explosion {
        Checks.range("spriteOffset", spriteOffset, 0, 64);
        meshes = List.copyOf(meshes);
    }
}
