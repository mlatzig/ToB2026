package de.latzig.game.tob.core.model;

/** 12-bit Amiga colour, 4 bits per channel. */
public record Rgb12(int r, int g, int b) {

    public Rgb12 {
        Checks.range("r", r, 0, 15);
        Checks.range("g", g, 0, 15);
        Checks.range("b", b, 0, 15);
    }

    public String hex() {
        return String.format("%X%X%X", r, g, b);
    }

    public static Rgb12 parseHex(String hex) {
        if (hex == null || !hex.matches("[0-9A-Fa-f]{3}")) {
            throw new IllegalArgumentException("Not a 3-digit hex colour: " + hex);
        }
        return new Rgb12(Character.digit(hex.charAt(0), 16), Character.digit(hex.charAt(1), 16),
                Character.digit(hex.charAt(2), 16));
    }
}
