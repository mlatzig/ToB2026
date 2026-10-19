package de.latzig.game.tob.client.view;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial.CullHint;

import de.latzig.game.tob.client.logic.game.ConfirmDialogLayout;
import de.latzig.game.tob.client.logic.game.PanelButton;
import de.latzig.game.tob.client.logic.game.PanelLayout;
import de.latzig.game.tob.client.logic.game.PanelState;
import de.latzig.game.tob.client.logic.game.PauseMenu;
import de.latzig.game.tob.client.logic.game.TextPlacement;
import de.latzig.game.tob.client.logic.ui.NineSlice;
import de.latzig.game.tob.core.model.GuiAtlas;
import de.latzig.game.tob.core.sim.Simulation;
import de.latzig.game.tob.core.sim.Viewpoint;

/**
 * The ToB 2009 panel as a 2D overlay in the GUI node (Phase 3b-2 spec §3): GUI atlas quads ({@link AtlasSprite}) for
 * the 27 buttons, the caption strip and the title bar, the pause menu in the title bar while paused with its ARE
 * YOU SURE? dialog ({@link DialogView}, acceptance round 2), and the software cursor ({@code gui.cursor_normal},
 * {@value #CURSOR_SIZE} px, hot spot top left) on top of everything. Texts are in the ToB 2009 bitmap font,
 * black as in ToB 2009, {@value #TEXT_SIZE} virtual units high. The view only reads.
 */
public final class PanelView {

    public static final float CURSOR_SIZE = 24f;
    /** ToB 2009 {@code gui-defs} {@code font.size}. */
    public static final float TEXT_SIZE = 20f;
    public static final String CURSOR = "gui.cursor_normal";
    public static final String MENU_BAR = "gui.window_mid_menu";
    public static final String MENU_ROW = "gui.menu_mid";
    public static final String MENU_ROW_SELECTED = "gui.menu_mid_selected";
    /** The tint of a grey button (ToB 2009 {@code Colors.GRAY}). */
    public static final ColorRGBA GREY = new ColorRGBA(0.5f, 0.5f, 0.5f, 1f);

    /** Below the buttons: the semi-transparent group fill of the button area (Phase 4a). */
    private static final float Z_FILL = -1f;
    private static final float Z_PANEL = 0f;
    private static final float Z_TEXT = 1f;
    private static final float Z_MENU = 2f;
    private static final float Z_MENU_TEXT = 3f;
    private static final float Z_CURSOR = 10f;

    private final Node node = new Node("panel");
    private final Material normal;
    private final Material grey;
    private final Map<PanelButton, AtlasSprite> buttons = new EnumMap<>(PanelButton.class);
    private final List<AtlasSprite> fill = new ArrayList<>();
    private final AtlasSprite caption;
    private final AtlasSprite titleBar;
    private final List<AtlasSprite> menuRows = new ArrayList<>();
    private final AtlasSprite cursor;
    private final BitmapText captionText;
    private final BitmapText titleText;
    private final List<BitmapText> menuTexts = new ArrayList<>();
    private final DialogView dialog;

    /** @throws AssetLoadException if the GUI atlas or its image is missing or unreadable */
    public PanelView(AssetLibrary assets, BitmapFont font) {
        GuiAtlas atlas = assets.guiAtlas();
        normal = assets.guiMaterial();
        grey = assets.guiMaterial();
        grey.setColor("Color", GREY);
        for (int i = 0; i < 9; i++) {
            fill.add(sprite("fill-" + i, atlas));
        }
        for (PanelButton button : PanelButton.values()) {
            buttons.put(button, sprite("button-" + button, atlas));
        }
        caption = sprite("caption", atlas);
        titleBar = sprite("title-bar", atlas);
        for (int row = 0; row < 2; row++) {
            menuRows.add(sprite("menu-row-" + row, atlas));
        }
        cursor = sprite("cursor", atlas);
        captionText = text(font);
        titleText = text(font);
        for (int row = 0; row < 2; row++) {
            menuTexts.add(text(font));
        }
        dialog = new DialogView(node, atlas, normal, font);
    }

