package de.latzig.game.tob.core.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.latzig.game.tob.core.model.ObjectType;

/**
 * The author's hopper collision drawings ({@code TobInfo/hopper-collisions-cases.txt}, all 14), read north up (spec
 * §2): rows north first, H = hopper on floor, o = floor, blank = void; both hoppers start facing N. "rechts verliert"
 * = the eastern hopper loses, "links verliert" = the western one. In every drawing both hoppers reach the claim frame
 * of the same cell in the same frame, and the one later in the list loses (oracle §1.5 rule 3): it reverses while the
 * other one claims the cell.
 */
class HopperCollisionTest {

    static Stream<Arguments> drawings() {
        return Stream.of(
                Arguments.of(1, List.of("HoH"), "rechts"),
                Arguments.of(2, List.of("HoooH"), "rechts"),
                Arguments.of(3, List.of("H H", "ooo"), "rechts"),
                Arguments.of(4, List.of("ooo", "H H"), "rechts"),
                Arguments.of(5, List.of("oH", "H "), "rechts"),
                Arguments.of(6, List.of("oooH", "H   "), "rechts"),
                Arguments.of(7, List.of("  H", "ooo", "H  "), "rechts"),
                Arguments.of(8, List.of("  H", " Ho"), "rechts"),
                Arguments.of(9, List.of("   H", "Hooo"), "rechts"),
                Arguments.of(10, List.of("H  ", "ooo", "  H"), "links"),
                Arguments.of(11, List.of("Ho", " H"), "links"),
                Arguments.of(12, List.of("Hooo", "   H"), "links"),
                Arguments.of(13, List.of("H ", "oH"), "links"),
                Arguments.of(14, List.of("H   ", "oooH"), "links"));
    }

    @ParameterizedTest(name = "drawing {0}: {2} verliert")
    @MethodSource("drawings")
    void theHopperLaterInTheListLosesTheTie(int number, List<String> drawing, String loserSide) {
        TestTowers.Builder tower = TestTowers.tower().level(0, drawing.stream()
                .map(row -> row.replace('H', 'F').replace('o', 'F').replace(' ', '.')).toArray(String[]::new));
        for (int r = 0; r < drawing.size(); r++) {
            String row = drawing.get(r);
            for (int x = 0; x < row.length(); x++) {
                if (row.charAt(x) == 'H') {
                    tower.object(x, drawing.size() - 1 - r, 0, ObjectType.HOP);
                }
            }
        }
        SimRun run = SimRun.startTime(tower);
        SimObject first = run.sim.world().object(3);
        SimObject second = run.sim.world().object(4);
        SimObject east = first.cell().x() > second.cell().x() ? first : second;
        SimObject west = east == first ? second : first;
        SimObject loser = loserSide.equals("rechts") ? east : west;
        SimObject winner = loser == first ? second : first;

        run.runUntilFrame(200);

        SimEvent.MoveReversed reversal = run.events(SimEvent.MoveReversed.class).get(0);
        assertEquals(loser.slot(), reversal.slot(), "drawing " + number + ": the first reversal");
        assertEquals(4, loser.slot(), "drawing " + number + ": the loser is the later one in the list");
        assertTrue(run.events(SimEvent.MoveClaimed.class).stream()
                .anyMatch(claimed -> claimed.frame() == reversal.frame() && claimed.slot() == winner.slot()),
                "drawing " + number + ": a tie, the winner claims in the same frame");
        assertEquals(List.of(), run.sim.world().occupancyProblems());
    }
}
