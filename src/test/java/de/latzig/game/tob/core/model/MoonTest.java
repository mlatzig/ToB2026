package de.latzig.game.tob.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/** Phase 6b-2 Task 0: the optional stored moon size. */
class MoonTest {

    @Test
    void theFiveArgumentConstructorHasNoStoredSize() {
        Moon moon = new Moon(0, 1, 8, 28, 2);

        assertEquals(Optional.empty(), moon.size());
    }

    @Test
    void theStoredSizeIsKeptWhenPresent() {
        Moon moon = new Moon(0, 1, 8, 28, 2, Optional.of(Moon.MAX_SIZE));

        assertEquals(Optional.of(Moon.MAX_SIZE), moon.size());
    }

    @Test
    void theStoredSizeIsValidatedWhenPresent() {
        assertThrows(IllegalArgumentException.class,
                () -> new Moon(0, 1, 8, 28, 2, Optional.of(Moon.MAX_SIZE + 1)));
        assertThrows(IllegalArgumentException.class, () -> new Moon(0, 1, 8, 28, 2, Optional.of(-1)));
    }

    @Test
    void aNullStoredSizeIsRejected() {
        assertThrows(NullPointerException.class, () -> new Moon(0, 1, 8, 28, 2, null));
    }
}
