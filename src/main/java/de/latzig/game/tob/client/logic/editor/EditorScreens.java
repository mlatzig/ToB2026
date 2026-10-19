package de.latzig.game.tob.client.logic.editor;

import java.util.ArrayList;
import java.util.List;

import de.latzig.game.tob.client.logic.Texts;
import de.latzig.game.tob.client.logic.editor.TowerDesigner.Command;
import de.latzig.game.tob.client.logic.editor.TowerDesigner.Menu;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Hit;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Widget;
import de.latzig.game.tob.client.logic.frontend.StartScreens;
import de.latzig.game.tob.client.logic.frontend.StartScreens.Area;
import de.latzig.game.tob.client.logic.game.ConfirmDialogLayout;
import de.latzig.game.tob.client.logic.game.PanelLayout;
import de.latzig.game.tob.client.logic.ui.CreditsTicker;
import de.latzig.game.tob.client.logic.ui.Drawing;
import de.latzig.game.tob.client.logic.ui.Drawing.Align;
import de.latzig.game.tob.client.logic.ui.NineSlice;
import de.latzig.game.tob.client.logic.ui.ScrollBox;
import de.latzig.game.tob.client.logic.ui.StartLayout;
import de.latzig.game.tob.core.model.BaseType;
import de.latzig.game.tob.core.model.Cell;
import de.latzig.game.tob.core.model.TowerGrid;
import de.latzig.game.tob.core.sim.TowerCheck;

/**
 * The Tower Designer's screen in ToB 2009's layout (Phase 6b spec decision 2; {@code EditorWindow.initControls},
 * {@code J:editor/EditorWindow.java:271-403}), drawn on black into a virtual screen 768 high whose width follows the
 * window's aspect ratio from 1024 (4:3, ToB 2009's screen) up to {@link #MAX_WIDTH} (21:9), centred between bars
 * beyond that ({@link StartLayout#wide}; acceptance change 1 of Phase 6b-1): the menu bar (24 high, the full
 * width); the library list on the left (300 wide); the title strip ({@code 001: Title (Author)}, 35 high) beside it
 * to the right edge; the current level's 16 × 16 grid below the title strip with the coordinates and the status line,
 * the grid square at ToB 2009's size and centred in the space between the list and the palette, its group widened
 * to fill that space; the palette column on the right at ToB 2009's width — the four base elements in the first row,
 * the 36 objects packed below without ToB 2009's blank row, the selected element's name as the column's caption
 * (acceptance change 2), and below them down to the column's bottom the 3D preview ({@link #PREVIEW}, a
 * {@link Drawing.Viewport}; Phase 6b-2: on the full screen it covers the whole window); the strip of the six levels
 * at the bottom, the six sharing the full width, each level's map at ToB 2009's size centred in its (wider) group.
 * Dialogs
 * keep ToB 2009's size, centred on the window; a menu opens below its title. A grid cell is ToB 2009's stack of
 * sprites (base, object at 80 %, the pulsing cursor); every cell the rule pass marks gets a red (error) or yellow
 * (hint) frame. The open menu, the library and Toolbox dialogs and the flow's questions draw on top; while a
 * dialog is open only it can be clicked. Phase 6b-2: the Settings and Effects dialogs ({@link #towerDialog}) in ToB
 * 2009's look — captioned groups, a label column and the controls (text boxes, number boxes and choices with − and
 * +, switches, colour buttons) — stand centred in the space left of the palette column, so that the 3D preview
 * beside them shows their values at once; the colour dialog opens over them.
 */
public final class EditorScreens {

