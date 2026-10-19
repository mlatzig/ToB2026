package de.latzig.game.tob.client.logic.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Phase 5c-2 decision 6: the original's shared interlude counter W001690 (report 17 §6). */
class InterludePickerTest {

    @Test
    void theFirstInterludesAreOneAndSeven() {
        assertEquals(1, new InterludePicker().next(true), "pre-incremented: the first success shows demo 1");
        assertEquals(7, new InterludePicker().next(false), "the first failure shows demo 7");
    }

    @Test
    void successAndFailureShareOneCounterThatWrapsAtSix() {
        InterludePicker picker = new InterludePicker();

        assertEquals(List.of(1, 8, 3), List.of(picker.next(true), picker.next(false), picker.next(true)),
                "report 17: success, failure, success gives 1, 8, 3");
        List<Integer> next = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            next.add(picker.next(i % 2 == 0));
        }
        assertEquals(List.of(4, 11, 0, 7, 2, 9), next, "mod 6: 4, 5, 6 -> 0, 7 -> 1, ...");
    }
}
