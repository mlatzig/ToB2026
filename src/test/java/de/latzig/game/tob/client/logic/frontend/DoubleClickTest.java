package de.latzig.game.tob.client.logic.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.client.logic.frontend.GameFlow.Hit;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Widget;

class DoubleClickTest {

    private static final Hit CELL_A = new Hit(Widget.GRID, 0);
    private static final Hit CELL_B = new Hit(Widget.GRID, 1);

    private final List<String> calls = new ArrayList<>();
    private final DoubleClick clicks = new DoubleClick();

    private void click(Hit hit, float x, float y, double seconds) {
        clicks.click(Optional.ofNullable(hit), x, y, seconds, h -> calls.add("click " + h.row()),
                h -> calls.add("double " + h.row()));
    }

    @Test
    void aSecondClickOnTheSameHitInTimeAndPlaceIsADoubleClickAndTheFirstIsNotDelayed() {
        click(CELL_A, 100f, 100f, 10.0);
        assertEquals(List.of("click 0"), calls, "the first click is reported at once");

        click(CELL_A, 102f, 99f, 10.0 + DoubleClick.INTERVAL - 0.01);
        click(CELL_A, 102f, 99f, 10.0 + DoubleClick.INTERVAL);

        assertEquals(List.of("click 0", "double 0", "click 0"), calls, "a third click starts a new pair");
    }

    @Test
    void tooLateTooFarOrAnotherHitIsASingleClick() {
        click(CELL_A, 100f, 100f, 1.0);
        click(CELL_A, 100f, 100f, 1.0 + DoubleClick.INTERVAL + 0.01);
        click(CELL_A, 100f + DoubleClick.DISTANCE + 1f, 100f, 1.0 + DoubleClick.INTERVAL + 0.1);
        click(CELL_B, 100f + DoubleClick.DISTANCE + 1f, 100f, 1.0 + DoubleClick.INTERVAL + 0.2);

        assertEquals(List.of("click 0", "click 0", "click 0", "click 1"), calls);
    }

    @Test
    void aClickBesideEveryWidgetBreaksThePairAndResetForgetsTheFirstClick() {
        click(CELL_A, 100f, 100f, 1.0);
        click(null, 100f, 100f, 1.1);
        click(CELL_A, 100f, 100f, 1.2);

        assertEquals(List.of("click 0", "click 0"), calls);

        clicks.reset();
        click(CELL_A, 100f, 100f, 1.3);

        assertEquals(List.of("click 0", "click 0", "click 0"), calls);
    }
}