    public Node node() {
        return node;
    }

    /**
     * Draws the panel for this render frame.
     *
     * @param title   the title bar text ({@code HudText.title})
     * @param caption the caption strip text ({@code HudText.caption})
     */
    public void update(PanelLayout layout, boolean collapsed, PanelState state, Simulation simulation,
            Viewpoint shown, boolean paused, PauseMenu menu, String title, String caption, float cursorX,
            float cursorY) {
        float scale = layout.scale();
        // Phase 4a spec §2 decision 7: ToB 2009 covers the button area with gui.group_outset_*, whose texels carry
        // alpha 160/255, so the scene shows through darkened; nothing while collapsed (ToB 2009 has no area then).
        List<PanelLayout.Rect> pieces = NineSlice.GROUP_OUTSET.pieces(layout.buttonArea(), scale);
        for (int i = 0; i < fill.size(); i++) {
            if (collapsed) {
                fill.get(i).hide();
            } else {
                fill.get(i).show(NineSlice.GROUP_OUTSET.regions().get(i), pieces.get(i), Z_FILL, normal);
            }
        }
        for (Map.Entry<PanelButton, AtlasSprite> entry : buttons.entrySet()) {
            if (collapsed) {
                entry.getValue().hide();
                continue;
            }
            PanelButton button = entry.getKey();
            PanelState.Look look = state.look(button, simulation, shown, paused);
            entry.getValue().show(state.region(button, look, simulation, shown), layout.button(button), Z_PANEL,
                    look == PanelState.Look.GREY ? grey : normal);
        }
        this.caption.show(collapsed ? "panel.caption_min" : "panel.caption_max", layout.caption(collapsed), Z_PANEL,
                normal);
        // Final review M1: left-aligned from virtual x = 512 (SJGL Label default BEGIN), not centred over the whole
        // strip, half of which is transparent; applies to the collapsed strip too. Acceptance fix 1: vertically
        // centred in the strip's grey face, not in the whole strip with its lower bevel.
        place(captionText, caption, layout.captionTextFace(collapsed), scale, false, ColorRGBA.Black, Z_TEXT);

        PanelLayout.Rect bar = layout.titleBar();
        if (paused) {
            titleBar.show(MENU_BAR, bar, Z_PANEL, normal);
            place(titleText, PauseMenu.TITLE, layout.menuTitleFace(), scale, true, ColorRGBA.Black, Z_TEXT);
        } else {
            titleBar.show("panel.bar", bar, Z_PANEL, normal);
            place(titleText, title, layout.titleTextFace(), scale, false, ColorRGBA.Black, Z_TEXT);
        }
        List<String> rows = paused ? menu.rows() : List.of();
        for (int row = 0; row < menuRows.size(); row++) {
            BitmapText text = menuTexts.get(row);
            if (row >= rows.size()) {
                menuRows.get(row).hide();
                text.setText("");
                continue;
            }
            boolean highlighted = row == menu.highlightedRow();
            PanelLayout.Rect rect = layout.menuRow(row);
            menuRows.get(row).show(highlighted ? MENU_ROW_SELECTED : MENU_ROW, rect, Z_MENU, normal);
            place(text, rows.get(row), rect, scale, false, highlighted ? ColorRGBA.White : ColorRGBA.Black,
                    Z_MENU_TEXT);
            text.setLocalTranslation(text.getLocalTranslation().x + 8f * scale, text.getLocalTranslation().y,
                    Z_MENU_TEXT);
        }
        if (paused && menu.level() == PauseMenu.Level.CONFIRM) {
            dialog.show(ConfirmDialogLayout.of(layout, collapsed), menu.choice(), menu.yesHighlighted());
        } else {
            dialog.hide();
        }
        cursor.show(CURSOR, new PanelLayout.Rect(cursorX, cursorY - CURSOR_SIZE, CURSOR_SIZE, CURSOR_SIZE),
                Z_CURSOR, normal);
    }

