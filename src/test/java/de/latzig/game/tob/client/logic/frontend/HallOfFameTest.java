package de.latzig.game.tob.client.logic.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.progress.Profile;
import de.latzig.game.tob.core.progress.TowerProgress;

class HallOfFameTest {

    /** A profile with {@code score} and {@code solved} completed towers in group {@code group}. */
    private static Profile profile(String name, long score, int solved, int group) {
        Map<String, TowerProgress> towers = new TreeMap<>();
        for (int i = 0; i < solved; i++) {
            towers.put(String.format("original-%03d", i),
                    new TowerProgress(1, 0, true, 100, Optional.empty(), OptionalInt.empty()));
        }
        return new Profile(name, "original", group, towers, score);
    }

    @Test
    void theProfilesAreRankedByScoreThenSolvedThenNameAndEqualScoresShareARank() {
        List<HallOfFame.Entry> ranked = HallOfFame.rank(List.of(profile("zed", 200, 1, 0), profile("Anna", 452, 2, 1),
                profile("bob", 200, 1, 0), profile("Cid", 200, 3, 2), profile("Dan", 0, 0, 0)));

        assertEquals(List.of(new HallOfFame.Entry(1, "Anna", 452, 2, 1), new HallOfFame.Entry(2, "Cid", 200, 3, 2),
                new HallOfFame.Entry(2, "bob", 200, 1, 0), new HallOfFame.Entry(2, "zed", 200, 1, 0),
                new HallOfFame.Entry(5, "Dan", 0, 0, 0)), ranked);
        assertEquals(List.of(), HallOfFame.rank(List.of()));
    }

    @Test
    void openingShowsTheScoresTabAtTheTopAndEachTabScrollsItsOwnBox() {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            lines.add("line " + i);
        }
        HallOfFame hall = new HallOfFame(lines);
        List<Profile> seven = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            seven.add(profile("P" + i, 10L * i, 0, 0));
        }

        hall.open(seven);
        assertEquals(HallOfFame.Tab.SCORES, hall.tab());
        assertEquals(hall.scores(), hall.box());
        assertTrue(hall.scores().scrollable(), "seven profiles from older data scroll");
        hall.box().end();
        hall.switchTab();
        assertEquals(HallOfFame.Tab.CREDITS, hall.tab());
        assertEquals(hall.credits(), hall.box());
        assertEquals(HallOfFame.CREDIT_ROWS, hall.credits().visible());
        hall.box().pageDown();
        assertEquals(HallOfFame.CREDIT_ROWS, hall.credits().first());
        assertEquals(2, hall.scores().first(), "the SCORES box kept its place");

        hall.open(seven.subList(0, 2));
        assertEquals(HallOfFame.Tab.SCORES, hall.tab(), "every opening starts on SCORES");
        assertEquals(0, hall.scores().first());
        assertEquals(0, hall.credits().first());
        hall.select(HallOfFame.Tab.CREDITS);
        hall.switchTab();
        assertEquals(HallOfFame.Tab.SCORES, hall.tab(), "two tabs: switching toggles");
    }
}