    public static final float MENU_HEIGHT = 24f;
    public static final float MENU_TITLE_WIDTH = 110f;
    public static final float MENU_WIDTH = 240f;
    public static final float MENU_ITEM = 24f;
    public static final float MENU_SEPARATOR = 6f;
    public static final float LIBRARY_WIDTH = 300f;
    public static final float TITLE_HEIGHT = 35f;
    /** A group's caption strip (ToB 2009's {@code GROUP_*_HEIGHT_OFFSET}). */
    public static final float CAPTION = 30f;
    public static final float BORDER = StartScreens.BORDER;
    public static final float CLIENT_HEIGHT = StartLayout.HEIGHT - MENU_HEIGHT;
    /** The widest virtual screen, 21:9 at the height 768; a wider window gets bars. */
    public static final float MAX_WIDTH = StartLayout.HEIGHT * 21f / 9f;
    /** A level's group at 4:3, ToB 2009's (wider screens widen the groups, the level's map keeps this size). */
    public static final float LEVEL_WIDTH = StartLayout.WIDTH / TowerGrid.LEVELS;
    public static final float LEVEL_HEIGHT = LEVEL_WIDTH + CAPTION;
    public static final float LEVELS_TOP = MENU_HEIGHT + CLIENT_HEIGHT - LEVEL_HEIGHT;
    public static final float TOWER_TOP = MENU_HEIGHT + TITLE_HEIGHT;
    public static final float TOWER_HEIGHT = CLIENT_HEIGHT - LEVEL_HEIGHT - TITLE_HEIGHT;
    /** The tower group's width at 4:3 (wider screens widen the group; the grid keeps this size). */
    public static final float TOWER_WIDTH = TOWER_HEIGHT - CAPTION;
    /** The palette column's left edge at 4:3; it is {@link #ELEMENTS_WIDTH} from the right edge on every screen. */
    public static final float ELEMENTS_LEFT = LIBRARY_WIDTH + TOWER_WIDTH;
    public static final float ELEMENTS_WIDTH = StartLayout.WIDTH - ELEMENTS_LEFT;
    /** ToB 2009's palette: six entries per row. */
    public static final int PER_ROW = 6;
    public static final float ENTRY = (ELEMENTS_WIDTH - 2f * BORDER) / PER_ROW;
    public static final float PALETTE_TOP = TOWER_TOP + BORDER + CAPTION;
    public static final int PALETTE_ROWS = 1 + (EditorElement.PALETTE.size() - EditorElement.BASE_COUNT + PER_ROW - 1)
            / PER_ROW;
    /**
     * The reserved area of 6b-2's 3D preview (spec decisions 2 and 3): the palette area's lower part, from the
     * packed palette down to the column's bottom (acceptance change 2: the element's name is the caption). Virtual
     * units of the 4:3 screen, origin top left; on a wider screen it moves right with the palette column
     * ({@link #preview}).
     */
    public static final PanelLayout.Rect PREVIEW = new PanelLayout.Rect(ELEMENTS_LEFT + BORDER,
            PALETTE_TOP + PALETTE_ROWS * ENTRY + BORDER, ELEMENTS_WIDTH - 2f * BORDER,
            TOWER_TOP + TOWER_HEIGHT - 2f * BORDER - (PALETTE_TOP + PALETTE_ROWS * ENTRY + BORDER));
    public static final float LIST_ROW = 20f;
    public static final float LIST_TEXT = 18f;
    public static final float TEXT = StartScreens.SMALL;
    /** ToB 2009's cursor colours ({@code Colors.DARK_RED} to {@code WHITE}, {@code sin(5 t)}), in 8 steps. */
    public static final int DARK_RED = 0x8B0000;
    private static final int PULSE_STEPS = 8;
    /** ToB 2009's menu skin ({@code gui.menu_*}, border {@code menu_border_width} = 5). */
    public static final NineSlice MENU = new NineSlice(List.of("gui.menu_left_top", "gui.menu_mid_top",
            "gui.menu_right_top", "gui.menu_left_mid", "gui.menu_mid", "gui.menu_right_mid", "gui.menu_left_bottom",
            "gui.menu_mid_bottom", "gui.menu_right_bottom"), 5f, 5f, 5f, 5f);
    /** The library dialog's size and the Toolbox dialog's. */
    public static final float LIBRARY_DIALOG_WIDTH = 560f;
    public static final float LIBRARY_DIALOG_HEIGHT = 420f;
    public static final float TOOLBOX_DIALOG_WIDTH = 560f;
    public static final float TOOLBOX_DIALOG_HEIGHT = 230f;
    public static final float DIALOG_BUTTON = 120f;
    /** Phase 6b-2: the Settings and Effects dialogs' width (ToB 2009's {@code DIALOG_WIDTH}), the colour dialog's. */
    public static final float FORM_DIALOG_WIDTH = 600f;
    public static final float COLOUR_DIALOG_WIDTH = 400f;
    /** A form's row pitch, a control's height, a group's caption strip. */
    public static final float FORM_ROW = 34f;
    public static final float FORM_CONTROL = 30f;
    public static final float FORM_CAPTION = 26f;
    /** The label column (ToB 2009's {@code GridLayout("200,20,380")}; the colour dialog's {@code "100,20,180"}). */
    public static final float FORM_LABEL = 200f;
    public static final float COLOUR_LABEL = 100f;
    public static final float FORM_GAP = 20f;
    /** The − and + buttons, a number box, a colour button (ToB 2009's {@code COLOR_WIDTH}), Randomize Colors. */
    public static final float STEP_BUTTON = 36f;
    public static final float NUMBER_BOX = 70f;
    public static final float SWATCH = 100f;
    public static final float RANDOMIZE_WIDTH = 230f;
    /**
     * An opaque GUI atlas region (ToB 2009's list box background), tinted black: the screen's background, the black
     * cell areas of the grid and the level maps, and the backing that makes a dialog cover what lies under it (ToB
     * 2009's window background is translucent).
     */
    public static final String OPAQUE = "gui.listbox_background";

    private final TowerDesigner designer;
    private final EditorModel model;
    private final Texts texts;
    private final StartLayout layout;
    /** The virtual screen's width, the palette column's left edge, the space left of it, a level's group width. */
    private final float screenWidth;
    private final float elementsLeft;
    private final float centreWidth;
    private final float levelWidth;
    private final Drawing.Builder drawing;
    private final List<Area> areas;
    private final float cursorX;
    private final float cursorY;
    private final double clock;

    private EditorScreens(TowerDesigner designer, Texts texts, StartLayout layout, Drawing.Builder drawing,
            List<Area> areas, float cursorX, float cursorY, double clock) {
        this.designer = designer;
        this.model = designer.model();
        this.texts = texts;
        this.layout = editorLayout(layout);
        this.screenWidth = this.layout.virtualWidth();
        this.elementsLeft = screenWidth - ELEMENTS_WIDTH;
        this.centreWidth = elementsLeft - LIBRARY_WIDTH;
        this.levelWidth = screenWidth / TowerGrid.LEVELS;
        this.drawing = drawing;
        this.areas = areas;
        this.cursorX = cursorX;
        this.cursorY = cursorY;
        this.clock = clock;
    }

    /** The designer's screen alone (tests); the start screens add the software cursor and the flow's questions. */
    public static StartScreens.Frame draw(TowerDesigner designer, Texts texts, StartLayout layout, float cursorX,
            float cursorY, double clock) {
        Drawing.Builder drawing = new Drawing.Builder();
        List<Area> areas = new ArrayList<>();
        draw(designer, texts, layout, drawing, areas, cursorX, cursorY, clock);
        return new StartScreens.Frame(drawing.build(), areas);
    }

