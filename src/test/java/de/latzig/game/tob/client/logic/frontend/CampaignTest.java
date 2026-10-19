package de.latzig.game.tob.client.logic.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.io.TowerLibraryLoader;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.progress.Outcome;
import de.latzig.game.tob.core.progress.Profile;
import de.latzig.game.tob.core.sim.TestTowers;
import de.latzig.game.tob.core.sim.TowerAssets;

public class CampaignTest {

    /** 117 test towers {@code original-000} … {@code original-116} in groups of 9. */
    public static List<TowerDefinition> towers() {
        TowerDefinition base = TestTowers.tower().level(0, "FF").build();
        List<TowerDefinition> towers = new ArrayList<>();
        for (int i = 0; i < 117; i++) {
            towers.add(new TowerDefinition(String.format("original-%03d", i), i / 9, i % 9, "Tower " + (i + 1),
                    "Pete Cooke", base.conditions(), base.behaviour(), base.effects(), base.moon(), base.palette(),
                    base.sounds(), base.lock(), base.grid(), base.remake()));
        }
        return towers;
    }

    public static Profile complete(Profile profile, String... ids) {
        for (String id : ids) {
            profile = profile.started(id).ended(id, Outcome.COMPLETED, 1, 100, Instant.EPOCH).profile();
        }
        return profile;
    }

    @Test
    void theTowersArePlacedByGroupAndIndex() {
        Campaign campaign = Campaign.of(towers());

        assertEquals("original-010", campaign.tower(1, 1).id());
        assertEquals(Optional.of(new Campaign.Location(12, 8)), campaign.locate("original-116"));
        assertEquals(Optional.empty(), campaign.locate("tutorial-000"));
        assertEquals(11, campaign.number(1, 1), "1-based as ToB 2009's list");
        assertThrows(IllegalArgumentException.class, () -> Campaign.of(towers().subList(0, 116)), "117 needed");
    }

    @Test
    void solvedTowersStayPlayableAsPracticeAndTheNextTowerIsTheFirstOpenOne() {
        Campaign campaign = Campaign.of(towers());
        Profile profile = complete(Profile.create("Anna", "original"), "original-000");

        assertEquals(0b000001010, campaign.accessibleMask(profile, 0), "A opens B and D");
        assertTrue(campaign.isPlayable(profile, 0, 0), "A as practice");
        assertTrue(campaign.isPlayable(profile, 0, 1));
        assertFalse(campaign.isPlayable(profile, 0, 2), "C closed again");
        assertEquals(1, campaign.nextIndex(profile, 0));
        assertEquals(1, campaign.completedCount(profile));
        assertFalse(campaign.allSolved(profile));
    }

    @Test
    void theOriginalLibraryFillsThirteenGroupsOfNine() throws IOException {
        assumeTrue(TowerAssets.available(), "converted towers not available");
        List<TowerDefinition> original = new TowerLibraryLoader()
                .load(TowerAssets.directory().resolve("original.json")).playableTowers();

        Campaign campaign = Campaign.of(original);

        assertEquals("original-000", campaign.tower(0, 0).id());
        assertEquals(Optional.of(new Campaign.Location(12, 8)), campaign.locate(campaign.tower(12, 8).id()));
    }
}
