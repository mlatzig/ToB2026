package de.latzig.game.tob.core.model;

/** Visual settings invented by ToB 2009 (not part of the original data). Colours are 0xRRGGBB. */
public record RemakeVisuals(int sunColor, int sunSize, int groundColor1, int groundColor2, int groundHeight,
        int liftColor, boolean clouds, boolean heavyClouds, int pattern, int patternColor1, int patternColor2) {

    public RemakeVisuals {
        Checks.range("remake.sunColor", sunColor, 0, 0xFFFFFF);
        Checks.range("remake.sunSize", sunSize, 0, 10);
        Checks.range("remake.groundColor1", groundColor1, 0, 0xFFFFFF);
        Checks.range("remake.groundColor2", groundColor2, 0, 0xFFFFFF);
        Checks.range("remake.groundHeight", groundHeight, 0, 10);
        Checks.range("remake.liftColor", liftColor, 0, 0xFFFFFF);
        Checks.range("remake.pattern", pattern, 0, 7);
        Checks.range("remake.patternColor1", patternColor1, 0, 0xFFFFFF);
        Checks.range("remake.patternColor2", patternColor2, 0, 0xFFFFFF);
    }
}
