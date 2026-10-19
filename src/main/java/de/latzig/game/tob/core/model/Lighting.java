package de.latzig.game.tob.core.model;

/**
 * How a mesh part is lit: {@code LIT} with the scene lights (normal map, specular flag), or {@code UNLIT} with the
 * ToB 2005 Simple shader (texture × vertex colour, no lighting, no bump, no specular; {@code dxe.cpp:1452-1460}).
 */
public enum Lighting {
    LIT, UNLIT
}
