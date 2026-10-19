package de.latzig.game.tob.client.logic.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.latzig.game.tob.client.logic.Texts;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Hit;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Widget;
import de.latzig.game.tob.client.logic.game.GameAction;
import de.latzig.game.tob.client.logic.game.PanelLayout;
import de.latzig.game.tob.client.logic.ui.Drawing;
import de.latzig.game.tob.client.logic.ui.ScreenFade;
import de.latzig.game.tob.client.logic.ui.StartLayout;
import de.latzig.game.tob.client.logic.ui.UiKey;
import de.latzig.game.tob.core.progress.Profile;
import de.latzig.game.tob.core.sim.Rules;

class StartScreensTest {

    private static final Campaign CAMPAIGN = Campaign.of(CampaignTest.towers());
    private static final Texts TEXTS = Texts.english();
    private static final StartLayout LAYOUT = new StartLayout(1280, 720);

    private static GameFlow at(Path userDir, UiKey... keys) {
        GameFlow flow = new GameFlow(TEXTS, new ProfileStore(userDir));
        flow.loaded(CAMPAIGN);
        settle(flow);
        for (UiKey key : keys) {
            flow.key(key);
            settle(flow);
        }
        return flow;
    }

    private static void settle(GameFlow flow) {
        for (int i = 0; i < 4; i++) {
            flow.update(ScreenFade.SECONDS);
        }
    }

    private static GameFlow overview(Path userDir) {
        GameFlow flow = at(userDir, UiKey.ENTER, UiKey.ENTER);
        for (char c : "Anna".toCharArray()) {
            flow.typed(c);
        }
        flow.key(UiKey.ENTER);
        flow.key(UiKey.ENTER);
        settle(flow);
        return flow;
    }

    private static StartScreens.Frame frame(GameFlow flow) {
        return StartScreens.draw(flow, TEXTS, LAYOUT, 100f, 100f);
    }

    private static Optional<Drawing.Label> label(StartScreens.Frame frame, String text) {
        return frame.drawing().labels().stream().filter(label -> label.text().equals(text)).findFirst();
    }

    private static List<Widget> clickable(StartScreens.Frame frame) {
        return frame.areas().stream().map(area -> area.hit().widget()).toList();
    }

    private static PanelLayout.Rect area(StartScreens.Frame frame, Widget widget) {
        return frame.areas().stream().filter(area -> area.hit().widget() == widget).findFirst().orElseThrow().rect();
    }

    private static PanelLayout.Rect cell(StartScreens.Frame frame, int index) {
        return frame.areas().stream().filter(area -> area.hit().equals(new Hit(Widget.GRID, index))).findFirst()
                .orElseThrow().rect();
    }

    @Test
    void theMenuHasTheTitleSixEntriesTheTickerAndTheCursorOnTop(@TempDir Path userDir) {
        StartScreens.Frame frame = frame(at(userDir));

        assertEquals(StartScreens.TITLE, frame.drawing().sprites().get(0).region());
        assertEquals(StartScreens.CURSOR, frame.drawing().sprites().get(frame.drawing().sprites().size() - 1)
                .region(), "the cursor is drawn last");
        assertTrue(label(frame, "<Play>").isPresent(), "ToB 2009's focus marks");
        assertEquals(Drawing.BLACK, label(frame, "Options").orElseThrow().colour());
        assertEquals(Drawing.GREY, label(frame, "Tutorial").orElseThrow().colour(), "greyed out");
        assertEquals(Drawing.GREY, label(frame, "Tower Designer").orElseThrow().colour(), "no libraries here");
        assertEquals(Drawing.BLACK, label(frame, "Hall of Fame").orElseThrow().colour());
        assertEquals(Drawing.BLACK, label(frame, "Quit").orElseThrow().colour());
        assertEquals(List.of(Widget.MENU_PLAY, Widget.MENU_OPTIONS, Widget.MENU_HALL_OF_FAME, Widget.MENU_QUIT),
                clickable(frame));
        assertEquals(StartScreens.BIG * LAYOUT.scale(), label(frame, "<Play>").orElseThrow().size(), 1e-4f,
                "decision 1: ToB 2009's big font");
        assertEquals(Optional.empty(), frame.hit(1f, 1f));
        assertEquals(LAYOUT.rect(128f, 32f, 768f, 128f), frame.drawing().sprites().get(0).rect());
    }

    /** Decision 1: ToB 2009's rows (64 apart, 512 × 50 buttons) and the hit-testing at 4:3 and 16:9. */
    @Test
    void theMenuButtonsHaveToB2009sSizeAndAreHitAt4By3And16By9(@TempDir Path userDir) {
        GameFlow flow = at(userDir);
        for (StartLayout layout : List.of(new StartLayout(1024, 768), LAYOUT)) {
            StartScreens.Frame frame = StartScreens.draw(flow, TEXTS, layout, 0f, 0f);
            for (Widget widget : List.of(Widget.MENU_PLAY, Widget.MENU_OPTIONS, Widget.MENU_HALL_OF_FAME,
                    Widget.MENU_QUIT)) {
                int row = GameFlow.MENU.indexOf(widget);
                PanelLayout.Rect expected = layout.rect(256f, 256f + 64f * row + 5f, 512f, 50f);
                PanelLayout.Rect rect = frame.areas().stream().filter(a -> a.hit().widget() == widget).findFirst()
                        .orElseThrow().rect();

                assertEquals(expected, rect, widget + " at " + layout.width() + " x " + layout.height());
                assertEquals(Optional.of(Hit.of(widget)), frame.hit(rect.x() + rect.width() / 2f,
                        rect.y() + rect.height() / 2f));
                assertEquals(Optional.empty(), frame.hit(rect.x() + rect.width() / 2f, rect.y() - 4f * layout
                        .scale()), "the 14 units between two buttons hit nothing");
            }
        }
        assertEquals(672f, StartScreens.TICKER_TOP, "ToB 2009: half a row below the six rows");
    }

    @Test
    void theCursorScalesWithTheLayoutAndKeepsItsHotSpotTopLeft(@TempDir Path userDir) {
        GameFlow flow = at(userDir);
        for (StartLayout layout : List.of(new StartLayout(1024, 768), LAYOUT, new StartLayout(2560, 1440))) {
            List<Drawing.Sprite> sprites = StartScreens.draw(flow, TEXTS, layout, 300f, 200f).drawing().sprites();
            PanelLayout.Rect cursor = sprites.get(sprites.size() - 1).rect();
            float size = StartScreens.CURSOR_SIZE * layout.scale();

            assertEquals(new PanelLayout.Rect(300f, 200f - size, size, size), cursor, layout.height() + " px high");
        }
    }

