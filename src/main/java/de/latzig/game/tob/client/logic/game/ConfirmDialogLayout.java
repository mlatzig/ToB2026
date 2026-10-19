package de.latzig.game.tob.client.logic.game;

import java.util.Optional;

/**
 * The layout of the "ARE YOU SURE?" confirmation of the pause menu (acceptance round 2, item 2): a modal ToB 2009
 * dialog window, {@value #WIDTH} × {@value #HEIGHT} virtual units, horizontally centred in the window and vertically
 * centred in the 3D area between the panel (or the collapsed caption strip) and the title bar; if that area is too
 * low, the dialog hangs from the title bar. Built like ToB 2009's {@code MessageDialog} without the icon
 * ({@code AbstractDialog}, {@code Window}, {@code Group}, {@code Button} of the SJGL GUI toolkit, sizes from the
 * {@code geometry} section of {@code gui-atlas.json}):
 * <ul>
 * <li>the caption bar ({@code window_caption_height} 24) with the chosen action, the frame ({@code
 * window_border_width} 5) left, right and below, the semi-transparent dark window face;</li>
 * <li>the message area: a raised group ({@code group_border_width} 5) holding a sunken group with the question, 16
 * units of padding round one text line ({@code MessageDialog}'s grid {@code "16,32,16"});</li>
 * <li>the button area below: a raised group with YES and NO ({@value #BUTTON_WIDTH} × {@code button_height} 32) side
 * by side at its right end, YES left of NO, as {@code AbstractDialog} places OK and Cancel.</li>
 * </ul>
 * Screen coordinates as {@link PanelLayout}: pixels, origin at the bottom left, y upwards; everything scales with
 * {@link PanelLayout#scale()}. Engine-free; {@code PanelView} draws it, {@link PauseMenu} hit-tests it.
 */
public final class ConfirmDialogLayout {

    /** YES or NO. */
    public enum Answer {
        YES, NO
    }

    public static final float WIDTH = 320f;
    /** {@code geometry.window_caption_height}. */
    public static final float CAPTION_HEIGHT = 24f;
    /** {@code geometry.window_border_width}. */
    public static final float BORDER = 5f;
    /** {@code geometry.group_border_width}. */
    public static final float GROUP_BORDER = 5f;
    /** Space round the question ({@code MessageDialog}'s grid layout {@code "16,32,16,0*"}). */
    public static final float PADDING = 16f;
    /** One text line: the font size ({@code general.font.size}). */
    public static final float TEXT_LINE = 20f;
    /** {@code geometry.button_height}. */
    public static final float BUTTON_HEIGHT = 32f;
    /** Wide enough for the longer label with ToB 2009's focus marks, "<YES>", plus the button border. */
    public static final float BUTTON_WIDTH = 96f;
    /** {@code geometry.button_border_width}. */
    public static final float BUTTON_BORDER = 5f;
    /** The sunken group with the question. */
    public static final float MESSAGE_GROUP_HEIGHT = 2f * GROUP_BORDER + 2f * PADDING + TEXT_LINE;
    /** The raised group round it. */
    public static final float MESSAGE_AREA_HEIGHT = MESSAGE_GROUP_HEIGHT + 2f * GROUP_BORDER;
    /** The raised group with the buttons. */
    public static final float BUTTON_AREA_HEIGHT = BUTTON_HEIGHT + 2f * GROUP_BORDER;
    public static final float HEIGHT = CAPTION_HEIGHT + MESSAGE_AREA_HEIGHT + BUTTON_AREA_HEIGHT + BORDER;
    /**
     * The blue face of the caption skin {@code gui.window_mid_active_caption} (24 rows): rows 0-1 are its light top
     * edge, rows 2-19 the face, rows 20-23 its dark and light lower edge.
     */
    public static final float CAPTION_FACE_TOP = 2f;
    public static final float CAPTION_FACE_BOTTOM = 20f;

    private final float scale;
    private final float x;
    private final float y;

