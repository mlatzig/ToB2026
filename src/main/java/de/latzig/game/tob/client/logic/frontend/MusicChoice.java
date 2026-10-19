package de.latzig.game.tob.client.logic.frontend;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import de.latzig.game.tob.client.logic.frontend.GameFlow.Screen;

/**
 * Which of the author's songs plays (Phase 5c-2 spec decisions 1-4), engine-free. The original has no music; this is
 * a ToB 2026 addition. {@link Song#DREAMLAND} is wanted on the start screens of decision 1 ({@link #MENU_SCREENS}),
 * {@link Song#IMPROVI} on the result window after a completed tower, {@link Song#FADEOUT} after a failure or an
 * abandon (decision 2); the loader, the game, the Tower Designer and a designer's test play want none (decision 3),
 * and so does every screen while the Music switch is off (decision 4).
 * <p>
 * {@link #next} compares the wanted song with the one chosen before: the same song is {@link Change#KEEP} (so
 * Dreamland plays on from one start screen to the next, and a result song that has ended is not started again: it
 * plays once), another song is {@link Change#START} from its beginning (so Dreamland starts afresh when the menus
 * return after a game), none is {@link Change#STOP}.
 * <p>
 * The author's answer to question 3 of the plan (2026-10-15): the music's volume follows the screen fade when the
 * song changes ({@link #gain}). While the screen fades out to a screen that wants another song (or none, or Quit)
 * the factor follows {@code 1 − alpha} down and never rises; otherwise it follows {@code 1 − alpha} up and never
 * falls; a starting song begins at 0. So a kept song stays at full volume, a song started at the black point fades in
 * with the next screen, and one started without a fade (the Music switch) is at full volume at once. The listener's
 * gain (Master or {@code -Dtob.volume}) multiplies the factor.
 */
public final class MusicChoice {

    /** The author's three songs, OGG Vorbis below the assets root. */
    public enum Song {
        DREAMLAND("music/dreamland.ogg", true),
        IMPROVI("music/improvi.ogg", false),
        FADEOUT("music/fadeout.ogg", false);

        private final String file;
        private final boolean loops;

        Song(String file, boolean loops) {
            this.file = file;
            this.loops = loops;
        }

        /** The OGG file relative to the assets root. */
        public String file() {
            return file;
        }

        /** Whether the song loops (Dreamland) or plays once and then leaves silence (the result songs). */
        public boolean loops() {
            return loops;
        }
    }

    /** What the music player has to do. */
    public enum Change {
        KEEP, START, STOP
    }

    /**
     * The screen fade as the music follows it: whether the screen fades out, the song wanted after that fade-out,
     * and the black overlay's alpha (0 shown, 1 black).
     */
    public record Fade(boolean fadingOut, Optional<Song> afterFade, float alpha) {

        /** No fade: the screen is fully shown. */
        public static final Fade NONE = new Fade(false, Optional.empty(), 0f);
    }

    /** The start screens Dreamland plays on (decision 1). */
    public static final Set<Screen> MENU_SCREENS = Collections.unmodifiableSet(EnumSet.of(Screen.MENU, Screen.PLAY,
            Screen.NEW_GAME, Screen.OVERVIEW, Screen.TUTORIAL, Screen.BRIEFING, Screen.OPTIONS, Screen.HALL_OF_FAME));

    private Song playing;
    /** The volume factor of the song chosen now, 0..1 ({@link #gain}). */
    private float gain;

    /**
     * The song wanted on {@code screen}.
     *
     * @param completed whether the result window shows a completed tower (only read on {@link Screen#RESULT})
     * @param testPlay  whether a designer's test play runs (decision 3: silence)
     * @param musicOn   the Music switch (decision 4)
     */
    public static Optional<Song> wanted(Screen screen, boolean completed, boolean testPlay, boolean musicOn) {
        if (!musicOn || testPlay) {
            return Optional.empty();
        }
        if (screen == Screen.RESULT) {
            return Optional.of(completed ? Song.IMPROVI : Song.FADEOUT);
        }
        return MENU_SCREENS.contains(screen) ? Optional.of(Song.DREAMLAND) : Optional.empty();
    }

    /** The song wanted in the flow's present state. */
    public static Optional<Song> wanted(GameFlow flow, boolean musicOn) {
        ResultText result = flow.result();
        return wanted(flow.screen(), result != null && result.completed(), flow.testPlay().isPresent(), musicOn);
    }

    /** The flow's screen fade with the song wanted after a fade-out (none after Quit). */
    public static Fade fade(GameFlow flow, boolean musicOn) {
        if (!flow.fadingOut()) {
            return new Fade(false, Optional.empty(), flow.fadeAlpha());
        }
        ResultText result = flow.result();
        boolean completed = result != null && result.completed();
        boolean testPlay = flow.testPlay().isPresent();
        return new Fade(true, flow.nextScreen().flatMap(next -> wanted(next, completed, testPlay, musicOn)),
                flow.fadeAlpha());
    }

    /** The change from the song chosen before to {@code wanted}, which becomes the song chosen now. */
    public Change next(Optional<Song> wanted) {
        Song song = wanted.orElse(null);
        if (song == playing) {
            return Change.KEEP;
        }
        playing = song;
        gain = 0f;
        return song == null ? Change.STOP : Change.START;
    }

    /**
     * The volume factor (0..1) of the song chosen now in this frame's {@code fade}; called every frame after
     * {@link #next}. Fading out to another song or none: down with the screen, never up; otherwise up with the
     * screen, never down.
     */
    public float gain(Fade fade) {
        float shown = 1f - Math.clamp(fade.alpha(), 0f, 1f);
        if (fade.fadingOut()) {
            if (!fade.afterFade().equals(song())) {
                gain = Math.min(gain, shown);
            }
        } else {
            gain = Math.max(gain, shown);
        }
        return gain;
    }

    /** The song chosen last (playing, or a result song that has ended), or none. */
    public Optional<Song> song() {
        return Optional.ofNullable(playing);
    }
}