    @Test
    void newGameShowsTheTypedNameInBlackWithTheFieldClickable(@TempDir Path userDir) {
        GameFlow flow = at(userDir, UiKey.ENTER, UiKey.ENTER);
        for (char c : "Anna".toCharArray()) {
            flow.typed(c);
        }

        StartScreens.Frame frame = frame(flow);

        Drawing.Label name = frame.drawing().labels().stream().filter(label -> label.text().startsWith("Anna"))
                .findFirst().orElseThrow();
        assertEquals(Drawing.BLACK, name.colour());
        assertEquals(Drawing.GREY, label(frame, "Name").orElseThrow().colour());
        assertEquals(List.of(Widget.NAME, Widget.START, Widget.BACK), clickable(frame));
        assertTrue(area(frame, Widget.START).x() < area(frame, Widget.BACK).x(), "Start left of Back");
    }

    /** Decision 2: "&lt; New Profile &gt;" on top, then each profile with its score and group; the last selected. */
    @Test
    void thePlayWindowListsNewProfileAndTheProfilesWithScoreAndGroup(@TempDir Path userDir)
            throws java.io.IOException {
        ProfileStore store = new ProfileStore(userDir);
        store.save(CampaignTest.complete(Profile.create("Anna", "original"), "original-000").withGroup(1));
        store.save(Profile.create("Bob", "original"));
        store.rememberLast("Anna");
        GameFlow flow = at(userDir, UiKey.ENTER);
        assertEquals(GameFlow.Screen.PLAY, flow.screen());

        StartScreens.Frame frame = frame(flow);

        assertEquals(Drawing.BLACK, label(frame, "< New Profile >").orElseThrow().colour());
        assertEquals(Drawing.ORANGE, label(frame, "Anna").orElseThrow().colour(), "the last played is selected");
        assertEquals(Drawing.ORANGE, label(frame, "000210").orElseThrow().colour(), "100 + 100 + 10");
        assertEquals(Drawing.ORANGE, label(frame, "02").orElseThrow().colour(), "group() 1, displayed 1-based");
        assertEquals(Drawing.BLACK, label(frame, "01").orElseThrow().colour(), "Bob's group() 0, displayed 1-based");
        assertEquals(Drawing.BLACK, label(frame, "Bob").orElseThrow().colour());
        assertEquals(Drawing.GREY, label(frame, "Score").orElseThrow().colour(), "a column head");
        assertEquals(List.of(new Hit(Widget.LIST, 0), new Hit(Widget.LIST, 1), new Hit(Widget.LIST, 2),
                Hit.of(Widget.START), Hit.of(Widget.DELETE), Hit.of(Widget.BACK)), hits(frame));
        assertTrue(label(frame, "Delete a profile first").isEmpty(), "two profiles: no hint");
        assertTrue(label(frame, "<Delete>").isEmpty() && label(frame, "Play").isPresent(), "the list has the focus");
        assertLabelsFitTheirRect(frame, "", "play");
    }

    /** Decision 2: with five profiles "&lt; New Profile &gt;" is grey and not clickable, the hint below the list. */
    @Test
    void withFiveProfilesNewProfileIsGreyWithTheHint(@TempDir Path userDir) throws java.io.IOException {
        ProfileStore store = new ProfileStore(userDir);
        for (String name : List.of("Anna", "Bob", "Cid", "Dan", "MMMMMMMMMMMMMMMM")) {
            store.save(Profile.create(name, "original"));
        }
        GameFlow flow = at(userDir, UiKey.ENTER);

        StartScreens.Frame frame = frame(flow);

        assertEquals(Drawing.GREY, label(frame, "< New Profile >").orElseThrow().colour());
        assertEquals(Drawing.GREY, label(frame, "Delete a profile first").orElseThrow().colour());
        assertEquals(Drawing.ORANGE, label(frame, "Anna").orElseThrow().colour(), "the first profile instead");
        assertEquals(List.of(new Hit(Widget.LIST, 1), new Hit(Widget.LIST, 2), new Hit(Widget.LIST, 3),
                new Hit(Widget.LIST, 4), new Hit(Widget.LIST, 5), Hit.of(Widget.START), Hit.of(Widget.DELETE),
                Hit.of(Widget.BACK)), hits(frame), "no scroll bar: six rows fit");
        assertLabelsFitTheirRect(frame, "", "play, full, the widest name");
        tabThrough(flow, 4, "play, full"); // LIST, START, DELETE, BACK
    }

    /** Decision 2: more than five profiles from older data stay listed; the list scrolls. */
    @Test
    void sevenOlderProfilesScroll(@TempDir Path userDir) throws java.io.IOException {
        ProfileStore store = new ProfileStore(userDir);
        for (String name : List.of("A", "B", "C", "D", "E", "F", "G")) {
            store.save(Profile.create(name, "original"));
        }
        GameFlow flow = at(userDir, UiKey.ENTER);

        StartScreens.Frame frame = frame(flow);

        assertTrue(hits(frame).contains(Hit.of(Widget.SCROLL_TRACK)));
        assertTrue(label(frame, "E").isPresent());
        assertTrue(label(frame, "F").isEmpty(), "below the six visible rows");
        flow.wheel(-1);
        assertTrue(label(frame(flow), "G").isPresent(), "the wheel scrolls the list");
        assertTrue(label(frame(flow), "< New Profile >").isEmpty(), "scrolled out at the top");
    }

