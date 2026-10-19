package de.latzig.game.tob.client.input;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import com.jme3.input.KeyInput;
import com.jme3.input.RawInputListener;
import com.jme3.input.event.JoyAxisEvent;
import com.jme3.input.event.JoyButtonEvent;
import com.jme3.input.event.KeyInputEvent;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.input.event.TouchEvent;

import de.latzig.game.tob.client.logic.game.GameAction;

/**
 * Raw keyboard listener: every key press that matches a binding fires one action, and the release of that key fires
 * the release of the same action (Phase 3b-2 hold-to-repeat, spec §4.2). The OS auto-repeat is ignored: jME/GLFW
 * delivers it as {@code pressed=false, repeating=true}, which is neither a press nor a release here. The left Ctrl
 * state is tracked for the ToB 2009 program keys; a key keeps the action it was pressed as, so releasing Ctrl before
 * Up still releases the program key that Ctrl+Up pressed.
 */
public final class KeyboardInput implements RawInputListener {

    private final KeyBindings bindings;
    private final Consumer<GameAction> pressed;
    private final Consumer<GameAction> released;
    private final Map<Integer, GameAction> down = new HashMap<>();
    private boolean leftCtrl;

    /** A listener for presses only (releases are dropped). */
    public KeyboardInput(KeyBindings bindings, Consumer<GameAction> pressed) {
        this(bindings, pressed, action -> {
        });
    }

    public KeyboardInput(KeyBindings bindings, Consumer<GameAction> pressed, Consumer<GameAction> released) {
        this.bindings = Objects.requireNonNull(bindings, "bindings");
        this.pressed = Objects.requireNonNull(pressed, "pressed");
        this.released = Objects.requireNonNull(released, "released");
    }

    @Override
    public void onKeyEvent(KeyInputEvent event) {
        if (event.isRepeating()) {
            // jME/GLFW auto-repeat delivers a held key as pressed=false, repeating=true (confirmed by the final
            // review's javap check on GlfwKeyInput$1): neither a press nor a release. For left Ctrl this keeps
            // leftCtrl true for as long as the key is actually held (Ctrl repeat finding, fixed before 3b).
            return;
        }
        if (event.getKeyCode() == KeyInput.KEY_LCONTROL) {
            leftCtrl = event.isPressed();
            return;
        }
        if (event.isPressed()) {
            bindings.lookup(event.getKeyCode(), leftCtrl).ifPresent(action -> {
                event.setConsumed();
                down.put(event.getKeyCode(), action);
                pressed.accept(action);
            });
        } else {
            GameAction action = down.remove(event.getKeyCode());
            if (action != null) {
                event.setConsumed();
                released.accept(action);
            }
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
    public void onMouseMotionEvent(MouseMotionEvent event) {
    }

    @Override
    public void onMouseButtonEvent(MouseButtonEvent event) {
    }

    @Override
    public void onTouchEvent(TouchEvent event) {
    }
}