    public static void draw(TowerDesigner designer, Texts texts, StartLayout layout, Drawing.Builder drawing,
            List<Area> areas, float cursorX, float cursorY, double clock) {
        new EditorScreens(designer, texts, layout, drawing, areas, cursorX, cursorY, clock).screen();
    }

    /** The editor's layout for {@code layout}'s window: as wide as the window's aspect ratio allows. */
    static StartLayout editorLayout(StartLayout layout) {
        return StartLayout.wide(layout.width(), layout.height(), MAX_WIDTH);
    }

    /** The screen rectangle of 6b-2's preview area in {@code layout}'s window. */
    public static PanelLayout.Rect preview(StartLayout layout) {
        StartLayout editor = editorLayout(layout);
        float shift = editor.virtualWidth() - StartLayout.WIDTH;
        return editor.rect(PREVIEW.x() + shift, PREVIEW.y(), PREVIEW.width(), PREVIEW.height());
    }

    /** The cursor's pulsing tint at {@code clock} seconds, from ToB 2009's dark red to white in eight steps. */
    public static int pulse(double clock) {
        double t = Math.round((0.5 + 0.5 * Math.sin(5.0 * clock)) * PULSE_STEPS) / (double) PULSE_STEPS;
        int r = (int) Math.round(((DARK_RED >> 16) & 0xFF) + (0xFF - ((DARK_RED >> 16) & 0xFF)) * t);
        int g = (int) Math.round(0xFF * t);
        return r << 16 | g << 8 | g;
    }

    private void screen() {
        drawing.sprite(OPAQUE, layout.window(), Drawing.BLACK);
        menuBar();
        libraryList();
        titleStrip();
        grid();
        palette();
        levels();
        designer.menu().ifPresent(this::dropDown);
        designer.libraryDialog().ifPresent(this::libraryDialog);
        designer.toolboxDialog().ifPresent(this::toolboxDialog);
        designer.towerDialog().ifPresent(this::towerDialog);
        designer.colourDialog().ifPresent(this::colourDialog);
        if (designer.preview().maximized()) {
            // spec decision 3: the preview on the full screen, the only thing to click
            areas.clear();
            drawing.viewport(layout.window());
            areas.add(new Area(Hit.of(Widget.EDITOR_PREVIEW), layout.window()));
        }
    }

    // ------------------------------------------------------------------ helpers

    private PanelLayout.Rect rect(float x, float y, float w, float h) {
        return layout.rect(x, y, w, h);
    }

    private void label(String text, float x, float y, float w, float h, float size, int colour, Align align) {
        drawing.label(text, rect(x, y, w, h), StartScreens.fitSize(text, w, size) * layout.scale(), colour, align);
    }

    private void group(float x, float y, float w, float h, boolean inset) {
        drawing.nineSlice(inset ? NineSlice.GROUP_INSET : NineSlice.GROUP_OUTSET, rect(x, y, w, h), layout.scale(),
                Drawing.WHITE);
    }

    /** A group's inset caption strip with {@code text}. */
    private void caption(String text, float x, float y, float w, int colour) {
        group(x + BORDER, y + BORDER, w - 2f * BORDER, CAPTION - BORDER, true);
        label(text, x + 2f * BORDER, y + BORDER, w - 4f * BORDER, CAPTION - BORDER, TEXT, colour, Align.LEFT);
    }

    private void area(Hit hit, float x, float y, float w, float h) {
        areas.add(new Area(hit, rect(x, y, w, h)));
    }

    /** {@code text} cut with "..." to what surely fits {@code width} at {@code size} (one size for every row). */
    static String cut(String text, float width, float size) {
        int max = StartScreens.maxChars(width, size);
        return text.length() <= max ? text : text.substring(0, Math.max(0, max - 3)).stripTrailing() + "...";
    }

    private boolean hovered(float x, float y, float w, float h) {
        return rect(x, y, w, h).contains(cursorX, cursorY);
    }

    /** A button: raised (or pressed when {@code chosen}), its text black (grey when disabled); clickable if enabled. */
    private void button(Hit hit, String text, float x, float y, float w, float h, boolean chosen, boolean enabled) {
        int tint = !enabled ? Drawing.GREY : hovered(x, y, w, h) ? StartScreens.HOVER : Drawing.WHITE;
        drawing.nineSlice(chosen ? NineSlice.BUTTON_INSET : NineSlice.BUTTON, rect(x, y, w, h), layout.scale(), tint);
        label(text, x + BORDER, y + BORDER, w - 2f * BORDER, h - 2f * BORDER, TEXT,
                enabled ? Drawing.BLACK : Drawing.GREY, Align.CENTRE);
        if (enabled) {
            area(hit, x, y, w, h);
        }
    }

    // ------------------------------------------------------------------ the parts of the screen

    /** ToB 2009's window menu bar: File, Edit, Options, Toolbox; the open one highlighted. */
    private void menuBar() {
        drawing.sprite("gui.window_left_menu", rect(0f, 0f, BORDER, MENU_HEIGHT), Drawing.WHITE);
        drawing.sprite("gui.window_mid_menu", rect(BORDER, 0f, screenWidth - 2f * BORDER, MENU_HEIGHT),
                Drawing.WHITE);
        drawing.sprite("gui.window_right_menu", rect(screenWidth - BORDER, 0f, BORDER, MENU_HEIGHT),
                Drawing.WHITE);
        for (Menu menu : Menu.values()) {
            float x = BORDER + menu.ordinal() * MENU_TITLE_WIDTH;
            boolean open = designer.menu().map(m -> m == menu).orElse(false);
            if (open) {
                drawing.sprite("gui.window_mid_menu_selected", rect(x, 0f, MENU_TITLE_WIDTH, MENU_HEIGHT),
                        Drawing.WHITE);
            }
            label(texts.get(menu.textKey()), x + BORDER, 0f, MENU_TITLE_WIDTH - 2f * BORDER, MENU_HEIGHT, TEXT,
                    open ? Drawing.WHITE : Drawing.BLACK, Align.LEFT);
            area(new Hit(Widget.EDITOR_MENU, menu.ordinal()), x, 0f, MENU_TITLE_WIDTH, MENU_HEIGHT);
        }
    }

