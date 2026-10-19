package de.latzig.game.tob.client.logic.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.latzig.game.tob.client.logic.Texts;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Effect;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Hit;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Modal;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Screen;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Widget;
import de.latzig.game.tob.client.logic.game.GameAction;
import de.latzig.game.tob.client.logic.game.KeyChord;
import de.latzig.game.tob.client.logic.ui.ScreenFade;
import de.latzig.game.tob.client.logic.ui.ScrollBox;
import de.latzig.game.tob.client.logic.ui.UiKey;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.progress.Profile;
import de.latzig.game.tob.core.sim.Rules;

class GameFlowTest {

    private static final Campaign CAMPAIGN = Campaign.of(CampaignTest.towers());
    private static final Instant NOW = Instant.parse("2026-10-12T12:00:00Z");

    private static GameFlow menu(Path userDir) {
        return menu(userDir, new ProfileStore(userDir));
    }

    private static GameFlow menu(Path userDir, ProfileStore store) {
        GameFlow flow = new GameFlow(Texts.english(), store);
        flow.loaded(CAMPAIGN);
        settle(flow);
        return flow;
    }

    /**
     * A {@link ProfileStore} whose next {@code save} fails with a controllable {@link IOException}, once, so a test
     * can make exactly one autosave fail without touching the real file system (review Important #1).
     */
    private static final class FlakyStore extends ProfileStore {

        private boolean failNextSave;

        FlakyStore(Path userDir) {
            super(userDir);
        }

        void failNextSave() {
            failNextSave = true;
        }

        @Override
        public void save(Profile profile) throws IOException {
            if (failNextSave) {
                failNextSave = false;
                throw new IOException("disk full (test)");
            }
            super.save(profile);
        }
    }

    /** Lets every pending fade finish. */
    private static void settle(GameFlow flow) {
        for (int i = 0; i < 4; i++) {
            flow.update(ScreenFade.SECONDS);
        }
    }

    private static void keys(GameFlow flow, UiKey... keys) {
        for (UiKey key : keys) {
            flow.key(key);
        }
        settle(flow);
    }

    private static void type(GameFlow flow, String text) {
        for (char c : text.toCharArray()) {
            flow.typed(c);
        }
    }

    /** Main menu → Play → "&lt; New Profile &gt;" → "Anna" → Overview. */
    private static GameFlow overview(Path userDir) {
        return overview(userDir, new ProfileStore(userDir));
    }

    private static GameFlow overview(Path userDir, ProfileStore store) {
        GameFlow flow = menu(userDir, store);
        keys(flow, UiKey.ENTER);
        keys(flow, UiKey.ENTER);
        type(flow, "Anna");
        keys(flow, UiKey.ENTER, UiKey.ENTER);
        return flow;
    }

    /** Overview → Briefing → sky ready, one key short of starting the tower. */
    private static GameFlow readyToPlay(Path userDir, ProfileStore store) {
        GameFlow flow = overview(userDir, store);
        keys(flow, UiKey.ENTER);
        flow.skyProgress(1f);
        return flow;
    }

    private static List<Effect> effects(GameFlow flow) {
        List<Effect> all = new ArrayList<>();
        for (Optional<Effect> effect = flow.poll(); effect.isPresent(); effect = flow.poll()) {
            all.add(effect.get());
        }
        return all;
    }

    @Test
    void theLoaderFadesToTheMenuWhoseUpAndDownSkipTheGreyedEntries(@TempDir Path userDir) {
        GameFlow flow = new GameFlow(Texts.english(), new ProfileStore(userDir));
        assertEquals(Screen.LOADER, flow.screen());

        flow.loaded(CAMPAIGN);
        assertEquals(Screen.LOADER, flow.screen(), "fading out first");
        settle(flow);

        assertEquals(Screen.MENU, flow.screen());
        assertEquals(List.of(Widget.MENU_PLAY, Widget.MENU_TUTORIAL, Widget.MENU_DESIGNER, Widget.MENU_OPTIONS,
                Widget.MENU_HALL_OF_FAME, Widget.MENU_QUIT), GameFlow.MENU, "Phase 5c-1 decision 1");
        assertEquals(Widget.MENU_PLAY, flow.focus());
        keys(flow, UiKey.DOWN);
        assertEquals(Widget.MENU_OPTIONS, flow.focus(), "Tutorial and (without libraries) Tower Designer greyed");
        keys(flow, UiKey.DOWN);
        assertEquals(Widget.MENU_HALL_OF_FAME, flow.focus());
        keys(flow, UiKey.DOWN);
        assertEquals(Widget.MENU_QUIT, flow.focus());
        keys(flow, UiKey.DOWN);
        assertEquals(Widget.MENU_QUIT, flow.focus(), "no wrap");
        assertFalse(flow.isEnabled(Widget.MENU_DESIGNER));
        flow.click(Hit.of(Widget.MENU_DESIGNER));
        settle(flow);
        assertEquals(Screen.MENU, flow.screen(), "a greyed entry does nothing");
    }

    // ------------------------------------------------------------------ Phase 5b: Options and Credits

    private static GameFlow options(Path userDir, LiveSettings live) {
        GameFlow flow = new GameFlow(Texts.english(), new ProfileStore(userDir), live, OptionsModelTest.table());
        flow.loaded(CAMPAIGN);
        settle(flow);
        flow.click(Hit.of(Widget.MENU_OPTIONS));
        settle(flow);
        return flow;
    }

    @Test
    void optionsAndTheHallOfFameOpenFromTheMenuAndEscReturns(@TempDir Path userDir) {
        GameFlow flow = menu(userDir);

        keys(flow, UiKey.DOWN, UiKey.ENTER);
        assertEquals(Screen.OPTIONS, flow.screen());
        assertEquals(OptionsModel.Tab.SOUND, flow.options().tab());
        keys(flow, UiKey.ESCAPE);
        assertEquals(Screen.MENU, flow.screen());

        flow.click(Hit.of(Widget.MENU_HALL_OF_FAME));
        settle(flow);
        assertEquals(Screen.HALL_OF_FAME, flow.screen());
        assertEquals(HallOfFame.Tab.SCORES, flow.hallOfFame().tab(), "Phase 5c-1 decision 4: SCORES first");
        assertTrue(flow.creditLines().contains("Pete Cooke"), "decision 8: " + flow.creditLines());
        assertTrue(flow.creditLines().contains("Marcus Pantel"));
        assertEquals(HallOfFame.CREDIT_ROWS, flow.credits().visible());
        keys(flow, UiKey.ESCAPE);
        assertEquals(Screen.MENU, flow.screen());
    }

    /**
     * Phase 5c-1 decision 4: SCORES ranks the profiles as they are now (no file of its own); Left, Right and Tab
     * switch the tab, the keys scroll the tab shown, Enter goes back.
     */
    @Test
    void theHallOfFameRanksTheProfilesAndItsTabsSwitchByKeyAndClick(@TempDir Path userDir) throws IOException {
        ProfileStore store = new ProfileStore(userDir);
        store.save(Profile.create("Anna", "original"));
        store.save(CampaignTest.complete(Profile.create("Bob", "original"), "original-000"));
        GameFlow flow = menu(userDir);

        flow.click(Hit.of(Widget.MENU_HALL_OF_FAME));
        settle(flow);
        assertEquals(List.of(new HallOfFame.Entry(1, "Bob", 210, 1, 0), new HallOfFame.Entry(2, "Anna", 0, 0, 0)),
                flow.hallOfFame().entries());
        assertEquals(Optional.of(flow.hallOfFame().scores()), flow.scrollBox());
        keys(flow, UiKey.RIGHT);
        assertEquals(HallOfFame.Tab.CREDITS, flow.hallOfFame().tab());
        assertEquals(Optional.of(flow.credits()), flow.scrollBox());
        keys(flow, UiKey.PAGE_DOWN);
        assertEquals(HallOfFame.CREDIT_ROWS, flow.credits().first());
        keys(flow, UiKey.TAB);
        assertEquals(HallOfFame.Tab.SCORES, flow.hallOfFame().tab());
        flow.click(new Hit(Widget.TAB, HallOfFame.Tab.CREDITS.ordinal()));
        assertEquals(HallOfFame.Tab.CREDITS, flow.hallOfFame().tab(), "a click on the tab");
        keys(flow, UiKey.ENTER);

        assertEquals(Screen.MENU, flow.screen());
        try (Stream<Path> files = Files.list(userDir)) {
            assertEquals(List.of("savegames"), files.map(file -> file.getFileName().toString()).toList(),
                    "no file of its own");
        }
    }

