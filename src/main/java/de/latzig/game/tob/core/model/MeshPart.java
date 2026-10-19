package de.latzig.game.tob.core.model;

import java.util.Objects;

/**
 * One mesh part of an element: asset paths relative to the assets root, render type, tint (0xRRGGBB), whether the
 * part gets specular highlights (ToB 2005 FX 3 and 4 = true, FX 2 diffuse bump = false) and how it is lit
 * ({@code elements.json} version 3; {@link Lighting#UNLIT} for the parts ToB 2005 drew with its Simple shader).
 */
public record MeshPart(String mesh, String texture, String normalMap, RenderType render, int tint,
        boolean specular, Lighting lighting) {

    public MeshPart {
        Objects.requireNonNull(mesh, "mesh");
        Objects.requireNonNull(texture, "texture");
        Objects.requireNonNull(render, "render");
        Objects.requireNonNull(lighting, "lighting");
        Checks.range("tint", tint, 0, 0xFFFFFF);
    }

    /** Lit part (the {@code elements.json} version 1 and 2 default). */
    public MeshPart(String mesh, String texture, String normalMap, RenderType render, int tint, boolean specular) {
        this(mesh, texture, normalMap, render, tint, specular, Lighting.LIT);
    }

    /** Lit part with specular highlights (the Phase 0/1b look and the {@code elements.json} version 1 default). */
    public MeshPart(String mesh, String texture, String normalMap, RenderType render, int tint) {
        this(mesh, texture, normalMap, render, tint, true);
    }
}