    /** The library's name or "(NOT SAVED)" and its towers, coloured by the rule pass; "..." if more follow. */
    private void libraryList() {
        float top = MENU_HEIGHT;
        float height = CLIENT_HEIGHT - LEVEL_HEIGHT;
        group(0f, top, LIBRARY_WIDTH, height, false);
        label(model.libraryLabel(), 2f * BORDER, top + BORDER, LIBRARY_WIDTH - 4f * BORDER, CAPTION - BORDER, TEXT,
                Drawing.WHITE, Align.LEFT);
        List<EditorModel.Row> rows = model.rows();
        ScrollBox box = model.list();
        float y = top + CAPTION + BORDER;
        float width = LIBRARY_WIDTH - 4f * BORDER;
        for (int r = 0; r < box.visible() && box.first() + r < rows.size(); r++) {
            int index = box.first() + r;
            EditorModel.Row row = rows.get(index);
            if (row.selected()) {
                drawing.sprite(StartScreens.SELECTION, rect(2f * BORDER, y, width, LIST_ROW), Drawing.GREY);
            }
            label(cut(row.text(), width - 2f * BORDER, LIST_TEXT), 3f * BORDER, y, width - 2f * BORDER, LIST_ROW,
                    LIST_TEXT, row.colour(), Align.LEFT);
            area(new Hit(Widget.EDITOR_LIST, index), 2f * BORDER, y, width, LIST_ROW);
            y += LIST_ROW;
        }
        if (box.first() + box.visible() < rows.size()) {
            label("...", 3f * BORDER, y, width, LIST_ROW, LIST_TEXT, Drawing.GREY, Align.LEFT);
        }
    }

    /** {@code 001: Title (Author)} and the objects placed of the limit; a click will open Settings (6b-2). */
    private void titleStrip() {
        float width = screenWidth - LIBRARY_WIDTH;
        group(LIBRARY_WIDTH, MENU_HEIGHT, width, TITLE_HEIGHT, false);
        EditorFindings findings = model.findings();
        String objects = texts.format("editor.objects", findings.objects(), findings.maxObjects());
        float objectsWidth = 200f;
        label(model.title(), LIBRARY_WIDTH + 2f * BORDER, MENU_HEIGHT + BORDER, width - objectsWidth - 4f * BORDER,
                TITLE_HEIGHT - 2f * BORDER, TEXT, Drawing.WHITE, Align.LEFT);
        label(objects, screenWidth - objectsWidth - 2f * BORDER, MENU_HEIGHT + BORDER, objectsWidth,
                TITLE_HEIGHT - 2f * BORDER, TEXT, findings.objects() > findings.maxObjects() ? EditorFindings.RED
                        : Drawing.GREY, Align.RIGHT);
        area(Hit.of(Widget.EDITOR_TITLE), LIBRARY_WIDTH, MENU_HEIGHT, width, TITLE_HEIGHT);
    }

    /**
     * The current level's grid with the coordinates and the status line (ToB 2009's tower group, widened to the space
     * between the list and the palette; the grid keeps its size, centred).
     */
    private void grid() {
        group(LIBRARY_WIDTH, TOWER_TOP, centreWidth, TOWER_HEIGHT, false);
        float captionWidth = centreWidth - 2f * BORDER;
        group(LIBRARY_WIDTH + BORDER, TOWER_TOP + BORDER, captionWidth, CAPTION - BORDER, true);
        float coordinates = 120f;
        label(model.coordinates(), LIBRARY_WIDTH + 2f * BORDER, TOWER_TOP + BORDER, coordinates, CAPTION - BORDER,
                TEXT, Drawing.GREY, Align.LEFT);
        label(model.status(), LIBRARY_WIDTH + 2f * BORDER + coordinates, TOWER_TOP + BORDER,
                captionWidth - coordinates - 2f * BORDER, CAPTION - BORDER, TEXT, model.statusColour(), Align.LEFT);
        float x0 = LIBRARY_WIDTH + (centreWidth - TOWER_WIDTH) / 2f + BORDER;
        float y0 = TOWER_TOP + BORDER + CAPTION;
        float size = TOWER_WIDTH - 2f * BORDER;
        cells(model.level(), x0, y0, size, Drawing.WHITE, true);
    }

