package de.latzig.game.tob.client.view;

import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import com.jme3.util.BufferUtils;

/**
 * jME textures from pixels generated at run time (Phase 4b-3: the clouds and the moon), RGBA8, row 0 at {@code v} = 0,
 * trilinear with mipmaps generated on upload and {@link AssetLibrary#ANISOTROPY}.
 */
final class GeneratedTextures {

    private GeneratedTextures() {
    }

    /**
     * A {@code width × height} texture of {@code rgba}; {@code repeatU}/{@code repeatV} choose repeat or clamp per
     * direction. {@code srgb}: colour data (albedo, clouds) rather than vectors (normal maps).
     */
    static Texture2D of(String name, int width, int height, byte[] rgba, boolean repeatU, boolean repeatV,
            boolean srgb) {
        Image image = new Image(Image.Format.RGBA8, width, height, BufferUtils.createByteBuffer(rgba),
                srgb ? ColorSpace.sRGB : ColorSpace.Linear);
        Texture2D texture = new Texture2D(image);
        texture.setName(name);
        texture.setWrap(Texture.WrapAxis.S, repeatU ? Texture.WrapMode.Repeat : Texture.WrapMode.EdgeClamp);
        texture.setWrap(Texture.WrapAxis.T, repeatV ? Texture.WrapMode.Repeat : Texture.WrapMode.EdgeClamp);
        texture.setMinFilter(Texture.MinFilter.Trilinear);
        texture.setMagFilter(Texture.MagFilter.Bilinear);
        texture.setAnisotropicFilter(AssetLibrary.ANISOTROPY);
        return texture;
    }
}
