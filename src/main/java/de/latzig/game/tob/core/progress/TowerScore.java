package de.latzig.game.tob.core.progress;

/**
 * The score of a completed tower as the original's score box shows it ({@code S:3867-3871}, {@code S:6444-6467}):
 * the tower score (100), the retry bonus {@code max(0, 100 − 4 · counter)} and the survivor bonus (10 per spider
 * present and alive). The counter is the original's per-tower retry byte ({@link #afterFailure},
 * {@link #afterRestart}).
 */
public record TowerScore(int tower, int retryBonus, int survivorBonus) {

    public static final int TOWER = 100;
    public static final int SURVIVOR = 10;

    public TowerScore {
        if (tower < 0 || retryBonus < 0 || survivorBonus < 0) {
            throw new IllegalArgumentException("negative score part");
        }
    }

    /** The score of a tower completed with {@code counter} on the retry counter and {@code survivors} spiders. */
    public static TowerScore completed(int counter, int survivors) {
        return new TowerScore(TOWER, Math.max(0, TOWER - 4 * counter), SURVIVOR * survivors);
    }

    /** A failed or abandoned attempt: +2 while the counter is below 99 ({@code S:6771-6773}). */
    public static int afterFailure(int counter) {
        return counter < 99 ? counter + 2 : counter;
    }

    /** A restart: +1 while the counter is below 100 ({@code S:6741-6750}). */
    public static int afterRestart(int counter) {
        return counter < 100 ? counter + 1 : counter;
    }

    public int total() {
        return tower + retryBonus + survivorBonus;
    }
}
