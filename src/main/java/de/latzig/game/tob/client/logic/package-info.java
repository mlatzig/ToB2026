/**
 * Engine-free client logic. This package holds the building blocks the feature packages share (Phase 7a split):
 * render poses and interpolated motion with its carry and walk look-ahead, the generated base geometry and its
 * colours, meshes, keyframes and face frames, the socket, the floor area, camera placement and shadow range, and
 * the front-end texts. The feature packages: {@code effects}, {@code audio}, {@code game}, {@code ui},
 * {@code frontend} and {@code editor}. This package uses none of them; no jME or Lemur types (ArchUnit).
 */
package de.latzig.game.tob.client.logic;
