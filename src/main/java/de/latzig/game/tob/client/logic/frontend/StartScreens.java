package de.latzig.game.tob.client.logic.frontend;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import de.latzig.game.tob.client.logic.Texts;
import de.latzig.game.tob.client.logic.editor.EditorScreens;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Hit;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Modal;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Widget;
import de.latzig.game.tob.client.logic.game.ConfirmDialogLayout;
import de.latzig.game.tob.client.logic.game.GameAction;
import de.latzig.game.tob.client.logic.game.PanelLayout;
import de.latzig.game.tob.client.logic.ui.CreditsTicker;
import de.latzig.game.tob.client.logic.ui.Drawing;
import de.latzig.game.tob.client.logic.ui.Drawing.Align;
import de.latzig.game.tob.client.logic.ui.NineSlice;
import de.latzig.game.tob.client.logic.ui.ScrollBox;
import de.latzig.game.tob.client.logic.ui.StartLayout;
import de.latzig.game.tob.core.progress.Profile;

/**
 * The start screens in ToB 2009's look (Phase 5a spec §2 decision 2, §5 open point 3; {@code J:start/*Window.java},
 * report 16 §2): a transparent full screen with the title image ({@code start.title} at 128,32 768 × 128), the
 * caption group (128,192 768 × 60, 40-unit text), and below it either the menu buttons or a dialog body
 * (128,256 768 × 380) with the button bar (128,640 768 × 60, buttons 256 wide, right-aligned, OK before Back).
 * Labels in dialogs are grey, text fields orange, list rows black with an orange selection. A focused button shows
 * ToB 2009's focus marks {@code <text>}; a disabled one is tinted grey; the enabled button under the mouse cursor is
 * tinted {@link #HOVER} without taking the keyboard focus (final review I2: hovering never moves the focus, so typing
 * and the arrow keys keep working while the cursor rests on a button). Everything is built in the virtual
 * 1024 × 768 screen ({@link StartLayout}) and returned as a {@link Drawing} plus the clickable {@link Area}s; while a
 * modal dialog is open only its buttons are clickable. Engine-free.
 * <p>
 * The main menu (Phase 5c-1 decision 1) holds six entries in ToB 2009's size ({@code J:start/MenuWindow.java},
 * {@code AbstractStartWindow.createMenuButton}): rows 64 units apart, each a 60-unit group with a 512 × 50 button and
 * 40-unit text, the credits ticker half a row below them at y 672.
 * <p>
 * The Play window (Phase 5c-1 decision 2) shows the column heads Name, Score and Group above ToB 2009's list box:
 * "&lt; New Profile &gt;" (grey when five profiles exist, with the hint "Delete a profile first" below the list),
 * then each profile with its six-digit total score and its two-digit group, displayed 1-based as the Overview's
 * "Group nn" (author decision 2026-10-15); the selection is ToB 2009's orange on the list's selection background; a
 * scroll bar only if more rows than {@link PlayModel#ROWS} exist (older data); Play, Delete and Back in the bar.
 * <p>
 * The Hall of Fame (Phase 5c-1 decision 4) has the Options window's tab bar with two tabs, SCORES and CREDITS, the
 * active one sunken; below it a sunken box with a scroll bar down to the body's border: on SCORES the column heads
 * Rank, Name, Score, Solved and Group and {@link HallOfFame#SCORE_ROWS} rows of ranked profiles ("No profiles yet"
 * without one), on CREDITS {@link HallOfFame#CREDIT_ROWS} lines of Phase 5b's credits text; Back in the bar.
 * <p>
 * The Overview (author decision 2026-10-12, replacing ToB 2009's list): "Score" left and "Group nn (xx%)" right, the
 * group's 9 towers as a 3×3 grid of sunken letter cells A–I centred in the body (rows A–C, D–F, G–I; one font size,
 * no titles), the letter coloured by the cell's state (solved green, open white, locked grey) and the selected cell
 * on ToB 2009's list selection background (tinted grey while the focus is on a button); below the grid the selected
 * tower's info line "nnn) Title - Author [n]" (wrapped once, then cut with "..."), then the two group buttons. A
 * solved tower is marked as practice (decision 8): its Play button reads "Practice". Phase 6d: Library, Play and
 * Back in the bar (Library switches between the original towers and the own libraries); with an own library the
 * caption reads "NAME (library)", the header "Solved: n of m", and the cells past its last tower stay empty.
 * <p>
 * The Tutorial (Phase 5c-1 decision 3, open point 2) reuses the Overview's layout: "Solved: n of 9" left (green when
 * all are) and "No profile, no score" right, the same grid with every tower open (solved ones green), the info line
 * "n) Title - Author"; no group buttons; Play and Back.
 * <p>
 * The result window (Phase 5c-2 decision 9) is flat and sits in the lower part of the screen: the logo stays on top,
 * the caption group starts at {@link #RESULT_TOP}, the body below it holds the reason and the score lines in two
 * columns, the button bar is the usual one; the band between the logo and the caption is left free for the interlude
 * behind the window (its view's axis at {@link #INTERLUDE_AXIS}). The all-solved lines make the body taller upwards.
 */
public final class StartScreens {

    public static final float MARGIN = 128f;
    public static final float CONTENT_WIDTH = StartLayout.WIDTH - 2f * MARGIN;
    public static final float BORDER = 5f;
    public static final float BIG = 40f;
    public static final float SMALL = 20f;
    public static final float BODY_TOP = 256f;
    public static final float BODY_HEIGHT = 380f;
    public static final float BAR_TOP = 640f;
    /** The title image (the logo): its top and height in virtual pixels. */
    public static final float TITLE_TOP = 32f;
    public static final float TITLE_HEIGHT = 128f;
    /** The bottom of the title image; the interlude's band starts here. */
    public static final float TITLE_BOTTOM = TITLE_TOP + TITLE_HEIGHT;
    /** Phase 5c-2 decision 9: the result window's caption group height, reason row and score rows. */
    public static final float RESULT_CAPTION = 50f;
    public static final float RESULT_REASON = 40f;
    public static final float RESULT_LINE = 34f;
    public static final float RESULT_TEXT = 28f;
    /** The reason's largest font size (smaller if it does not fit the width). */
    public static final float RESULT_REASON_TEXT = 32f;
    /** The result window's body: the reason and two rows of score lines (two columns each). */
    public static final float RESULT_BODY = 2f * BORDER + RESULT_REASON + 2f * RESULT_LINE + 6f;
    /** The result window's top (its caption group), the bottom of the band left free for the interlude. */
    public static final float RESULT_TOP = BAR_TOP - 4f - RESULT_BODY - RESULT_CAPTION;
    /** The interlude view's axis: the middle of the band between the logo and the result window, from the top. */
    public static final float INTERLUDE_AXIS = (TITLE_BOTTOM + RESULT_TOP) / 2f / StartLayout.HEIGHT;
    public static final float ROW_HEIGHT = 60f;
    public static final float BUTTON_WIDTH = 256f;
    /** ToB 2009 {@code DIALOG_OFFSETX}: fields and lists start this far into the dialog. */
    public static final float FIELD_X = 220f;
    /** Phase 5c-1 decision 1: ToB 2009's menu rows ({@code ROW_HEIGHT} 64, {@code BUTTON_HEIGHT} 60, big font). */
    public static final float MENU_ROW = 64f;
    public static final float MENU_ROW_HEIGHT = 60f;
    public static final float MENU_TEXT = BIG;
    public static final float MENU_BUTTON_WIDTH = 512f;
    /** ToB 2009: the ticker's group half a row below the six rows ({@code 6 * ROW_HEIGHT + ROW_HEIGHT / 2}). */
    public static final float TICKER_TOP = BODY_TOP + 6.5f * MENU_ROW;
    /** ToB 2009 {@code MARGIN_CREDITS}. */
    public static final float TICKER_MARGIN = 32f;
    /** Phase 5c-1, the Play window: one list row, its text, and the columns from the text's left edge. */
    public static final float PLAY_ROW = 48f;
    public static final float PLAY_TEXT = 32f;
    public static final float PLAY_NAME_WIDTH = 400f;
    public static final float PLAY_SCORE_X = 410f;
    public static final float PLAY_SCORE_WIDTH = 160f;
    public static final float PLAY_GROUP_X = 580f;
    public static final float PLAY_GROUP_WIDTH = 100f;
    /**
     * The software cursor's size in virtual units (24 pixels at ToB 2009's 768 lines), scaled with the layout like
     * everything else; its hot spot is its top left corner.
     */
    public static final float CURSOR_SIZE = 24f;
    public static final String CURSOR = "gui.cursor_normal";
    public static final String TITLE = "start.title";
    /** ToB 2009's selected list row: the Play list's selection and the Overview's selected cell. */
    public static final String SELECTION = "gui.listbox_background_selected";

