package de.latzig.game.tob.client.view;

import java.nio.ByteBuffer;
import java.util.Random;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial.CullHint;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import com.jme3.util.BufferUtils;

/**
 * Full-screen grey noise for the view of a spider that is no longer alive (spec §3 {@code CameraController}); a new
 * pattern every frame. Replaced by the ToB 2009 noise effect in Phase 4.
 */
public final class NoiseOverlay {

    public static final int SIZE = 128;

    private final Geometry geometry;
    private final Image image;
    private final ByteBuffer data = BufferUtils.createByteBuffer(SIZE * SIZE);
    private final Random random = new Random(20261008L);

    public NoiseOverlay(AssetManager assets) {
        image = new Image(Image.Format.Luminance8, SIZE, SIZE, data, ColorSpace.Linear);
        Texture2D texture = new Texture2D(image);
        texture.setMagFilter(Texture.MagFilter.Nearest);
        Material material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setTexture("ColorMap", texture);
        geometry = new Geometry("noise", new Quad(1f, 1f));
        geometry.setMaterial(material);
        geometry.setCullHint(CullHint.Always);
        refill();
    }

    public Geometry geometry() {
        return geometry;
    }

    public void update(boolean shown, int width, int height) {
        geometry.setCullHint(shown ? CullHint.Never : CullHint.Always);
        if (!shown) {
            return;
        }
        geometry.setLocalScale(width, height, 1f);
        geometry.setLocalTranslation(0f, 0f, -1f);
        refill();
    }

    private void refill() {
        data.clear();
        for (int i = 0; i < SIZE * SIZE; i++) {
            data.put((byte) (random.nextInt(160) + 48));
        }
        data.flip();
        image.setUpdateNeeded();
    }
}
