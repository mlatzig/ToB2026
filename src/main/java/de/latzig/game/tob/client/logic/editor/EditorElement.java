package de.latzig.game.tob.client.logic.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import de.latzig.game.tob.core.model.BaseType;
import de.latzig.game.tob.core.model.ObjectType;

/**
 * One entry of the Tower Designer's palette (Phase 6b, ToB 2009's {@code EditorWindow.ELEMENT_IDS}): a base element —
 * floor, box (the original's support), up lift, down lift — or one of the 36 objects, each facing its own entry. The
 * palette lists the four base elements, then the objects in ToB 2009's order, packed without ToB 2009's blank row
 * (spec decision 2). Each entry has its GUI atlas sprite ({@code editor.*}) and its name in the bundle.
 *
 * @param kind   what the entry places
 * @param object the object of an {@link Kind#OBJECT} entry, else null
 */
public record EditorElement(Kind kind, ObjectType object) {

    /** What an entry places. */
    public enum Kind {
        FLOOR, BOX, LIFT_UP, LIFT_DOWN, OBJECT
    }

    /** ToB 2009's object order of the palette ({@code EditorWindow.java:227-242}). */
    private static final List<ObjectType> OBJECT_ORDER = List.of(ObjectType.ZAP, ObjectType.GRB, ObjectType.PSH,
            ObjectType.KLD, ObjectType.FZN, ObjectType.FZE, ObjectType.FZS, ObjectType.FZW, ObjectType.FPN,
            ObjectType.FPE, ObjectType.FPS, ObjectType.FPW, ObjectType.RZN, ObjectType.RPN, ObjectType.WOE,
            ObjectType.WON, ObjectType.WAT, ObjectType.HOP, ObjectType.PRM, ObjectType.TMB, ObjectType.BLK,
            ObjectType.REL, ObjectType.PNW, ObjectType.PNE, ObjectType.PSE, ObjectType.PSW, ObjectType.LZE,
            ObjectType.LZN, ObjectType.BFL, ObjectType.CVN, ObjectType.CVE, ObjectType.FCU, ObjectType.FCD,
            ObjectType.FRZ, ObjectType.WIP, ObjectType.EXC);

    public static final EditorElement FLOOR = new EditorElement(Kind.FLOOR, null);
    public static final EditorElement BOX = new EditorElement(Kind.BOX, null);
    public static final EditorElement LIFT_UP = new EditorElement(Kind.LIFT_UP, null);
    public static final EditorElement LIFT_DOWN = new EditorElement(Kind.LIFT_DOWN, null);

    /** The base elements of the palette's first row. */
    public static final int BASE_COUNT = 4;
    /** The palette: the four base elements, then the 36 objects. */
    public static final List<EditorElement> PALETTE = palette();

    public EditorElement {
        Objects.requireNonNull(kind, "kind");
        if ((kind == Kind.OBJECT) != (object != null)) {
            throw new IllegalArgumentException("an object entry needs its object, a base entry none: " + kind);
        }
    }

    public static EditorElement of(ObjectType object) {
        return new EditorElement(Kind.OBJECT, object);
    }

    private static List<EditorElement> palette() {
        List<EditorElement> entries = new ArrayList<>(List.of(FLOOR, BOX, LIFT_UP, LIFT_DOWN));
        for (ObjectType object : OBJECT_ORDER) {
            entries.add(of(object));
        }
        return List.copyOf(entries);
    }

    /** The palette sprite: ToB 2009's {@code EditorPartIDs} (the lifts' "1" sprites). */
    public String region() {
        return switch (kind) {
            case FLOOR -> region(BaseType.FLOOR);
            case BOX -> region(BaseType.BOX);
            case LIFT_UP -> region(BaseType.LIFT_UP_START);
            case LIFT_DOWN -> region(BaseType.LIFT_DOWN_START);
            case OBJECT -> region(object);
        };
    }

    /** The bundle key of the entry's name (ToB 2009's {@code tower.elements.constants.*}). */
    public String textKey() {
        return switch (kind) {
            case FLOOR -> "editor.element.flr";
            case BOX -> "editor.element.box";
            case LIFT_UP -> "editor.element.lfu";
            case LIFT_DOWN -> "editor.element.lfd";
            case OBJECT -> "editor.element." + object.code().toLowerCase(Locale.ROOT);
        };
    }

    /**
     * The grid sprite of a base element: a lift's start shows ToB 2009's "1" sprite (the palette's), its other cell
     * the "2" sprite ToB 2009 drew over the cell above a lift (a down lift's stop lies below its start).
     */
    public static String region(BaseType base) {
        return switch (base) {
            case EMPTY -> "editor.spc";
            case FLOOR -> "editor.flr";
            case BOX -> "editor.box";
            case LIFT_UP_START -> "editor.lfu1";
            case LIFT_UP_STOP -> "editor.lfu2";
            case LIFT_DOWN_START -> "editor.lfd1";
            case LIFT_DOWN_STOP -> "editor.lfd2";
        };
    }

    /** The sprite of an object, e.g. {@code editor.zap}. */
    public static String region(ObjectType object) {
        return "editor." + object.code().toLowerCase(Locale.ROOT);
    }
}