    private ConfirmDialogLayout(float scale, float x, float y) {
        this.scale = scale;
        this.x = x;
        this.y = y;
    }

    /** The dialog for {@code layout}; {@code collapsed} moves the 3D area's lower edge down to the caption strip. */
    public static ConfirmDialogLayout of(PanelLayout layout, boolean collapsed) {
        float scale = layout.scale();
        float width = WIDTH * scale;
        float height = HEIGHT * scale;
        float bottom = layout.panelHeight(collapsed);
        float top = layout.titleBar().y();
        float y = (bottom + top) / 2f - height / 2f;
        if (y + height > top) {
            y = top - height;
        }
        return new ConfirmDialogLayout(scale, (layout.width() - width) / 2f, y);
    }

    /** A rectangle of the dialog in virtual units from its top left corner (y downwards). */
    private PanelLayout.Rect rect(float left, float top, float width, float height) {
        return new PanelLayout.Rect(x + left * scale, y + (HEIGHT - top - height) * scale, width * scale,
                height * scale);
    }

    public PanelLayout.Rect window() {
        return rect(0f, 0f, WIDTH, HEIGHT);
    }

    public PanelLayout.Rect caption() {
        return rect(0f, 0f, WIDTH, CAPTION_HEIGHT);
    }

    /** The caption text: left-aligned after the frame (ToB 2009 {@code Window.paintWindow}), the caption's face. */
    public PanelLayout.Rect captionTextFace() {
        return rect(BORDER, CAPTION_FACE_TOP, WIDTH - 2f * BORDER, CAPTION_FACE_BOTTOM - CAPTION_FACE_TOP);
    }

    public PanelLayout.Rect messageArea() {
        return rect(BORDER, CAPTION_HEIGHT, WIDTH - 2f * BORDER, MESSAGE_AREA_HEIGHT);
    }

    public PanelLayout.Rect messageGroup() {
        return rect(BORDER + GROUP_BORDER, CAPTION_HEIGHT + GROUP_BORDER, WIDTH - 2f * (BORDER + GROUP_BORDER),
                MESSAGE_GROUP_HEIGHT);
    }

    /** The question: centred in the sunken group inside its border. */
    public PanelLayout.Rect messageTextFace() {
        float inset = BORDER + 2f * GROUP_BORDER;
        return rect(inset, CAPTION_HEIGHT + 2f * GROUP_BORDER, WIDTH - 2f * inset, 2f * PADDING + TEXT_LINE);
    }

    public PanelLayout.Rect buttonArea() {
        return rect(BORDER, CAPTION_HEIGHT + MESSAGE_AREA_HEIGHT, WIDTH - 2f * BORDER, BUTTON_AREA_HEIGHT);
    }

    public PanelLayout.Rect button(Answer answer) {
        float right = WIDTH - BORDER - GROUP_BORDER - (answer == Answer.YES ? BUTTON_WIDTH : 0f);
        return rect(right - BUTTON_WIDTH, CAPTION_HEIGHT + MESSAGE_AREA_HEIGHT + GROUP_BORDER, BUTTON_WIDTH,
                BUTTON_HEIGHT);
    }

    /** A button's label: centred inside the button's border. */
    public PanelLayout.Rect buttonTextFace(Answer answer) {
        PanelLayout.Rect button = button(answer);
        float border = BUTTON_BORDER * scale;
        return new PanelLayout.Rect(button.x() + border, button.y() + border, button.width() - 2f * border,
                button.height() - 2f * border);
    }

    /** Whether a screen point lies on the dialog. */
    public boolean contains(float px, float py) {
        return window().contains(px, py);
    }

    /** The button at a screen point, if any. */
    public Optional<Answer> answerAt(float px, float py) {
        for (Answer answer : Answer.values()) {
            if (button(answer).contains(px, py)) {
                return Optional.of(answer);
            }
        }
        return Optional.empty();
    }

    public float scale() {
        return scale;
    }
}
