/**
 * Headless, deterministic simulation core. One step is one original game frame (nominal 7.14 frames/s; the client
 * owns the rate). Reproduces the original frame pipeline, live occupancy and rules (behaviour oracle, report 11) and
 * emits events instead of calling renderers. Depends on the JDK and {@code core.model} only.
 */
package de.latzig.game.tob.core.sim;
