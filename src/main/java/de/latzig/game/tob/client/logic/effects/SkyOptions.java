package de.latzig.game.tob.client.logic.effects;

import java.util.Optional;

/**
 * The sky's options (Phase 4b-3): the moon texture detail (spec decision 12, "Texture detail: Full/Half" of ToB
 * 2005) and two overrides that force a moon look or size class on every tower with a moon. Phase 5b decision 7: the
 * moon detail is the player's option ({@code Settings.halfMoonDetail}, {@link #withHalfMoonDetail}); the overrides
 * are no longer read from system properties and remain only as a seam for the headless tests (no original tower
 * draws a giant moon from its seed).
 *
 * @param halfMoonDetail one texture level smaller in each direction; the displayed size stays
 * @param moonLook       a forced look, or empty for the tower's own
 * @param moonSize       a forced size class, or empty for the tower's own
 */
public record SkyOptions(boolean halfMoonDetail, Optional<MoonDesign.Look> moonLook,
        Optional<MoonDesign.SizeClass> moonSize) {

    /** The tuned detail, no overrides. */
    public static final SkyOptions DEFAULT = new SkyOptions(SkyTuning.MOON_HALF_DETAIL, Optional.empty(),
            Optional.empty());

    /** These options with half detail if {@code half} (the player's Moon detail option) or if they say so already. */
    public SkyOptions withHalfMoonDetail(boolean half) {
        return new SkyOptions(halfMoonDetail || half, moonLook, moonSize);
    }
}