    @Test
    void theOverviewShowsAThreeByThreeLetterGridColouredByState(@TempDir Path userDir) {
        GameFlow flow = overview(userDir);

        StartScreens.Frame frame = frame(flow);

        assertEquals(Drawing.WHITE, label(frame, "A").orElseThrow().colour(), "open (and selected)");
        assertEquals(Drawing.WHITE, label(frame, "C").orElseThrow().colour(), "open");
        assertEquals(Drawing.GREY, label(frame, "D").orElseThrow().colour(), "locked: greyed");
        assertEquals(Drawing.GREY, label(frame, "I").orElseThrow().colour());
        assertEquals(1, frame.drawing().sprites().stream()
                .filter(s -> s.region().equals(StartScreens.SELECTION)).count(), "one selected cell");
        assertTrue(label(frame, "Score: 000000").isPresent());
        assertTrue(label(frame, "Group 01 (  0%)").isPresent(), "the first group, displayed 1-based");
        assertTrue(label(frame, "001) Tower 1 - Pete Cooke").isPresent(), "the info line of the selection");
        assertEquals(List.of(Widget.GRID, Widget.GRID, Widget.GRID, Widget.GRID, Widget.GRID, Widget.GRID,
                Widget.GRID, Widget.GRID, Widget.GRID, Widget.PLAY, Widget.BACK), clickable(frame),
                "every cell can be clicked; the group buttons are disabled in group 01 without 7 solved");
        assertEquals(Drawing.GREY, label(frame, "< Go Back to Last Group").orElseThrow().colour());
        assertEquals(Drawing.GREY, label(frame, "Go Forward to Next Group >").orElseThrow().colour());
        PanelLayout.Rect a = cell(frame, 0);
        PanelLayout.Rect b = cell(frame, 1);
        PanelLayout.Rect d = cell(frame, 3);
        assertEquals(a.y(), b.y(), 1e-3f, "A and B in one row");
        assertTrue(b.x() > a.x(), "B right of A");
        assertEquals(a.x(), d.x(), 1e-3f, "D below A");
        assertTrue(d.y() < a.y(), "D below A (GUI y upwards)");
        PanelLayout.Rect i = cell(frame, 8);
        assertEquals(Optional.of(new Hit(Widget.GRID, 8)), frame.hit(i.x() + i.width() / 2f, i.y() + 2f));
        float centre = (a.x() + cell(frame, 2).x() + cell(frame, 2).width()) / 2f;
        assertEquals(LAYOUT.width() / 2f, centre, 1f, "the grid is centred");

        flow.click(new Hit(Widget.GRID, 7));
        StartScreens.Frame locked = frame(flow);
        assertTrue(label(locked, "008) Tower 8 - Pete Cooke").isPresent());
        assertFalse(clickable(locked).contains(Widget.PLAY), "a locked tower cannot be played");
        assertEquals(Drawing.GREY, label(locked, "Play").orElseThrow().colour());
    }

    @Test
    void aSolvedTowerIsMarkedAsPracticeOnThePlayButton(@TempDir Path userDir) throws java.io.IOException {
        ProfileStore store = new ProfileStore(userDir);
        store.save(CampaignTest.complete(de.latzig.game.tob.core.progress.Profile.create("Anna", "original"),
                "original-000"));
        store.rememberLast("Anna");
        GameFlow flow = at(userDir, UiKey.ENTER, UiKey.ENTER);
        assertEquals(GameFlow.Screen.OVERVIEW, flow.screen());

        assertTrue(label(frame(flow), "Play").isPresent(), "B, the next tower, is selected");
        flow.click(new Hit(Widget.GRID, 0));

        StartScreens.Frame frame = frame(flow);
        assertTrue(label(frame, "Practice").isPresent(), "decision 8: marked as practice");
        assertEquals(Drawing.GREEN, label(frame, "A").orElseThrow().colour(), "solved");
        assertTrue(label(frame, "001) Tower 1 - Pete Cooke [1]").isPresent());
    }

    /** Phase 5c-1 decision 3: the Tutorial in the Overview's layout, every tower open, the solved ones green. */
    @Test
    void theTutorialShowsAllNineOpenWithTheSolvedOnesGreen(@TempDir Path userDir) throws java.io.IOException {
        new ProfileStore(userDir).settings().update(settings -> settings.withSolvedTutorial("tutorial-007"));
        GameFlow flow = new GameFlow(TEXTS, new ProfileStore(userDir));
        flow.tutorial(TutorialModelTest.towers());
        flow.loaded(CAMPAIGN);
        settle(flow);
        assertEquals(Drawing.BLACK, label(frame(flow), "Tutorial").orElseThrow().colour(), "enabled in the menu");
        flow.click(Hit.of(Widget.MENU_TUTORIAL));
        settle(flow);

        StartScreens.Frame frame = frame(flow);

        assertEquals(Drawing.WHITE, label(frame, "Tutorial").orElseThrow().colour(), "the caption");
        assertEquals(Drawing.GREY, label(frame, "Solved: 1 of 9").orElseThrow().colour());
        assertEquals(Drawing.GREY, label(frame, "No profile, no score").orElseThrow().colour());
        assertEquals(Drawing.GREEN, label(frame, "H").orElseThrow().colour(), "solved");
        for (String letter : List.of("A", "B", "C", "D", "E", "F", "G", "I")) {
            assertEquals(Drawing.WHITE, label(frame, letter).orElseThrow().colour(), letter + ": open");
        }
        assertTrue(label(frame, "1) Tutorial 1 - Pete Cooke").isPresent(), "the info line");
        assertTrue(label(frame, "<Play>").isEmpty() && label(frame, "Play").isPresent(), "the grid has the focus");
        assertEquals(List.of(Widget.GRID, Widget.GRID, Widget.GRID, Widget.GRID, Widget.GRID, Widget.GRID,
                Widget.GRID, Widget.GRID, Widget.GRID, Widget.PLAY, Widget.BACK), clickable(frame),
                "no group buttons");
        assertEquals(cell(frame, 0), cell(frame(overview(userDir)), 0), "the Overview's grid");
        tabThrough(flow, 3, "tutorial"); // GRID, PLAY, BACK
    }

    @Test
    void theInfoLineWrapsOnceAndThenEndsWithDots() {
        assertEquals(48, StartScreens.maxChars(728f, 20f), "the widest glyph is 24 of 32 font pixels");
        assertEquals(List.of("001) Short - Pete"), StartScreens.fit("001) Short - Pete", 20));
        assertEquals(List.of("001) A rather", "long title - X"), StartScreens.fit("001) A rather long title - X", 15));
        assertEquals(List.of("aaaa bbbb", "cccc dd..."), StartScreens.fit("aaaa bbbb cccc dddd eeee ffff", 10));
        assertEquals(List.of("abcdefghij", "klmnopq"), StartScreens.fit("abcdefghijklmnopq", 10),
                "no space: a hard break");
        assertEquals(List.of("001) Kill!", "!!!!!!!..."), StartScreens.fit("001) Kill!!!!!!!!!!!! - X", 10),
                "a space only in the first half: a hard break (the original's tower 028)");
    }

    @Test
    void buttonLabelsShrinkToFitWhenTheFocusMarksWouldOverflow() {
        assertEquals(20f, StartScreens.fitSize("<OK>", 100f, 20f), "well within the box: no shrink");
        float shrunk = StartScreens.fitSize("<Go Forward to Next Group >", 100f, 20f);
        assertTrue(shrunk < 20f, "too wide at the nominal size");
        assertTrue(shrunk * "<Go Forward to Next Group >".length() * StartScreens.MAX_ADVANCE <= 100f + 1e-3f,
                "shrunk just enough to fit, by the same width estimate as fit()/maxChars()");
    }

