package de.latzig.game.tob.core.progress;

/**
 * The original's tower access rules (Phase 5a spec decision 7). A group holds 9 towers A–I as a 3×3 grid (A B C /
 * D E F / G H I), index 0–8 = the tower's {@code index} in the library. Masks have bit {@code i} for tower {@code i}
 * (A = bit 0), as the original's {@code d0}/{@code d1} words in {@code L00810C} ({@code S:6136-6157}).
 */
public final class Progression {

    public static final int GROUPS = 13;
    public static final int TOWERS_PER_GROUP = 9;
    public static final int TOWERS = GROUPS * TOWERS_PER_GROUP;
    /** At least this many completed towers of a group open the next group ({@code S:6410-6411}). */
    public static final int TO_ADVANCE = 7;
    public static final int LAST_GROUP = GROUPS - 1;
    /** With nothing completed, the top row A–C is open ({@code S:6383-6385}). */
    public static final int FIRST_ROW = 0b000000111;
    /**
     * The tower access matrix {@code L0080F2} ({@code S:6126-6135}): row {@code i} = the towers completing tower
     * {@code i} opens. Row G is {@code $0018} (D and E) in the original, not D and H; kept as the original has it.
     */
    private static final int[] ACCESS = {0x00A, 0x015, 0x022, 0x051, 0x0AA, 0x114, 0x018, 0x150, 0x0A0};

    private Progression() {
    }

    /** The mask of the completed towers of a group ({@code completed[i]} for tower {@code i}). */
    public static int doneMask(boolean[] completed) {
        if (completed.length != TOWERS_PER_GROUP) {
            throw new IllegalArgumentException("a group has 9 towers: " + completed.length);
        }
        int mask = 0;
        for (int i = 0; i < TOWERS_PER_GROUP; i++) {
            if (completed[i]) {
                mask |= 1 << i;
            }
        }
        return mask;
    }

    /**
     * The accessible towers of a group: the rows of every completed tower ORed, minus the completed towers
     * ({@code or d0,d1 / eor d0,d1}, {@code S:6153-6154}); the top row while none is completed.
     */
    public static int accessibleMask(int doneMask) {
        if (doneMask == 0) {
            return FIRST_ROW;
        }
        int open = 0;
        for (int i = 0; i < TOWERS_PER_GROUP; i++) {
            if ((doneMask & (1 << i)) != 0) {
                open |= ACCESS[i];
            }
        }
        return (open | doneMask) ^ doneMask;
    }

    public static boolean isAccessible(int doneMask, int index) {
        return (accessibleMask(doneMask) & (1 << index)) != 0;
    }

    /** "Go Back to Last Group" ({@code S:6405-6409}). */
    public static boolean canGoBack(int group) {
        return group > 0;
    }

    /** "Go Forward to Next Group": 7 of the group's 9 completed and not the last group ({@code S:6410-6414}). */
    public static boolean canGoForward(int group, int doneMask) {
        return Integer.bitCount(doneMask) >= TO_ADVANCE && group < LAST_GROUP;
    }

    /** The "(xx%)" of the tower screen ({@code S:7456-7471}): of 117, rounded up unless the quotient is 99. */
    public static int completedPercent(int completed) {
        int scaled = completed * 100;
        int percent = scaled / TOWERS;
        return percent != 99 && scaled % TOWERS != 0 ? percent + 1 : percent;
    }

    /** The tower's letter A–I. */
    public static char letter(int index) {
        return (char) ('A' + index);
    }
}
