package de.latzig.game.tob.client.logic.frontend;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import de.latzig.game.tob.client.logic.Texts;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.progress.Progression;

/**
 * The Tutorial window's state (Phase 5c-1 spec decision 3): the 9 tutorial towers as the Overview's 3×3 grid of
 * letter cells A–I ({@link OverviewModel.Cell}), all open and without a profile, as the original's tutorial
 * (mask {@code $1FF}, {@code S:6189-6240}); a tower marked in the global {@code settings.json} list is shown solved
 * (green) — towers with a goal (tutorial-007/008) once completed, the goal-less ones (tutorial-000..006) once played
 * and left regularly (author decision 2026-10-15); this class only reports the mark ({@code StartScreens} draws
 * it); {@link GameFlow} decides when a tower joins that list. The header shows how many are solved and that nothing
 * is scored; the info line names the selected tower ("n) Title - Author"). The arrow keys move without wrapping, as
 * on the Overview. Engine-free.
 */
public final class TutorialModel {

    /** The tutorial's towers: one 3×3 grid. */
    public static final int TOWERS = OverviewModel.CELLS;

    private final List<TowerDefinition> towers;
    private final Set<String> solved;
    private final Texts texts;
    private int selected;

    /**
     * @param towers the 9 tutorial towers (indices 0–8, in any order)
     * @param solved the ids of the solved (or done) tutorial towers
     * @throws IllegalArgumentException unless there are exactly 9 towers with the indices 0–8
     */
    public TutorialModel(List<TowerDefinition> towers, Set<String> solved, Texts texts) {
        this.towers = sorted(towers);
        this.solved = Set.copyOf(solved);
        this.texts = texts;
    }

    /**
     * The towers by index.
     *
     * @throws IllegalArgumentException unless there are exactly 9 towers with the indices 0–8
     */
    public static List<TowerDefinition> sorted(List<TowerDefinition> towers) {
        List<TowerDefinition> byIndex = new ArrayList<>(towers);
        byIndex.sort(Comparator.comparingInt(TowerDefinition::index));
        for (int i = 0; i < byIndex.size(); i++) {
            if (byIndex.get(i).index() != i) {
                throw new IllegalArgumentException("no tutorial tower at index " + i);
            }
        }
        if (byIndex.size() != TOWERS) {
            throw new IllegalArgumentException("the tutorial has 9 towers, not " + byIndex.size());
        }
        return List.copyOf(byIndex);
    }

    public String caption() {
        return texts.get("tutorial.caption");
    }

    /** "Solved: n of 9". */
    public String solvedText() {
        return texts.format("tutorial.solved", solvedCount(), TOWERS);
    }

    /** "No profile, no score" (decision 3). */
    public String hintText() {
        return texts.get("tutorial.hint");
    }

    public int solvedCount() {
        return (int) towers.stream().filter(tower -> solved.contains(tower.id())).count();
    }

    public boolean solved(int index) {
        return solved.contains(towers.get(index).id());
    }

    /** The 9 cells A–I in index order: solved (green) or open (white), never locked. */
    public List<OverviewModel.Cell> cells() {
        List<OverviewModel.Cell> cells = new ArrayList<>(TOWERS);
        for (int i = 0; i < TOWERS; i++) {
            OverviewModel.State state = solved(i) ? OverviewModel.State.SOLVED : OverviewModel.State.OPEN;
            cells.add(new OverviewModel.Cell(i, Progression.letter(i), i / OverviewModel.SIZE,
                    i % OverviewModel.SIZE, state, i == selected));
        }
        return cells;
    }

    /** The selected tower's index 0–8. */
    public int selected() {
        return selected;
    }

    public TowerDefinition selectedTower() {
        return towers.get(selected);
    }

    /** "n) Title - Author" of the selected tower, 1-based within the tutorial ("Anonymous" for an empty author). */
    public String infoText() {
        TowerDefinition tower = selectedTower();
        String author = tower.author().isBlank() ? texts.get("briefing.author.none") : tower.author();
        return texts.format("tutorial.tower", selected + 1, tower.title(), author);
    }

    public boolean up() {
        return selectAt(selected / OverviewModel.SIZE - 1, selected % OverviewModel.SIZE);
    }

    public boolean down() {
        return selectAt(selected / OverviewModel.SIZE + 1, selected % OverviewModel.SIZE);
    }

    public boolean left() {
        return selectAt(selected / OverviewModel.SIZE, selected % OverviewModel.SIZE - 1);
    }

    public boolean right() {
        return selectAt(selected / OverviewModel.SIZE, selected % OverviewModel.SIZE + 1);
    }

    /** Selects cell {@code index} 0–8; false if outside the grid. */
    public boolean select(int index) {
        if (index < 0 || index >= TOWERS) {
            return false;
        }
        selected = index;
        return true;
    }

    /** Selects the cell at grid {@code row}/{@code column} 0–2; false if outside the grid. */
    public boolean selectAt(int row, int column) {
        if (row < 0 || row >= OverviewModel.SIZE || column < 0 || column >= OverviewModel.SIZE) {
            return false;
        }
        return select(row * OverviewModel.SIZE + column);
    }

    /** Selects {@code towerId} if it is a tutorial tower (back from the Briefing, the result or a failed start). */
    public void preselect(String towerId) {
        for (int i = 0; i < TOWERS; i++) {
            if (towers.get(i).id().equals(towerId)) {
                selected = i;
            }
        }
    }
}