    /**
     * Every drawn label, in every screen and focus state, must stay inside its own box (review Important #1) -
     * except the credits ticker, which is a deliberately scrolling marquee, free to be wider than its window.
     */
    private static void assertLabelsFitTheirRect(StartScreens.Frame frame, String tickerText, String context) {
        for (Drawing.Label label : frame.drawing().labels()) {
            if (label.text().equals(tickerText)) {
                continue;
            }
            float estimate = label.text().length() * label.size() * StartScreens.MAX_ADVANCE;
            assertTrue(estimate <= label.rect().width() + 0.5f, context + ": \"" + label.text() + "\" ("
                    + estimate + " units estimated) overflows its " + label.rect().width() + "-unit wide box");
        }
    }

    private static void tabThrough(GameFlow flow, int times, String context) {
        assertLabelsFitTheirRect(frame(flow), flow.ticker().shown(flow.clock()), context);
        for (int i = 0; i < times; i++) {
            flow.key(UiKey.TAB);
            settle(flow);
            assertLabelsFitTheirRect(frame(flow), flow.ticker().shown(flow.clock()), context);
        }
    }

    @Test
    void everyLabelOfEveryScreenFitsItsWidgetRectFocusedOrNot(@TempDir Path userDir) throws java.io.IOException {
        tabThrough(at(userDir), 4, "menu"); // 4 enabled entries: PLAY, OPTIONS, HALL OF FAME, QUIT

        GameFlow newGame = at(userDir, UiKey.ENTER, UiKey.ENTER);
        for (char c : "Anna".toCharArray()) {
            newGame.typed(c);
        }
        tabThrough(newGame, 4, "new game"); // NAME, START, BACK

        ProfileStore store = new ProfileStore(userDir);
        store.save(CampaignTest.complete(Profile.create("Anna", "original"), "original-000", "original-001",
                "original-002", "original-003", "original-004", "original-005", "original-006"));
        store.rememberLast("Anna");

        GameFlow playScreen = at(userDir, UiKey.ENTER);
        tabThrough(playScreen, 4, "play"); // LIST, START, DELETE, BACK

        GameFlow flow = at(userDir, UiKey.ENTER, UiKey.ENTER);
        assertEquals(GameFlow.Screen.OVERVIEW, flow.screen());
        // group 0, 7 solved: GRID, PREVIOUS_GROUP (disabled, skipped), NEXT_GROUP, PLAY, BACK.
        assertTrue(clickable(frame(flow)).contains(Widget.NEXT_GROUP), "Next Group is enabled with 7 of 9 solved");
        tabThrough(flow, 6, "overview group 0 (Next Group enabled)");

        flow.click(new Hit(Widget.GRID, 0)); // A, solved: Play reads "Practice"
        assertTrue(label(frame(flow), "Practice").isPresent());
        tabThrough(flow, 6, "overview, solved tower selected (<Practice> focused)");

        flow.click(Hit.of(Widget.NEXT_GROUP));
        settle(flow);
        assertEquals(1, flow.overview().group());
        tabThrough(flow, 6, "overview group 1 (Previous Group now enabled too)");

        GameFlow briefing = at(userDir, UiKey.ENTER, UiKey.ENTER);
        briefing.key(UiKey.ENTER);
        settle(briefing);
        briefing.skyProgress(0.3f);
        tabThrough(briefing, 2, "briefing, sky loading (<Play> focused, disabled)");
        briefing.skyProgress(1f);
        tabThrough(briefing, 2, "briefing, sky ready (<Play> focused, enabled)");

        // click PLAY directly (not via the TAB-moved focus above) so the attempt actually starts, as in the shots
        // driver.
        briefing.click(Hit.of(Widget.PLAY));
        settle(briefing);
        briefing.attemptEnded(Rules.EXIT_COMPLETED, 3, 700, Instant.EPOCH);
        settle(briefing);
        tabThrough(briefing, 3, "result, completed");

        GameFlow dev = new GameFlow(TEXTS, new ProfileStore(userDir));
        dev.developerSession(CAMPAIGN, CAMPAIGN.tower(0, 0));
        dev.attemptEnded(Rules.EXIT_GRABBER_DESTROYED, 2, 100, Instant.EPOCH);
        tabThrough(dev, 3, "result, failed developer session (<Menu> focused)");

        GameFlow confirm = at(userDir, UiKey.ESCAPE);
        tabThrough(confirm, 3, "ARE YOU SURE? (YES/NO toggling the focus)");
    }

    @Test
    void theMessageDialogWrapsALongSaveErrorAndEndsWithDots(@TempDir Path userDir) {
        // review Important #2: a real IOException on save carries a full path and is far wider than the dialog.
        String longPath = "D:/games/tower-of-babel-2026/portable-user-data/some/very/deeply/nested/profile/"
                + "storage/directory/that/no/longer/exists/on/this/machine/profiles/Anna.json (Access is denied)";
        String message = TEXTS.format("new.error.save", longPath);
        assertTrue(message.length() > 100, "a realistic save error, long enough to overflow unwrapped");

        GameFlow flow = new GameFlow(TEXTS, new ProfileStore(userDir));
        flow.gameLeft(message);
        assertEquals(GameFlow.Modal.MESSAGE, flow.modal());
        assertEquals(message, flow.message());

        StartScreens.Frame frame = frame(flow);

        // The message dialog is 600 wide; its text area (inside the window border and the two group borders) is
        // 570 units, as the review measured. fit()/maxChars() are the same helpers the Overview's info line uses.
        List<String> lines = StartScreens.fit(message, StartScreens.maxChars(570f, StartScreens.SMALL));
        assertTrue(lines.size() <= 2, "wraps to at most two lines instead of running off the dialog");
        assertTrue(lines.get(lines.size() - 1).endsWith("..."), "still too long even wrapped: cut with dots");
        for (String line : lines) {
            assertTrue(label(frame, line).isPresent(), "\"" + line + "\" is drawn as its own label");
        }
        assertLabelsFitTheirRect(frame, flow.ticker().shown(flow.clock()), "message dialog, long save error");
    }