    /** Decision 1: Apply saves; a fresh flow (a restart of the game) reads the options back. */
    @Test
    void applySavesTheOptionsAndAFreshFlowReadsThemBack(@TempDir Path userDir) {
        LiveSettings live = LiveSettings.load(new ProfileStore(userDir).settings());
        GameFlow flow = options(userDir, live);

        keys(flow, UiKey.DOWN, UiKey.RIGHT, UiKey.RIGHT);
        assertEquals(120, live.current().masterVolume(), "previewed (audible) at once");
        flow.click(new Hit(Widget.TAB, OptionsModel.Tab.GAME.ordinal()));
        flow.click(new Hit(Widget.OPTION_SWITCH, OptionsModel.Option.BACKWARDS_ALLOWED.ordinal()));
        flow.click(Hit.of(Widget.APPLY));
        settle(flow);

        assertEquals(Screen.MENU, flow.screen());
        Settings read = LiveSettings.load(new ProfileStore(userDir).settings()).saved();
        assertEquals(120, read.masterVolume());
        assertFalse(read.backwardsAllowed());
    }

    /** Decision 1: Back (and Esc) restore the values from before the window was opened. */
    @Test
    void backDiscardsEveryChange(@TempDir Path userDir) {
        LiveSettings live = LiveSettings.load(new ProfileStore(userDir).settings());
        GameFlow flow = options(userDir, live);

        flow.click(new Hit(Widget.OPTION_MINUS, OptionsModel.Option.MASTER_VOLUME.ordinal()));
        flow.click(new Hit(Widget.TAB, OptionsModel.Tab.VIDEO.ordinal()));
        flow.click(new Hit(Widget.OPTION_PLUS, OptionsModel.Option.BRIGHTNESS.ordinal()));
        assertEquals(90, live.current().masterVolume());
        assertEquals(110, live.current().brightness());
        flow.click(Hit.of(Widget.BACK));
        settle(flow);

        assertEquals(Screen.MENU, flow.screen());
        assertEquals(Settings.DEFAULTS, live.current());
        assertEquals(Settings.DEFAULTS, LiveSettings.load(new ProfileStore(userDir).settings()).saved());
    }

    /** Decision 5: a key is captured through the flow; Reset to defaults asks ARE YOU SURE? with NO preselected. */
    @Test
    void aKeyIsCapturedAndResetAsksFirst(@TempDir Path userDir) {
        LiveSettings live = LiveSettings.load(new ProfileStore(userDir).settings());
        GameFlow flow = options(userDir, live);
        int fire = flow.options().actions().indexOf(GameAction.FIRE);

        flow.click(new Hit(Widget.KEY_ROW, fire));
        assertTrue(flow.capturing());
        assertTrue(flow.captureKey(OptionsModelTest.F11, false));
        assertFalse(flow.capturing());
        assertEquals(KeyChord.of(OptionsModelTest.F11), live.current().keys().get(GameAction.FIRE));
        assertFalse(flow.captureKey(OptionsModelTest.Q, false), "not capturing");

        flow.click(Hit.of(Widget.KEYS_RESET));
        assertEquals(Modal.CONFIRM_RESET_KEYS, flow.modal());
        assertFalse(flow.modalYes(), "NO preselected");
        keys(flow, UiKey.ENTER);
        assertEquals(1, live.current().keys().size(), "NO keeps the keys");
        flow.click(Hit.of(Widget.KEYS_RESET));
        flow.click(Hit.of(Widget.YES));
        assertEquals(Map.of(), live.current().keys());
        assertEquals(Screen.OPTIONS, flow.screen());
    }

    /** Decision 10: a failed save is shown; the window stays open with the changes. */
    @Test
    void aFailedApplyShowsAMessageAndStays(@TempDir Path userDir) throws IOException {
        Files.createDirectories(userDir.resolve("settings.json").resolve("blocked"));
        LiveSettings live = LiveSettings.load(new ProfileStore(userDir).settings());
        GameFlow flow = options(userDir, live);

        flow.click(new Hit(Widget.OPTION_PLUS, OptionsModel.Option.BACKGROUND_VOLUME.ordinal()));
        flow.click(Hit.of(Widget.APPLY));
        settle(flow);

        assertEquals(Screen.OPTIONS, flow.screen());
        assertEquals(Modal.MESSAGE, flow.modal());
        assertTrue(flow.message().startsWith("The options cannot be saved"), flow.message());
        assertEquals(110, live.current().backgroundVolume(), "the change stays shown");
    }

    /** Decision 8: the credits scroll with the keys, the wheel and the scroll bar. */
    @Test
    void theCreditsScroll(@TempDir Path userDir) {
        GameFlow flow = menu(userDir);
        flow.click(Hit.of(Widget.MENU_HALL_OF_FAME));
        settle(flow);
        flow.click(new Hit(Widget.TAB, HallOfFame.Tab.CREDITS.ordinal()));
        ScrollBox credits = flow.credits();
        assertTrue(credits.scrollable(), credits.size() + " lines");

        keys(flow, UiKey.DOWN, UiKey.DOWN);
        assertEquals(2, credits.first());
        keys(flow, UiKey.PAGE_DOWN);
        assertEquals(Math.min(credits.maxFirst(), 2 + HallOfFame.CREDIT_ROWS), credits.first());
        keys(flow, UiKey.HOME);
        flow.wheel(-1);
        assertEquals(ScrollBox.WHEEL_ROWS, credits.first());
        keys(flow, UiKey.END);
        assertEquals(credits.maxFirst(), credits.first());
        flow.scrollPress(credits.thumbStart() + 0.01f);
        flow.scrollDrag(0f);
        flow.scrollRelease();
        assertEquals(0, credits.first(), "dragged to the top");
        assertEquals(Optional.of(credits), flow.scrollBox());
        keys(flow, UiKey.ENTER);
        assertEquals(Screen.MENU, flow.screen());
        assertEquals(Optional.empty(), flow.scrollBox());
    }

    @Test
    void escOnTheMenuAndQuitAskWithNoPreselected(@TempDir Path userDir) {
        GameFlow flow = menu(userDir);

        keys(flow, UiKey.ESCAPE);
        assertEquals(Modal.CONFIRM_QUIT, flow.modal());
        assertFalse(flow.modalYes(), "NO preselected");
        keys(flow, UiKey.ENTER);
        assertEquals(Modal.NONE, flow.modal(), "NO");
        assertEquals(List.of(), effects(flow));

        flow.click(Hit.of(Widget.MENU_QUIT));
        keys(flow, UiKey.LEFT, UiKey.ENTER);

        assertEquals(List.of(new GameFlow.Quit()), effects(flow), "YES quits after the fade");
    }

