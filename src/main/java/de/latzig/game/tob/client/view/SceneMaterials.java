package de.latzig.game.tob.client.view;

import com.jme3.material.Material;

import de.latzig.game.tob.core.model.MeshPart;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.StaticQuadrant;

/**
 * Phase 5e: where a scene's views take their materials from, chosen once when the scene is built (decision 5: the
 * Look switch applies at the next scene build, never in the middle of a tower): the Remake look's
 * ({@link #remake}: the {@link AssetLibrary}'s textured materials) or the Classic Look's ({@link ClassicMaterials}).
 */
public interface SceneMaterials {

    /** The material of {@code type}'s mesh part {@code part}; {@code vertexColors} when its mesh has colours. */
    Material part(ObjectType type, MeshPart part, boolean vertexColors);

    /** The material of the base geometry's faces on the STATIC quadrant {@code quadrant} (vertex colours). */
    Material base(StaticQuadrant quadrant);

    /** A new material of the blue pulse's surface; {@link HazeView} scales its {@code Color} every frame. */
    Material haze();

    /** The Remake look: the library's materials, as before Phase 5e. */
    static SceneMaterials remake(AssetLibrary assets) {
        return new SceneMaterials() {

            @Override
            public Material part(ObjectType type, MeshPart part, boolean vertexColors) {
                return assets.material(part, vertexColors);
            }

            @Override
            public Material base(StaticQuadrant quadrant) {
                return assets.staticMaterial(quadrant);
            }

            @Override
            public Material haze() {
                return assets.hazeMaterial();
            }
        };
    }
}