    @Test
    void theBriefingGreysPlayAndShowsTheLoadingBarUntilTheSkyIsReady(@TempDir Path userDir) {
        GameFlow flow = at(userDir, UiKey.ENTER, UiKey.ENTER);
        flow.typed('A');
        flow.key(UiKey.ENTER);
        flow.key(UiKey.ENTER);
        settle(flow);
        flow.key(UiKey.ENTER);
        settle(flow);
        flow.skyProgress(0.5f);

        StartScreens.Frame loading = frame(flow);

        assertEquals(Drawing.GREY, label(loading, "<Play>").orElseThrow().colour());
        assertFalse(clickable(loading).contains(Widget.PLAY));
        assertTrue(label(loading, "Preparing the sky").isPresent());
        assertTrue(label(loading, "Collect 1 Klondike").isEmpty() && label(loading, "There is no task for this tower!")
                .isPresent(), "the test towers are 0/0");
        Drawing.Sprite indicator = loading.drawing().sprites().stream()
                .filter(sprite -> sprite.region().equals("gui.progress_indicator")).findFirst().orElseThrow();

        flow.skyProgress(1f);
        StartScreens.Frame ready = frame(flow);

        assertTrue(clickable(ready).contains(Widget.PLAY));
        assertTrue(ready.drawing().sprites().stream().noneMatch(s -> s.region().equals("gui.progress_indicator")));
        assertTrue(indicator.rect().width() > 0f);
    }

    @Test
    void aModalDialogTakesEveryClickAndShowsNoPreselected(@TempDir Path userDir) {
        GameFlow flow = at(userDir, UiKey.ESCAPE);

        StartScreens.Frame frame = frame(flow);

        assertEquals(List.of(Widget.YES, Widget.NO), clickable(frame));
        assertTrue(label(frame, "<NO>").isPresent());
        assertTrue(label(frame, "YES").isPresent());
        assertTrue(label(frame, "ARE YOU SURE?").isPresent());
        assertTrue(area(frame, Widget.YES).x() < area(frame, Widget.NO).x(), "YES left of NO");
    }

    @Test
    void theResultWindowShowsTheScoreBox(@TempDir Path userDir) {
        GameFlow flow = new GameFlow(TEXTS, new ProfileStore(userDir));
        flow.developerSession(CAMPAIGN, CAMPAIGN.tower(0, 0));
        flow.attemptEnded(Rules.EXIT_GRABBER_DESTROYED, 2, 100, Instant.EPOCH);

        StartScreens.Frame frame = frame(flow);

        assertTrue(label(frame, "Tower Not Completed").isPresent());
        assertTrue(label(frame, "Grabber Destroyed").isPresent());
        assertTrue(label(frame, "<Menu>").isPresent(), "a developer session goes back to the menu");
        assertEquals(List.of(Widget.RETRY, Widget.OVERVIEW), clickable(frame));
    }

    /**
     * Phase 5c-2 decision 9: the result window is flat and sits in the lower part of the screen, below the band
     * between the logo and the window where the interlude shows; its buttons are hit where they are drawn, at 4:3
     * and at 16:9.
     */
    @Test
    void theResultWindowSitsLowAndLeavesTheBandAboveItForTheInterlude(@TempDir Path userDir) {
        GameFlow flow = overview(userDir);
        flow.key(UiKey.ENTER);
        settle(flow);
        flow.skyProgress(1f);
        flow.key(UiKey.ENTER);
        settle(flow);
        flow.attemptEnded(Rules.EXIT_COMPLETED, 3, 700, Instant.EPOCH);
        GameFlow failed = new GameFlow(TEXTS, new ProfileStore(userDir));
        failed.developerSession(CAMPAIGN, CAMPAIGN.tower(0, 0));
        failed.attemptEnded(Rules.EXIT_SPIDERS_DESTROYED, 0, 100, Instant.EPOCH);

        assertEquals(462f, StartScreens.RESULT_TOP, 1e-3f);
        assertEquals((160f + 462f) / 2f / 768f, StartScreens.INTERLUDE_AXIS, 1e-6f);
        assertTrue(StartScreens.RESULT_TOP - 6f * StartScreens.INFO_LINE > StartScreens.TITLE_BOTTOM,
                "even with the six all-solved lines the window stays below the logo");
        for (StartLayout layout : List.of(new StartLayout(1024, 768), new StartLayout(1280, 720))) {
            for (GameFlow shown : List.of(flow, failed)) {
                StartScreens.Frame frame = StartScreens.draw(shown, TEXTS, layout, 0f, 0f);
                String context = layout.width() + "x" + layout.height() + " " + shown.result().caption();
                float bandBottom = layout.rect(0f, StartScreens.RESULT_TOP, 1f, 0f).y();
                float bandTop = layout.rect(0f, StartScreens.TITLE_BOTTOM, 1f, 0f).y();
                assertEquals(1, frame.drawing().sprites().stream()
                        .filter(sprite -> sprite.region().equals(StartScreens.TITLE)).count(), context + ": one logo");
                for (Drawing.Sprite sprite : frame.drawing().sprites()) {
                    if (sprite.region().equals(StartScreens.TITLE)) {
                        assertEquals(bandTop, sprite.rect().y(), 0.01f, context + ": the logo ends at TITLE_BOTTOM");
                    }
                    if (!sprite.region().equals(StartScreens.TITLE) && !sprite.region().equals(StartScreens.CURSOR)) {
                        assertTrue(sprite.rect().y() + sprite.rect().height() <= bandBottom + 0.01f,
                                context + ": " + sprite.region() + " reaches into the band");
                    }
                }
                for (Drawing.Label label : frame.drawing().labels()) {
                    assertTrue(label.rect().y() + label.rect().height() <= bandBottom + 0.01f,
                            context + ": " + label.text() + " reaches into the band");
                }
                assertTrue(bandTop - bandBottom > 0.35f * layout.height(), context + ": the band is wide");
                for (Widget widget : shown.resultButtons()) {
                    PanelLayout.Rect button = area(frame, widget);
                    assertEquals(Optional.of(Hit.of(widget)), frame.hit(button.x() + button.width() / 2f,
                            button.y() + button.height() / 2f), context + ": " + widget);
                    assertTrue(button.y() + button.height() < 0.2f * layout.height(), context + ": at the bottom");
                }
                assertEquals(shown.resultButtons(), clickable(frame), context);
            }
        }
        StartScreens.Frame success = StartScreens.draw(flow, TEXTS, LAYOUT, 0f, 0f);
        Drawing.Label total = label(success, "Total Score:").orElseThrow();
        Drawing.Label tower = label(success, "Tower Score").orElseThrow();
        Drawing.Label survivor = label(success, "Survivor Bonus").orElseThrow();
        assertTrue(total.rect().x() > tower.rect().x() + 100f, "two columns: the total right");
        assertEquals(total.rect().y(), survivor.rect().y(), 0.01f, "the second row: Survivor Bonus and Total");
        assertTrue(label(frame(failed), "All Spiders Destroyed").isPresent());
    }