    @Test
    void aNewProfileNeedsAFreshNonEmptyName(@TempDir Path userDir) throws IOException {
        GameFlow flow = menu(userDir);
        new ProfileStore(userDir).save(Profile.create("ANNA", "original"));
        keys(flow, UiKey.ENTER);
        assertEquals(Screen.PLAY, flow.screen());
        assertEquals(PlayModel.NEW_PROFILE, flow.play().selected(), "no last profile: New Profile");
        keys(flow, UiKey.ENTER);
        assertEquals(Screen.NEW_GAME, flow.screen());
        assertEquals(Widget.NAME, flow.focus());

        keys(flow, UiKey.ENTER, UiKey.ENTER);
        assertEquals(Modal.MESSAGE, flow.modal(), "an empty name");
        assertEquals("Please enter a name for a new game!", flow.message());
        keys(flow, UiKey.ENTER);
        flow.click(Hit.of(Widget.NAME));
        type(flow, "anna");
        flow.click(Hit.of(Widget.START));
        assertEquals("There is already a profile with this name!", flow.message(), "ignoring case");
        keys(flow, UiKey.ESCAPE);
        flow.click(Hit.of(Widget.NAME));
        keys(flow, UiKey.BACKSPACE, UiKey.BACKSPACE, UiKey.BACKSPACE, UiKey.BACKSPACE);
        type(flow, "Bob");
        keys(flow, UiKey.TAB, UiKey.SPACE);

        assertEquals(Screen.OVERVIEW, flow.screen());
        assertEquals("Bob", flow.overview().caption());
        assertTrue(new ProfileStore(userDir).exists("Bob"), "saved at once");
        assertEquals(Optional.of("Bob"), new ProfileStore(userDir).lastProfile());
    }

    @Test
    void playListsTheProfilesWithTheLastOnePreselectedAndDeletesAfterYes(@TempDir Path userDir)
            throws IOException {
        ProfileStore store = new ProfileStore(userDir);
        store.save(Profile.create("Anna", "original"));
        store.save(Profile.create("Bob", "original").withGroup(1));
        store.save(Profile.create("Cid", "original"));
        store.rememberLast("Bob");
        GameFlow flow = menu(userDir);

        keys(flow, UiKey.ENTER);
        assertEquals(Screen.PLAY, flow.screen());
        assertEquals(2, flow.play().selected(), "the last profile is preselected (row 0 is New Profile)");
        keys(flow, UiKey.DELETE);
        assertEquals(Modal.CONFIRM_DELETE, flow.modal());
        assertFalse(flow.modalYes(), "NO preselected");
        keys(flow, UiKey.ESCAPE);
        assertEquals(3, flow.play().profiles().size(), "Esc is NO");
        flow.click(Hit.of(Widget.DELETE));
        flow.click(Hit.of(Widget.YES));
        assertEquals(List.of("Anna", "Cid"), flow.play().profiles().stream().map(Profile::name).toList());
        assertEquals(2, flow.play().selected(), "the row of the deleted profile: Cid");

        flow.click(new Hit(Widget.LIST, 2));
        keys(flow, UiKey.ENTER);

        assertEquals(Screen.OVERVIEW, flow.screen());
        assertEquals("Cid", flow.overview().caption());
        assertEquals(Optional.of("Cid"), store.lastProfile());
    }

    /** Phase 5c-1 decision 2: with five profiles "&lt; New Profile &gt;" is greyed until one is deleted. */
    @Test
    void fiveProfilesGreyNewProfileUntilOneIsDeleted(@TempDir Path userDir) throws IOException {
        ProfileStore store = new ProfileStore(userDir);
        for (String name : List.of("A", "B", "C", "D", "E")) {
            store.save(Profile.create(name, "original"));
        }
        GameFlow flow = menu(userDir);
        keys(flow, UiKey.ENTER);

        assertFalse(flow.play().newProfileEnabled());
        assertEquals(1, flow.play().selected(), "the first profile instead of the greyed row");
        keys(flow, UiKey.UP);
        assertEquals(1, flow.play().selected(), "the greyed row cannot be selected by the keys");
        flow.doubleClick(new Hit(Widget.LIST, PlayModel.NEW_PROFILE));
        settle(flow);
        assertEquals(Screen.PLAY, flow.screen(), "nor by the mouse");
        assertEquals(1, flow.play().selected());

        flow.click(Hit.of(Widget.DELETE));
        flow.click(Hit.of(Widget.YES));
        assertTrue(flow.play().newProfileEnabled(), "four left");
        assertEquals(4, store.list().size());
        flow.click(new Hit(Widget.LIST, PlayModel.NEW_PROFILE));
        keys(flow, UiKey.ENTER);
        assertEquals(Screen.NEW_GAME, flow.screen());

        store.save(Profile.create("Z", "original"));
        type(flow, "Zoe");
        keys(flow, UiKey.ENTER, UiKey.ENTER);
        assertEquals(Modal.MESSAGE, flow.modal(), "full meanwhile (another copy of the game): not created");
        assertEquals("Delete a profile first", flow.message());
        assertFalse(store.exists("Zoe"));
        keys(flow, UiKey.ENTER, UiKey.ESCAPE);
        assertEquals(Screen.PLAY, flow.screen(), "Back from the name entry returns to the list");
    }

    /** Phase 5c-1 decision 2: more than five profiles from older data stay listed and playable. */
    @Test
    void moreThanFiveOlderProfilesStayListedAndADoubleClickPlaysOne(@TempDir Path userDir) throws IOException {
        ProfileStore store = new ProfileStore(userDir);
        for (String name : List.of("A", "B", "C", "D", "E", "F", "G")) {
            store.save(Profile.create(name, "original"));
        }
        GameFlow flow = menu(userDir);
        keys(flow, UiKey.ENTER);
        assertEquals(7, flow.play().profiles().size());
        keys(flow, UiKey.DOWN, UiKey.DOWN, UiKey.DOWN, UiKey.DOWN, UiKey.DOWN, UiKey.DOWN, UiKey.DOWN);
        assertEquals(7, flow.play().selected(), "the last row, scrolled into view");
        assertTrue(flow.play().box().scrollable(), "New Profile and seven profiles do not fit");

        flow.doubleClick(new Hit(Widget.LIST, 7));
        settle(flow);

        assertEquals(Screen.OVERVIEW, flow.screen());
        assertEquals("G", flow.overview().caption());
        assertEquals(7, store.list().size(), "nothing deleted");
    }

    @Test
    void theBriefingWaitsForTheSkyAndPlayStartsTheTowerAndSavesTheAttempt(@TempDir Path userDir) {
        GameFlow flow = overview(userDir);
        effects(flow);

        keys(flow, UiKey.ENTER);
        assertEquals(Screen.BRIEFING, flow.screen());
        TowerDefinition first = CAMPAIGN.tower(0, 0);
        assertEquals(List.of(new GameFlow.PrepareTower(first)), effects(flow));
        assertEquals("Tower 1", flow.briefing().title());
        assertFalse(flow.isEnabled(Widget.PLAY), "the sky is not ready yet");
        keys(flow, UiKey.ENTER);
        assertEquals(Screen.BRIEFING, flow.screen());

        flow.skyProgress(1f);
        keys(flow, UiKey.ENTER);

        assertEquals(Screen.GAME, flow.screen());
        assertEquals(List.of(new GameFlow.StartTower(first, false)), effects(flow));
        assertEquals(1, new ProfileStore(userDir).find("Anna").orElseThrow().progress("original-000").attempts(),
                "autosave at the tower start");
    }