    /** The Overview's header line ("Score", "Group"). */
    public static final float HEADER_HEIGHT = 32f;
    /** One Overview grid cell (square) and the gap between cells. */
    public static final float CELL = 64f;
    public static final float CELL_GAP = 8f;
    public static final float GRID_SIZE = OverviewModel.SIZE * CELL + (OverviewModel.SIZE - 1) * CELL_GAP;
    public static final float GRID_TOP = BODY_TOP + BORDER + HEADER_HEIGHT + 6f;
    /** The info line(s) below the grid: at most two lines of {@value #SMALL}-unit text. */
    public static final float INFO_TOP = GRID_TOP + GRID_SIZE + 6f;
    public static final float INFO_LINE = 25f;
    /** The group buttons below the info lines, each half the body wide. */
    public static final float GROUP_BUTTONS_TOP = INFO_TOP + 2f * INFO_LINE + 12f;
    public static final float GROUP_BUTTON_HEIGHT = 50f;
    /**
     * The widest glyph of the ToB 2009 font ({@code tob-font.fnt}: 24 of 32 pixels, most are 22) relative to the
     * font size: an upper bound of a text's width, so a fitted line never leaves its box.
     */
    public static final float MAX_ADVANCE = 24f / 32f;
    private static final String ELLIPSIS = "...";
    /** The tint of the enabled button under the mouse cursor (ToB 2009's orange over the grey button skin). */
    public static final int HOVER = Drawing.ORANGE;

    /** Phase 5b, the Options window: the tab bar below the body's top border. */
    public static final float TAB_TOP = BODY_TOP + BORDER;
    public static final float TAB_HEIGHT = 44f;
    public static final float TAB_GAP = 6f;
    public static final float TAB_WIDTH = (CONTENT_WIDTH - 2f * BORDER - 3f * TAB_GAP) / 4f;
    /** The first option row; rows are {@value #OPTION_ROW} high, VIDEO's {@value #VIDEO_OPTION_ROW}. */
    public static final float OPTION_TOP = TAB_TOP + TAB_HEIGHT + 8f;
    public static final float OPTION_ROW = 50f;
    /**
     * Phase 5e: the VIDEO tab's seven rows (the Look is the seventh) fill 308 of the 318 left in the body
     * ({@link #OPTION_TOP} 313 to the body's inner bottom 631).
     */
    public static final float VIDEO_OPTION_ROW = 44f;
    public static final float OPTION_TEXT = 24f;
    public static final float LABEL_WIDTH = 300f;
    /** The controls of a row start here: {@code -}, the bar or text box, {@code +}, the percent value. */
    public static final float CONTROL_X = MARGIN + 2f * BORDER + LABEL_WIDTH + 10f;
    public static final float STEP_BUTTON = 40f;
    public static final float BAR_WIDTH = 240f;
    public static final float SWITCH_WIDTH = 2f * STEP_BUTTON + BAR_WIDTH + 12f;
    /** The scroll bars of the CONTROLS tab's key list, the Play list and the Hall of Fame's tabs. */
    public static final float SCROLL_WIDTH = 24f;
    /** The key list's, the Play list's and the Hall of Fame's sunken box, left of the scroll bar. */
    public static final float LIST_WIDTH = CONTENT_WIDTH - 2f * BORDER - SCROLL_WIDTH - 6f;
    /** The key list's action column; the key column takes the rest. */
    public static final float ACTION_WIDTH = 290f;
    /**
     * Author's extra (2026-10-14): the CONTROLS tab's "Hide Mouse Cursor" switch takes the first {@value
     * #OPTION_ROW}-high row at {@link #OPTION_TOP} (drawn by {@code optionRows}, as every other tab's rows); the key
     * list below it starts here instead of at {@code OPTION_TOP}, one row lower (same bottom margin as before: the
     * key list lost a row, {@link OptionsModel#KEY_ROWS}).
     */
    public static final float CONTROLS_LIST_TOP = OPTION_TOP + OPTION_ROW;
    /** A line of the Hall of Fame's CREDITS tab. */
    public static final float LINE = 25f;
    /** Phase 5c-1, the Hall of Fame: two tabs over the body's width, the list box below down to the body's border. */
    public static final float HALL_TAB_WIDTH = (CONTENT_WIDTH - 2f * BORDER - TAB_GAP) / 2f;
    public static final float HALL_LIST_TOP = OPTION_TOP;
    public static final float HALL_LIST_HEIGHT = BODY_TOP + BODY_HEIGHT - BORDER - HALL_LIST_TOP;
    /** One SCORES row: {@value #OPTION_TEXT}-unit text, five rows below the column heads. */
    public static final float SCORE_ROW = 55f;
    /** The SCORES columns Rank, Name, Score, Solved, Group: left edges from the text's left edge, and widths. */
    private static final float[] SCORE_X = {0f, 70f, 380f, 520f, 620f};
    private static final float[] SCORE_WIDTH = {60f, 300f, 130f, 90f, 78f};
    private static final String[] SCORE_HEADS = {"halloffame.rank", "halloffame.name", "halloffame.score",
        "halloffame.solved", "halloffame.group"};

    /** A clickable rectangle. */
    public record Area(Hit hit, PanelLayout.Rect rect) {
    }

    /** One frame: what to draw and where to click. */
    public record Frame(Drawing drawing, List<Area> areas) {

        public Frame {
            areas = List.copyOf(areas);
        }

        /** The front-most area at a screen point (GUI pixels, origin bottom left), if any. */
        public Optional<Hit> hit(float x, float y) {
            for (int i = areas.size() - 1; i >= 0; i--) {
                if (areas.get(i).rect().contains(x, y)) {
                    return Optional.of(areas.get(i).hit());
                }
            }
            return Optional.empty();
        }
    }

