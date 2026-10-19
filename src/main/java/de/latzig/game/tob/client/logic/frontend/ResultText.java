package de.latzig.game.tob.client.logic.frontend;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import de.latzig.game.tob.client.logic.Texts;
import de.latzig.game.tob.core.progress.TowerScore;
import de.latzig.game.tob.core.sim.Rules;

/**
 * The result window's texts (Phase 5a spec decision 11): the original's result box ({@code S:4369-4383}: "Tower
 * Completed" / "Congratulations", or "Tower Not Completed" with the exit reason) and, after a first completion, its
 * score box ({@code S:3867-3871}: tower score, retry bonus, survivor bonus, the 6-digit total). Practice shows that the
 * profile is unchanged instead.
 *
 * @param score the score lines (label, value); empty after a failure
 */
public record ResultText(String caption, String reason, List<Line> score, boolean completed) {

    /** One line of the score box. */
    public record Line(String label, String value) {
    }

    public ResultText {
        score = List.copyOf(score);
    }

    /**
     * @param exitCode the simulation's exit code ({@link Rules#EXIT_COMPLETED} ... {@link Rules#EXIT_ABANDONED})
     * @param score    the tower's score after a first completion
     * @param total    the profile's total score afterwards
     * @throws IllegalArgumentException for any other exit code ({@link Rules#EXIT_RESTART} never reaches the result
     *                                  window; the review of Task 6 asked for a loud failure instead of a wrong text)
     */
    public static ResultText of(int exitCode, Optional<TowerScore> score, long total, boolean practice, Texts texts) {
        if (exitCode != Rules.EXIT_COMPLETED) {
            String reason = switch (exitCode) {
                case Rules.EXIT_TIMEOUT -> "result.timeout";
                case Rules.EXIT_SPIDERS_DESTROYED -> "result.spiders";
                case Rules.EXIT_GRABBER_DESTROYED -> "result.grabber";
                case Rules.EXIT_ABANDONED -> "result.abandoned";
                default -> throw new IllegalArgumentException("no result text for exit code " + exitCode);
            };
            return new ResultText(texts.get("result.not.completed"), texts.get(reason), List.of(), false);
        }
        List<Line> lines;
        if (practice || score.isEmpty()) {
            lines = practice ? List.of(new Line(texts.get("result.practice"), texts.get("result.practice.value")))
                    : List.of();
        } else {
            TowerScore s = score.get();
            lines = List.of(new Line(texts.get("result.tower.score"), Integer.toString(s.tower())),
                    new Line(texts.get("result.retry.bonus"), Integer.toString(s.retryBonus())),
                    new Line(texts.get("result.survivor.bonus"), Integer.toString(s.survivorBonus())),
                    new Line(texts.get("result.total"), String.format(Locale.ROOT, "%06d", total)));
        }
        return new ResultText(texts.get("result.completed"), texts.get("result.congratulations"), lines, true);
    }

    /**
     * Phase 6d decision 5: an own tower's result: a failure as usual, a completion with one line "Own Tower: no score"
     * instead of the score box.
     */
    public static ResultText custom(int exitCode, Texts texts) {
        ResultText plain = of(exitCode, Optional.empty(), 0L, false, texts);
        return plain.completed() ? new ResultText(plain.caption(), plain.reason(),
                List.of(new Line(texts.get("result.custom"), texts.get("result.custom.value"))), true) : plain;
    }

    /** The original's text after the last tower ({@code S:3873-3877}) and the 6-digit final score. */
    public static List<String> allSolved(long total, Texts texts) {
        return List.of(texts.get("result.all.1"), texts.get("result.all.2"), texts.get("result.all.3"),
                texts.get("result.all.4"), texts.get("result.all.5"), String.format(Locale.ROOT, "%06d", total));
    }
}