    @Test
    void aFailureOffersRetryAndACompletionScoresAndPreselectsTheNextTower(@TempDir Path userDir) {
        GameFlow flow = overview(userDir);
        keys(flow, UiKey.ENTER);
        flow.skyProgress(1f);
        keys(flow, UiKey.ENTER);
        effects(flow);

        flow.attemptEnded(Rules.EXIT_TIMEOUT, 3, 500, NOW);
        assertEquals(Screen.RESULT, flow.screen());
        assertEquals("Out Of Time", flow.result().reason());
        assertEquals(List.of(Widget.RETRY, Widget.OVERVIEW), flow.resultButtons());
        assertEquals(Widget.OVERVIEW, flow.focus(), "Overview is the default");
        settle(flow);
        keys(flow, UiKey.LEFT, UiKey.ENTER);
        assertEquals(List.of(new GameFlow.StartTower(CAMPAIGN.tower(0, 0), false)), effects(flow), "Retry");

        flow.attemptEnded(Rules.EXIT_COMPLETED, 3, 700, NOW);

        Profile saved = new ProfileStore(userDir).find("Anna").orElseThrow();
        assertEquals(2, saved.progress("original-000").attempts());
        assertEquals(2, saved.progress("original-000").counter(), "one failure");
        assertEquals(222L, saved.score(), "100 + 92 + 30");
        assertEquals("000222", flow.result().score().get(3).value());
        assertEquals(List.of(Widget.OVERVIEW), flow.resultButtons(), "no Retry after a completion");
        settle(flow);
        keys(flow, UiKey.ENTER);
        assertEquals(Screen.OVERVIEW, flow.screen());
        assertEquals(1, flow.overview().selected(), "the next tower");
    }

    @Test
    void aSolvedTowerIsPlayedAsPracticeWithoutChangingTheProfile(@TempDir Path userDir) {
        GameFlow flow = overview(userDir);
        keys(flow, UiKey.ENTER);
        flow.skyProgress(1f);
        keys(flow, UiKey.ENTER);
        flow.attemptEnded(Rules.EXIT_COMPLETED, 3, 700, NOW);
        settle(flow);
        keys(flow, UiKey.ENTER);
        Profile before = new ProfileStore(userDir).find("Anna").orElseThrow();
        effects(flow);

        flow.click(new Hit(Widget.GRID, 0));
        flow.click(Hit.of(Widget.PLAY));
        settle(flow);
        assertTrue(flow.practice());
        flow.skyProgress(1f);
        keys(flow, UiKey.ENTER);
        flow.attemptRestarted();
        flow.attemptEnded(Rules.EXIT_COMPLETED, 3, 300, NOW);

        assertEquals(before, new ProfileStore(userDir).find("Anna").orElseThrow());
        assertEquals("Practice", flow.result().score().get(0).label());
    }

    @Test
    void escGoesBackOnEveryScreen(@TempDir Path userDir) {
        GameFlow flow = overview(userDir);
        keys(flow, UiKey.ENTER);
        assertEquals(Screen.BRIEFING, flow.screen());
        keys(flow, UiKey.ESCAPE);
        assertEquals(Screen.OVERVIEW, flow.screen());
        keys(flow, UiKey.ESCAPE);
        assertEquals(Screen.MENU, flow.screen());
        keys(flow, UiKey.ENTER);
        assertEquals(Screen.PLAY, flow.screen());
        assertEquals(1, flow.play().selected(), "Anna, the last profile");
        keys(flow, UiKey.UP, UiKey.ENTER);
        assertEquals(Screen.NEW_GAME, flow.screen());
        keys(flow, UiKey.ESCAPE);
        assertEquals(Screen.PLAY, flow.screen(), "back from the name entry");
        keys(flow, UiKey.ESCAPE);
        assertEquals(Screen.MENU, flow.screen(), "back from Play");
    }

    @Test
    void aRestartCountsOneAndANewAttempt(@TempDir Path userDir) {
        GameFlow flow = overview(userDir);
        keys(flow, UiKey.ENTER);
        flow.skyProgress(1f);
        keys(flow, UiKey.ENTER);

        flow.attemptRestarted();

        Profile saved = new ProfileStore(userDir).find("Anna").orElseThrow();
        assertEquals(2, saved.progress("original-000").attempts());
        assertEquals(1, saved.progress("original-000").counter(), "S:6741-6750");
    }

    @Test
    void aDeveloperSessionHasNoProfileAndItsResultLeadsToTheMenu(@TempDir Path userDir) {
        GameFlow flow = new GameFlow(Texts.english(), new ProfileStore(userDir));
        flow.developerSession(CAMPAIGN, CAMPAIGN.tower(0, 4));
        assertEquals(Screen.GAME, flow.screen());

        flow.attemptEnded(Rules.EXIT_ABANDONED, 3, 50, NOW);
        assertEquals("Attempt Abandoned", flow.result().reason());
        assertTrue(flow.isDeveloperSession());
        settle(flow);
        keys(flow, UiKey.ESCAPE);

        assertEquals(Screen.MENU, flow.screen());
        assertEquals(List.of(), new ProfileStore(userDir).list(), "nothing saved");
    }

    @Test
    void aGameLeftWithAnErrorShowsItOverTheOverviewOrTheMenu(@TempDir Path userDir) {
        GameFlow developer = new GameFlow(Texts.english(), new ProfileStore(userDir));
        developer.developerSession(CAMPAIGN, CAMPAIGN.tower(0, 0));

        developer.gameLeft("Missing or unreadable asset: x.png");

        assertEquals(Screen.MENU, developer.screen());
        assertEquals(Modal.MESSAGE, developer.modal());
        assertEquals("Missing or unreadable asset: x.png", developer.message());
        GameFlow flow = overview(userDir);
        keys(flow, UiKey.ENTER);
        flow.skyProgress(1f);
        keys(flow, UiKey.ENTER);
        flow.gameLeft("");
        assertEquals(Screen.OVERVIEW, flow.screen());
        assertEquals(Modal.NONE, flow.modal());
    }

    @Test
    void theGroupButtonsSwitchTheGroupAndSaveIt(@TempDir Path userDir) throws IOException {
        ProfileStore store = new ProfileStore(userDir);
        Profile done = CampaignTest.complete(Profile.create("Anna", "original"), "original-000", "original-001",
                "original-002", "original-003", "original-004", "original-005", "original-006");
        store.save(done);
        store.rememberLast("Anna");
        GameFlow flow = menu(userDir);
        keys(flow, UiKey.ENTER);
        keys(flow, UiKey.ENTER);
        assertEquals(Screen.OVERVIEW, flow.screen());
        assertFalse(flow.isEnabled(Widget.PREVIOUS_GROUP), "group 0 has no previous group");
        flow.click(Hit.of(Widget.PREVIOUS_GROUP));
        assertEquals(0, flow.overview().group());

        flow.click(Hit.of(Widget.NEXT_GROUP));

        assertEquals(1, flow.overview().group());
        assertEquals(1, store.find("Anna").orElseThrow().group(), "the group is remembered");
        assertFalse(flow.isEnabled(Widget.NEXT_GROUP), "nothing solved in group 1 yet");
        assertEquals(Widget.GRID, flow.focus(), "the disabled button gives the focus back to the grid");
        keys(flow, UiKey.TAB);
        assertEquals(Widget.PREVIOUS_GROUP, flow.focus());
        keys(flow, UiKey.ENTER);
        assertEquals(0, flow.overview().group());
        assertEquals(0, store.find("Anna").orElseThrow().group());
    }

    @Test
    void theArrowKeysMoveInTheGridAndADoubleClickStartsTheTower(@TempDir Path userDir) {
        GameFlow flow = overview(userDir);
        assertEquals(Widget.GRID, flow.focus());
        assertEquals(0, flow.overview().selected(), "A is the first open tower");

        keys(flow, UiKey.LEFT, UiKey.UP);
        assertEquals(0, flow.overview().selected(), "no wrap");
        keys(flow, UiKey.RIGHT, UiKey.DOWN, UiKey.DOWN, UiKey.DOWN);
        assertEquals(7, flow.overview().selected(), "B, E, H");
        assertFalse(flow.isEnabled(Widget.PLAY), "H is locked");
        keys(flow, UiKey.ENTER);
        assertEquals(Screen.OVERVIEW, flow.screen(), "a locked tower does not start");
        flow.doubleClick(new Hit(Widget.GRID, 8));
        settle(flow);
        assertEquals(Screen.OVERVIEW, flow.screen(), "nor by a double click");
        assertEquals(8, flow.overview().selected(), "but it is selected");

        flow.click(new Hit(Widget.GRID, 1));
        assertEquals(1, flow.overview().selected());
        assertEquals(Screen.OVERVIEW, flow.screen(), "a single click only selects");
        flow.doubleClick(new Hit(Widget.GRID, 0));
        settle(flow);

        assertEquals(Screen.BRIEFING, flow.screen());
        assertEquals(CAMPAIGN.tower(0, 0), flow.tower());
    }

