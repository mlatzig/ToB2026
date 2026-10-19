package de.latzig.game.tob.client.logic;

/**
 * What an object shows: the mesh group id of {@code elements.json} (1-based), the keyframe (16 keys per simulation
 * frame) and the heading in radians ({@link Motion#heading}). Only the watcher turns its mesh; every other element
 * has its facing baked into its groups (ToB 2009 renderers).
 */
public record MeshFrame(int group, float key, float heading) {

    public static MeshFrame of(int group, float key) {
        return new MeshFrame(group, key, 0f);
    }
}
