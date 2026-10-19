package de.latzig.game.tob.client.input;

import java.util.Objects;

import com.jme3.cursors.plugins.JmeCursor;
import com.jme3.input.RawInputListener;
import com.jme3.input.event.JoyAxisEvent;
import com.jme3.input.event.JoyButtonEvent;
import com.jme3.input.event.KeyInputEvent;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.input.event.TouchEvent;
import com.jme3.util.BufferUtils;

/**
 * Raw mouse listener (Phase 3b-2 spec §4.1): the cursor position in GUI coordinates (pixels, origin bottom left, as
 * jME reports it) and the left button; the other buttons and the wheel are not used. The OS cursor keeps moving the
 * position with the OS's speed and acceleration; it is only made invisible ({@link #hiddenCursor()}), so the game draws
 * the ToB 2009 software cursor there.
 */
public final class MouseInput implements RawInputListener {

    /** Receives the left button's presses and releases with the cursor position. */
    public interface Listener {
        void leftPressed(float x, float y);

        void leftReleased(float x, float y);
    }

    /** Task 9 review (addition A): created once and reused, {@link #hiddenCursor()}. */
    private static JmeCursor hiddenCursor;

    private final Listener listener;
    private float x;
    private float y;
    private boolean leftDown;

    public MouseInput(Listener listener) {
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    /**
     * A fully transparent 2 × 2 native cursor. {@code InputManager.setCursorVisible(false)} is not usable: under
     * LWJGL 3 it switches GLFW to {@code GLFW_CURSOR_DISABLED}, which captures the mouse and reports unbounded
     * relative positions ({@code GlfwMouseInput.setCursorVisible}). A transparent cursor image keeps the OS cursor
     * behaviour and hides it.
     * <p>
     * Task 9 review (addition A): jME 3.9's GLFW mouse input keeps a native cursor per instance for as long as the
     * application runs, so creating a fresh one on every call (every {@code GameState.onEnable}, i.e. every tower
     * start) leaks one native cursor per start. The cursor is immutable once built, so the first call creates it and
     * every later call returns the same instance.
     */
    public static JmeCursor hiddenCursor() {
        if (hiddenCursor == null) {
            JmeCursor cursor = new JmeCursor();
            cursor.setWidth(2);
            cursor.setHeight(2);
            cursor.setNumImages(1);
            cursor.setxHotSpot(0);
            cursor.setyHotSpot(1);
            cursor.setImagesData(BufferUtils.createIntBuffer(4));
            hiddenCursor = cursor;
        }
        return hiddenCursor;
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public boolean isLeftDown() {
        return leftDown;
    }

    /**
     * Drops a held left button without firing {@link Listener#leftReleased} (Task 9 review, addition B): a release
     * missed while this listener is detached, e.g. in {@code GameState.onDisable}, must not keep the button looking
     * held for whoever reads {@link #isLeftDown()} next.
     */
    public void resetHeld() {
        leftDown = false;
    }

    /**
     * Seeds the cursor position (final review M3): {@code GameState.onEnable} calls this from
     * {@code InputManager.getCursorPosition()} right after attaching this listener, because jME has already tracked
     * the OS cursor's position while the previous screen was shown, but x/y here still default to 0 until the next
     * real motion event; without the seed, the software cursor is drawn off-window (invisible) after every tower
     * start or restart until the mouse moves. Does not fire a press or a release.
     */
    public void seedPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public void onMouseMotionEvent(MouseMotionEvent event) {
        x = event.getX();
        y = event.getY();
    }

    @Override
    public void onMouseButtonEvent(MouseButtonEvent event) {
        if (event.getButtonIndex() != com.jme3.input.MouseInput.BUTTON_LEFT) {
            return;
        }
        x = event.getX();
        y = event.getY();
        event.setConsumed();
        if (event.isPressed() && !leftDown) {
            leftDown = true;
            listener.leftPressed(x, y);
        } else if (event.isReleased() && leftDown) {
            leftDown = false;
            listener.leftReleased(x, y);
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
    public void onKeyEvent(KeyInputEvent event) {
    }

    @Override
    public void onTouchEvent(TouchEvent event) {
    }
}