    // ------------------------------------------------------------------ fix round: save failures (review Important #1)

    @Test
    void aFailedSaveAtAttemptEndShowsAMessageOverTheResultInsteadOfBeingCleared(@TempDir Path userDir) {
        FlakyStore store = new FlakyStore(userDir);
        GameFlow flow = readyToPlay(userDir, store);
        keys(flow, UiKey.ENTER);
        assertEquals(Screen.GAME, flow.screen(), "the tower start save succeeded");

        store.failNextSave();
        flow.attemptEnded(Rules.EXIT_COMPLETED, 3, 700, NOW);

        assertEquals(Screen.RESULT, flow.screen(), "the result window still appears");
        assertEquals(Modal.MESSAGE, flow.modal(), "the save error is not erased by the screen change");
        assertEquals("The profile cannot be saved: disk full (test)", flow.message());
    }

    @Test
    void aFailedSaveAtTheTowerStartIsReportedAtTheLatestWhenTheAttemptEnds(@TempDir Path userDir) {
        FlakyStore store = new FlakyStore(userDir);
        GameFlow flow = readyToPlay(userDir, store);
        store.failNextSave();

        keys(flow, UiKey.ENTER);
        assertEquals(Screen.GAME, flow.screen(), "the game starts despite the failed autosave");
        assertEquals(Modal.NONE, flow.modal(), "not shown now, so it does not block the game start");

        flow.attemptEnded(Rules.EXIT_COMPLETED, 3, 700, NOW);

        assertEquals(Screen.RESULT, flow.screen());
        assertEquals(Modal.MESSAGE, flow.modal(), "the pending error surfaces at the latest here");
        assertEquals("The profile cannot be saved: disk full (test)", flow.message());
    }

    @Test
    void aFailedSaveAtARestartIsReportedAtTheEndToo(@TempDir Path userDir) {
        FlakyStore store = new FlakyStore(userDir);
        GameFlow flow = readyToPlay(userDir, store);
        keys(flow, UiKey.ENTER);
        assertEquals(Screen.GAME, flow.screen());

        store.failNextSave();
        flow.attemptRestarted();
        assertEquals(Modal.NONE, flow.modal(), "GAME takes no input, nothing to show yet");

        flow.attemptEnded(Rules.EXIT_COMPLETED, 3, 700, NOW);

        assertEquals(Modal.MESSAGE, flow.modal(), "the restart's save failure is not lost");
        assertEquals("The profile cannot be saved: disk full (test)", flow.message());
    }

    @Test
    void normalSavesLeaveTheResultWithoutAModal(@TempDir Path userDir) {
        GameFlow flow = readyToPlay(userDir, new ProfileStore(userDir));
        keys(flow, UiKey.ENTER);

        flow.attemptEnded(Rules.EXIT_COMPLETED, 3, 700, NOW);

        assertEquals(Screen.RESULT, flow.screen());
        assertEquals(Modal.NONE, flow.modal(), "nothing failed, so nothing shows");
    }

    // ------------------------------------------------------------------ fix round: trivially safe minors

    @Test
    void retryIsOnlyEnabledOnAFailedResultMinor4(@TempDir Path userDir) {
        GameFlow flow = overview(userDir);
        assertFalse(flow.isEnabled(Widget.RETRY), "no result window yet");
        keys(flow, UiKey.ENTER);
        flow.skyProgress(1f);
        keys(flow, UiKey.ENTER);
        assertFalse(flow.isEnabled(Widget.RETRY), "still in GAME, not RESULT");

        flow.attemptEnded(Rules.EXIT_TIMEOUT, 3, 500, NOW);
        assertTrue(flow.isEnabled(Widget.RETRY), "a failure offers Retry");
        settle(flow);
        keys(flow, UiKey.LEFT, UiKey.ENTER);
        effects(flow);

        flow.attemptEnded(Rules.EXIT_COMPLETED, 3, 700, NOW);
        assertFalse(flow.isEnabled(Widget.RETRY), "a completion offers no Retry");
    }

    @Test
    void deletingTheLastProfileMovesTheFocusOffTheDisabledDeleteButtonMinor5(@TempDir Path userDir)
            throws IOException {
        new ProfileStore(userDir).save(Profile.create("Anna", "original"));
        GameFlow flow = menu(userDir);
        keys(flow, UiKey.ENTER);
        assertEquals(Screen.PLAY, flow.screen());
        assertFalse(flow.isEnabled(Widget.DELETE), "New Profile is selected: nothing to delete");
        flow.click(new Hit(Widget.LIST, 1));
        flow.click(Hit.of(Widget.DELETE));
        flow.click(Hit.of(Widget.YES));

        assertTrue(flow.play().profiles().isEmpty(), "the last profile is gone");
        assertEquals(Widget.BACK, flow.focus(), "unlike the DELETE button before this fix");
        assertTrue(flow.isEnabled(flow.focus()));
    }

    // ------------------------------------------------------------------ final review fix wave

    /** I1: the window's close button asks like Quit on every screen, NO preselected; a second request quits. */
    @Test
    void theWindowCloseAsksFirstWithNoPreselectedAndASecondCloseQuits(@TempDir Path userDir) {
        GameFlow flow = overview(userDir);
        effects(flow);

        flow.requestQuit();
        assertEquals(Modal.CONFIRM_QUIT, flow.modal(), "asked on the Overview too");
        assertFalse(flow.modalYes(), "NO preselected");
        keys(flow, UiKey.ENTER);
        assertEquals(Modal.NONE, flow.modal(), "NO: back to the Overview");
        assertEquals(Screen.OVERVIEW, flow.screen());
        assertEquals(List.of(), effects(flow));

        flow.requestQuit();
        flow.requestQuit();
        assertEquals(List.of(new GameFlow.Quit()), effects(flow), "the second close quits at once");

        GameFlow message = menu(userDir);
        keys(message, UiKey.ENTER);
        keys(message, UiKey.UP, UiKey.ENTER);
        keys(message, UiKey.ENTER, UiKey.ENTER);
        assertEquals(Modal.MESSAGE, message.modal(), "an empty name");
        message.requestQuit();
        assertEquals(Modal.CONFIRM_QUIT, message.modal(), "the question replaces a message");
        keys(message, UiKey.LEFT, UiKey.ENTER);
        assertEquals(List.of(new GameFlow.Quit()), effects(message), "YES quits after the fade");
    }

    /** I1: while a screen change fades out (or in the game) there is nothing to ask: the close quits at once. */
    @Test
    void aWindowCloseDuringAFadeOutQuitsAtOnce(@TempDir Path userDir) {
        GameFlow flow = menu(userDir);
        flow.key(UiKey.ENTER);

        flow.requestQuit();

        assertEquals(Modal.NONE, flow.modal());
        assertEquals(new GameFlow.Quit(), flow.poll().orElseThrow(), "Quit comes before anything else");
    }

