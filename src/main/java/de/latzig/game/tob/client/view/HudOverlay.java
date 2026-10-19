package de.latzig.game.tob.client.view;

import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial.CullHint;

import de.latzig.game.tob.client.logic.game.HudText;
import de.latzig.game.tob.client.logic.game.PanelLayout;
import de.latzig.game.tob.core.sim.WorldState;

/**
 * The text HUD in the ToB 2009 bitmap font (Phase 3b-2 spec §3.3): the message (READY, PAUSED, COMPLETED, FAILED,
 * TIME UP) large in the centre of the 3D view between the panel and the title bar, its colour pulsing between black
 * and white as in ToB 2009; the goal line below it; the two help lines just above the panel while paused. Sizes are
 * virtual and scale with the panel. The counters and the program are on the panel ({@code PanelView}).
 */
public final class HudOverlay {

    /** ToB 2009 {@code BIG_FONT_SIZE}. */
    public static final float MESSAGE_SIZE = 40f;
    public static final float GOAL_SIZE = 20f;
    public static final float HELP_SIZE = 11f;
    /** In front of the noise overlay (z = −1) and behind the panel (z ≥ 0). */
    public static final float Z = -0.5f;

    private final Node node = new Node("hud");
    private final BitmapText message;
    private final BitmapText goal;
    private final BitmapText help;

    public HudOverlay(BitmapFont font) {
        message = text(font, ColorRGBA.White);
        goal = text(font, ColorRGBA.White);
        help = text(font, new ColorRGBA(0.85f, 0.85f, 0.85f, 1f));
        help.setText(HudText.HELP);
    }

    public Node node() {
        return node;
    }

    /**
     * @param seconds    the time since the attempt started, for the pulse
     * @param dialogOpen the pause menu's ARE YOU SURE? dialog covers the centre: the message and the goal line are
     *                   hidden meanwhile (acceptance round 2, item 2)
     */
    public void update(WorldState world, boolean paused, double seconds, PanelLayout layout, boolean collapsed,
            boolean dialogOpen) {
        float scale = layout.scale();
        int width = layout.width();
        float bottom = layout.panelHeight(collapsed);
        float centre = (bottom + layout.titleBar().y()) / 2f;
        message.setSize(MESSAGE_SIZE * scale);
        message.setText(HudText.message(world, paused).orElse(""));
        float pulse = HudText.pulse(seconds);
        message.setColor(new ColorRGBA(pulse, pulse, pulse, 1f));
        message.setLocalTranslation((width - message.getLineWidth()) / 2f, centre + message.getHeight() / 2f, Z);
        goal.setSize(GOAL_SIZE * scale);
        goal.setText(HudText.goal(world, paused).orElse(""));
        goal.setLocalTranslation((width - goal.getLineWidth()) / 2f,
                centre - message.getHeight() / 2f - 8f * scale, Z);
        message.setCullHint(dialogOpen ? CullHint.Always : CullHint.Inherit);
        goal.setCullHint(dialogOpen ? CullHint.Always : CullHint.Inherit);
        help.setSize(HELP_SIZE * scale);
        help.setCullHint(paused ? CullHint.Inherit : CullHint.Always);
        help.setLocalTranslation(8f * scale, bottom + help.getHeight() + 8f * scale, Z);
    }

    private BitmapText text(BitmapFont font, ColorRGBA colour) {
        BitmapText text = new BitmapText(font);
        text.setColor(colour);
        node.attachChild(text);
        return text;
    }
}
