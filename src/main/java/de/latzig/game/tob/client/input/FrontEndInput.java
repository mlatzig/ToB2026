package de.latzig.game.tob.client.input;

import java.util.Objects;
import java.util.Optional;
import java.util.function.IntFunction;

import com.jme3.input.KeyInput;
import com.jme3.input.RawInputListener;
import com.jme3.input.event.JoyAxisEvent;
import com.jme3.input.event.JoyButtonEvent;
import com.jme3.input.event.KeyInputEvent;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.input.event.TouchEvent;

import de.latzig.game.tob.client.logic.ui.UiKey;
import de.latzig.game.tob.core.progress.Profile;

/**
 * Raw input of the start screens (Phase 5a). Keys by key code ({@link #uiKey}, independent of the keyboard layout);
 * Up/Down/Left/Right, Backspace and Delete also repeat while held (jME/GLFW delivers the OS repeat as
 * {@code pressed=false, repeating=true}), Enter, Space, Tab and Esc do not. Typed text arrives as GLFW character
 * events, which jME 3.9 hands over as {@code KEY_UNKNOWN} events with the character ({@code GlfwKeyInput}'s char
 * callback, a press and a release per character): the operating system's keyboard layout applies (spec §5). Only
 * printable ASCII ({@link Profile#isNameChar}) is passed on; umlauts and every other non-ASCII character are dropped
 * here (author decision, 2026-10-12). The mouse reports the cursor position (GUI pixels) and left clicks; telling a
 * double click from two clicks needs the clicked widget and is {@code DoubleClick}'s business.
 * <p>
 * Phase 5b: every key press (not the OS repeat, not the left Ctrl key itself) is first offered raw, with the left
 * Ctrl state, to {@link Listener#pressed} — the Options window's key capture takes it there, Tab, the arrows and
 * Esc included; only an untaken press becomes a menu key. Page Up and Page Down are menu keys (the KEYS list,
 * the Play list and the Hall of Fame's tabs); the wheel reports notches and the left button's release is
 * reported (dragging a scroll bar's thumb).
 * <p>
 * Phase 6b (the Tower Designer): the right button's press is reported ({@link Listener#rightClicked}: remove), and the
 * left Ctrl key with Z or Y becomes {@link UiKey#UNDO} or {@link UiKey#REDO}, repeating while held: the keys the
 * keyboard layout labels Z and Y ({@link #undoKeys}; on a German keyboard they swap places), by position where the
 * layout's names are unknown (headless).
 */
public final class FrontEndInput implements RawInputListener {

    /** jME 3.9's GLFW wheel delta per notch ({@code GlfwMouseInput.WHEEL_SCALE}). */
    public static final int WHEEL_DELTA = 120;

    /** Receives the start screens' input. */
    public interface Listener {
        void key(UiKey key);

        void typed(char c);

        void moved(float x, float y);

        void clicked(float x, float y);

        /** A key press, raw (Phase 5b); true if it was taken and must not become a menu key. */
        default boolean pressed(int keyCode, boolean leftCtrl) {
            return false;
        }

        /** Wheel notches (Phase 5b), positive away from the player. */
        default void wheel(int notches) {
        }

        /** The left button was released (Phase 5b). */
        default void released(float x, float y) {
        }

        /** The right button went down (Phase 6b: the Tower Designer removes). */
        default void rightClicked(float x, float y) {
        }
    }

    private final Listener listener;
    private float x;
    private float y;
    private boolean leftCtrl;
    /** Phase 6b: the keyboard layout's name of a jME key code, asked at each Ctrl press (Ctrl+Z and Ctrl+Y). */
    private IntFunction<String> keyNames = code -> null;