    /** M3: a failed delete and a failed "remember the last profile" say what failed. */
    @Test
    void aFailedDeleteOrRememberShowsItsOwnMessage(@TempDir Path userDir) throws IOException {
        ProfileStore store = new ProfileStore(userDir) {
            @Override
            public void delete(String name) throws IOException {
                throw new IOException("locked (test)");
            }

            @Override
            public void rememberLast(String name) throws IOException {
                throw new IOException("read-only (test)");
            }
        };
        new ProfileStore(userDir).save(Profile.create("Anna", "original"));
        GameFlow flow = menu(userDir, store);
        keys(flow, UiKey.ENTER);
        flow.click(new Hit(Widget.LIST, 1));

        flow.click(Hit.of(Widget.DELETE));
        flow.click(Hit.of(Widget.YES));
        assertEquals("The profile cannot be deleted: locked (test)", flow.message());
        keys(flow, UiKey.ENTER);
        flow.click(Hit.of(Widget.START));
        assertEquals("The profile cannot be selected: read-only (test)", flow.message());
        assertEquals(Screen.PLAY, flow.screen());
        assertEquals(Optional.empty(), flow.profile(), "not chosen");
    }

    /** M11: a game left without a result (a missing asset) returns to the Overview with that tower selected. */
    @Test
    void aGameLeftWithAnErrorSelectsTheTowerJustPlayed(@TempDir Path userDir) {
        GameFlow flow = overview(userDir);
        keys(flow, UiKey.RIGHT, UiKey.ENTER);
        flow.skyProgress(1f);
        keys(flow, UiKey.ENTER);
        assertEquals(Screen.GAME, flow.screen());
        assertEquals(CAMPAIGN.tower(0, 1), flow.tower());

        flow.gameLeft("Missing or unreadable asset: x.png");

        assertEquals(Screen.OVERVIEW, flow.screen());
        assertEquals(1, flow.overview().selected(), "B, not the first open tower A");
    }

    // ------------------------------------------------------------------ Phase 5c-2: the interludes

    /**
     * Decision 6: every result window of the standard game shows an interlude, chosen by the one shared counter
     * (success, failure, success: 1, 8, 3); it is reported only while the result window is shown.
     */
    @Test
    void everyResultWindowGetsTheNextInterludeOfTheSharedCounter(@TempDir Path userDir) {
        GameFlow flow = readyToPlay(userDir, new ProfileStore(userDir));
        assertEquals(OptionalInt.empty(), flow.interlude(), "the Briefing");
        keys(flow, UiKey.ENTER);
        assertEquals(Screen.GAME, flow.screen());

        flow.attemptEnded(Rules.EXIT_COMPLETED, 3, 500, NOW);
        assertEquals(OptionalInt.of(1), flow.interlude(), "the first success: demo 1");
        settle(flow);
        keys(flow, UiKey.ENTER);
        assertEquals(Screen.OVERVIEW, flow.screen());
        assertEquals(OptionalInt.empty(), flow.interlude(), "only behind the result window");

        keys(flow, UiKey.ENTER);
        flow.skyProgress(1f);
        keys(flow, UiKey.ENTER);
        flow.attemptEnded(Rules.EXIT_ABANDONED, 3, 500, NOW);
        assertEquals(OptionalInt.of(8), flow.interlude(), "an abandon is a failure; the counter is shared");
        settle(flow);
        flow.click(Hit.of(Widget.RETRY));
        settle(flow);
        assertEquals(Screen.GAME, flow.screen());
        assertEquals(OptionalInt.empty(), flow.interlude());
        flow.attemptEnded(Rules.EXIT_COMPLETED, 3, 500, NOW);
        assertEquals(OptionalInt.of(3), flow.interlude());
    }

    /** Decision 6: the Tutorial and a developer start get interludes too, each flow (program run) from 0. */
    @Test
    void theTutorialAndADeveloperStartGetInterludesToo(@TempDir Path userDir) {
        GameFlow developer = new GameFlow(Texts.english(), new ProfileStore(userDir));
        developer.developerSession(CAMPAIGN, CAMPAIGN.tower(0, 0));
        developer.attemptEnded(Rules.EXIT_SPIDERS_DESTROYED, 0, 500, NOW);
        assertEquals(OptionalInt.of(7), developer.interlude(), "-Dtob.tower: the first failure, demo 7");

        GameFlow flow = tutorialMenu(userDir);
        flow.click(Hit.of(Widget.MENU_TUTORIAL));
        settle(flow);
        flow.doubleClick(new Hit(Widget.GRID, 7));
        settle(flow);
        flow.skyProgress(1f);
        keys(flow, UiKey.ENTER);
        flow.attemptEnded(Rules.EXIT_COMPLETED, 3, 700, NOW);
        assertEquals(Screen.RESULT, flow.screen());
        assertEquals(OptionalInt.of(1), flow.interlude(), "the Tutorial: the first success, demo 1");
    }

    /**
     * Decisions 1-2 and question 3 at the flow level: Retry from the result window fades the result song out to the
     * silent game; leaving the result for the Overview fades towards Dreamland.
     */
    @Test
    void theResultSongFadesOutToTheGameOnRetryAndTowardsDreamlandOnOverview(@TempDir Path userDir) {
        GameFlow flow = readyToPlay(userDir, new ProfileStore(userDir));
        keys(flow, UiKey.ENTER);
        flow.attemptEnded(Rules.EXIT_TIMEOUT, 3, 500, NOW);
        settle(flow);
        assertEquals(Optional.of(MusicChoice.Song.FADEOUT), MusicChoice.wanted(flow, true), "a failure");

        flow.click(Hit.of(Widget.RETRY));
        flow.update(ScreenFade.SECONDS / 2f);
        MusicChoice.Fade retry = MusicChoice.fade(flow, true);
        assertTrue(retry.fadingOut(), "Retry fades out");
        assertEquals(Optional.empty(), retry.afterFade(), "the game wants no song");
        assertTrue(retry.alpha() > 0f && retry.alpha() < 1f, "half-way: " + retry.alpha());
        MusicChoice music = new MusicChoice();
        music.next(Optional.of(MusicChoice.Song.FADEOUT));
        music.gain(MusicChoice.Fade.NONE);
        assertEquals(1f - retry.alpha(), music.gain(retry), 1e-6f, "the result song goes down with the screen");
        settle(flow);
        assertEquals(Screen.GAME, flow.screen());
        assertEquals(Optional.empty(), MusicChoice.wanted(flow, true));

        flow.attemptEnded(Rules.EXIT_COMPLETED, 3, 700, NOW);
        settle(flow);
        assertEquals(Optional.of(MusicChoice.Song.IMPROVI), MusicChoice.wanted(flow, true), "a completion");
        flow.key(UiKey.ENTER);
        flow.update(ScreenFade.SECONDS / 2f);
        MusicChoice.Fade overview = MusicChoice.fade(flow, true);
        assertTrue(overview.fadingOut());
        assertEquals(Optional.of(MusicChoice.Song.DREAMLAND), overview.afterFade(), "RESULT -> OVERVIEW");
        settle(flow);
        assertEquals(Screen.OVERVIEW, flow.screen());
        assertEquals(Optional.of(MusicChoice.Song.DREAMLAND), MusicChoice.wanted(flow, true));
    }