    private final GameFlow flow;
    private final Texts texts;
    private final StartLayout layout;
    private final Drawing.Builder drawing = new Drawing.Builder();
    private final List<Area> areas = new ArrayList<>();
    private final float cursorX;
    private final float cursorY;

    private StartScreens(GameFlow flow, Texts texts, StartLayout layout, float cursorX, float cursorY) {
        this.flow = flow;
        this.texts = texts;
        this.layout = layout;
        this.cursorX = cursorX;
        this.cursorY = cursorY;
    }

    /** The frame of the flow's screen with the software cursor at ({@code cursorX}, {@code cursorY}). */
    public static Frame draw(GameFlow flow, Texts texts, StartLayout layout, float cursorX, float cursorY) {
        StartScreens screens = new StartScreens(flow, texts, layout, cursorX, cursorY);
        screens.screen();
        if (flow.modal() != Modal.NONE) {
            screens.areas.clear();
            screens.modal();
        }
        float cursor = CURSOR_SIZE * layout.scale();
        screens.drawing.sprite(CURSOR, new PanelLayout.Rect(cursorX, cursorY - cursor, cursor, cursor), Drawing.WHITE);
        return new Frame(screens.drawing.build(), screens.areas);
    }

    /** How many characters of {@code size}-unit text surely fit into {@code width} units ({@link #MAX_ADVANCE}). */
    public static int maxChars(float width, float size) {
        return Math.max(1, (int) Math.floor(width / (size * MAX_ADVANCE)));
    }

    /**
     * {@code text} in at most two lines of {@code max} characters: broken at the last space that fits (hard if
     * there is none in the line's second half, as for the original's "Kill!!!…"), the second line cut with "..." if
     * it is still too long.
     */
    static List<String> fit(String text, int max) {
        if (text.length() <= max) {
            return List.of(text);
        }
        int space = text.lastIndexOf(' ', max);
        int cut = space >= max / 2 ? space : max;
        String first = text.substring(0, cut).stripTrailing();
        String rest = text.substring(cut).stripLeading();
        if (rest.length() > max) {
            rest = rest.substring(0, Math.max(0, max - ELLIPSIS.length())).stripTrailing() + ELLIPSIS;
        }
        return List.of(first, rest);
    }

    private void screen() {
        switch (flow.screen()) {
            case GAME -> {
                return;
            }
            case LOADER -> loader();
            case MENU -> menu();
            case PLAY -> play();
            case NEW_GAME -> newGame();
            case OVERVIEW -> overview();
            case TUTORIAL -> tutorial();
            case BRIEFING -> briefing();
            case RESULT -> result();
            case OPTIONS -> options();
            case HALL_OF_FAME -> hallOfFame();
            case EDITOR -> EditorScreens.draw(flow.designer(), texts, layout, drawing, areas, cursorX, cursorY,
                    flow.clock());
        }
    }

    /** The fraction of {@code track} (a scroll bar, GUI pixels, y upwards) at screen height {@code y}: 0 at its top. */
    public static float trackFraction(PanelLayout.Rect track, float y) {
        return (track.y() + track.height() - y) / track.height();
    }

    // ------------------------------------------------------------------ frame

    private void frame(String caption) {
        drawing.sprite(TITLE, rect(MARGIN, TITLE_TOP, CONTENT_WIDTH, TITLE_HEIGHT), Drawing.WHITE);
        group(MARGIN, 192f, CONTENT_WIDTH, ROW_HEIGHT);
        label(caption, MARGIN + BORDER, 192f + BORDER, CONTENT_WIDTH - 2f * BORDER, ROW_HEIGHT - 2f * BORDER, BIG,
                Drawing.WHITE, Align.CENTRE);
    }

    private void body() {
        group(MARGIN, BODY_TOP, CONTENT_WIDTH, BODY_HEIGHT);
    }

    /** The button bar with {@code buttons} right-aligned (the last at the right edge), each 256 wide. */
    private void bar(List<Widget> buttons, List<String> labels) {
        group(MARGIN, BAR_TOP, CONTENT_WIDTH, ROW_HEIGHT);
        float width = buttons.size() > 2 ? 240f : BUTTON_WIDTH;
        float x = MARGIN + CONTENT_WIDTH - BORDER - buttons.size() * width - (buttons.size() - 1) * 10f;
        for (int i = 0; i < buttons.size(); i++) {
            button(buttons.get(i), labels.get(i), x, BAR_TOP + BORDER, width, ROW_HEIGHT - 2f * BORDER, BIG);
            x += width + 10f;
        }
    }

    private void group(float x, float y, float w, float h) {
        drawing.nineSlice(NineSlice.GROUP_OUTSET, rect(x, y, w, h), layout.scale(), Drawing.WHITE);
    }

    private void button(Widget widget, String text, float x, float y, float w, float h, float size) {
        widget(Hit.of(widget), text, x, y, w, h, size, flow.focus() == widget && flow.modal() == Modal.NONE,
                flow.isEnabled(widget), NineSlice.BUTTON);
    }

    /**
     * A button: its skin (tinted grey when disabled, {@link #HOVER} under the cursor), its label with ToB 2009's focus
     * marks, and its clickable area ({@code hit}) if it is enabled.
     */
    private void widget(Hit hit, String text, float x, float y, float w, float h, float size, boolean focused,
            boolean enabled, NineSlice skin) {
        int tint = !enabled ? Drawing.GREY
                : flow.modal() == Modal.NONE && hovered(rect(x, y, w, h)) ? HOVER : Drawing.WHITE;
        drawing.nineSlice(skin, rect(x, y, w, h), layout.scale(), tint);
        String shown = focused ? "<" + text + ">" : text;
        float labelWidth = w - 2f * BORDER;
        label(shown, x + BORDER, y + BORDER, labelWidth, h - 2f * BORDER, fitSize(shown, labelWidth, size),
                enabled ? Drawing.BLACK : Drawing.GREY, Align.CENTRE);
        if (enabled) {
            areas.add(new Area(hit, rect(x, y, w, h)));
        }
    }

    private boolean hovered(PanelLayout.Rect rect) {
        return rect.contains(cursorX, cursorY);
    }

    /**
     * The largest size not over {@code size} at which {@code text} is guaranteed (by {@link #MAX_ADVANCE}) to fit
     * {@code width} units: a button's focus marks ({@code <text>}) make the label longer, so it must shrink rather
     * than spill past the button's bevel (review Important #1).
     */
    public static float fitSize(String text, float width, float size) {
        return Math.min(size, width / (Math.max(1, text.length()) * MAX_ADVANCE));
    }

    private void label(String text, float x, float y, float w, float h, float size, int colour, Align align) {
        drawing.label(text, rect(x, y, w, h), size * layout.scale(), colour, align);
    }

    private PanelLayout.Rect rect(float x, float y, float w, float h) {
        return layout.rect(x, y, w, h);
    }

    private void progress(float x, float y, float w, float h, float fraction) {
        progress(x, y, w, h, fraction, Drawing.WHITE);
    }

