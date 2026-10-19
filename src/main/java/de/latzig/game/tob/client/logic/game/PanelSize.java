package de.latzig.game.tob.client.logic.game;

/**
 * The game panel's size (Phase 5b decision 6, the two variants of the Phase 3b-2 spec §2): {@link #FULL_WIDTH}
 * scales the 1024-unit panel to the window width (variant B, the default), {@link #FIT_HEIGHT} scales it by the window
 * height / 768 and centres it (variant A, ToB 2009's 4:3 panel).
 */
public enum PanelSize {
    FULL_WIDTH, FIT_HEIGHT
}