    /** The tints of the button skin sprites drawn inside {@code rect} (over a modal dialog: only the dialog's). */
    private static List<Integer> buttonTints(StartScreens.Frame frame, PanelLayout.Rect rect) {
        int dialog = frame.drawing().sprites().stream().filter(sprite -> sprite.region().startsWith("gui.window"))
                .mapToInt(Drawing.Sprite::order).min().orElse(-1);
        return frame.drawing().sprites().stream().filter(sprite -> sprite.region().startsWith("gui.button_outset_"))
                .filter(sprite -> sprite.order() > dialog)
                .filter(sprite -> rect.contains(sprite.rect().x() + sprite.rect().width() / 2f,
                        sprite.rect().y() + sprite.rect().height() / 2f))
                .map(Drawing.Sprite::tint).distinct().toList();
    }

    // ------------------------------------------------------------------ Phase 5b: Options and Credits

    private static GameFlow options(Path userDir) {
        GameFlow flow = new GameFlow(TEXTS, new ProfileStore(userDir), LiveSettings.inMemory(Settings.DEFAULTS),
                OptionsModelTest.table());
        flow.loaded(CAMPAIGN);
        settle(flow);
        flow.click(Hit.of(Widget.MENU_OPTIONS));
        settle(flow);
        return flow;
    }

    /** R2 (final review re-review): the Options window with {@code -Dtob.volume} overriding Master. */
    private static GameFlow optionsOverridden(Path userDir) {
        GameFlow flow = new GameFlow(TEXTS, new ProfileStore(userDir), LiveSettings.inMemory(Settings.DEFAULTS),
                OptionsModelTest.table(), true);
        flow.loaded(CAMPAIGN);
        settle(flow);
        flow.click(Hit.of(Widget.MENU_OPTIONS));
        settle(flow);
        return flow;
    }

    /** Phase 7c: the Options window of a game without an audio device. */
    private static GameFlow optionsWithoutSound(Path userDir) {
        GameFlow flow = new GameFlow(TEXTS, new ProfileStore(userDir), LiveSettings.inMemory(Settings.DEFAULTS),
                OptionsModelTest.table(), false, false);
        flow.loaded(CAMPAIGN);
        settle(flow);
        flow.click(Hit.of(Widget.MENU_OPTIONS));
        settle(flow);
        return flow;
    }

    private static List<Hit> hits(StartScreens.Frame frame) {
        return frame.areas().stream().map(StartScreens.Area::hit).toList();
    }

    @Test
    void theOptionsWindowHasFourTabsAndTheSoundRows(@TempDir Path userDir) {
        StartScreens.Frame frame = frame(options(userDir));

        assertTrue(label(frame, "Options").isPresent());
        assertTrue(label(frame, "<Sound>").isPresent(), "the focused active tab");
        for (String tab : List.of("Video", "Game", "Controls", "Master Volume", "Background", "Sound Detail", "High",
                "Apply", "Back")) {
            assertTrue(label(frame, tab).isPresent(), tab);
        }
        assertEquals(Drawing.WHITE, label(frame, "100%").orElseThrow().colour());
        List<Hit> hits = hits(frame);
        for (OptionsModel.Tab tab : OptionsModel.Tab.values()) {
            assertTrue(hits.contains(new Hit(Widget.TAB, tab.ordinal())), tab.name());
        }
        for (OptionsModel.Option option : List.of(OptionsModel.Option.MASTER_VOLUME,
                OptionsModel.Option.BACKGROUND_VOLUME, OptionsModel.Option.SOUND_DETAIL)) {
            assertTrue(hits.contains(new Hit(Widget.OPTION_MINUS, option.ordinal())), option.name());
            assertTrue(hits.contains(new Hit(Widget.OPTION_PLUS, option.ordinal())), option.name());
        }
        assertTrue(hits.contains(Hit.of(Widget.APPLY)));
        assertEquals(LAYOUT.rect(StartScreens.CONTROL_X, StartScreens.OPTION_TOP + 5f, StartScreens.STEP_BUTTON,
                StartScreens.OPTION_ROW - 10f), area(frame, Widget.OPTION_MINUS));
        assertTrue(frame.drawing().sprites().stream().anyMatch(s -> s.region().equals("gui.progress_indicator")),
                "the volume bars");
        assertTrue(frame.drawing().sprites().stream().anyMatch(s -> s.region().startsWith("gui.button_inset_")),
                "the active tab is sunken");
    }

    /** Phase 7c: without an audio device the SOUND rows are grey and not clickable, with the hint below them. */
    @Test
    void withoutAnAudioDeviceTheSoundTabIsGreyWithAHint(@TempDir Path userDir) {
        StartScreens.Frame frame = frame(optionsWithoutSound(userDir));

        List<Hit> hits = hits(frame);
        for (OptionsModel.Option option : List.of(OptionsModel.Option.MASTER_VOLUME,
                OptionsModel.Option.BACKGROUND_VOLUME, OptionsModel.Option.SOUND_DETAIL)) {
            assertFalse(hits.contains(new Hit(Widget.OPTION_MINUS, option.ordinal())), option.name());
            assertFalse(hits.contains(new Hit(Widget.OPTION_PLUS, option.ordinal())), option.name());
        }
        assertFalse(hits.contains(new Hit(Widget.OPTION_SWITCH, OptionsModel.Option.MUSIC.ordinal())));
        assertEquals(Drawing.GREY, label(frame, "On").orElseThrow().colour(), "the Music switch is grey");
        // fix wave (Task 3-4 review #6): the values and the volume bars of the disabled rows are grey as well
        assertEquals(Drawing.GREY, label(frame, "100%").orElseThrow().colour(), "the volume value");
        assertEquals(Drawing.GREY, label(frame, "High").orElseThrow().colour(), "the Sound Detail value");
        List<Drawing.Sprite> bars = frame.drawing().sprites().stream()
                .filter(s -> s.region().startsWith("gui.progress_")).toList();
        assertTrue(bars.size() >= 2, "the two volume bars: " + bars);
        assertTrue(bars.stream().allMatch(s -> s.tint() == Drawing.GREY), "grey bars: " + bars);
        Drawing.Label hint = label(frame, "No audio device found").orElseThrow();
        assertEquals(Drawing.ORANGE, hint.colour());
        assertTrue(hits.contains(new Hit(Widget.TAB, OptionsModel.Tab.VIDEO.ordinal())), "the other tabs work");
    }