    /** A progress bar tinted {@code tint} (grey on a disabled option row). */
    private void progress(float x, float y, float w, float h, float fraction, int tint) {
        drawing.nineSlice(NineSlice.PROGRESS, rect(x, y, w, h), layout.scale(), tint);
        float inner = w - 2f * BORDER;
        if (fraction > 0f) {
            drawing.sprite("gui.progress_indicator", rect(x + BORDER, y + BORDER, inner * Math.min(1f, fraction),
                    h - 2f * BORDER), tint);
        }
    }

    // ------------------------------------------------------------------ screens

    private void loader() {
        frame(texts.get("loader.caption"));
        progress(MARGIN + 100f, 420f, CONTENT_WIDTH - 200f, 40f, flow.loadProgress());
        label(String.format(Locale.ROOT, "%3d%%", Math.round(flow.loadProgress() * 100f)), MARGIN, 470f,
                CONTENT_WIDTH, 30f, SMALL, Drawing.WHITE, Align.CENTRE);
    }

    private void menu() {
        frame(texts.get("menu.caption"));
        for (int i = 0; i < GameFlow.MENU.size(); i++) {
            Widget widget = GameFlow.MENU.get(i);
            float top = BODY_TOP + i * MENU_ROW;
            group(MARGIN, top, CONTENT_WIDTH, MENU_ROW_HEIGHT);
            button(widget, texts.get(menuKey(widget)), MARGIN + (CONTENT_WIDTH - MENU_BUTTON_WIDTH) / 2f,
                    top + BORDER, MENU_BUTTON_WIDTH, MENU_ROW_HEIGHT - 2f * BORDER, MENU_TEXT);
        }
        group(MARGIN, TICKER_TOP, CONTENT_WIDTH, MENU_ROW_HEIGHT);
        label(flow.ticker().shown(flow.clock()), MARGIN + BORDER + TICKER_MARGIN, TICKER_TOP + BORDER,
                CONTENT_WIDTH - 2f * (BORDER + TICKER_MARGIN), MENU_ROW_HEIGHT - 2f * BORDER, SMALL, Drawing.WHITE,
                Align.LEFT);
    }

    private static String menuKey(Widget widget) {
        return switch (widget) {
            case MENU_PLAY -> "menu.play";
            case MENU_TUTORIAL -> "menu.tutorial";
            case MENU_DESIGNER -> "menu.designer";
            case MENU_OPTIONS -> "menu.options";
            case MENU_HALL_OF_FAME -> "menu.halloffame";
            default -> "menu.quit";
        };
    }

    private void newGame() {
        frame(texts.get("new.caption"));
        body();
        float x = MARGIN + BORDER;
        float y = BODY_TOP + BORDER;
        label(texts.get("new.name"), x, y, FIELD_X, ROW_HEIGHT, BIG, Drawing.GREY, Align.LEFT);
        float fieldX = x + FIELD_X;
        float fieldWidth = CONTENT_WIDTH - 2f * BORDER - FIELD_X;
        drawing.nineSlice(NineSlice.TEXTBOX, rect(fieldX, y, fieldWidth, ROW_HEIGHT), layout.scale(), Drawing.WHITE);
        boolean focused = flow.focus() == Widget.NAME && flow.modal() == Modal.NONE;
        String text = flow.name().text() + (focused && (int) (flow.clock() * 2.0) % 2 == 0 ? CreditsTicker.CURSOR : "");
        label(text, fieldX + 2f * BORDER, y + BORDER, fieldWidth - 4f * BORDER, ROW_HEIGHT - 2f * BORDER, BIG,
                Drawing.BLACK, Align.LEFT);
        areas.add(new Area(Hit.of(Widget.NAME), rect(fieldX, y, fieldWidth, ROW_HEIGHT)));
        bar(List.of(Widget.START, Widget.BACK), List.of(texts.get("new.start"), texts.get("frame.back")));
    }

    /** The Play window (Phase 5c-1 decision 2): column heads, the list, the hint when full, Play, Delete, Back. */
    private void play() {
        PlayModel model = flow.play();
        frame(texts.get("play.caption"));
        body();
        float x = MARGIN + BORDER;
        float textX = x + 3f * BORDER;
        float top = BODY_TOP + BORDER;
        label(texts.get("play.name"), textX, top, PLAY_NAME_WIDTH, HEADER_HEIGHT, SMALL, Drawing.GREY, Align.LEFT);
        label(texts.get("play.score"), textX + PLAY_SCORE_X, top, PLAY_SCORE_WIDTH, HEADER_HEIGHT, SMALL,
                Drawing.GREY, Align.RIGHT);
        label(texts.get("play.group"), textX + PLAY_GROUP_X, top, PLAY_GROUP_WIDTH, HEADER_HEIGHT, SMALL,
                Drawing.GREY, Align.RIGHT);
        float listTop = top + HEADER_HEIGHT;
        float listHeight = PlayModel.ROWS * PLAY_ROW + 2f * BORDER;
        drawing.nineSlice(NineSlice.LISTBOX, rect(x, listTop, LIST_WIDTH, listHeight), layout.scale(),
                Drawing.WHITE);
        ScrollBox box = model.box();
        for (int r = 0; r < box.visible() && box.first() + r < model.rows(); r++) {
            int row = box.first() + r;
            float y = listTop + BORDER + r * PLAY_ROW;
            boolean selected = row == model.selected();
            if (selected) {
                drawing.sprite(SELECTION, rect(x + BORDER, y, LIST_WIDTH - 2f * BORDER, PLAY_ROW), Drawing.WHITE);
            }
            int colour = !model.selectable(row) ? Drawing.GREY : selected ? Drawing.ORANGE : Drawing.BLACK;
            if (row == PlayModel.NEW_PROFILE) {
                cell(texts.get("play.new"), textX, y, PLAY_NAME_WIDTH, colour, Align.LEFT);
            } else {
                Profile profile = model.profiles().get(row - 1);
                cell(profile.name(), textX, y, PLAY_NAME_WIDTH, colour, Align.LEFT);
                cell(String.format(Locale.ROOT, "%06d", profile.score()), textX + PLAY_SCORE_X, y, PLAY_SCORE_WIDTH,
                        colour, Align.RIGHT);
                cell(String.format(Locale.ROOT, "%02d", profile.group() + 1), textX + PLAY_GROUP_X, y,
                        PLAY_GROUP_WIDTH, colour, Align.RIGHT);
            }
            if (model.selectable(row)) {
                areas.add(new Area(new Hit(Widget.LIST, row), rect(x + BORDER, y, LIST_WIDTH - 2f * BORDER,
                        PLAY_ROW)));
            }
        }
        if (box.scrollable()) {
            scrollBar(box, x + LIST_WIDTH + 6f, listTop, listHeight);
        }
        if (!model.newProfileEnabled()) {
            String hint = texts.get("play.full");
            label(hint, x, listTop + listHeight + 4f, LIST_WIDTH, INFO_LINE, fitSize(hint, LIST_WIDTH, SMALL),
                    Drawing.GREY, Align.CENTRE);
        }
        bar(List.of(Widget.START, Widget.DELETE, Widget.BACK), List.of(texts.get("play.start"),
                texts.get("play.delete"), texts.get("frame.back")));
    }