    /**
     * The 16 × 16 cells of {@code level} in a {@code size} square (ToB 2009's {@code initElementImages}: a sprite is
     * 1.2 steps wide, the steps overlap) on black, so that the cell area stands out of its group's grey margin. The
     * grid shows the marks, the cursor and the clickable cells.
     */
    private void cells(int level, float x0, float y0, float size, int tint, boolean edit) {
        drawing.sprite(OPAQUE, rect(x0, y0, size, size), Drawing.BLACK);
        float element = size * 1.2f / (0.2f + TowerGrid.WIDTH);
        float step = element * 0.833333f;
        TowerGrid grid = model.tower().grid();
        for (int x = 0; x < TowerGrid.WIDTH; x++) {
            for (int y = 0; y < TowerGrid.DEPTH; y++) {
                Cell cell = grid.cell(x, y, level);
                float cx = x0 + x * step;
                float cy = y0 + y * step;
                if (!Cell.EMPTY.equals(cell)) {
                    if (cell.base() != BaseType.EMPTY) {
                        drawing.sprite(EditorElement.region(cell.base()), rect(cx, cy, element, element), tint);
                    }
                    if (cell.hasObject()) {
                        drawing.sprite(EditorElement.region(cell.object()), rect(cx + 0.1f * element,
                                cy + 0.1f * element, 0.8f * element, 0.8f * element), tint);
                    }
                }
                if (edit) {
                    area(new Hit(Widget.EDITOR_CELL, y * TowerGrid.WIDTH + x), cx, cy, step, step);
                }
            }
        }
        if (!edit) {
            return;
        }
        for (TowerCheck.Finding finding : model.findings().on(level)) {
            finding.location().ifPresent(p -> drawing.sprite("editor.cur", rect(x0 + p.x() * step,
                    y0 + p.y() * step, element, element), finding.severity() == TowerCheck.Severity.ERROR
                            ? EditorFindings.RED : EditorFindings.YELLOW));
        }
        drawing.sprite("editor.cur", rect(x0 + model.x() * step, y0 + model.y() * step, element, element),
                pulse(clock));
    }

    /** The selected element's name as the caption, the four base elements, the 36 objects packed below, the cursor. */
    private void palette() {
        group(elementsLeft, TOWER_TOP, ELEMENTS_WIDTH, TOWER_HEIGHT, false);
        caption(texts.get(model.tool().textKey()), elementsLeft, TOWER_TOP, ELEMENTS_WIDTH, Drawing.WHITE);
        float x0 = elementsLeft + BORDER;
        for (int i = 0; i < EditorElement.PALETTE.size(); i++) {
            EditorElement element = EditorElement.PALETTE.get(i);
            int column = i < EditorElement.BASE_COUNT ? i : (i - EditorElement.BASE_COUNT) % PER_ROW;
            int row = i < EditorElement.BASE_COUNT ? 0 : 1 + (i - EditorElement.BASE_COUNT) / PER_ROW;
            float x = x0 + column * ENTRY;
            float y = PALETTE_TOP + row * ENTRY;
            drawing.sprite(element.region(), rect(x + 0.05f * ENTRY, y + 0.05f * ENTRY, 0.9f * ENTRY, 0.9f * ENTRY),
                    Drawing.WHITE);
            if (element.equals(model.tool())) {
                drawing.sprite("editor.cur", rect(x, y, ENTRY, ENTRY), pulse(clock));
            }
            area(new Hit(Widget.EDITOR_PALETTE, i), x, y, ENTRY, ENTRY);
        }
        if (!designer.preview().maximized()) {
            // spec decision 3: the 3D preview in the palette column's lower part
            float x = PREVIEW.x() + elementsLeft - ELEMENTS_LEFT;
            drawing.viewport(rect(x, PREVIEW.y(), PREVIEW.width(), PREVIEW.height()));
            area(Hit.of(Widget.EDITOR_PREVIEW), x, PREVIEW.y(), PREVIEW.width(), PREVIEW.height());
        }
    }

    /**
     * The six levels over the full width: the current one sunken and white, the others grey; a level with an error
     * has a red caption. A level's map keeps ToB 2009's size, centred in its group.
     */
    private void levels() {
        for (int level = 0; level < TowerGrid.LEVELS; level++) {
            float x = level * levelWidth;
            boolean current = level == model.level();
            group(x, LEVELS_TOP, levelWidth, LEVEL_HEIGHT, current);
            boolean error = model.findings().on(level).stream()
                    .anyMatch(f -> f.severity() == TowerCheck.Severity.ERROR);
            caption(texts.format("editor.level", level + 1), x, LEVELS_TOP, levelWidth,
                    error ? EditorFindings.RED : current ? Drawing.WHITE : Drawing.GREY);
            cells(level, x + (levelWidth - LEVEL_WIDTH) / 2f + BORDER, LEVELS_TOP + BORDER + CAPTION,
                    LEVEL_WIDTH - 2f * BORDER, current ? Drawing.WHITE : Drawing.GREY, false);
            area(new Hit(Widget.EDITOR_LEVEL, level), x, LEVELS_TOP, levelWidth, LEVEL_HEIGHT);
        }
    }

    /** The open menu below its title: ToB 2009's menu skin, separators, the entry under the mouse highlighted. */
    private void dropDown(Menu menu) {
        List<Command> commands = Command.of(menu);
        float height = 2f * BORDER;
        for (Command command : commands) {
            height += MENU_ITEM + (command.separated() ? MENU_SEPARATOR : 0f);
        }
        float x = BORDER + menu.ordinal() * MENU_TITLE_WIDTH;
        drawing.nineSlice(MENU, rect(x, MENU_HEIGHT, MENU_WIDTH, height), layout.scale(), Drawing.WHITE);
        float y = MENU_HEIGHT + BORDER;
        for (Command command : commands) {
            if (command.separated()) {
                drawing.sprite("gui.menu_left_separator", rect(x, y, BORDER, MENU_SEPARATOR), Drawing.WHITE);
                drawing.sprite("gui.menu_mid_separator", rect(x + BORDER, y, MENU_WIDTH - 2f * BORDER,
                        MENU_SEPARATOR), Drawing.WHITE);
                drawing.sprite("gui.menu_right_separator", rect(x + MENU_WIDTH - BORDER, y, BORDER, MENU_SEPARATOR),
                        Drawing.WHITE);
                y += MENU_SEPARATOR;
            }
            boolean enabled = designer.enabled(command);
            boolean hovered = enabled && designer.hovered().map(c -> c == command).orElse(false);
            if (hovered) {
                drawing.sprite("gui.menu_mid_selected", rect(x + BORDER, y, MENU_WIDTH - 2f * BORDER, MENU_ITEM),
                        Drawing.WHITE);
            }
            label(designer.label(command), x + 2f * BORDER, y, MENU_WIDTH - 4f * BORDER, MENU_ITEM, TEXT,
                    !enabled ? Drawing.GREY : hovered ? Drawing.WHITE : Drawing.BLACK, Align.LEFT);
            area(new Hit(Widget.EDITOR_ITEM, command.ordinal()), x, y, MENU_WIDTH, MENU_ITEM);
            y += MENU_ITEM;
        }
    }

