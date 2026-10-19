package de.latzig.game.tob.core.model;

import java.util.List;

/** The original 18-colour tower palette. */
public record Palette(List<Rgb12> colours) {

    public static final int SIZE = 18;

    public Palette {
        colours = List.copyOf(colours);
        if (colours.size() != SIZE) {
            throw new IllegalArgumentException("palette needs " + SIZE + " colours, got " + colours.size());
        }
    }
}