    /**
     * Hides the software cursor regardless of the last {@link #update} (author's extra, 2026-10-14): the game's
     * cursor auto-hide ({@code GameState}, {@link de.latzig.game.tob.client.logic.game.CursorAutoHide}) calls this
     * right after a frame's {@code update} when it decided the cursor should not show; the next {@code update} shows
     * it again at its new position unless this is called once more.
     */
    public void hideCursor() {
        cursor.hide();
    }

    /** Whether the software cursor is shown (tests). */
    boolean cursorShown() {
        return cursor.isShown();
    }

    /** The atlas region a button shows, or null while it is hidden (tests). */
    String shownRegion(PanelButton button) {
        AtlasSprite sprite = buttons.get(button);
        return sprite.isShown() ? sprite.region() : null;
    }

    /** The atlas region of fill piece {@code index} (0–8, top left first), or null while hidden (tests). */
    String fillRegion(int index) {
        AtlasSprite sprite = fill.get(index);
        return sprite.isShown() ? sprite.region() : null;
    }

    /** Whether a button is tinted grey (tests). */
    boolean isGrey(PanelButton button) {
        return buttons.get(button).geometry().getMaterial() == grey;
    }

    /** The texts of the shown menu rows (tests). */
    List<String> menuTexts() {
        List<String> texts = new ArrayList<>();
        for (int row = 0; row < menuRows.size(); row++) {
            if (menuRows.get(row).isShown()) {
                texts.add(menuTexts.get(row).getText());
            }
        }
        return texts;
    }

    String titleText() {
        return titleText.getText();
    }

    String captionText() {
        return captionText.getText();
    }

    BitmapText titleLabel() {
        return titleText;
    }

    BitmapText captionLabel() {
        return captionText;
    }

    BitmapText menuLabel(int row) {
        return menuTexts.get(row);
    }

    boolean dialogShown() {
        return dialog.isShown();
    }

    BitmapText dialogCaptionLabel() {
        return dialog.caption();
    }

    BitmapText dialogQuestionLabel() {
        return dialog.question();
    }

    BitmapText dialogButtonLabel(ConfirmDialogLayout.Answer answer) {
        return dialog.label(answer);
    }

    /** The atlas region of the dialog's sprite {@code index} (tests). */
    String dialogRegion(int index) {
        return dialog.region(index);
    }

    /** The caption text's x position (tests, M1: left-aligned from virtual x = 512, not centred). */
    float captionTextX() {
        return captionText.getLocalTranslation().x;
    }

    /**
     * Places a one-line text horizontally centred in {@code rect} or at its left edge, and vertically so that its
     * cap band is centred in {@code rect} ({@link TextPlacement}: the translation of a {@link BitmapText} is the top
     * left of its one-line-high box). The one helper for every panel, menu and dialog text (acceptance fix 1, round
     * 2 item 1).
     */
    static void place(BitmapText text, String value, PanelLayout.Rect rect, float scale, boolean centred,
            ColorRGBA colour, float z) {
        text.setSize(TEXT_SIZE * scale);
        text.setText(value);
        text.setColor(colour);
        float x = centred ? rect.x() + (rect.width() - text.getLineWidth()) / 2f : rect.x();
        float y = TextPlacement.top(rect.y() + rect.height() / 2f, text.getLineHeight());
        text.setLocalTranslation(x, y, z);
    }

    private AtlasSprite sprite(String name, GuiAtlas atlas) {
        AtlasSprite sprite = new AtlasSprite(name, atlas, normal);
        node.attachChild(sprite.geometry());
        return sprite;
    }

    private BitmapText text(BitmapFont font) {
        BitmapText text = new BitmapText(font);
        text.setCullHint(CullHint.Inherit);
        node.attachChild(text);
        return text;
    }
}