    @Test
    void theVideoTabHasSevenRowsWithSwitchesAndAHintForTheRestart(@TempDir Path userDir) {
        GameFlow flow = options(userDir);
        flow.click(new Hit(Widget.TAB, OptionsModel.Tab.VIDEO.ordinal()));
        flow.click(new Hit(Widget.OPTION_SWITCH, OptionsModel.Option.DISPLAY.ordinal()));

        StartScreens.Frame frame = frame(flow);

        List<Hit> hits = hits(frame);
        for (OptionsModel.Option option : List.of(OptionsModel.Option.DISPLAY, OptionsModel.Option.MOON_DETAIL,
                OptionsModel.Option.AMBIENT_EFFECTS, OptionsModel.Option.PANEL_SIZE, OptionsModel.Option.LOOK)) {
            assertTrue(hits.contains(new Hit(Widget.OPTION_SWITCH, option.ordinal())), option.name());
        }
        assertTrue(label(frame, "<Window (after restart)>").isPresent(), "the focused switch with the hint");
        assertTrue(label(frame, "Full Width").isPresent());
        assertTrue(label(frame, "Remake").isPresent(), "Phase 5e: the Look");
        assertEquals(Drawing.WHITE, label(frame, "Display").orElseThrow().colour(), "the focused row");
        assertEquals(Drawing.GREY, label(frame, "Brightness").orElseThrow().colour());
        PanelLayout.Rect look = frame.areas().stream().filter(a -> a.hit().equals(new Hit(Widget.OPTION_SWITCH,
                OptionsModel.Option.LOOK.ordinal()))).findFirst().orElseThrow().rect();
        PanelLayout.Rect bar = LAYOUT.rect(StartScreens.MARGIN, StartScreens.BAR_TOP, StartScreens.CONTENT_WIDTH,
                StartScreens.ROW_HEIGHT);
        assertTrue(look.y() >= bar.y() + bar.height(), "the seventh row ends above the button bar");
        assertEquals(LAYOUT.rect(StartScreens.CONTROL_X, StartScreens.OPTION_TOP + 6f * StartScreens.VIDEO_OPTION_ROW
                + 5f, StartScreens.SWITCH_WIDTH, StartScreens.VIDEO_OPTION_ROW - 10f), look);
    }

    /** Task 3 review M1: the seventh VIDEO row sits where it should on a 4:3 screen too. */
    @Test
    void theVideoTabsLookRowOnAFourToThreeScreen(@TempDir Path userDir) {
        GameFlow flow = options(userDir);
        flow.click(new Hit(Widget.TAB, OptionsModel.Tab.VIDEO.ordinal()));
        StartLayout square = new StartLayout(1024, 768);

        StartScreens.Frame frame = StartScreens.draw(flow, TEXTS, square, 100f, 100f);

        PanelLayout.Rect look = frame.areas().stream().filter(a -> a.hit().equals(new Hit(Widget.OPTION_SWITCH,
                OptionsModel.Option.LOOK.ordinal()))).findFirst().orElseThrow().rect();
        assertEquals(square.rect(StartScreens.CONTROL_X, StartScreens.OPTION_TOP + 6f
                * StartScreens.VIDEO_OPTION_ROW + 5f, StartScreens.SWITCH_WIDTH, StartScreens.VIDEO_OPTION_ROW - 10f),
                look);
    }

    @Test
    void theControlsTabHasTheHideCursorSwitchAboveFourActionsWithTheirKeysAScrollBarAndReset(
            @TempDir Path userDir) {
        GameFlow flow = options(userDir);
        flow.click(new Hit(Widget.TAB, OptionsModel.Tab.CONTROLS.ordinal()));
        int last = OptionsModel.KEY_ROWS - 1;
        flow.click(new Hit(Widget.KEY_ROW, last));

        StartScreens.Frame frame = frame(flow);

        assertTrue(hits(frame).contains(new Hit(Widget.OPTION_SWITCH, OptionsModel.Option.HIDE_CURSOR.ordinal())),
                "the switch above the key list");
        assertTrue(label(frame, "Hide Mouse Cursor").isPresent());
        assertTrue(label(frame, "On").isPresent(), "default on");
        List<Hit> hits = hits(frame);
        for (int row = 0; row <= last; row++) {
            assertTrue(hits.contains(new Hit(Widget.KEY_ROW, row)), "row " + row);
        }
        assertFalse(hits.contains(new Hit(Widget.KEY_ROW, OptionsModel.KEY_ROWS)), "four visible, not scrolled yet");
        assertTrue(hits.contains(Hit.of(Widget.SCROLL_TRACK)));
        assertTrue(hits.contains(Hit.of(Widget.KEYS_RESET)));
        assertEquals(GameAction.FORWARD, flow.options().actions().get(0));
        assertTrue(label(frame, "Forward").isPresent());
        assertTrue(label(frame, "Up").isPresent(), "Forward's key");
        assertEquals(Drawing.ORANGE, label(frame, "Press a key").orElseThrow().colour(), "the clicked row captures");
        PanelLayout.Rect track = area(frame, Widget.SCROLL_TRACK);
        assertEquals(LAYOUT.rect(StartScreens.MARGIN + StartScreens.BORDER + StartScreens.LIST_WIDTH + 6f,
                StartScreens.CONTROLS_LIST_TOP, StartScreens.SCROLL_WIDTH,
                OptionsModel.KEY_ROWS * StartScreens.OPTION_ROW), track);
    }

    @Test
    void theCreditsTabShowsTwelveLinesWithGreyHeadingsAndAScrollBar(@TempDir Path userDir) {
        GameFlow flow = at(userDir);
        flow.click(Hit.of(Widget.MENU_HALL_OF_FAME));
        settle(flow);
        flow.click(new Hit(Widget.TAB, HallOfFame.Tab.CREDITS.ordinal()));

        StartScreens.Frame frame = frame(flow);

        assertTrue(label(frame, "Hall of Fame").isPresent());
        assertTrue(label(frame, "Credits").isPresent(), "the tab");
        assertEquals(Drawing.GREY, label(frame, "Original game:").orElseThrow().colour(), "a heading");
        assertEquals(Drawing.WHITE, label(frame, "Pete Cooke").orElseThrow().colour());
        assertEquals(Drawing.WHITE, label(frame, "Axeman Towers Ltd.").orElseThrow().colour(), "line 11 of 12");
        assertFalse(label(frame, "Remake:").isPresent(), "line 13, below the first 12 lines");
        assertEquals(List.of(new Hit(Widget.TAB, 0), new Hit(Widget.TAB, 1), Hit.of(Widget.SCROLL_TRACK),
                Hit.of(Widget.BACK)), hits(frame));
        flow.credits().end();
        assertTrue(label(frame(flow), "Tower of Babel (c) Pete Cooke").isPresent(), "the last line");
    }

