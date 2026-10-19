package de.latzig.game.tob.client.logic;

/**
 * Phase 5e (Classic Look, controller gamma rule): jME renders with gamma correction, so plain material colours and
 * vertex colours are taken as linear while sRGB-flagged textures are decoded from sRGB. The Classic Look's colours
 * that are not textures (the floors', boxes' and lifts' vertex colours, plain material colours) are the palette's
 * sRGB colours and must be converted with the standard sRGB curve (IEC 61966-2-1) so they show as the palette's
 * colours. The Remake look's colours are not converted.
 */
public final class SrgbColours {

    private SrgbColours() {
    }

    /** One sRGB channel 0..1 as a linear channel 0..1 (the standard curve, the inverse of the display's encoding). */
    public static float toLinear(float srgb) {
        if (srgb <= 0.04045f) {
            return srgb / 12.92f;
        }
        return (float) Math.pow((srgb + 0.055) / 1.055, 2.4);
    }

    /** The red, green and blue channels 0..1 of {@code colour} (0xRRGGBB), linear if {@code linear}. */
    public static float[] rgb(int colour, boolean linear) {
        float[] rgb = {((colour >> 16) & 0xFF) / 255f, ((colour >> 8) & 0xFF) / 255f, (colour & 0xFF) / 255f};
        if (linear) {
            for (int i = 0; i < rgb.length; i++) {
                rgb[i] = toLinear(rgb[i]);
            }
        }
        return rgb;
    }

    /** Phase 5e: vertex colours (r, g, b, a per vertex, 0..1) with r, g and b converted to linear; a new array. */
    public static float[] linearRgba(float[] rgba) {
        float[] linear = rgba.clone();
        for (int i = 0; i < linear.length; i++) {
            if (i % 4 != 3) {
                linear[i] = toLinear(linear[i]);
            }
        }
        return linear;
    }
}