    /** Decisions 1-2 at the flow level: the Tutorial's result plays Improvi and leads back towards Dreamland. */
    @Test
    void theTutorialsResultPlaysImproviAndLeavesTowardsDreamland(@TempDir Path userDir) {
        GameFlow flow = tutorialMenu(userDir);
        flow.click(Hit.of(Widget.MENU_TUTORIAL));
        settle(flow);
        assertEquals(Optional.of(MusicChoice.Song.DREAMLAND), MusicChoice.wanted(flow, true), "the Tutorial");
        startTutorialTower(flow);
        assertEquals(Optional.empty(), MusicChoice.wanted(flow, true), "the game");

        flow.attemptEnded(Rules.EXIT_COMPLETED, 3, 700, NOW);
        assertEquals(Screen.RESULT, flow.screen());
        assertEquals(Optional.of(MusicChoice.Song.IMPROVI), MusicChoice.wanted(flow, true));
        settle(flow);
        flow.key(UiKey.ENTER);
        flow.update(ScreenFade.SECONDS / 2f);
        MusicChoice.Fade fade = MusicChoice.fade(flow, true);
        assertTrue(fade.fadingOut());
        assertEquals(Optional.of(Screen.TUTORIAL), flow.nextScreen());
        assertEquals(Optional.of(MusicChoice.Song.DREAMLAND), fade.afterFade(), "RESULT -> TUTORIAL");
        settle(flow);
        assertEquals(Screen.TUTORIAL, flow.screen());
        assertEquals(Optional.of(MusicChoice.Song.DREAMLAND), MusicChoice.wanted(flow, true));
    }

    // ------------------------------------------------------------------ Phase 5c-1: the Tutorial

    /** The main menu of a flow that has the 9 test tutorial towers. */
    private static GameFlow tutorialMenu(Path userDir) {
        GameFlow flow = new GameFlow(Texts.english(), new ProfileStore(userDir));
        flow.tutorial(TutorialModelTest.towers());
        flow.loaded(CAMPAIGN);
        settle(flow);
        return flow;
    }

    @Test
    void theTutorialEntryNeedsTheNineTowersAndShowsThemAllOpen(@TempDir Path userDir) {
        GameFlow without = menu(userDir);
        assertFalse(without.isEnabled(Widget.MENU_TUTORIAL), "no tutorial towers handed over");
        GameFlow eight = new GameFlow(Texts.english(), new ProfileStore(userDir));
        eight.tutorial(TutorialModelTest.towers().subList(0, 8));
        assertFalse(eight.isEnabled(Widget.MENU_TUTORIAL), "a tower missing: greyed");

        GameFlow flow = tutorialMenu(userDir);
        assertTrue(flow.isEnabled(Widget.MENU_TUTORIAL));
        keys(flow, UiKey.DOWN);
        assertEquals(Widget.MENU_TUTORIAL, flow.focus());
        keys(flow, UiKey.ENTER);

        assertEquals(Screen.TUTORIAL, flow.screen());
        assertEquals(Widget.GRID, flow.focus());
        assertTrue(flow.tutorial().cells().stream().allMatch(cell -> cell.state() == OverviewModel.State.OPEN),
                "decision 3: all open");
        assertEquals(Optional.empty(), flow.profile(), "no profile");
        keys(flow, UiKey.RIGHT, UiKey.DOWN, UiKey.DOWN, UiKey.TAB);
        assertEquals(7, flow.tutorial().selected());
        assertEquals(Widget.PLAY, flow.focus(), "Tab: the grid, Play, Back");
        keys(flow, UiKey.TAB, UiKey.TAB);
        assertEquals(Widget.GRID, flow.focus());
        keys(flow, UiKey.ESCAPE);
        assertEquals(Screen.MENU, flow.screen());
    }

    /**
     * Decision 3: a tutorial tower runs Briefing → game → result → Tutorial without a profile and without a score; a
     * completion is marked in settings.json for every profile and no profile file is touched.
     */
    @Test
    void aTutorialTowerRunsWithoutAProfileAndASolvedOneIsMarkedInTheSettings(@TempDir Path userDir)
            throws IOException {
        ProfileStore store = new ProfileStore(userDir);
        store.save(Profile.create("Anna", "original"));
        store.rememberLast("Anna");
        String annaBefore = Files.readString(store.directory().resolve(ProfileStore.fileName("Anna")));
        GameFlow flow = tutorialMenu(userDir);
        keys(flow, UiKey.ENTER);
        keys(flow, UiKey.ENTER);
        assertEquals("Anna", flow.profile().orElseThrow().name(), "Anna's Overview first");
        keys(flow, UiKey.ESCAPE);
        flow.click(Hit.of(Widget.MENU_TUTORIAL));
        settle(flow);
        effects(flow);

        flow.doubleClick(new Hit(Widget.GRID, 7));
        settle(flow);
        assertEquals(Screen.BRIEFING, flow.screen());
        TowerDefinition h = TutorialModelTest.towers().get(7);
        assertEquals(List.of(new GameFlow.PrepareTower(h)), effects(flow));
        assertFalse(flow.practice(), "never practice");
        flow.skyProgress(1f);
        keys(flow, UiKey.ENTER);
        assertEquals(List.of(new GameFlow.StartTower(h, false)), effects(flow));
        assertEquals(Optional.empty(), flow.profile());
        flow.attemptRestarted();

        flow.attemptEnded(Rules.EXIT_COMPLETED, 3, 700, NOW);

        assertEquals(Screen.RESULT, flow.screen());
        assertEquals("Tower Completed", flow.result().caption());
        assertEquals(List.of(), flow.result().score(), "no score");
        assertEquals(List.of(Widget.OVERVIEW), flow.resultButtons());
        assertFalse(flow.isDeveloperSession(), "the button leads back to the Tutorial, not to the menu");
        assertEquals(Set.of("tutorial-007"), store.settings().read().solvedTutorials());
        assertEquals(Optional.of("Anna"), store.settings().read().lastProfile(), "the rest of the file stays");
        settle(flow);
        keys(flow, UiKey.ENTER);
        assertEquals(Screen.TUTORIAL, flow.screen());
        assertEquals(7, flow.tutorial().selected(), "the tower just played");
        assertEquals(OverviewModel.State.SOLVED, flow.tutorial().cells().get(7).state());
        assertEquals("Solved: 1 of 9", flow.tutorial().solvedText());
        assertEquals(annaBefore, Files.readString(store.directory().resolve(ProfileStore.fileName("Anna"))),
                "no profile written");
        try (Stream<Path> files = Files.list(store.directory())) {
            assertEquals(1L, files.count(), "only Anna's file");
        }
        GameFlow again = tutorialMenu(userDir);
        again.click(Hit.of(Widget.MENU_TUTORIAL));
        settle(again);
        assertEquals(OverviewModel.State.SOLVED, again.tutorial().cells().get(7).state(), "a fresh flow reads it");
    }

    /** Decision 3: a failure offers Retry; every way back (result, Briefing, a failed start) leads to the Tutorial. */
    @Test
    void aFailedTutorialAttemptRetriesAndEveryWayBackLeadsToTheTutorial(@TempDir Path userDir) {
        GameFlow flow = tutorialMenu(userDir);
        flow.click(Hit.of(Widget.MENU_TUTORIAL));
        settle(flow);
        keys(flow, UiKey.RIGHT, UiKey.ENTER);
        assertEquals(Screen.BRIEFING, flow.screen());
        keys(flow, UiKey.ESCAPE);
        assertEquals(Screen.TUTORIAL, flow.screen(), "back from the Briefing");
        assertEquals(1, flow.tutorial().selected());
        keys(flow, UiKey.ENTER);
        flow.skyProgress(1f);
        keys(flow, UiKey.ENTER);
        effects(flow);

        flow.attemptEnded(Rules.EXIT_TIMEOUT, 3, 500, NOW);
        assertEquals(List.of(Widget.RETRY, Widget.OVERVIEW), flow.resultButtons());
        settle(flow);
        keys(flow, UiKey.LEFT, UiKey.ENTER);
        assertEquals(List.of(new GameFlow.StartTower(TutorialModelTest.towers().get(1), false)), effects(flow),
                "Retry");
        flow.gameLeft("Missing or unreadable asset: x.png");

        assertEquals(Screen.TUTORIAL, flow.screen());
        assertEquals(Modal.MESSAGE, flow.modal());
        assertEquals(1, flow.tutorial().selected());
        assertEquals(Set.of("tutorial-001"), new ProfileStore(userDir).settings().read().solvedTutorials(),
                "author 2026-10-15: the goal-less B is done once its attempt ended regularly (the time-out)");
        assertEquals(List.of(), new ProfileStore(userDir).list(), "no profile");
    }