    /** One Play list cell of {@value #PLAY_TEXT}-unit text, shrunk if it would not fit. */
    private void cell(String text, float x, float y, float width, int colour, Align align) {
        label(text, x, y, width, PLAY_ROW, fitSize(text, width, PLAY_TEXT), colour, align);
    }

    private void overview() {
        OverviewModel model = flow.overview();
        frame(model.caption());
        body();
        float x = MARGIN + BORDER;
        float y = BODY_TOP + BORDER;
        float width = CONTENT_WIDTH - 2f * BORDER;
        label(model.scoreText(), x + BORDER, y, width / 2f, HEADER_HEIGHT, SMALL, Drawing.GREY, Align.LEFT);
        label(model.groupText(), x + width / 2f, y, width / 2f - BORDER, HEADER_HEIGHT, SMALL,
                model.completedAll() ? Drawing.GREEN : Drawing.GREY, Align.RIGHT);
        grid(model.cells());
        info(model.infoText(), x, width);
        float half = (width - 2f * BORDER) / 2f;
        button(Widget.PREVIOUS_GROUP, texts.get("overview.group.back"), x, GROUP_BUTTONS_TOP, half,
                GROUP_BUTTON_HEIGHT, SMALL);
        button(Widget.NEXT_GROUP, texts.get("overview.group.forward"), x + half + 2f * BORDER, GROUP_BUTTONS_TOP,
                half, GROUP_BUTTON_HEIGHT, SMALL);
        // Phase 6d decision 2 (plan decision 1): Library left of Play, three buttons as in the Play window's bar
        bar(List.of(Widget.LIBRARY, Widget.PLAY, Widget.BACK), List.of(texts.get("overview.library"),
                texts.get(model.practice() ? "overview.practice" : "overview.play"), texts.get("frame.back")));
    }

    /** The Tutorial (Phase 5c-1 decision 3): the Overview's header, grid and info line; Play and Back. */
    private void tutorial() {
        TutorialModel model = flow.tutorial();
        frame(model.caption());
        body();
        float x = MARGIN + BORDER;
        float y = BODY_TOP + BORDER;
        float width = CONTENT_WIDTH - 2f * BORDER;
        label(model.solvedText(), x + BORDER, y, width / 2f, HEADER_HEIGHT, SMALL,
                model.solvedCount() == TutorialModel.TOWERS ? Drawing.GREEN : Drawing.GREY, Align.LEFT);
        label(model.hintText(), x + width / 2f, y, width / 2f - BORDER, HEADER_HEIGHT, SMALL, Drawing.GREY,
                Align.RIGHT);
        grid(model.cells());
        info(model.infoText(), x, width);
        bar(List.of(Widget.PLAY, Widget.BACK), List.of(texts.get("overview.play"), texts.get("frame.back")));
    }

    /** The selected tower's info line below the grid: wrapped once, then cut with "...". */
    private void info(String text, float x, float width) {
        float infoX = x + 2f * BORDER;
        float infoWidth = width - 4f * BORDER;
        List<String> info = fit(text, maxChars(infoWidth, SMALL));
        float infoY = INFO_TOP + (2 - info.size()) * INFO_LINE / 2f;
        for (String line : info) {
            label(line, infoX, infoY, infoWidth, INFO_LINE, SMALL, Drawing.WHITE, Align.CENTRE);
            infoY += INFO_LINE;
        }
    }

    /** The 3×3 letter grid, centred; every cell is clickable as {@code Hit(GRID, index)}. */
    private void grid(List<OverviewModel.Cell> cells) {
        float left = (StartLayout.WIDTH - GRID_SIZE) / 2f;
        boolean focused = flow.focus() == Widget.GRID && flow.modal() == Modal.NONE;
        for (OverviewModel.Cell cell : cells) {
            float cx = left + cell.column() * (CELL + CELL_GAP);
            float cy = GRID_TOP + cell.row() * (CELL + CELL_GAP);
            drawing.nineSlice(NineSlice.GROUP_INSET, rect(cx, cy, CELL, CELL), layout.scale(), Drawing.WHITE);
            if (cell.selected()) {
                drawing.sprite(SELECTION, rect(cx + BORDER, cy + BORDER, CELL - 2f * BORDER, CELL - 2f * BORDER),
                        focused ? Drawing.WHITE : Drawing.GREY);
            }
            if (cell.state() == OverviewModel.State.EMPTY) {
                // Phase 6d: past an own library's last tower: a sunken cell without a letter, not clickable
                continue;
            }
            int colour = switch (cell.state()) {
                case SOLVED -> Drawing.GREEN;
                case OPEN -> Drawing.WHITE;
                case LOCKED, EMPTY -> Drawing.GREY;
            };
            label(String.valueOf(cell.letter()), cx + BORDER, cy + BORDER, CELL - 2f * BORDER, CELL - 2f * BORDER,
                    BIG, colour, Align.CENTRE);
            areas.add(new Area(new Hit(Widget.GRID, cell.index()), rect(cx, cy, CELL, CELL)));
        }
    }

    private void briefing() {
        BriefingText briefing = flow.briefing();
        frame(texts.get(flow.practice() ? "briefing.caption.practice" : "briefing.caption"));
        body();
        float x = MARGIN + BORDER;
        float width = CONTENT_WIDTH - 2f * BORDER;
        float[] heights = {60f, 60f, 60f, 190f};
        String[] headings = {"briefing.tower", "briefing.author", "briefing.spiders", "briefing.objective"};
        List<List<String>> values = List.of(List.of(briefing.title()), List.of(briefing.author()),
                List.of(briefing.spiders()), briefing.objective());
        float y = BODY_TOP + BORDER;
        for (int i = 0; i < heights.length; i++) {
            group(x, y, width, heights[i]);
            label(texts.get(headings[i]), x, y + BORDER, width, 25f, SMALL, Drawing.GREY, Align.CENTRE);
            List<String> lines = values.get(i);
            for (int line = 0; line < lines.size(); line++) {
                label(lines.get(line), x, y + 30f + line * 25f, width, 25f, SMALL, Drawing.WHITE, Align.CENTRE);
            }
            y += heights[i];
        }
        bar(List.of(Widget.PLAY, Widget.BACK), List.of(texts.get("briefing.play"), texts.get("frame.back")));
        if (flow.skyProgress() < 1f) {
            float barX = MARGIN + 2f * BORDER;
            float barWidth = CONTENT_WIDTH - 2f * BUTTON_WIDTH - 4f * BORDER - 20f;
            progress(barX, BAR_TOP + 2f * BORDER, barWidth, 20f, flow.skyProgress());
            // review Minor #3: "Preparing the sky" (230 units) is wider than this 216-unit box; shrink like a
            // button label rather than overlap the bar group's border.
            String loading = texts.get("briefing.loading");
            label(loading, barX, BAR_TOP + 30f, barWidth, 25f, fitSize(loading, barWidth, SMALL), Drawing.WHITE,
                    Align.CENTRE);
        }
    }

