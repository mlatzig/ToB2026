package de.latzig.game.tob.core.model;

import java.util.Objects;

/** Argument checks shared by the model records. */
final class Checks {

    private Checks() {
    }

    static int range(String field, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(field + " out of range " + min + ".." + max + ": " + value);
        }
        return value;
    }

    static String maxLength(String field, String value, int max) {
        Objects.requireNonNull(value, field);
        if (value.length() > max) {
            throw new IllegalArgumentException(field + " longer than " + max + " characters: " + value.length());
        }
        return value;
    }
}
