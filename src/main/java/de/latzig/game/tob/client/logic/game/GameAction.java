package de.latzig.game.tob.client.logic.game;

/**
 * Everything a key or a panel button can trigger. Steering, selection and panel actions become simulation commands
 * (queued, one per frame, mapped against the selected viewpoint by {@link InputMapper}); the client and developer
 * actions are handled by the game at once.
 */
public enum GameAction {
    FORWARD(Kind.STEERING),
    BACK(Kind.STEERING),
    TURN_LEFT(Kind.STEERING),
    TURN_RIGHT(Kind.STEERING),
    FIRE(Kind.STEERING),
    SELECT_ZAPPER(Kind.SELECTION),
    SELECT_PUSHER(Kind.SELECTION),
    SELECT_GRABBER(Kind.SELECTION),
    CAMERA_NORTH(Kind.SELECTION),
    CAMERA_EAST(Kind.SELECTION),
    CAMERA_SOUTH(Kind.SELECTION),
    CAMERA_WEST(Kind.SELECTION),
    ZOOM_IN(Kind.PANEL),
    ZOOM_OUT(Kind.PANEL),
    PROGRAM_FIRE(Kind.PANEL),
    PROGRAM_UP(Kind.PANEL),
    PROGRAM_DOWN(Kind.PANEL),
    PROGRAM_LEFT(Kind.PANEL),
    PROGRAM_RIGHT(Kind.PANEL),
    PROGRAM_WRITE(Kind.PANEL),
    PROGRAM_DELETE(Kind.PANEL),
    PROGRAM_CLEAR(Kind.PANEL),
    PROGRAM_SLOT_1(Kind.PANEL),
    PROGRAM_SLOT_2(Kind.PANEL),
    PROGRAM_SLOT_3(Kind.PANEL),
    PROGRAM_SLOT_4(Kind.PANEL),
    PROGRAM_SLOT_5(Kind.PANEL),
    PROGRAM_SLOT_6(Kind.PANEL),
    PROGRAM_SLOT_7(Kind.PANEL),
    PROGRAM_SLOT_8(Kind.PANEL),
    PROGRAM_STOP(Kind.PANEL),
    PROGRAM_RUN(Kind.PANEL),
    PROGRAM_RUN_ALL(Kind.PANEL),
    PANEL_CHANGE(Kind.CLIENT),
    PAUSE(Kind.CLIENT),
    MENU(Kind.CLIENT),
    MENU_CONFIRM(Kind.CLIENT),
    RESTART_TOWER(Kind.CLIENT),
    ABANDON_TOWER(Kind.CLIENT),
    TOGGLE_RECORDING(Kind.DEVELOPER);

    public enum Kind {
        /** Moves, turns or fires the selected spider, or moves the selected camera. */
        STEERING,
        /** Selects a spider or a camera (the view follows it). */
        SELECTION,
        /** The program buttons and the zoom of the ToB 2009 panel. */
        PANEL,
        /**
         * Handled by the client at once, never sent to the simulation: Tab shows or hides the panel, P pauses, Esc
         * opens and closes the pause menu, Enter chooses in it (Phase 3b-2); F9 and F10 ask ARE YOU SURE? and then
         * restart or abandon the tower (Phase 5a decision 1).
         */
        CLIENT,
        /**
         * Developer keys, handled by the client at once, never sent to the simulation: F12 toggles the developer
         * recording (Phase 3c rework; not in the release, Phase 7).
         */
        DEVELOPER
    }

    private final Kind kind;

    GameAction(Kind kind) {
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    /** Steering, selection and panel actions go through the command queue; client and developer keys do not. */
    public boolean isQueued() {
        return kind != Kind.CLIENT && kind != Kind.DEVELOPER;
    }
}