    /**
     * The result window (Phase 5c-2 decision 9): the logo on top, then the free band for the interlude, the caption
     * group at {@link #RESULT_TOP}, the body (the reason, the score lines in two columns, the all-solved lines) and
     * the button bar.
     */
    private void result() {
        ResultText result = flow.result();
        List<String> allSolved = flow.allSolvedLines();
        float bodyHeight = RESULT_BODY + allSolved.size() * INFO_LINE;
        float bodyTop = BAR_TOP - 4f - bodyHeight;
        float captionTop = bodyTop - RESULT_CAPTION;
        drawing.sprite(TITLE, rect(MARGIN, TITLE_TOP, CONTENT_WIDTH, TITLE_HEIGHT), Drawing.WHITE);
        group(MARGIN, captionTop, CONTENT_WIDTH, RESULT_CAPTION);
        label(result.caption(), MARGIN + BORDER, captionTop + BORDER, CONTENT_WIDTH - 2f * BORDER,
                RESULT_CAPTION - 2f * BORDER, fitSize(result.caption(), CONTENT_WIDTH - 2f * BORDER, BIG),
                Drawing.WHITE, Align.CENTRE);
        group(MARGIN, bodyTop, CONTENT_WIDTH, bodyHeight);
        float x = MARGIN + BORDER;
        float width = CONTENT_WIDTH - 2f * BORDER;
        label(result.reason(), x, bodyTop + BORDER, width, RESULT_REASON,
                fitSize(result.reason(), width, RESULT_REASON_TEXT), Drawing.WHITE, Align.CENTRE);
        float top = bodyTop + BORDER + RESULT_REASON + 3f;
        int columns = result.score().size() > 1 ? 2 : 1;
        float column = width / columns;
        float labelWidth = column * 0.6f - 20f;
        float valueWidth = column * 0.4f - 20f;
        for (int i = 0; i < result.score().size(); i++) {
            ResultText.Line line = result.score().get(i);
            float left = x + (i % columns) * column;
            float y = top + (i / columns) * RESULT_LINE;
            label(line.label(), left + 20f, y, labelWidth, RESULT_LINE, fitSize(line.label(), labelWidth, RESULT_TEXT),
                    Drawing.GREY, Align.LEFT);
            label(line.value(), left + 20f + labelWidth, y, valueWidth, RESULT_LINE,
                    fitSize(line.value(), valueWidth, RESULT_TEXT), Drawing.WHITE, Align.RIGHT);
        }
        float y = top + 2f * RESULT_LINE + 3f;
        for (String text : allSolved) {
            label(text, x, y, width, INFO_LINE, fitSize(text, width, SMALL), Drawing.WHITE, Align.CENTRE);
            y += INFO_LINE;
        }
        List<Widget> buttons = flow.resultButtons();
        List<String> labels = new ArrayList<>();
        for (Widget widget : buttons) {
            labels.add(widget == Widget.RETRY ? texts.get("result.retry")
                    : texts.get(flow.isDeveloperSession() ? "result.menu" : "result.overview"));
        }
        bar(buttons, labels);
    }

    // ------------------------------------------------------------------ Phase 5b: Options; the Hall of Fame

    /** The Options window (Phase 5b decision 1): the tab bar, the current tab's rows or key list, Apply and Back. */
    private void options() {
        OptionsModel model = flow.options();
        boolean keyboard = flow.modal() == Modal.NONE;
        frame(texts.get("options.caption"));
        body();
        for (OptionsModel.Tab tab : OptionsModel.Tab.values()) {
            boolean active = tab == model.tab();
            widget(new Hit(Widget.TAB, tab.ordinal()), texts.get(tab.textKey()),
                    MARGIN + BORDER + tab.ordinal() * (TAB_WIDTH + TAB_GAP), TAB_TOP, TAB_WIDTH, TAB_HEIGHT,
                    OPTION_TEXT, keyboard && active && model.focus() == OptionsModel.Focus.TABS, true,
                    active ? NineSlice.BUTTON_INSET : NineSlice.BUTTON);
        }
        // Author's extra (2026-10-14): optionRows draws every tab's own option rows, including CONTROLS's single
        // "Hide Mouse Cursor" switch; on CONTROLS the key list is drawn below it.
        optionRows(model, keyboard);
        if (model.tab() == OptionsModel.Tab.CONTROLS) {
            keyList(model, keyboard);
        }
        group(MARGIN, BAR_TOP, CONTENT_WIDTH, ROW_HEIGHT);
        float x = MARGIN + CONTENT_WIDTH - BORDER - 2f * BUTTON_WIDTH - 10f;
        widget(Hit.of(Widget.APPLY), texts.get("options.apply"), x, BAR_TOP + BORDER, BUTTON_WIDTH,
                ROW_HEIGHT - 2f * BORDER, BIG, keyboard && model.focus() == OptionsModel.Focus.APPLY, true,
                NineSlice.BUTTON);
        widget(Hit.of(Widget.BACK), texts.get("frame.back"), x + BUTTON_WIDTH + 10f, BAR_TOP + BORDER, BUTTON_WIDTH,
                ROW_HEIGHT - 2f * BORDER, BIG, keyboard && model.focus() == OptionsModel.Focus.BACK, true,
                NineSlice.BUTTON);
    }

    /** The height of {@code tab}'s option rows (Phase 5e: VIDEO's seven are lower). */
    public static float optionRow(OptionsModel.Tab tab) {
        return tab == OptionsModel.Tab.VIDEO ? VIDEO_OPTION_ROW : OPTION_ROW;
    }

    /** One row per option: the label, then a switch button, or {@code -} / bar or text / {@code +}. */
    private void optionRows(OptionsModel model, boolean keyboard) {
        List<OptionsModel.Option> options = model.options();
        float row = optionRow(model.tab());
        for (int i = 0; i < options.size(); i++) {
            OptionsModel.Option option = options.get(i);
            float y = OPTION_TOP + i * row;
            // Author's extra (2026-10-14): on CONTROLS, model.row() may still hold the key list's selected index
            // while the switch itself has the focus (or vice versa); controlsListFocused() tells them apart.
            boolean focused = keyboard && model.focus() == OptionsModel.Focus.ROWS && model.row() == i
                    && !model.controlsListFocused();
            String name = model.label(option, texts);
            label(name, MARGIN + 2f * BORDER, y, LABEL_WIDTH, row, fitSize(name, LABEL_WIDTH, OPTION_TEXT),
                    focused ? Drawing.WHITE : Drawing.GREY, Align.LEFT);
            float top = y + 5f;
            float height = row - 10f;
            String value = model.value(option, texts);
            // M4 (final review): with -Dtob.volume set, Master has no audible effect; Phase 7c: without an audio
            // device no SOUND row has; such a row shows disabled
            boolean enabled = model.enabled(option);
            // fix wave (Task 3-4 review #6): the value and the bar of a disabled row are grey as well
            int valueColour = enabled ? Drawing.WHITE : Drawing.GREY;
            if (option.kind() == OptionsModel.Kind.SWITCH) {
                widget(new Hit(Widget.OPTION_SWITCH, option.ordinal()), value, CONTROL_X, top, SWITCH_WIDTH, height,
                        OPTION_TEXT, focused, enabled, NineSlice.BUTTON);
                continue;
            }
            widget(new Hit(Widget.OPTION_MINUS, option.ordinal()), "-", CONTROL_X, top, STEP_BUTTON, height,
                    OPTION_TEXT, false, enabled, NineSlice.BUTTON);
            float middle = CONTROL_X + STEP_BUTTON + 6f;
            if (option.kind() == OptionsModel.Kind.PERCENT) {
                progress(middle, top, BAR_WIDTH, height, model.fraction(option), valueColour);
                float valueX = middle + BAR_WIDTH + STEP_BUTTON + 12f;
                float valueWidth = MARGIN + CONTENT_WIDTH - BORDER - valueX;
                label(value, valueX, y, valueWidth, row, fitSize(value, valueWidth, OPTION_TEXT),
                        valueColour, Align.LEFT);
            } else {
                drawing.nineSlice(NineSlice.GROUP_INSET, rect(middle, top, BAR_WIDTH, height), layout.scale(),
                        valueColour);
                label(value, middle + BORDER, top, BAR_WIDTH - 2f * BORDER, height,
                        fitSize(value, BAR_WIDTH - 2f * BORDER, OPTION_TEXT), valueColour, Align.CENTRE);
            }
            widget(new Hit(Widget.OPTION_PLUS, option.ordinal()), "+", middle + BAR_WIDTH + 6f, top, STEP_BUTTON,
                    height, OPTION_TEXT, false, enabled, NineSlice.BUTTON);
        }
        // Phase 7c: "No audio device found" below the SOUND rows
        float hintTop = OPTION_TOP + options.size() * row;
        model.hint(texts).ifPresent(hint -> label(hint, MARGIN + 2f * BORDER, hintTop, CONTENT_WIDTH - 4f * BORDER,
                row, fitSize(hint, CONTENT_WIDTH - 4f * BORDER, OPTION_TEXT), Drawing.ORANGE, Align.LEFT));
    }