    /** Phase 5c-1 decision 4: SCORES shows the ranked profiles under grey column heads; the active tab is sunken. */
    @Test
    void theScoresTabRanksTheProfiles(@TempDir Path userDir) throws java.io.IOException {
        GameFlow empty = at(userDir);
        empty.click(Hit.of(Widget.MENU_HALL_OF_FAME));
        settle(empty);
        assertEquals(Drawing.GREY, label(frame(empty), "No profiles yet").orElseThrow().colour());

        ProfileStore store = new ProfileStore(userDir);
        store.save(Profile.create("MMMMMMMMMMMMMMMM", "original").withGroup(12));
        store.save(CampaignTest.complete(Profile.create("Anna", "original"), "original-000").withGroup(1));
        GameFlow flow = at(userDir);
        flow.click(Hit.of(Widget.MENU_HALL_OF_FAME));
        settle(flow);

        StartScreens.Frame frame = frame(flow);

        for (String head : List.of("Rank", "Name", "Score", "Solved", "Group")) {
            assertEquals(Drawing.GREY, label(frame, head).orElseThrow().colour(), head);
        }
        // author decision 2026-10-15: the group is displayed 1-based ("01".."13"); Anna's group() is 1 (-> "02"),
        // MMMMMMMMMMMMMMMM's group() is 12 (-> "13").
        for (String cell : List.of("1.", "Anna", "000210", "1", "02", "2.", "000000", "0", "13")) {
            assertEquals(Drawing.WHITE, label(frame, cell).orElseThrow().colour(), cell);
        }
        float anna = label(frame, "Anna").orElseThrow().rect().y();
        assertTrue(anna > label(frame, "MMMMMMMMMMMMMMMM").orElseThrow().rect().y(), "rank 1 above rank 2 (GUI y up)");
        PanelLayout.Rect scores = area(frame, Widget.TAB);
        assertTrue(frame.drawing().sprites().stream().anyMatch(sprite -> sprite.region().startsWith(
                "gui.button_inset_") && scores.contains(sprite.rect().x() + 1f, sprite.rect().y() + 1f)),
                "SCORES is the sunken (active) tab");
        assertLabelsFitTheirRect(frame, "", "scores, the widest name");
    }

    @Test
    void aPointOnTheTrackIsAFractionFromItsTop() {
        PanelLayout.Rect track = new PanelLayout.Rect(10f, 100f, 20f, 200f);

        assertEquals(0f, StartScreens.trackFraction(track, 300f), 1e-6f, "the top (GUI y is upwards)");
        assertEquals(1f, StartScreens.trackFraction(track, 100f), 1e-6f);
        assertEquals(0.25f, StartScreens.trackFraction(track, 250f), 1e-6f);
    }

    @Test
    void everyLabelOfTheOptionsAndTheCreditsFits(@TempDir Path userDir) {
        GameFlow flow = options(userDir);
        for (OptionsModel.Tab tab : OptionsModel.Tab.values()) {
            flow.click(new Hit(Widget.TAB, tab.ordinal()));
            tabThrough(flow, 5, "options " + tab);
        }
        flow.click(new Hit(Widget.KEY_ROW, flow.options().actions().indexOf(GameAction.PROGRAM_RUN_ALL)));
        flow.captureKey(OptionsModelTest.UP, true);
        assertLabelsFitTheirRect(frame(flow), "", "a refused key");
        flow.captureKey(OptionsModelTest.ESC, false);
        flow.click(new Hit(Widget.TAB, OptionsModel.Tab.VIDEO.ordinal()));
        flow.click(new Hit(Widget.OPTION_SWITCH, OptionsModel.Option.DISPLAY.ordinal()));
        assertLabelsFitTheirRect(frame(flow), "", "the restart hint");
        for (GameAction action : GameAction.values()) {
            assertTrue(TEXTS.get("action." + action.name()).length() * StartScreens.OPTION_TEXT
                    * StartScreens.MAX_ADVANCE <= StartScreens.ACTION_WIDTH, action.name());
        }

        // R2 (final review re-review): with -Dtob.volume overriding Master, the note moved to the label column
        // (300 units wide) must still fit and stay readable, unlike the much narrower value column it used to use.
        GameFlow overridden = optionsOverridden(userDir);
        assertLabelsFitTheirRect(frame(overridden), "", "master overridden");
        float masterLabelSize = StartScreens.fitSize(
                overridden.options().label(OptionsModel.Option.MASTER_VOLUME, TEXTS), StartScreens.LABEL_WIDTH,
                StartScreens.OPTION_TEXT);
        assertTrue(masterLabelSize >= StartScreens.OPTION_TEXT * 0.5f,
                "the override note stays readable: " + masterLabelSize);

        GameFlow credits = at(userDir);
        credits.click(Hit.of(Widget.MENU_HALL_OF_FAME));
        settle(credits);
        tabThrough(credits, 2, "hall of fame (TAB switches the tabs)");
        credits.click(new Hit(Widget.TAB, HallOfFame.Tab.CREDITS.ordinal()));
        for (int first = 0; first <= credits.credits().maxFirst(); first++) {
            credits.credits().scrollTo(first);
            assertLabelsFitTheirRect(frame(credits), "", "credits from line " + first);
        }
    }

    /**
     * Final review I2: the cursor resting on a button only tints it; the keyboard focus stays where it was (the name
     * field keeps its blinking cursor and no button shows the focus marks), so typing goes on into the name.
     */
    @Test
    void hoveringAButtonHighlightsItWithoutTakingTheFocus(@TempDir Path userDir) {
        GameFlow flow = at(userDir, UiKey.ENTER, UiKey.ENTER);
        PanelLayout.Rect start = area(frame(flow), Widget.START);
        float x = start.x() + start.width() / 2f;
        float y = start.y() + start.height() / 2f;

        StartScreens.Frame hovered = StartScreens.draw(flow, TEXTS, LAYOUT, x, y);
        flow.typed('A');

        assertEquals(List.of(StartScreens.HOVER), buttonTints(hovered, start));
        assertEquals(List.of(Drawing.WHITE), buttonTints(hovered, area(hovered, Widget.BACK)));
        assertTrue(label(hovered, "Start").isPresent(), "no focus marks on the hovered button");
        assertEquals(Widget.NAME, flow.focus());
        assertEquals("A", flow.name().text(), "typing still goes into the name");
        assertEquals(List.of(Drawing.WHITE), buttonTints(frame(flow), start), "not hovered: untinted");

        GameFlow confirm = at(userDir, UiKey.ESCAPE);
        PanelLayout.Rect yes = area(frame(confirm), Widget.YES);
        StartScreens.Frame overYes = StartScreens.draw(confirm, TEXTS, LAYOUT, yes.x() + 5f, yes.y() + 5f);
        assertEquals(List.of(StartScreens.HOVER), buttonTints(overYes, yes));
        assertTrue(label(overYes, "<NO>").isPresent(), "NO keeps the keyboard focus");
        assertFalse(confirm.modalYes());
    }
}
