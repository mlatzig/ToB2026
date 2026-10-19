package de.latzig.game.tob.core.model;

/** Per-tower sound settings only (background 0 none, 1 breath, 2 drone A, 3 drone B); no audio data. */
public record Sounds(int background, boolean spiderWalk, boolean other) {

    public Sounds {
        Checks.range("sounds.background", background, 0, 3);
    }
}
