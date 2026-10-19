package de.latzig.game.tob.core.sim;

/**
 * A selectable point of view ({@code W001C1C}): one of the four cameras (0–3) or one of the three spiders (4–6). Every
 * viewpoint owns a program; camera programs never run, they only carry a program from one spider to another
 * ({@code S:8284-8317}).
 */
public sealed interface Viewpoint permits CameraId, SpiderId {
}
