package de.latzig.game.tob.client.logic.frontend;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.latzig.game.tob.client.logic.game.GameAction;
import de.latzig.game.tob.client.logic.game.KeyChord;

/**
 * What the Options window needs to know about the keys (Phase 5b decision 5), handed over by {@code client.input}
 * so that this package stays engine-free: the default binding of every action, the fixed actions (Esc, Enter, F9,
 * F10, F12: shown, never remapped, their keys never given to another action) and the display names of the key codes
 * (jME's positional codes, named after the US layout).
 * <p>
 * Task 8 minor (final review): {@link #bindings} resolves a fixed or "bound twice" override exactly as
 * {@code KeyBindings.of} does (which calls this method), so the KEYS list and the game's actual bindings always
 * agree, even for a hand-edited {@code settings.json} that the Options window itself never writes.
 */
public record KeyTable(Map<GameAction, KeyChord> defaults, Set<GameAction> fixed, Map<Integer, String> names) {

    private static final Logger LOG = LoggerFactory.getLogger(KeyTable.class);

    public KeyTable {
        Map<GameAction, KeyChord> copy = new EnumMap<>(GameAction.class);
        copy.putAll(defaults);
        defaults = Collections.unmodifiableMap(copy);
        fixed = Set.copyOf(fixed);
        names = Map.copyOf(names);
    }

    /** {@code "Space"}, {@code "Ctrl+Up"}; {@code "Key <code>"} for a code without a name. */
    public String name(KeyChord chord) {
        String key = names.get(chord.keyCode());
        return (chord.leftCtrl() ? "Ctrl+" : "") + (key != null ? key : "Key " + chord.keyCode());
    }

    /** Whether the key code has a name, i.e. is a key an action can have (modifiers alone have none). */
    public boolean nameable(int keyCode) {
        return names.containsKey(keyCode);
    }

    /** Whether the key code belongs to a fixed action (with or without Ctrl): it cannot be given to another one. */
    public boolean reserved(int keyCode) {
        for (GameAction action : fixed) {
            KeyChord chord = defaults.get(action);
            if (chord != null && chord.keyCode() == keyCode) {
                return true;
            }
        }
        return false;
    }

    /**
     * The binding of every action: the defaults with the settings' {@code overrides}, resolving a fixed-action or
     * "bound twice" override silently. R4 (final review re-review): the Options window calls this every frame for
     * the KEYS list, so it must not log — a hand-edited conflicting file would otherwise spam hundreds of WARN
     * lines per second while the tab is open. {@link #bindings(Map, boolean)} with {@code log} true (used by
     * {@code KeyBindings.of}) logs the same resolution once, for the game's actual bindings.
     */
    public Map<GameAction, KeyChord> bindings(Map<GameAction, KeyChord> overrides) {
        return bindings(overrides, false);
    }

    /**
     * {@link #bindings(Map)}, optionally logging ({@code log}) an override of a fixed action or a chord bound
     * twice with another action's, instead of silently dropping it: that action keeps its default key either way.
     */
    public Map<GameAction, KeyChord> bindings(Map<GameAction, KeyChord> overrides, boolean log) {
        Map<GameAction, KeyChord> wanted = new EnumMap<>(GameAction.class);
        overrides.forEach((action, chord) -> {
            if (fixed.contains(action)) {
                if (log) {
                    LOG.warn("Ignoring the key {} for the fixed action {}", chord.toText(), action);
                }
            } else {
                wanted.put(action, chord);
            }
        });
        Map<GameAction, KeyChord> bindings;
        boolean dropped;
        do {
            bindings = new EnumMap<>(defaults);
            bindings.putAll(wanted);
            Map<KeyChord, List<GameAction>> byChord = new HashMap<>();
            bindings.forEach((action, chord) -> byChord.computeIfAbsent(chord, c -> new ArrayList<>()).add(action));
            dropped = false;
            for (List<GameAction> actions : byChord.values()) {
                if (actions.size() > 1) {
                    for (GameAction action : actions) {
                        if (wanted.remove(action) != null) {
                            if (log) {
                                LOG.warn("Ignoring the key of {}: bound twice with {}", action, actions);
                            }
                            dropped = true;
                        }
                    }
                }
            }
        } while (dropped);
        return bindings;
    }
}