    /** Decision 3: a solved mark that cannot be saved is shown over the result window. */
    @Test
    void aSolvedMarkThatCannotBeSavedIsShownOverTheResult(@TempDir Path userDir) throws IOException {
        GameFlow flow = tutorialMenu(userDir);
        flow.click(Hit.of(Widget.MENU_TUTORIAL));
        settle(flow);
        keys(flow, UiKey.DOWN, UiKey.DOWN, UiKey.RIGHT);
        assertEquals("tutorial-007", flow.tutorial().selectedTower().id(), "H, a tower with a goal");
        keys(flow, UiKey.ENTER);
        flow.skyProgress(1f);
        keys(flow, UiKey.ENTER);
        Files.createDirectories(userDir.resolve("settings.json").resolve("blocked"));

        flow.attemptEnded(Rules.EXIT_COMPLETED, 3, 700, NOW);

        assertEquals(Screen.RESULT, flow.screen());
        assertEquals(Modal.MESSAGE, flow.modal());
        assertTrue(flow.message().startsWith("The tutorial progress cannot be saved"), flow.message());
    }

    /** Starts the selected tutorial tower from the Tutorial: Briefing, sky ready, Play. */
    private static void startTutorialTower(GameFlow flow) {
        keys(flow, UiKey.ENTER);
        flow.skyProgress(1f);
        keys(flow, UiKey.ENTER);
        effects(flow);
    }

    /** Author 2026-10-15: a tutorial tower with a goal (H, I) turns green only when completed. */
    @Test
    void aTutorialTowerWithAGoalIsSolvedOnlyWhenCompleted(@TempDir Path userDir) {
        GameFlow flow = tutorialMenu(userDir);
        flow.click(Hit.of(Widget.MENU_TUTORIAL));
        settle(flow);
        keys(flow, UiKey.DOWN, UiKey.DOWN, UiKey.RIGHT);
        assertEquals(7, flow.tutorial().selected());

        for (int exit : List.of(Rules.EXIT_TIMEOUT, Rules.EXIT_SPIDERS_DESTROYED, Rules.EXIT_GRABBER_DESTROYED,
                Rules.EXIT_ABANDONED)) {
            startTutorialTower(flow);
            flow.attemptEnded(exit, 3, 500, NOW);
            settle(flow);
            keys(flow, UiKey.ESCAPE);
            assertEquals(Screen.TUTORIAL, flow.screen());
            assertEquals(OverviewModel.State.OPEN, flow.tutorial().cells().get(7).state(), "exit " + exit);
            assertEquals(Set.of(), new ProfileStore(userDir).settings().read().solvedTutorials(), "exit " + exit);
        }
        startTutorialTower(flow);
        flow.attemptEnded(Rules.EXIT_COMPLETED, 3, 500, NOW);
        settle(flow);
        keys(flow, UiKey.ENTER);

        assertEquals(OverviewModel.State.SOLVED, flow.tutorial().cells().get(7).state());
        assertEquals(Set.of("tutorial-007"), new ProfileStore(userDir).settings().read().solvedTutorials());
    }

    /**
     * Author 2026-10-15: a goal-less tutorial tower (A–G) is done (green) once played and left regularly, e.g.
     * abandoned; leaving the game with an error does not count.
     */
    @Test
    void aGoallessTutorialTowerIsDoneOncePlayedAndLeftRegularly(@TempDir Path userDir) {
        GameFlow flow = tutorialMenu(userDir);
        flow.click(Hit.of(Widget.MENU_TUTORIAL));
        settle(flow);
        startTutorialTower(flow);
        flow.gameLeft("Missing or unreadable asset: x.png");
        settle(flow);
        keys(flow, UiKey.ENTER);
        assertEquals(Screen.TUTORIAL, flow.screen());
        assertEquals(OverviewModel.State.OPEN, flow.tutorial().cells().get(0).state(), "a failed start: not played");
        assertEquals(Set.of(), new ProfileStore(userDir).settings().read().solvedTutorials());

        startTutorialTower(flow);
        flow.attemptEnded(Rules.EXIT_ABANDONED, 3, 500, NOW);

        assertEquals("Attempt Abandoned", flow.result().reason());
        assertEquals(List.of(), flow.result().score(), "no score");
        assertEquals(Set.of("tutorial-000"), new ProfileStore(userDir).settings().read().solvedTutorials());
        settle(flow);
        keys(flow, UiKey.ESCAPE);
        assertEquals(Screen.TUTORIAL, flow.screen());
        assertEquals(OverviewModel.State.SOLVED, flow.tutorial().cells().get(0).state(), "shown green");
        assertEquals("Solved: 1 of 9", flow.tutorial().solvedText());
        assertEquals(List.of(), new ProfileStore(userDir).list(), "no profile");
    }

    /**
     * Author's guard 2026-10-15: "done once played" is for goal-less tutorial towers only. A campaign tower (the
     * test campaign's towers have no goal either) played but not completed stays unsolved, even right after a visit
     * to the Tutorial (the Tutorial's flag must not leak into campaign play), and neither it nor a developer start of
     * a goal-less tutorial tower enters the solved tutorial list.
     */
    @Test
    void aPlayedButNotCompletedCampaignTowerStaysUnsolved(@TempDir Path userDir) {
        ProfileStore store = new ProfileStore(userDir);
        GameFlow flow = tutorialMenu(userDir);
        flow.click(Hit.of(Widget.MENU_TUTORIAL));
        settle(flow);
        assertTrue(flow.inTutorial());
        keys(flow, UiKey.ESCAPE);
        assertEquals(Screen.MENU, flow.screen());
        assertFalse(flow.inTutorial(), "the main menu ends the Tutorial");
        keys(flow, UiKey.ENTER);
        keys(flow, UiKey.ENTER);
        type(flow, "Anna");
        keys(flow, UiKey.ENTER, UiKey.ENTER);
        assertEquals(Screen.OVERVIEW, flow.screen());
        keys(flow, UiKey.ENTER);
        flow.skyProgress(1f);
        effects(flow);
        keys(flow, UiKey.ENTER);
        assertEquals(List.of(new GameFlow.StartTower(CAMPAIGN.tower(0, 0), false)), effects(flow));
        assertFalse(flow.inTutorial());

        flow.attemptEnded(Rules.EXIT_ABANDONED, 3, 500, NOW);
        assertFalse(flow.inTutorial());
        assertEquals(Set.of(), store.settings().read().solvedTutorials(), "no campaign id in the tutorial list");
        settle(flow);
        keys(flow, UiKey.ENTER);

        assertEquals(Screen.OVERVIEW, flow.screen(), "the result leads to the Overview, not to the Tutorial");
        assertEquals(OverviewModel.State.OPEN, flow.overview().cells().get(0).state(), "not green");
        assertEquals(0, flow.profile().orElseThrow().completedCount());
        assertFalse(flow.profile().orElseThrow().isPractice("original-000"), "a replay would not be practice");
        assertEquals(0, store.list().get(0).completedCount(), "the saved profile");
        assertEquals(Set.of(), store.settings().read().solvedTutorials());

        GameFlow developer = new GameFlow(Texts.english(), store);
        developer.tutorial(TutorialModelTest.towers());
        developer.developerSession(CAMPAIGN, TutorialModelTest.towers().get(0));
        developer.attemptEnded(Rules.EXIT_ABANDONED, 3, 500, NOW);
        assertTrue(developer.isDeveloperSession());
        assertEquals(Set.of(), store.settings().read().solvedTutorials(), "-Dtob.tower: no tutorial mark");
    }
}
