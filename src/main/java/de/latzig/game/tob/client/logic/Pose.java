package de.latzig.game.tob.client.logic;

/**
 * A render position in fields (1 field = 1.0): x west → east, y up (level 0 bottom = 0), z north → south (grid row).
 * The scene uses the same axes, so a cell's bottom centre is (x, level, y).
 */
public record Pose(float x, float y, float z) {
}
