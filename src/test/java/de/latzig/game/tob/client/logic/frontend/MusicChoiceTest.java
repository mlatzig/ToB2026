package de.latzig.game.tob.client.logic.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.latzig.game.tob.client.logic.Texts;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Hit;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Screen;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Widget;
import de.latzig.game.tob.client.logic.frontend.MusicChoice.Change;
import de.latzig.game.tob.client.logic.frontend.MusicChoice.Fade;
import de.latzig.game.tob.client.logic.frontend.MusicChoice.Song;
import de.latzig.game.tob.client.logic.ui.ScreenFade;
import de.latzig.game.tob.client.logic.ui.UiKey;
import de.latzig.game.tob.core.sim.Rules;

/** Phase 5c-2 decisions 1-4: which song plays on which screen, and when it starts, keeps playing or stops. */
class MusicChoiceTest {

    private static final Optional<Song> DREAMLAND = Optional.of(Song.DREAMLAND);
    private static final Optional<Song> NONE = Optional.empty();

    @Test
    void dreamlandOnTheStartScreensTheResultSongsOnTheResultWindowSilenceElsewhere() {
        for (Screen screen : Screen.values()) {
            Optional<Song> expected = switch (screen) {
                case MENU, PLAY, NEW_GAME, OVERVIEW, TUTORIAL, BRIEFING, OPTIONS, HALL_OF_FAME -> DREAMLAND;
                case RESULT -> Optional.of(Song.IMPROVI);
                case LOADER, GAME, EDITOR -> NONE;
            };
            assertEquals(expected, MusicChoice.wanted(screen, true, false, true), screen.name());
            assertEquals(screen == Screen.RESULT ? Optional.of(Song.FADEOUT) : expected,
                    MusicChoice.wanted(screen, false, false, true), screen.name() + ", failed or abandoned");
            assertEquals(NONE, MusicChoice.wanted(screen, true, false, false), screen.name() + ", music off");
            assertEquals(NONE, MusicChoice.wanted(screen, true, true, true), screen.name() + ", test play");
        }
    }

    @Test
    void dreamlandKeepsPlayingAcrossTheMenusStopsAtATowerAndStartsAfreshAfterTheGame() {
        MusicChoice choice = new MusicChoice();
        List<Change> changes = new ArrayList<>();
        for (Screen screen : List.of(Screen.LOADER, Screen.MENU, Screen.PLAY, Screen.NEW_GAME, Screen.OVERVIEW,
                Screen.BRIEFING, Screen.GAME, Screen.RESULT, Screen.RESULT, Screen.OVERVIEW, Screen.MENU,
                Screen.OPTIONS, Screen.MENU, Screen.EDITOR, Screen.MENU, Screen.HALL_OF_FAME, Screen.TUTORIAL)) {
            changes.add(choice.next(MusicChoice.wanted(screen, true, false, true)));
        }

        assertEquals(List.of(Change.KEEP, Change.START, Change.KEEP, Change.KEEP, Change.KEEP, Change.KEEP,
                Change.STOP, Change.START, Change.KEEP, Change.START, Change.KEEP, Change.KEEP, Change.KEEP,
                Change.STOP, Change.START, Change.KEEP, Change.KEEP), changes);
        assertEquals(DREAMLAND, choice.song());
    }

    @Test
    void theSwitchStopsAndRestartsTheSongAtOnce() {
        MusicChoice choice = new MusicChoice();

        assertEquals(Change.START, choice.next(MusicChoice.wanted(Screen.OPTIONS, false, false, true)));
        assertEquals(Change.STOP, choice.next(MusicChoice.wanted(Screen.OPTIONS, false, false, false)));
        assertEquals(NONE, choice.song());
        assertEquals(Change.KEEP, choice.next(MusicChoice.wanted(Screen.MENU, false, false, false)));
        assertEquals(Change.START, choice.next(MusicChoice.wanted(Screen.OPTIONS, false, false, true)),
                "switched on again: from the beginning");
        assertEquals(Change.STOP, choice.next(MusicChoice.wanted(Screen.GAME, false, false, true)));
        assertEquals(Change.START, choice.next(MusicChoice.wanted(Screen.RESULT, false, false, true)));
        assertEquals(Optional.of(Song.FADEOUT), choice.song());
    }

    @Test
    void onlyDreamlandLoopsAndEverySongIsAnOggInTheMusicFolder() {
        assertTrue(Song.DREAMLAND.loops());
        assertFalse(Song.IMPROVI.loops(), "decision 2: once, then silence");
        assertFalse(Song.FADEOUT.loops());
        assertEquals(List.of("music/dreamland.ogg", "music/improvi.ogg", "music/fadeout.ogg"),
                List.of(Song.DREAMLAND.file(), Song.IMPROVI.file(), Song.FADEOUT.file()));
    }

