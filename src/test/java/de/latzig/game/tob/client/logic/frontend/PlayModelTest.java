package de.latzig.game.tob.client.logic.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.progress.Profile;

class PlayModelTest {

    private static List<Profile> profiles(String... names) {
        List<Profile> profiles = new ArrayList<>();
        for (String name : names) {
            profiles.add(Profile.create(name, "original"));
        }
        return profiles;
    }

    @Test
    void withoutProfilesOnlyNewProfileIsThereAndSelected() {
        PlayModel play = new PlayModel(List.of(), Optional.empty());

        assertEquals(1, play.rows());
        assertEquals(PlayModel.NEW_PROFILE, play.selected());
        assertTrue(play.newProfileEnabled());
        assertEquals(Optional.empty(), play.selectedProfile());
        assertFalse(play.move(1), "nothing below");
        assertFalse(play.move(-1), "nothing above");
    }

    @Test
    void theLastProfileIsPreselectedIgnoringCaseAndTheArrowsMoveWithoutWrapping() {
        PlayModel play = new PlayModel(profiles("Anna", "Bob", "Cid"), Optional.of("BOB"));

        assertEquals(2, play.selected(), "decision 2: the last played");
        assertEquals("Bob", play.selectedProfile().orElseThrow().name());
        assertTrue(play.move(-1));
        assertTrue(play.move(-1));
        assertEquals(PlayModel.NEW_PROFILE, play.selected());
        assertFalse(play.move(-1), "no wrap");
        assertTrue(play.select(3));
        assertFalse(play.move(1), "no wrap at the bottom");
        assertFalse(play.select(4), "outside");
        assertEquals("Cid", play.selectedProfile().orElseThrow().name());
        assertEquals(PlayModel.NEW_PROFILE, new PlayModel(profiles("Anna"), Optional.of("Gone")).selected(),
                "an unknown last profile: New Profile");
    }

    @Test
    void fiveProfilesGreyNewProfileWhichCannotBeSelected() {
        PlayModel play = new PlayModel(profiles("A", "B", "C", "D", "E"), Optional.empty());

        assertFalse(play.newProfileEnabled(), "decision 2: delete a profile first");
        assertEquals(1, play.selected(), "the first profile instead");
        assertFalse(play.selectable(PlayModel.NEW_PROFILE));
        assertFalse(play.select(PlayModel.NEW_PROFILE));
        assertFalse(play.move(-1), "the greyed row is skipped and there is nothing above it");
        assertEquals(1, play.selected());
        assertEquals(PlayModel.ROWS, play.rows());
        assertFalse(play.box().scrollable(), "six rows fit");
    }

    @Test
    void moreProfilesFromOlderDataStayListedAndScroll() {
        PlayModel play = new PlayModel(profiles("A", "B", "C", "D", "E", "F", "G"), Optional.of("G"));

        assertEquals(8, play.rows(), "nothing is deleted automatically");
        assertEquals(7, play.selected());
        assertTrue(play.box().scrollable());
        assertEquals(2, play.box().first(), "the selection is scrolled into view");
        assertTrue(play.select(1));
        assertEquals(1, play.box().first());
        assertFalse(play.move(-1), "New Profile is greyed with seven profiles");
        assertEquals(1, play.selected());
    }
}