    public FrontEndInput(Listener listener) {
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    /** The start screens' key for a jME key code, if it is one of theirs. */
    public static Optional<UiKey> uiKey(int keyCode) {
        return Optional.ofNullable(switch (keyCode) {
            case KeyInput.KEY_UP -> UiKey.UP;
            case KeyInput.KEY_DOWN -> UiKey.DOWN;
            case KeyInput.KEY_LEFT -> UiKey.LEFT;
            case KeyInput.KEY_RIGHT -> UiKey.RIGHT;
            case KeyInput.KEY_TAB -> UiKey.TAB;
            case KeyInput.KEY_RETURN, KeyInput.KEY_NUMPADENTER -> UiKey.ENTER;
            case KeyInput.KEY_SPACE -> UiKey.SPACE;
            case KeyInput.KEY_ESCAPE -> UiKey.ESCAPE;
            case KeyInput.KEY_BACK -> UiKey.BACKSPACE;
            case KeyInput.KEY_DELETE -> UiKey.DELETE;
            case KeyInput.KEY_HOME -> UiKey.HOME;
            case KeyInput.KEY_END -> UiKey.END;
            case KeyInput.KEY_PGUP -> UiKey.PAGE_UP;
            case KeyInput.KEY_PGDN -> UiKey.PAGE_DOWN;
            default -> null;
        });
    }

    /** The wheel notches of a jME wheel delta: at least one per event, positive away from the player. */
    public static int notches(int deltaWheel) {
        return Integer.signum(deltaWheel) * Math.max(1, Math.round(Math.abs(deltaWheel) / (float) WHEEL_DELTA));
    }

    private static boolean repeats(UiKey key) {
        return key != UiKey.ENTER && key != UiKey.SPACE && key != UiKey.TAB && key != UiKey.ESCAPE;
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    /**
     * Phase 6b: Ctrl+Z and Ctrl+Y on the keys the keyboard layout labels Z and Y; {@code names} is the layout's name
     * of a jME key code ({@code InputManager.getKeyName}, GLFW's key names). It is asked at each press, so a layout
     * switched while the game runs applies at once (acceptance change 2).
     */
    public void undoKeys(IntFunction<String> names) {
        keyNames = Objects.requireNonNull(names, "names");
    }

    /**
     * Undo or redo for Ctrl with {@code keyCode}: by the layout's name of that key ("z" undoes, "y" redoes, any
     * other letter or name nothing); where the key has no single-letter name (headless: "Unknown", none, or the
     * name source fails), by position, the US layout's Z and Y.
     */
    static Optional<UiKey> undoRedo(int keyCode, IntFunction<String> names) {
        String name;
        try {
            name = names.apply(keyCode);
        } catch (RuntimeException e) {
            name = null;
        }
        if (name == null || name.length() != 1) {
            return Optional.ofNullable(keyCode == KeyInput.KEY_Z ? UiKey.UNDO
                    : keyCode == KeyInput.KEY_Y ? UiKey.REDO : null);
        }
        return Optional.ofNullable(name.equalsIgnoreCase("z") ? UiKey.UNDO
                : name.equalsIgnoreCase("y") ? UiKey.REDO : null);
    }

    /** Seeds the cursor position from the input manager when the screens are shown (as {@code MouseInput}). */
    public void seedPosition(float cursorX, float cursorY) {
        x = cursorX;
        y = cursorY;
    }

    /**
     * M2 (final review): clears the left-Ctrl state. Call when the start screens are (re)entered or left, so a
     * Ctrl held across the switch (e.g. held while a tower starts, released in the game) never leaks into the next
     * key capture here.
     */
    public void reset() {
        leftCtrl = false;
    }

    @Override
    public void onKeyEvent(KeyInputEvent event) {
        if (event.getKeyCode() == KeyInput.KEY_UNKNOWN && event.getKeyChar() != 0) {
            char c = event.getKeyChar();
            if (event.isPressed() && Profile.isNameChar(c)) {
                event.setConsumed();
                listener.typed(c);
            }
            return;
        }
        if (event.getKeyCode() == KeyInput.KEY_LCONTROL) {
            if (!event.isRepeating()) {
                leftCtrl = event.isPressed();
            }
            return;
        }
        if (event.isPressed() && event.getKeyCode() != KeyInput.KEY_UNKNOWN
                && listener.pressed(event.getKeyCode(), leftCtrl)) {
            event.setConsumed();
            return;
        }
        if (leftCtrl && (event.isPressed() || event.isRepeating()) && event.getKeyCode() != KeyInput.KEY_UNKNOWN) {
            Optional<UiKey> step = undoRedo(event.getKeyCode(), keyNames);
            if (step.isPresent()) {
                event.setConsumed();
                listener.key(step.get());
                return;
            }
        }
        Optional<UiKey> key = uiKey(event.getKeyCode());
        if (key.isEmpty()) {
            return;
        }
        if (event.isPressed() || event.isRepeating() && repeats(key.get())) {
            event.setConsumed();
            listener.key(key.get());
        }
    }

    @Override
    public void onMouseMotionEvent(MouseMotionEvent event) {
        if (event.getDeltaWheel() != 0) {
            listener.wheel(notches(event.getDeltaWheel()));
        }
        x = event.getX();
        y = event.getY();
        listener.moved(x, y);
    }

    @Override
    public void onMouseButtonEvent(MouseButtonEvent event) {
        if (event.getButtonIndex() == com.jme3.input.MouseInput.BUTTON_RIGHT && event.isPressed()) {
            x = event.getX();
            y = event.getY();
            event.setConsumed();
            listener.rightClicked(x, y);
            return;
        }
        if (event.getButtonIndex() != com.jme3.input.MouseInput.BUTTON_LEFT) {
            return;
        }
        x = event.getX();
        y = event.getY();
        event.setConsumed();
        if (event.isPressed()) {
            listener.clicked(x, y);
        } else {
            listener.released(x, y);
        }
    }

    @Override
    public void beginInput() {
    }

    @Override
    public void endInput() {
    }

    @Override
    public void onJoyAxisEvent(JoyAxisEvent event) {
    }

    @Override
    public void onJoyButtonEvent(JoyButtonEvent event) {
    }

    @Override
    public void onTouchEvent(TouchEvent event) {
    }
}
