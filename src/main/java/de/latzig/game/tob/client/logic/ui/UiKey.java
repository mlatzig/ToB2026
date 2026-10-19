package de.latzig.game.tob.client.logic.ui;

/**
 * The keys the start screens react to (Phase 5a), by key code, independent of the keyboard layout; typed text comes
 * separately as characters ({@code KeyInputEvent.getKeyChar()}, spec §5). ToB 2009's SJGL toolkit: Tab moves the
 * focus, Enter or Space press a button, Up/Down move in a list; Esc = Back is new in ToB 2026 (decision 6). Left and
 * Right also move the Overview's 3×3 grid selection by column (author decision, 2026-10-12). Phase 5b: Page Up and
 * Page Down scroll the KEYS list, the Play list and the Hall of Fame's tabs (Phase 5c-1). Phase 6b: Ctrl+Z and
 * Ctrl+Y (the left Ctrl key, Z and Y as the keyboard layout labels them) are the Tower Designer's undo and redo.
 */
public enum UiKey {
    UP, DOWN, LEFT, RIGHT, TAB, ENTER, SPACE, ESCAPE, BACKSPACE, DELETE, HOME, END, PAGE_UP, PAGE_DOWN, UNDO, REDO
}
