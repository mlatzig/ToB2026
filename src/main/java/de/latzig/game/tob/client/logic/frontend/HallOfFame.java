package de.latzig.game.tob.client.logic.frontend;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import de.latzig.game.tob.client.logic.ui.ScrollBox;
import de.latzig.game.tob.core.progress.Profile;

/**
 * The Hall of Fame window's state (Phase 5c-1 spec decision 4): two tabs, {@link Tab#SCORES} — the profiles ranked by
 * total score (rank, name, score, solved towers, group), derived from the profiles on every opening, no file of its
 * own — and {@link Tab#CREDITS}, the credits text (Phase 5b's Credits window). Each tab scrolls its own
 * {@link ScrollBox}. The ranking: higher score first, then more solved towers, then the name ignoring case; equal
 * scores share a rank (1, 2, 2, 4). Engine-free.
 */
public final class HallOfFame {

    /** The visible rows of the SCORES tab (more only with older data: it scrolls). */
    public static final int SCORE_ROWS = 5;
    /** The visible lines of the CREDITS tab (two fewer than Phase 5b's window: the tab bar takes their room). */
    public static final int CREDIT_ROWS = 12;

    /** The tabs, left to right. */
    public enum Tab {
        SCORES("halloffame.tab.scores"), CREDITS("halloffame.tab.credits");

        private final String textKey;

        Tab(String textKey) {
            this.textKey = textKey;
        }

        public String textKey() {
            return textKey;
        }
    }

    /** One SCORES row: the 1-based rank, the profile's name, total score, solved towers and current group. */
    public record Entry(int rank, String name, long score, int solved, int group) {
    }

    private final List<String> creditLines;
    private final ScrollBox scores = new ScrollBox(SCORE_ROWS);
    private final ScrollBox credits = new ScrollBox(CREDIT_ROWS);
    private List<Entry> entries = List.of();
    private Tab tab = Tab.SCORES;

    /** @param creditLines the credits text, one entry per line (a line ending in ':' is a heading) */
    public HallOfFame(List<String> creditLines) {
        this.creditLines = List.copyOf(creditLines);
        credits.size(this.creditLines.size());
    }

    /** The ranking of {@code profiles} (decision 4). */
    public static List<Entry> rank(List<Profile> profiles) {
        List<Profile> sorted = new ArrayList<>(profiles);
        sorted.sort(Comparator.comparingLong(Profile::score).reversed()
                .thenComparing(Comparator.comparingInt(Profile::completedCount).reversed())
                .thenComparing(Profile::name, String.CASE_INSENSITIVE_ORDER));
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            Profile profile = sorted.get(i);
            int rank = i > 0 && sorted.get(i - 1).score() == profile.score() ? entries.get(i - 1).rank() : i + 1;
            entries.add(new Entry(rank, profile.name(), profile.score(), profile.completedCount(), profile.group()));
        }
        return List.copyOf(entries);
    }

    /** The window opens: the SCORES tab with {@code profiles} ranked, both tabs scrolled to the top. */
    public void open(List<Profile> profiles) {
        entries = rank(profiles);
        scores.size(entries.size());
        scores.home();
        credits.home();
        tab = Tab.SCORES;
    }

    public Tab tab() {
        return tab;
    }

    public void select(Tab selected) {
        tab = selected;
    }

    /** Left/Right (and Tab): the other tab. */
    public void switchTab() {
        tab = tab == Tab.SCORES ? Tab.CREDITS : Tab.SCORES;
    }

    public List<Entry> entries() {
        return entries;
    }

    public List<String> creditLines() {
        return creditLines;
    }

    public ScrollBox scores() {
        return scores;
    }

    public ScrollBox credits() {
        return credits;
    }

    /** The scroll box of the shown tab (the keys, the wheel and the scroll bar move it). */
    public ScrollBox box() {
        return tab == Tab.SCORES ? scores : credits;
    }

    /** The mouse button is released: a thumb drag ends on either tab. */
    public void release() {
        scores.release();
        credits.release();
    }
}