    // ------------------------------------------------------------------ dialogs

    /**
     * A dialog window centred on the screen, on an opaque backing so that nothing under it shows through: only its
     * own widgets can be clicked. Returns its top left.
     */
    private float[] window(String title, float width, float height) {
        return window(title, (screenWidth - width) / 2f, width, height);
    }

    /** A dialog window as {@link #window(String, float, float)}, its left edge at {@code left}. */
    private float[] window(String title, float left, float width, float height) {
        areas.clear();
        float top = (StartLayout.HEIGHT - height) / 2f;
        drawing.sprite(OPAQUE, rect(left, top, width, height), Drawing.BLACK);
        drawing.nineSlice(NineSlice.WINDOW, rect(left, top, width, height), layout.scale(), Drawing.WHITE);
        label(title, left + BORDER, top + ConfirmDialogLayout.CAPTION_FACE_TOP, width - 2f * BORDER,
                ConfirmDialogLayout.CAPTION_FACE_BOTTOM - ConfirmDialogLayout.CAPTION_FACE_TOP, TEXT, Drawing.WHITE,
                Align.LEFT);
        return new float[] {left, top};
    }

    /** OK (or {@code okKey}'s text) and Cancel at the window's bottom right. */
    private void okCancel(String okText, boolean okEnabled, float left, float top, float width, float height) {
        float y = top + height - BORDER - 2f * BORDER - ConfirmDialogLayout.BUTTON_HEIGHT;
        float x = left + width - 2f * BORDER - 2f * DIALOG_BUTTON - 10f;
        button(new Hit(Widget.EDITOR_DIALOG, TowerDesigner.DIALOG_OK), okText, x, y, DIALOG_BUTTON,
                ConfirmDialogLayout.BUTTON_HEIGHT, false, okEnabled);
        button(new Hit(Widget.EDITOR_DIALOG, TowerDesigner.DIALOG_CANCEL), texts.get("editor.dialog.cancel"),
                x + DIALOG_BUTTON + 10f, y, DIALOG_BUTTON, ConfirmDialogLayout.BUTTON_HEIGHT, false, true);
    }

    /** File → Open and Save As (ToB 2009's {@code TowerLibraryDialog}): the libraries, Save As's name. */
    private void libraryDialog(LibraryDialog dialog) {
        boolean open = dialog.mode() == LibraryDialog.Mode.OPEN;
        float width = LIBRARY_DIALOG_WIDTH;
        float height = LIBRARY_DIALOG_HEIGHT;
        float[] at = window(texts.get(open ? "editor.library.open" : "editor.library.save_as"), width, height);
        float left = at[0] + 2f * BORDER;
        float y = at[1] + ConfirmDialogLayout.CAPTION_HEIGHT + BORDER;
        float inner = width - 4f * BORDER;
        label(texts.get("editor.library.libraries"), left, y, inner, 25f, TEXT, Drawing.GREY, Align.LEFT);
        y += 25f;
        float listHeight = LibraryDialog.ROWS * LIST_ROW + 2f * BORDER;
        drawing.nineSlice(NineSlice.LISTBOX, rect(left, y, inner, listHeight), layout.scale(), Drawing.WHITE);
        ScrollBox box = dialog.box();
        for (int r = 0; r < box.visible() && box.first() + r < dialog.entries().size(); r++) {
            int index = box.first() + r;
            TowerLibraries.Entry entry = dialog.entries().get(index);
            float rowY = y + BORDER + r * LIST_ROW;
            boolean selected = index == dialog.selected();
            if (selected) {
                drawing.sprite(StartScreens.SELECTION, rect(left + BORDER, rowY, inner - 2f * BORDER, LIST_ROW),
                        Drawing.WHITE);
            }
            String text = entry.readOnly() ? texts.format("editor.library.entry.read_only", entry.name())
                    : entry.name();
            label(text, left + 2f * BORDER, rowY, inner - 4f * BORDER, LIST_ROW, LIST_TEXT,
                    selected ? Drawing.ORANGE : Drawing.BLACK, Align.LEFT);
            area(new Hit(Widget.EDITOR_DIALOG_LIST, index), left + BORDER, rowY, inner - 2f * BORDER, LIST_ROW);
        }
        y += listHeight + 10f;
        if (!open) {
            label(texts.get("editor.library.name"), left, y, 100f, 40f, TEXT, Drawing.GREY, Align.LEFT);
            drawing.nineSlice(NineSlice.TEXTBOX, rect(left + 100f, y, inner - 100f, 40f), layout.scale(),
                    Drawing.WHITE);
            String name = dialog.name() + ((int) (clock * 2.0) % 2 == 0 ? CreditsTicker.CURSOR : "");
            label(name, left + 100f + 2f * BORDER, y, inner - 100f - 4f * BORDER, 40f, TEXT, Drawing.BLACK,
                    Align.LEFT);
            label(dialog.error(), left + 100f, y + 42f, inner - 100f, 25f, TEXT, EditorFindings.RED, Align.LEFT);
        }
        okCancel(texts.get(open ? "editor.command.open" : "editor.command.save"),
                !open || dialog.choice().isPresent(), at[0], at[1], width, height);
    }

