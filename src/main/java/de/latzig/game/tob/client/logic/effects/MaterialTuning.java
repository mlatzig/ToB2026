package de.latzig.game.tob.client.logic.effects;

/**
 * Every tuning value of the Phase 4b-4 material fidelity in one place (spec decisions 1–6): the author tunes them at
 * the acceptance. The values are derived from ToB 2005's shaders ({@code _ShaderEdit/*-14.vsh/.psh},
 * {@code ShaderConsts.txt}) and their constants ({@code sky.cpp:680-708}, {@code sky.h:26-27}, {@code dxe.h:50},
 * {@code twr.cpp:353}); lengths are in fields (32 ToB 2005 units).
 */
public final class MaterialTuning {

    /** ToB 2005's units per field. */
    public static final float UNITS_PER_FIELD = 32f;

    /**
     * Fog start in fields: ToB 2005 passes fog start 0.0 of {@code IDX_ZDEPTH} for every tower ({@code twr.cpp:353}).
     */
    public static final float FOG_START = 0f;
    /**
     * Fog end in fields: 0.8 × {@code IDX_ZDEPTH} 8192 units = 204.8 fields ({@code twr.cpp:353}), the same linear
     * fog for every world material (decision 2).
     */
    public static final float FOG_END = 0.8f * 8192f / UNITS_PER_FIELD;

    /**
     * The ground detail texture's repeats on top of the ground texture's ({@code SKY_TEXTILES_GROUNDMUL},
     * {@code sky.h:27}; vertex shader constant c12): 15 × 20 = 300 tiles across the plane.
     */
    public static final float GROUND_DETAIL_TILES = 20f;
    /**
     * The height in ToB 2005 units over which the mountain texture replaces the ground texture. ToB 2005's
     * {@code Ground} vertex shader constant c13 = (−0.001, 1, 0.001, 0) gives the ground weight 1 − 0.001 · y and
     * the mountain weight 0.001 · y, both clamped to 0..1 (Direct3D clamps colour outputs), i.e. 1000 units; Phase 4a
     * lowered the hills to {@link Terrain#RELIEF} (0.35) of ToB 2005's, so the 1000 units are scaled alike (350):
     * the mountain texture covers the same parts of the hills as in ToB 2005. With 1000 units only 28 of the 117
     * original towers would show a fully mountain-textured vertex at all.
     */
    public static final float MOUNTAIN_HEIGHT_UNITS = (float) (1000 * Terrain.RELIEF);

    /**
     * The STATIC detail texture's repeats per atlas: {@code DXE_STATIC_TEXTILES_MULTI} ({@code dxe.h:50}, vertex shader
     * constant c12 of {@code RenderBM}); a face spans half the atlas, so 2 repeats per face.
     */
    public static final float STATIC_DETAIL_TILES = 4f;

    /**
     * Glossy parts (ToB 2005 FX 3, {@code RenderBS}): light = ((N·H)^16 + N·L) / 2, so the highlight has half
     * weight; jME's specular colour (grey) and Blinn exponent.
     */
    public static final float GLOSS_SPECULAR = 0.5f;
    /** The glossy parts' Blinn exponent: ToB 2005 squares N·H four times ((N·H)^16). */
    public static final float GLOSS_SHININESS = 16f;

    /**
     * Glass (ToB 2005 FX 4, {@code RenderABS}): light = (4 (N·H)^16 + N·L) / 2, so the highlight has weight 2.
     */
    public static final float GLASS_SPECULAR = 2f;
    /** The glass' Blinn exponent ((N·H)^16). */
    public static final float GLASS_SHININESS = 16f;
    /**
     * Glass brightening: {@code RenderABS} adds 0.5 to the colour; jME has no additive term, so the glass' ambient
     * colour is raised to 0.5 / 0.4 (the scene's ambient light, {@code GameState}): ambient light × texture ≈ 0.5 ×
     * texture.
     */
    public static final float GLASS_AMBIENT = 1.25f;
    /**
     * Glass alpha factor: {@code RenderABS} gives alpha = texture alpha × light + 0.2 (light = 0.25..0.5 for lit
     * faces); jME only scales the texture alpha (material diffuse alpha). At the default prism texture's mean alpha
     * 0.57 and a mean light of 0.375 that is (0.57 × 0.375 + 0.2) / 0.57 ≈ 0.7.
     */
    public static final float GLASS_ALPHA = 0.7f;

    /**
     * How much the shadows darken: 0.7 (decision 6, author 2026-10-11) instead of Phase 4a's 0.55; ToB 2005 used
     * 80/255 stencil shadows.
     */
    public static final float SHADOW_INTENSITY = 0.7f;

    /**
     * Phase 5e (Classic Look, decision 2): the blue pulse's translucent single-colour surface, 0xRRGGBB, in place
     * of Phase 4b-2's water texture. The original tints the tower blue (R − 1, G − 1, B + 2 of 7) for 3 of every 8
     * frames (report 14 §4); a blue surface over the level-0 floors and the ground, pulsing as the Remake's haze.
     * An sRGB colour: the material takes it converted to linear ({@code SrgbColours}, the gamma rule).
     */
    public static final int CLASSIC_PULSE_COLOUR = 0x2040FF;
    /** The Classic blue pulse's alpha. */
    public static final float CLASSIC_PULSE_ALPHA = 0.35f;

    /**
     * Phase 5e shadow fix (author decision 2026-10-16): how far (cells) every lit, shadow-receiving surface looks up
     * the sun's shadow maps along its normal (normal-offset shadows, {@code MatDefs/Tob/PostShadow.vert}); the value
     * is the {@code ShadowNormalOffset} default of {@code MatDefs/Tob/Lighting.j3md} and {@code Terrain.j3md}. Thin
     * floor sides stand almost parallel to the sun (N·L ≈ 0.12): a lookup on the face itself picks up the slab's own
     * depth in the neighbouring texels (PCF4) and shadows them in stripes (shadow acne).
     */
    public static final float SHADOW_NORMAL_OFFSET = 0.02f;

    private MaterialTuning() {
    }
}
