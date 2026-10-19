package de.latzig.game.tob.client.view;

import com.jme3.material.Material;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial.CullHint;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.scene.shape.Quad;

import de.latzig.game.tob.client.logic.game.PanelLayout;
import de.latzig.game.tob.core.model.GuiAtlas;
import de.latzig.game.tob.core.model.GuiRegion;

/**
 * One GUI atlas region drawn as a quad in the GUI node. The atlas image keeps its top row first (no V flip, as every
 * texture of {@link AssetLibrary}), so the region's top edge is at v = y / height and its bottom edge at
 * v = (y + h) / height.
 */
final class AtlasSprite {

    private final GuiAtlas atlas;
    private final Quad quad = new Quad(1f, 1f);
    private final Geometry geometry;
    private String region;

    AtlasSprite(String name, GuiAtlas atlas, Material material) {
        this.atlas = atlas;
        this.geometry = new Geometry(name, quad);
        geometry.setMaterial(material);
        geometry.setCullHint(CullHint.Always);
    }

    Geometry geometry() {
        return geometry;
    }

    /** The region shown last, or null. */
    String region() {
        return region;
    }

    boolean isShown() {
        return geometry.getCullHint() != CullHint.Always;
    }

    /**
     * Shows {@code regionName} stretched over {@code rect} at depth {@code z} with {@code material}.
     *
     * @throws AssetLoadException if the atlas has no such region
     */
    void show(String regionName, PanelLayout.Rect rect, float z, Material material) {
        if (!regionName.equals(region)) {
            GuiRegion r = atlas.regions().get(regionName);
            if (r == null) {
                throw new AssetLoadException(AssetLibrary.GUI_ATLAS + " (region " + regionName + ")", null);
            }
            float u0 = (float) r.x() / atlas.imageWidth();
            float u1 = (float) (r.x() + r.width()) / atlas.imageWidth();
            float top = (float) r.y() / atlas.imageHeight();
            float bottom = (float) (r.y() + r.height()) / atlas.imageHeight();
            quad.setBuffer(Type.TexCoord, 2, new float[] {u0, bottom, u1, bottom, u1, top, u0, top});
            region = regionName;
        }
        if (geometry.getMaterial() != material) {
            geometry.setMaterial(material);
        }
        geometry.setLocalTranslation(rect.x(), rect.y(), z);
        geometry.setLocalScale(rect.width(), rect.height(), 1f);
        geometry.setCullHint(CullHint.Inherit);
    }

    void hide() {
        geometry.setCullHint(CullHint.Always);
    }
}
