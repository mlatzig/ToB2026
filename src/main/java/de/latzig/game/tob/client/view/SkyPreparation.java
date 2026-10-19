package de.latzig.game.tob.client.view;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;

import de.latzig.game.tob.client.logic.effects.CloudTexture;
import de.latzig.game.tob.client.logic.effects.MoonDesign;
import de.latzig.game.tob.client.logic.effects.SkyOptions;
import de.latzig.game.tob.core.model.TowerDefinition;

/**
 * A tower's generated sky textures, prepared while the Briefing shows (Phase 5a spec decision 14): the moon's maps
 * ({@link MoonView#prepare}, the bulk of the work) and the cloud texture ({@link CloudTexture}), both in the
 * background. The Briefing's loading bar shows {@link #progress}, Play waits for {@link #isDone}; the game's
 * {@link SkyView} then takes the finished textures instead of generating them again.
 */
public final class SkyPreparation {

    /** The clouds' share of the progress when the tower has a moon too. */
    static final float CLOUD_SHARE = 0.1f;

    private final String towerId;
    private final Optional<MoonView.Prepared> moon;
    private final Optional<CompletableFuture<byte[]>> clouds;

    private SkyPreparation(String towerId, Optional<MoonView.Prepared> moon,
            Optional<CompletableFuture<byte[]>> clouds) {
        this.towerId = towerId;
        this.moon = moon;
        this.clouds = clouds;
    }

    /** Starts preparing {@code tower}'s sky with the game's {@code options} for the Remake look. */
    public static SkyPreparation start(TowerDefinition tower, SkyOptions options) {
        return start(tower, options, false);
    }

    /**
     * Starts preparing {@code tower}'s sky with the game's {@code options}; {@code classic}: the Classic Look draws
     * no clouds (Phase 5e decision 2), so none are generated.
     */
    public static SkyPreparation start(TowerDefinition tower, SkyOptions options, boolean classic) {
        Optional<MoonView.Prepared> moon = MoonDesign.of(tower, options)
                .map(design -> MoonView.prepare(design, options.halfMoonDetail()));
        OptionalInt cover = classic ? OptionalInt.empty() : CloudTexture.cover(tower);
        long seed = tower.id().hashCode();
        Optional<CompletableFuture<byte[]>> clouds = cover.isPresent()
                ? Optional.of(CompletableFuture.supplyAsync(() -> CloudTexture.generate(seed, cover.getAsInt())))
                : Optional.empty();
        return new SkyPreparation(tower.id(), moon, clouds);
    }

    public String towerId() {
        return towerId;
    }

    /** 0..1; 1 once everything is ready (at once for a tower without moon and clouds). */
    public float progress() {
        if (isDone()) {
            return 1f;
        }
        float cloudShare = moon.isPresent() ? CLOUD_SHARE : 1f;
        float moonPart = moon.map(prepared -> prepared.progress() * (1f - CLOUD_SHARE)).orElse(0f);
        float cloudPart = clouds.map(job -> job.isDone() ? cloudShare : 0f).orElse(cloudShare);
        return Math.min(0.99f, moonPart + cloudPart);
    }

    public boolean isDone() {
        return moon.map(MoonView.Prepared::isDone).orElse(true) && clouds.map(CompletableFuture::isDone).orElse(true);
    }

    /**
     * Stops the moon's generation and the cloud job (the Briefing was left without playing; Task 13 review: both).
     * A cancelled preparation is done at once or after the moon's current pass, and is never handed to a game.
     */
    public void cancel() {
        moon.ifPresent(MoonView.Prepared::cancel);
        clouds.ifPresent(job -> job.cancel(false));
    }

    Optional<MoonView.Prepared> moon() {
        return moon;
    }

    /** The cloud job (tests). */
    Optional<CompletableFuture<byte[]>> clouds() {
        return clouds;
    }

    /** The cloud texture, waiting for it if needed (Play is only enabled once it is done). */
    Optional<byte[]> cloudTexture() {
        return clouds.map(CompletableFuture::join);
    }
}