    /**
     * The CONTROLS tab's key list, below its "Hide Mouse Cursor" switch ({@code optionRows}, author's extra
     * 2026-10-14): the visible actions with their keys (fixed ones grey), the scroll bar and Reset to Defaults.
     */
    private void keyList(OptionsModel model, boolean keyboard) {
        float x = MARGIN + BORDER;
        float top = CONTROLS_LIST_TOP;
        float height = OptionsModel.KEY_ROWS * OPTION_ROW;
        drawing.nineSlice(NineSlice.GROUP_INSET, rect(x, top, LIST_WIDTH, height), layout.scale(), Drawing.WHITE);
        ScrollBox box = model.keyList();
        boolean rows = keyboard && model.focus() == OptionsModel.Focus.ROWS && model.controlsListFocused();
        for (int r = 0; r < box.visible() && box.first() + r < model.actions().size(); r++) {
            int index = box.first() + r;
            GameAction action = model.actions().get(index);
            float y = top + r * OPTION_ROW;
            boolean selected = rows && index == model.row();
            if (selected) {
                drawing.sprite(SELECTION, rect(x + BORDER, y + BORDER, LIST_WIDTH - 2f * BORDER,
                        OPTION_ROW - 2f * BORDER), Drawing.WHITE);
            }
            int colour = model.fixed(action) ? Drawing.GREY : Drawing.WHITE;
            String name = texts.get("action." + action.name());
            label(name, x + 3f * BORDER, y, ACTION_WIDTH, OPTION_ROW, fitSize(name, ACTION_WIDTH, OPTION_TEXT), colour,
                    Align.LEFT);
            String key = model.keyText(index, texts);
            float keyX = x + 3f * BORDER + ACTION_WIDTH + 10f;
            float keyWidth = LIST_WIDTH - (keyX - x) - 3f * BORDER;
            label(key, keyX, y, keyWidth, OPTION_ROW, fitSize(key, keyWidth, OPTION_TEXT),
                    selected && model.capturing() ? Drawing.ORANGE : colour, Align.RIGHT);
            areas.add(new Area(new Hit(Widget.KEY_ROW, index), rect(x, y, LIST_WIDTH, OPTION_ROW)));
        }
        scrollBar(box, x + LIST_WIDTH + 6f, top, height);
        widget(Hit.of(Widget.KEYS_RESET), texts.get("options.keys.reset"), x, top + height + 8f, 320f,
                TAB_HEIGHT, OPTION_TEXT, keyboard && model.focus() == OptionsModel.Focus.RESET, true,
                NineSlice.BUTTON);
    }

    /** A scroll bar: the progress skin as the track, a raised button as the thumb; the track is clickable. */
    private void scrollBar(ScrollBox box, float x, float y, float height) {
        drawing.nineSlice(NineSlice.PROGRESS, rect(x, y, SCROLL_WIDTH, height), layout.scale(), Drawing.WHITE);
        float inner = height - 2f * BORDER;
        float length = Math.min(inner, Math.max(2f * SCROLL_WIDTH / 3f, box.thumbLength() * inner));
        float top = y + BORDER + box.thumbStart() * inner;
        drawing.nineSlice(NineSlice.BUTTON, rect(x, Math.min(top, y + BORDER + inner - length), SCROLL_WIDTH,
                length), layout.scale(), Drawing.WHITE);
        areas.add(new Area(Hit.of(Widget.SCROLL_TRACK), rect(x, y, SCROLL_WIDTH, height)));
    }

    /** The Hall of Fame (Phase 5c-1 decision 4): the tabs, the shown tab's box with its scroll bar, Back. */
    private void hallOfFame() {
        HallOfFame hall = flow.hallOfFame();
        frame(texts.get("halloffame.caption"));
        body();
        for (HallOfFame.Tab tab : HallOfFame.Tab.values()) {
            widget(new Hit(Widget.TAB, tab.ordinal()), texts.get(tab.textKey()),
                    MARGIN + BORDER + tab.ordinal() * (HALL_TAB_WIDTH + TAB_GAP), TAB_TOP, HALL_TAB_WIDTH, TAB_HEIGHT,
                    OPTION_TEXT, false, true, tab == hall.tab() ? NineSlice.BUTTON_INSET : NineSlice.BUTTON);
        }
        float x = MARGIN + BORDER;
        drawing.nineSlice(NineSlice.GROUP_INSET, rect(x, HALL_LIST_TOP, LIST_WIDTH, HALL_LIST_HEIGHT), layout.scale(),
                Drawing.WHITE);
        if (hall.tab() == HallOfFame.Tab.SCORES) {
            scores(hall, x + 3f * BORDER);
        } else {
            creditLines(hall, x);
        }
        scrollBar(hall.box(), x + LIST_WIDTH + 6f, HALL_LIST_TOP, HALL_LIST_HEIGHT);
        bar(List.of(Widget.BACK), List.of(texts.get("frame.back")));
    }

