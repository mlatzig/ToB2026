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
import de.latzig.game.tob.client.logic.game.ConfirmDialogLayout.Answer;
import de.latzig.game.tob.client.logic.game.PanelLayout;
import de.latzig.game.tob.client.logic.game.PauseMenu;
import de.latzig.game.tob.client.logic.ui.NineSlice;
import de.latzig.game.tob.core.model.GuiAtlas;

/**
 * The pause menu's ARE YOU SURE? dialog (acceptance round 2, item 2), drawn as ToB 2009's SJGL toolkit draws a
 * {@code MessageDialog} without icon: the window skin (blue caption bar with the chosen action in white, light frame,
 * semi-transparent dark face), a raised group holding a sunken group with the question (white, centred), and a raised
 * group with the YES and NO buttons (metallic, black labels). The highlighted button shows ToB 2009's focus marks
 * ({@code Button}: {@code '<' + text + '>'}). Layout from {@link ConfirmDialogLayout}; the view only reads.
 */
final class DialogView {

    /** ToB 2009 {@code AbstractButton.FOCUS1}/{@code FOCUS2}. */
    static final char FOCUS_LEFT = '<';
    static final char FOCUS_RIGHT = '>';

    private static final float Z_WINDOW = 4f;
    private static final float Z_GROUP = 4.1f;
    private static final float Z_INNER = 4.2f;
    private static final float Z_TEXT = 4.5f;

    private final Material material;
    private final List<AtlasSprite> sprites = new ArrayList<>();
    private final BitmapText caption;
    private final BitmapText question;
    private final Map<Answer, BitmapText> labels = new EnumMap<>(Answer.class);
    private boolean shown;

    DialogView(Node node, GuiAtlas atlas, Material material, BitmapFont font) {
        this.material = material;
        for (int i = 0; i < 6 * 9; i++) {
            AtlasSprite sprite = new AtlasSprite("dialog-" + i, atlas, material);
            node.attachChild(sprite.geometry());
            sprites.add(sprite);
        }
        caption = text(node, font);
        question = text(node, font);
        for (Answer answer : Answer.values()) {
            labels.put(answer, text(node, font));
        }
    }

    /** Shows the dialog asking about {@code choice}, YES or NO highlighted. */
    void show(ConfirmDialogLayout dialog, PauseMenu.Choice choice, boolean yesHighlighted) {
        float scale = dialog.scale();
        int next = 0;
        next = slice(next, NineSlice.WINDOW, dialog.window(), scale, Z_WINDOW);
        next = slice(next, NineSlice.GROUP_OUTSET, dialog.messageArea(), scale, Z_GROUP);
        next = slice(next, NineSlice.GROUP_INSET, dialog.messageGroup(), scale, Z_INNER);
        next = slice(next, NineSlice.GROUP_OUTSET, dialog.buttonArea(), scale, Z_GROUP);
        for (Answer answer : Answer.values()) {
            next = slice(next, NineSlice.BUTTON, dialog.button(answer), scale, Z_INNER);
        }
        PanelView.place(caption, choice.name(), dialog.captionTextFace(), scale, false, ColorRGBA.White, Z_TEXT);
        PanelView.place(question, PauseMenu.QUESTION, dialog.messageTextFace(), scale, true, ColorRGBA.White,
                Z_TEXT);
        for (Answer answer : Answer.values()) {
            boolean focus = (answer == Answer.YES) == yesHighlighted;
            String text = focus ? FOCUS_LEFT + answer.name() + FOCUS_RIGHT : answer.name();
            PanelView.place(labels.get(answer), text, dialog.buttonTextFace(answer), scale, true, ColorRGBA.Black,
                    Z_TEXT);
        }
        setTextsVisible(true);
        shown = true;
    }

    void hide() {
        for (AtlasSprite sprite : sprites) {
            sprite.hide();
        }
        setTextsVisible(false);
        shown = false;
    }

    boolean isShown() {
        return shown;
    }

    /** The region of sprite {@code index} (tests): nine per part, window first. */
    String region(int index) {
        return sprites.get(index).region();
    }

    BitmapText caption() {
        return caption;
    }

    BitmapText question() {
        return question;
    }

    BitmapText label(Answer answer) {
        return labels.get(answer);
    }

    private int slice(int first, NineSlice skin, PanelLayout.Rect rect, float scale, float z) {
        List<PanelLayout.Rect> pieces = skin.pieces(rect, scale);
        for (int i = 0; i < 9; i++) {
            sprites.get(first + i).show(skin.regions().get(i), pieces.get(i), z, material);
        }
        return first + 9;
    }

    private void setTextsVisible(boolean visible) {
        CullHint hint = visible ? CullHint.Inherit : CullHint.Always;
        caption.setCullHint(hint);
        question.setCullHint(hint);
        for (BitmapText label : labels.values()) {
            label.setCullHint(hint);
        }
    }

    private static BitmapText text(Node node, BitmapFont font) {
        BitmapText text = new BitmapText(font);
        text.setCullHint(CullHint.Always);
        node.attachChild(text);
        return text;
    }
}
