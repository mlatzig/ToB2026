package de.latzig.game.tob.core.sim;

/**
 * Facing and walking direction in the original order 0 N, 1 E, 2 S, 3 W (offsets {@code W002A98}): turning right is
 * +1, left −1, reversing +2 ({@code S:8722-8729}, {@code S:8695-8721}). North is row y − 1.
 */
public enum Direction {
    NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0);

    private static final Direction[] VALUES = values();

    private final int dx;
    private final int dy;

    Direction(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }

    public int dx() {
        return dx;
    }

    public int dy() {
        return dy;
    }

    public Direction left() {
        return VALUES[(ordinal() + 3) % 4];
    }

    public Direction right() {
        return VALUES[(ordinal() + 1) % 4];
    }

    public Direction opposite() {
        return VALUES[(ordinal() + 2) % 4];
    }
}
