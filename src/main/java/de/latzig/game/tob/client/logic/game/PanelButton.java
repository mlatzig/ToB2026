package de.latzig.game.tob.client.logic.game;

import java.util.List;

/**
 * The 27 buttons of the ToB 2009 panel ({@code GamePanelWindowNormal.java:124-237}) with their virtual rectangle
 * (x, y, width, height in the 1024 × 256 button area, y downwards), the atlas region name without the
 * {@code panel.} prefix and the 0/1 suffix, and the action of the matching key. The program slots show the icon of
 * their step instead of the fixed {@code prg_spc} region (see {@code PanelState}).
 */
public enum PanelButton {
    ZAPPER(12, 8, 200, 60, "zapper", GameAction.SELECT_ZAPPER),
    PUSHER(12, 68, 200, 60, "pusher", GameAction.SELECT_PUSHER),
    GRABBER(12, 128, 200, 60, "grabber", GameAction.SELECT_GRABBER),
    NORTH(12, 188, 50, 60, "north", GameAction.CAMERA_NORTH),
    EAST(62, 188, 50, 60, "east", GameAction.CAMERA_EAST),
    SOUTH(112, 188, 50, 60, "south", GameAction.CAMERA_SOUTH),
    WEST(162, 188, 50, 60, "west", GameAction.CAMERA_WEST),
    FIRE(212, 8, 400, 60, "fire", GameAction.FIRE),
    LEFT(212, 68, 100, 120, "left", GameAction.TURN_LEFT),
    UP(312, 68, 200, 60, "up", GameAction.FORWARD),
    DOWN(312, 128, 200, 60, "down", GameAction.BACK),
    RIGHT(512, 68, 100, 120, "right", GameAction.TURN_RIGHT),
    PAUSE(212, 188, 400, 60, "pause", GameAction.PAUSE),
    STOP(612, 8, 200, 60, "stop", GameAction.PROGRAM_STOP),
    RUN(612, 68, 200, 60, "run", GameAction.PROGRAM_RUN),
    RUN_ALL(612, 128, 200, 60, "run_all", GameAction.PROGRAM_RUN_ALL),
    WRITE(812, 8, 200, 60, "write", GameAction.PROGRAM_WRITE),
    DELETE(812, 68, 200, 60, "delete", GameAction.PROGRAM_DELETE),
    CLEAR(812, 128, 200, 60, "clear", GameAction.PROGRAM_CLEAR),
    SLOT_1(612, 188, 50, 60, "prg_spc", GameAction.PROGRAM_SLOT_1),
    SLOT_2(662, 188, 50, 60, "prg_spc", GameAction.PROGRAM_SLOT_2),
    SLOT_3(712, 188, 50, 60, "prg_spc", GameAction.PROGRAM_SLOT_3),
    SLOT_4(762, 188, 50, 60, "prg_spc", GameAction.PROGRAM_SLOT_4),
    SLOT_5(812, 188, 50, 60, "prg_spc", GameAction.PROGRAM_SLOT_5),
    SLOT_6(862, 188, 50, 60, "prg_spc", GameAction.PROGRAM_SLOT_6),
    SLOT_7(912, 188, 50, 60, "prg_spc", GameAction.PROGRAM_SLOT_7),
    SLOT_8(962, 188, 50, 60, "prg_spc", GameAction.PROGRAM_SLOT_8);

    /** The eight program slots in order. */
    public static final List<PanelButton> SLOTS = List.of(SLOT_1, SLOT_2, SLOT_3, SLOT_4, SLOT_5, SLOT_6, SLOT_7,
            SLOT_8);

    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final String region;
    private final GameAction action;

    PanelButton(int x, int y, int width, int height, String region, GameAction action) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.region = region;
        this.action = action;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** The atlas region without prefix and suffix, e.g. {@code run_all} for {@code panel.run_all0}. */
    public String region() {
        return region;
    }

    /** The action a click on this button triggers, the same as its key. */
    public GameAction action() {
        return action;
    }

    /** The slot index 0–7, or −1 for a button that is not a program slot. */
    public int slot() {
        return SLOTS.indexOf(this);
    }
}