    /** The Toolbox dialogs: the choices in a row of buttons, "Only current Level?", OK and Cancel. */
    private void toolboxDialog(ToolboxDialog dialog) {
        float width = TOOLBOX_DIALOG_WIDTH;
        float height = TOOLBOX_DIALOG_HEIGHT;
        float[] at = window(texts.get(dialog.captionKey()), width, height);
        float left = at[0] + 2f * BORDER;
        float y = at[1] + ConfirmDialogLayout.CAPTION_HEIGHT + 2f * BORDER;
        float labelWidth = 200f;
        label(texts.get(dialog.labelKey()), left, y, labelWidth, 40f, TEXT, Drawing.GREY, Align.LEFT);
        List<String> choices = dialog.choices();
        float choiceWidth = (width - 4f * BORDER - labelWidth) / choices.size();
        for (int i = 0; i < choices.size(); i++) {
            String key = choices.get(i);
            String text = key.startsWith("editor.") ? texts.get(key) : key;
            button(new Hit(Widget.EDITOR_DIALOG, TowerDesigner.DIALOG_CHOICE + i), text,
                    left + labelWidth + i * choiceWidth, y, choiceWidth - 4f, 40f, i == dialog.choice(), true);
        }
        if (dialog.hasSwitch()) {
            y += 50f;
            label(texts.get("editor.toolbox.only"), left, y, labelWidth + 100f, 40f, TEXT, Drawing.GREY, Align.LEFT);
            button(new Hit(Widget.EDITOR_DIALOG, TowerDesigner.DIALOG_SWITCH),
                    texts.get(dialog.onlyLevel() ? "options.on" : "options.off"), left + labelWidth + 100f, y, 80f,
                    40f, dialog.onlyLevel(), true);
        }
        okCancel(texts.get("frame.ok"), dialog.valid(), at[0], at[1], width, height);
    }

    // ------------------------------------------------------------------ Phase 6b-2: the form dialogs

    /** A form's height: its groups with their captions and rows. */
    static float formHeight(EditorForm form) {
        float height = 0f;
        for (int group = 0; group < form.groups().size(); group++) {
            height += groupHeight(form, group);
        }
        return height;
    }

    private static float groupHeight(EditorForm form, int group) {
        long rows = form.fields().stream().filter(f -> f.group() == group).mapToInt(EditorForm.Field::row).distinct()
                .count();
        return 2f * BORDER + (form.groups().get(group).isEmpty() ? 0f : FORM_CAPTION) + rows * FORM_ROW;
    }

    /** A form dialog's height: the caption, the form and the row of buttons. */
    public static float formDialogHeight(EditorForm form) {
        return ConfirmDialogLayout.CAPTION_HEIGHT + 2f * BORDER + formHeight(form) + ConfirmDialogLayout.BUTTON_HEIGHT
                + 4f * BORDER;
    }

    /** The left edge of the Settings and Effects dialogs: centred in the space left of the palette column. */
    private float formLeft() {
        return (elementsLeft - FORM_DIALOG_WIDTH) / 2f;
    }

    /** Settings or Effects (ToB 2009's {@code OptionsSettingsDialog}, {@code OptionsEffectsDialog}). */
    private void towerDialog(TowerDialog dialog) {
        EditorForm form = dialog.form();
        float height = formDialogHeight(form);
        float[] at = window(texts.get(dialog.captionKey()), formLeft(), FORM_DIALOG_WIDTH, height);
        form(form, at[0] + BORDER, at[1] + ConfirmDialogLayout.CAPTION_HEIGHT + BORDER, FORM_DIALOG_WIDTH - 2f * BORDER,
                FORM_LABEL);
        if (dialog.randomizable()) {
            float y = at[1] + height - BORDER - 2f * BORDER - ConfirmDialogLayout.BUTTON_HEIGHT;
            button(new Hit(Widget.EDITOR_DIALOG, TowerDesigner.DIALOG_RANDOMIZE),
                    texts.get("editor.effects.randomize"), at[0] + 3f * BORDER, y, RANDOMIZE_WIDTH,
                    ConfirmDialogLayout.BUTTON_HEIGHT, false, true);
        }
        okCancel(texts.get("frame.ok"), true, at[0], at[1], FORM_DIALOG_WIDTH, height);
    }

    /** The colour dialog over the Effects dialog (ToB 2009's {@code ColorDialog}): R, G, B and the colour beside. */
    private void colourDialog(ColourDialog dialog) {
        EditorForm form = dialog.form();
        float height = formDialogHeight(form);
        float left = formLeft() + (FORM_DIALOG_WIDTH - COLOUR_DIALOG_WIDTH) / 2f;
        float[] at = window(texts.get(dialog.captionKey()), left, COLOUR_DIALOG_WIDTH, height);
        float top = at[1] + ConfirmDialogLayout.CAPTION_HEIGHT + BORDER;
        float inner = COLOUR_DIALOG_WIDTH - 2f * BORDER;
        form(form, at[0] + BORDER, top, inner, COLOUR_LABEL);
        float swatch = formHeight(form) - 4f * BORDER;
        float x = at[0] + BORDER + inner - 2f * BORDER - SWATCH;
        group(x, top + 2f * BORDER, SWATCH, swatch, true);
        drawing.sprite("editor.col", rect(x + BORDER, top + 3f * BORDER, SWATCH - 2f * BORDER,
                swatch - 2f * BORDER), dialog.colour());
        okCancel(texts.get("frame.ok"), true, at[0], at[1], COLOUR_DIALOG_WIDTH, height);
    }

