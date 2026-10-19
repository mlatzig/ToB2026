package de.latzig.game.tob.core.model;

import java.util.Objects;

/** Texture (and optional normal map) of a non-object visual such as the playground, floor/box, lift or sky. */
public record BaseTexture(String texture, String normalMap) {

    /** The base entry of the generated ground haze texture (Phase 4b-2, the blue pulse tower flag). */
    public static final String HAZE = "haze";

    public BaseTexture {
        Objects.requireNonNull(texture, "texture");
    }
}