    /** SCORES: the column heads in grey, then the visible ranked rows (rank and name left, the numbers right). */
    private void scores(HallOfFame hall, float textX) {
        float y = HALL_LIST_TOP + BORDER;
        for (int c = 0; c < SCORE_HEADS.length; c++) {
            String head = texts.get(SCORE_HEADS[c]);
            label(head, textX + SCORE_X[c], y, SCORE_WIDTH[c], HEADER_HEIGHT, fitSize(head, SCORE_WIDTH[c], SMALL),
                    Drawing.GREY, c < 2 ? Align.LEFT : Align.RIGHT);
        }
        y += HEADER_HEIGHT;
        if (hall.entries().isEmpty()) {
            String none = texts.get("halloffame.empty");
            label(none, textX, y, LIST_WIDTH - 6f * BORDER, SCORE_ROW, OPTION_TEXT, Drawing.GREY, Align.CENTRE);
            return;
        }
        ScrollBox box = hall.scores();
        for (int r = 0; r < box.visible() && box.first() + r < hall.entries().size(); r++) {
            HallOfFame.Entry entry = hall.entries().get(box.first() + r);
            // author decision 2026-10-15: the group is displayed 1-based ("01".."13"), as the Play rows and the
            // Overview; entry.group() itself stays the model's 0-based value.
            String[] cells = {entry.rank() + ".", entry.name(), String.format(Locale.ROOT, "%06d", entry.score()),
                Integer.toString(entry.solved()), String.format(Locale.ROOT, "%02d", entry.group() + 1)};
            for (int c = 0; c < cells.length; c++) {
                label(cells[c], textX + SCORE_X[c], y + r * SCORE_ROW, SCORE_WIDTH[c], SCORE_ROW,
                        fitSize(cells[c], SCORE_WIDTH[c], OPTION_TEXT), Drawing.WHITE,
                        c < 2 ? Align.LEFT : Align.RIGHT);
            }
        }
    }

    /** CREDITS (Phase 5b decision 8): the visible text lines, headings grey. */
    private void creditLines(HallOfFame hall, float x) {
        ScrollBox box = hall.credits();
        List<String> lines = hall.creditLines();
        float width = LIST_WIDTH - 6f * BORDER;
        for (int r = 0; r < box.visible() && box.first() + r < lines.size(); r++) {
            String line = lines.get(box.first() + r);
            label(line, x + 3f * BORDER, HALL_LIST_TOP + 2f * BORDER + r * LINE, width, LINE,
                    fitSize(line, width, SMALL), line.endsWith(":") ? Drawing.GREY : Drawing.WHITE, Align.CENTRE);
        }
    }

    // ------------------------------------------------------------------ modal dialog

    /**
     * ToB 2009's {@code MessageDialog} without icon, as the pause menu's ARE YOU SURE? ({@link ConfirmDialogLayout}):
     * the window skin with the caption, a raised group holding a sunken group with the text, and a raised group with
     * the answers (96 × 32) at its right end; centred on the virtual screen.
     */
    private void modal() {
        boolean message = flow.modal() == Modal.MESSAGE;
        float width = message ? 600f : ConfirmDialogLayout.WIDTH;
        float b = ConfirmDialogLayout.BORDER;
        float g = ConfirmDialogLayout.GROUP_BORDER;
        float caption = ConfirmDialogLayout.CAPTION_HEIGHT;
        float textWidth = width - 2f * (b + 2f * g);
        // Important #2: a long save-error message (an IOException's path) must wrap within the text area instead of
        // running off the dialog; fit()/maxChars() give the same two-lines-then-"..." rule as the Overview's info
        // line, with a hard break for a long token without spaces.
        List<String> textLines = message ? fit(flow.message(), maxChars(textWidth, SMALL))
                : List.of(texts.get("confirm.question"));
        float textHeight = textLines.size() * ConfirmDialogLayout.TEXT_LINE;
        float messageGroupHeight = 2f * g + 2f * ConfirmDialogLayout.PADDING + textHeight;
        float areaHeight = messageGroupHeight + 2f * g;
        float height = caption + areaHeight + ConfirmDialogLayout.BUTTON_AREA_HEIGHT + b;
        float left = (StartLayout.WIDTH - width) / 2f;
        float top = (StartLayout.HEIGHT - height) / 2f;
        if (flow.screen() == GameFlow.Screen.EDITOR) {
            // the window background is translucent: over the editor the question covers the grid fully
            drawing.sprite(EditorScreens.OPAQUE, rect(left, top, width, height), Drawing.BLACK);
        }
        drawing.nineSlice(NineSlice.WINDOW, rect(left, top, width, height), layout.scale(), Drawing.WHITE);
        String title = switch (flow.modal()) {
            case CONFIRM_QUIT -> texts.get("confirm.quit");
            case CONFIRM_DELETE -> texts.get("confirm.delete");
            case CONFIRM_RESET_KEYS -> texts.get("confirm.reset");
            case EDITOR_CONFIRM -> flow.editorQuestion();
            case MESSAGE -> texts.get(flow.messageCaption());
            default -> texts.get("frame.error");
        };
        label(title, left + b, top + ConfirmDialogLayout.CAPTION_FACE_TOP, width - 2f * b,
                ConfirmDialogLayout.CAPTION_FACE_BOTTOM - ConfirmDialogLayout.CAPTION_FACE_TOP, SMALL, Drawing.WHITE,
                Align.LEFT);
        drawing.nineSlice(NineSlice.GROUP_OUTSET, rect(left + b, top + caption, width - 2f * b, areaHeight),
                layout.scale(), Drawing.WHITE);
        drawing.nineSlice(NineSlice.GROUP_INSET, rect(left + b + g, top + caption + g, width - 2f * (b + g),
                messageGroupHeight), layout.scale(), Drawing.WHITE);
        float textTop = top + caption + 2f * g + ConfirmDialogLayout.PADDING;
        for (int i = 0; i < textLines.size(); i++) {
            label(textLines.get(i), left + b + 2f * g, textTop + i * ConfirmDialogLayout.TEXT_LINE, textWidth,
                    ConfirmDialogLayout.TEXT_LINE, SMALL, Drawing.WHITE, Align.CENTRE);
        }
        float barTop = top + caption + areaHeight;
        drawing.nineSlice(NineSlice.GROUP_OUTSET, rect(left + b, barTop, width - 2f * b,
                ConfirmDialogLayout.BUTTON_AREA_HEIGHT), layout.scale(), Drawing.WHITE);
        List<Widget> answers = message ? List.of(Widget.OK) : List.of(Widget.YES, Widget.NO);
        float bx = left + width - b - g - answers.size() * ConfirmDialogLayout.BUTTON_WIDTH;
        for (Widget answer : answers) {
            boolean focused = message || (answer == Widget.YES) == flow.modalYes();
            String text = texts.get(switch (answer) {
                case YES -> "confirm.yes";
                case NO -> "confirm.no";
                default -> "frame.ok";
            });
            String shown = focused ? "<" + text + ">" : text;
            float labelWidth = ConfirmDialogLayout.BUTTON_WIDTH - 2f * ConfirmDialogLayout.BUTTON_BORDER;
            PanelLayout.Rect buttonRect = rect(bx, barTop + g, ConfirmDialogLayout.BUTTON_WIDTH,
                    ConfirmDialogLayout.BUTTON_HEIGHT);
            drawing.nineSlice(NineSlice.BUTTON, buttonRect, layout.scale(),
                    hovered(buttonRect) ? HOVER : Drawing.WHITE);
            label(shown, bx + ConfirmDialogLayout.BUTTON_BORDER, barTop + g + ConfirmDialogLayout.BUTTON_BORDER,
                    labelWidth, ConfirmDialogLayout.BUTTON_HEIGHT - 2f * ConfirmDialogLayout.BUTTON_BORDER,
                    fitSize(shown, labelWidth, SMALL), Drawing.BLACK, Align.CENTRE);
            areas.add(new Area(Hit.of(answer), buttonRect));
            bx += ConfirmDialogLayout.BUTTON_WIDTH;
        }
    }
}