    /** The flow's state as the app reads it every frame: its screen, its result and a test play. */
    @Test
    void theFlowsStateChoosesTheSong(@TempDir Path userDir) {
        Campaign campaign = Campaign.of(CampaignTest.towers());
        GameFlow flow = new GameFlow(Texts.english(), new ProfileStore(userDir));
        assertEquals(NONE, MusicChoice.wanted(flow, true), "the loader");
        flow.loaded(campaign);
        for (int i = 0; i < 4; i++) {
            flow.update(ScreenFade.SECONDS);
        }
        assertEquals(DREAMLAND, MusicChoice.wanted(flow, true), "the main menu");
        assertEquals(NONE, MusicChoice.wanted(flow, false), "music off");

        GameFlow developer = new GameFlow(Texts.english(), new ProfileStore(userDir));
        developer.developerSession(campaign, campaign.tower(0, 0));
        assertEquals(NONE, MusicChoice.wanted(developer, true), "the game");
        developer.attemptEnded(Rules.EXIT_ABANDONED, 3, 100, Instant.EPOCH);
        assertEquals(Optional.of(Song.FADEOUT), MusicChoice.wanted(developer, true), "abandoned");
        developer.attemptEnded(Rules.EXIT_COMPLETED, 3, 100, Instant.EPOCH);
        assertEquals(Optional.of(Song.IMPROVI), MusicChoice.wanted(developer, true), "completed");
    }

    /**
     * The author's answer to question 3 (2026-10-15): the music fades out with the screen when the next screen wants
     * another song or none, a kept song keeps its volume, a song started at the black point fades in with the next
     * screen, and one started without a fade (the Music switch) plays at full volume at once.
     */
    @Test
    void theMusicFadesOutAndInWithTheScreenWhenTheSongChanges() {
        MusicChoice choice = new MusicChoice();
        Optional<Song> improvi = Optional.of(Song.IMPROVI);

        choice.next(DREAMLAND);
        assertEquals(1f, choice.gain(Fade.NONE), 1e-6f, "started without a fade (the switch): at once");
        assertEquals(1f, choice.gain(new Fade(true, DREAMLAND, 0.6f)), 1e-6f, "to a start screen: kept, no dip");
        assertEquals(1f, choice.gain(new Fade(false, NONE, 0.6f)), 1e-6f, "the next screen fades in: still kept");
        assertEquals(0.75f, choice.gain(new Fade(true, NONE, 0.25f)), 1e-6f, "to the game: out with the screen");
        assertEquals(0.25f, choice.gain(new Fade(true, improvi, 0.75f)), 1e-6f);
        choice.next(NONE);
        choice.next(improvi);
        assertEquals(0f, choice.gain(new Fade(false, NONE, 1f)), 1e-6f, "started at black");
        assertEquals(0.4f, choice.gain(new Fade(false, NONE, 0.6f)), 1e-6f, "in with the screen");
        assertEquals(1f, choice.gain(Fade.NONE), 1e-6f);
        assertEquals(0.5f, choice.gain(new Fade(true, DREAMLAND, 0.5f)), 1e-6f, "leaving the result window");
        assertEquals(0.5f, choice.gain(new Fade(true, DREAMLAND, 0.3f)), 1e-6f, "never louder while fading out");
        choice.next(DREAMLAND);
        assertEquals(0.3f, choice.gain(new Fade(false, NONE, 0.7f)), 1e-6f, "back in the menus: in with the screen");
        assertEquals(0.3f, choice.gain(new Fade(true, NONE, 0.1f)), 1e-6f,
                "a fade-out during the fade-in: no jump up, down from where it is");
        assertEquals(0.2f, choice.gain(new Fade(true, NONE, 0.8f)), 1e-6f);
    }

    /** The flow tells the fade's target: the next screen while it fades out, nothing for Quit. */
    @Test
    void theFlowsFadeTellsTheSongAfterIt(@TempDir Path userDir) {
        GameFlow flow = new GameFlow(Texts.english(), new ProfileStore(userDir));
        flow.loaded(Campaign.of(CampaignTest.towers()));

        assertTrue(flow.fadingOut());
        assertEquals(Optional.of(Screen.MENU), flow.nextScreen(), "the loader fades out to the main menu");
        assertEquals(new Fade(true, DREAMLAND, 0f), MusicChoice.fade(flow, true));
        assertEquals(new Fade(true, NONE, 0f), MusicChoice.fade(flow, false), "music off");
        flow.update(ScreenFade.SECONDS);
        assertFalse(flow.fadingOut(), "black: the main menu fades in");
        assertEquals(Optional.empty(), flow.nextScreen());
        assertEquals(new Fade(false, NONE, 1f), MusicChoice.fade(flow, true));
        flow.update(ScreenFade.SECONDS);
        flow.click(Hit.of(Widget.MENU_OPTIONS));
        flow.update(ScreenFade.SECONDS / 2);
        assertEquals(new Fade(true, DREAMLAND, 0.5f), MusicChoice.fade(flow, true), "to Options: Dreamland stays");
        flow.update(ScreenFade.SECONDS);
        flow.update(ScreenFade.SECONDS);
        flow.key(UiKey.ESCAPE);
        flow.update(ScreenFade.SECONDS);
        flow.update(ScreenFade.SECONDS);
        assertEquals(Screen.MENU, flow.screen());
        flow.key(UiKey.ESCAPE);
        flow.click(Hit.of(Widget.YES));

        assertTrue(flow.fadingOut(), "Quit fades out");
        assertEquals(Optional.empty(), flow.nextScreen(), "no screen after Quit");
        assertEquals(new Fade(true, NONE, 0f), MusicChoice.fade(flow, true));
    }
}
