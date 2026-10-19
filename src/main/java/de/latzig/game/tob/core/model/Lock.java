package de.latzig.game.tob.core.model;

/** Designer lock of the original tower designer. */
public record Lock(boolean locked, String password) {

    public Lock {
        Checks.maxLength("lock.password", password, 10);
    }
}
