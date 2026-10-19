package de.latzig.game.tob.core.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.ObjectType;

/** The descriptor table {@code L012C96} ({@code S:17779-17806}) as decoded by {@code tob_objtable.py}. */
class ObjectDescriptorTest {

    /** Decoder output: object index 4..39 in ObjectType order, "flags=" and "dir=" columns. */
    private static final int[][] DECODED = {
            {0x0005, 0}, {0x0005, 0}, {0x0005, 0},
            {0x0001, 0}, {0x0001, 1}, {0x0001, 2}, {0x0001, 3},
            {0x0000, 0}, {0x0000, 1}, {0x0000, 2}, {0x0000, 3},
            {0x0001, 0}, {0x0000, 0},
            {0x0804, 0}, {0x0804, 1}, {0x0204, 0}, {0x0001, 0}, {0x0204, 0}, {0x0204, 0}, {0x0004, 0},
            {0x0006, 0}, {0x0024, 0}, {0x0024, 1}, {0x0024, 2}, {0x0024, 3},
            {0x0200, 0}, {0x0200, 1}, {0x0209, 0}, {0x0001, 0}, {0x0010, 0}, {0x0010, 1},
            {0x0041, 0}, {0x0041, 0}, {0x0081, 0}, {0x0285, 0}, {0x0082, 0}};

    @Test
    void everyTypeHasTheDecodedFlagsAndDirection() {
        ObjectType[] types = ObjectType.values();
        assertEquals(36, types.length);
        assertEquals(36, DECODED.length);
        for (int i = 0; i < types.length; i++) {
            assertEquals(DECODED[i][0], ObjectDescriptor.flags(types[i]), types[i].code() + " flags");
            assertEquals(Direction.values()[DECODED[i][1]], ObjectDescriptor.direction(types[i]),
                    types[i].code() + " direction");
        }
    }

    @Test
    void theFlagBitsMatchTheObjectSetsOfOracleSectionTwoOne() {
        expectedSets().forEach((bit, types) -> assertEquals(types, typesWith(bit), "bit " + Integer.toHexString(bit)));
    }

    @Test
    void robotsAreTheZappableNonSpidersAndTheExchanger() {
        Set<ObjectType> counted = EnumSet.noneOf(ObjectType.class);
        for (ObjectType type : ObjectType.values()) {
            if (ObjectDescriptor.countsAsRobot(type)) {
                counted.add(type);
            }
        }
        // the set TowerCheck used before Phase 2b (TowerCheck.COUNTABLE)
        assertEquals(EnumSet.of(ObjectType.FZN, ObjectType.FZE, ObjectType.FZS, ObjectType.FZW, ObjectType.RZN,
                ObjectType.HOP, ObjectType.KLD, ObjectType.BFL, ObjectType.FCU, ObjectType.FCD, ObjectType.FRZ,
                ObjectType.WIP, ObjectType.EXC), counted);
    }

    private static Set<ObjectType> typesWith(int bit) {
        Set<ObjectType> types = EnumSet.noneOf(ObjectType.class);
        for (ObjectType type : ObjectType.values()) {
            if ((ObjectDescriptor.flags(type) & bit) != 0) {
                types.add(type);
            }
        }
        return types;
    }

    /** Oracle §2.1, column "Objects", per bit; the runtime bits start clear for every type. */
    private static Map<Integer, Set<ObjectType>> expectedSets() {
        Map<Integer, Set<ObjectType>> sets = new LinkedHashMap<>();
        sets.put(ObjectFlags.ZAPPABLE, EnumSet.of(ObjectType.ZAP, ObjectType.PSH, ObjectType.GRB, ObjectType.FZN,
                ObjectType.FZE, ObjectType.FZS, ObjectType.FZW, ObjectType.RZN, ObjectType.HOP, ObjectType.KLD,
                ObjectType.BFL, ObjectType.FCU, ObjectType.FCD, ObjectType.FRZ, ObjectType.WIP));
        sets.put(ObjectFlags.REFLECTS, EnumSet.of(ObjectType.REL, ObjectType.EXC));
        sets.put(ObjectFlags.PUSHABLE, EnumSet.of(ObjectType.ZAP, ObjectType.PSH, ObjectType.GRB, ObjectType.WON,
                ObjectType.WOE, ObjectType.WAT, ObjectType.PRM, ObjectType.TMB, ObjectType.BLK, ObjectType.REL,
                ObjectType.PNE, ObjectType.PSE, ObjectType.PSW, ObjectType.PNW, ObjectType.WIP));
        sets.put(ObjectFlags.KLONDIKE, EnumSet.of(ObjectType.KLD));
        sets.put(ObjectFlags.CONVERTER, EnumSet.of(ObjectType.CVN, ObjectType.CVE));
        sets.put(ObjectFlags.PRISM, EnumSet.of(ObjectType.PNE, ObjectType.PSE, ObjectType.PSW, ObjectType.PNW));
        sets.put(ObjectFlags.FORCER, EnumSet.of(ObjectType.FCU, ObjectType.FCD));
        sets.put(ObjectFlags.TOOL, EnumSet.of(ObjectType.FRZ, ObjectType.WIP, ObjectType.EXC));
        sets.put(ObjectFlags.KLONDIKE_GRABBED, EnumSet.noneOf(ObjectType.class));
        sets.put(ObjectFlags.EXPLOSION_VARIANT, EnumSet.of(ObjectType.WAT, ObjectType.PRM, ObjectType.TMB,
                ObjectType.LZN, ObjectType.LZE, ObjectType.KLD, ObjectType.WIP));
        sets.put(ObjectFlags.DESTROYS_FLOOR, EnumSet.noneOf(ObjectType.class));
        sets.put(ObjectFlags.ZAP_REVERSES, EnumSet.of(ObjectType.WON, ObjectType.WOE));
        return sets;
    }
}