    /** The groups and rows of {@code form} from ({@code left}, {@code top}), {@code width} wide. */
    private void form(EditorForm form, float left, float top, float width, float labelWidth) {
        float y = top;
        float controls = left + 2f * BORDER + labelWidth + FORM_GAP;
        float controlWidth = left + width - 2f * BORDER - controls;
        for (int group = 0; group < form.groups().size(); group++) {
            float height = groupHeight(form, group);
            group(left, y, width, height, false);
            float rowTop = y + BORDER;
            String caption = form.groups().get(group);
            if (!caption.isEmpty()) {
                label(texts.get(caption), left, rowTop, width, FORM_CAPTION, TEXT, Drawing.GREY, Align.CENTRE);
                rowTop += FORM_CAPTION;
            }
            int firstRow = -1;
            for (int i = 0; i < form.fields().size(); i++) {
                EditorForm.Field field = form.field(i);
                if (field.group() != group) {
                    continue;
                }
                if (firstRow < 0) {
                    firstRow = field.row();
                }
                float rowY = rowTop + (field.row() - firstRow) * FORM_ROW;
                boolean focused = form.focus() == i;
                if (!field.rowKey().isEmpty()) {
                    boolean rowFocused = form.focused().row() == field.row();
                    label(texts.get(field.rowKey()), left + 2f * BORDER, rowY, labelWidth, FORM_CONTROL, TEXT,
                            rowFocused ? StartScreens.HOVER : Drawing.WHITE, Align.LEFT);
                }
                boolean pair = form.fields().stream().anyMatch(f -> f.row() == field.row() && f.column() == 1);
                float columnWidth = pair ? (controlWidth - 10f) / 2f : controlWidth;
                float x = controls + field.column() * (columnWidth + 10f);
                control(i, field, focused, x, rowY, columnWidth);
            }
            y += height;
        }
    }

    /** One field's control in ToB 2009's look at ({@code x}, {@code y}), at most {@code width} wide. */
    private void control(int index, EditorForm.Field field, boolean focused, float x, float y, float width) {
        int frame = focused ? StartScreens.HOVER : Drawing.WHITE;
        Hit body = new Hit(Widget.EDITOR_FIELD, EditorForm.hitRow(index, EditorForm.BODY));
        switch (field.kind()) {
            case TEXT -> {
                drawing.nineSlice(NineSlice.TEXTBOX, rect(x, y, width, FORM_CONTROL), layout.scale(), frame);
                String text = field.display() + (focused && (int) (clock * 2.0) % 2 == 0 ? CreditsTicker.CURSOR : "");
                label(text, x + 2f * BORDER, y, width - 4f * BORDER, FORM_CONTROL, TEXT, Drawing.BLACK, Align.LEFT);
                area(body, x, y, width, FORM_CONTROL);
            }
            case NUMBER, CHOICE -> {
                float box = field.kind() == EditorForm.Kind.NUMBER ? NUMBER_BOX : width - 2f * STEP_BUTTON;
                button(new Hit(Widget.EDITOR_FIELD, EditorForm.hitRow(index, EditorForm.MINUS)), "-", x, y,
                        STEP_BUTTON, FORM_CONTROL, false, true);
                drawing.nineSlice(NineSlice.TEXTBOX, rect(x + STEP_BUTTON, y, box, FORM_CONTROL), layout.scale(),
                        frame);
                label(field.display(), x + STEP_BUTTON + BORDER, y, box - 2f * BORDER, FORM_CONTROL, TEXT,
                        Drawing.BLACK, Align.LEFT);
                area(body, x + STEP_BUTTON, y, box, FORM_CONTROL);
                button(new Hit(Widget.EDITOR_FIELD, EditorForm.hitRow(index, EditorForm.PLUS)), "+",
                        x + STEP_BUTTON + box, y, STEP_BUTTON, FORM_CONTROL, false, true);
            }
            case CHECK -> {
                drawing.nineSlice(field.on() ? NineSlice.BUTTON_INSET : NineSlice.BUTTON, rect(x, y, STEP_BUTTON,
                        FORM_CONTROL), layout.scale(), frame);
                label(field.on() ? "*" : "", x, y, STEP_BUTTON, FORM_CONTROL, TEXT, Drawing.BLACK, Align.CENTRE);
                label(texts.get(field.labelKey()), x + STEP_BUTTON + 2f * BORDER, y, width - STEP_BUTTON - 2f * BORDER,
                        FORM_CONTROL, TEXT, focused ? StartScreens.HOVER : Drawing.WHITE, Align.LEFT);
                area(body, x, y, width, FORM_CONTROL);
            }
            case COLOUR -> {
                drawing.nineSlice(NineSlice.GROUP_INSET, rect(x, y, SWATCH, FORM_CONTROL), layout.scale(), frame);
                drawing.sprite("editor.col", rect(x + BORDER, y + BORDER, SWATCH - 2f * BORDER,
                        FORM_CONTROL - 2f * BORDER), field.value());
                area(body, x, y, SWATCH, FORM_CONTROL);
            }
            case INFO -> label(field.display(), x, y, width, FORM_CONTROL, TEXT, Drawing.GREY, Align.LEFT);
        }
    }
}
