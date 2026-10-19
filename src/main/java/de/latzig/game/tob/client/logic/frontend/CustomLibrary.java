package de.latzig.game.tob.client.logic.frontend;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import de.latzig.game.tob.client.logic.Texts;
import de.latzig.game.tob.client.logic.editor.EditorFindings;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.progress.Profile;
import de.latzig.game.tob.core.sim.TowerCheck.Finding;
import de.latzig.game.tob.core.sim.TowerCheck.Severity;

/**
 * An own library as the Overview plays it (Phase 6d spec decisions 2, 3 and 6, the original's Custom Game): its name
 * and its readable towers in the file's order, 9 to a group (the original designer's group; position {@code p} is
 * group {@code p / 9}, letter {@code p % 9}), all open. A tower the designer marks unplayable is refused: a check
 * error, or any finding of {@link Severity#UNPLAYABLE} (no task, no spider, too few klondikes, …) — only a tower in
 * {@link EditorFindings.State#VALID} can be started (author's acceptance amendment, 2026-10-17; a hint-level finding
 * does not refuse it). The message is the designer's own words, {@link EditorFindings#messageKey} of the deciding
 * finding (the error, else the first thing that makes the tower unwinnable). The checks run once, here. Solved marks
 * come from the profile (per library name and tower id). Engine-free.
 */
public final class CustomLibrary {

    private final String name;
    private final List<TowerDefinition> towers;
    private final List<Optional<Finding>> refusals;

    /**
     * @param name   the library's name ({@code <userdir>/towers/<name>.json})
     * @param towers its readable towers, in order
     * @throws IllegalArgumentException without a tower
     */
    public CustomLibrary(String name, List<TowerDefinition> towers) {
        this.name = Objects.requireNonNull(name, "name");
        if (towers.isEmpty()) {
            throw new IllegalArgumentException("the own library '" + name + "' has no tower");
        }
        this.towers = List.copyOf(towers);
        this.refusals = this.towers.stream().map(CustomLibrary::refusal).toList();
    }

    /**
     * The deciding finding for the designer's verdict (author's acceptance amendment, 2026-10-17): the first error,
     * else the first finding that makes the tower unwinnable ({@link Severity#UNPLAYABLE}); empty once the designer
     * marks the tower {@link EditorFindings.State#VALID} (green) — only such a tower can be started.
     */
    private static Optional<Finding> refusal(TowerDefinition tower) {
        EditorFindings findings = EditorFindings.of(tower);
        if (findings.state() == EditorFindings.State.VALID) {
            return Optional.empty();
        }
        return findings.first(Severity.ERROR).or(() -> findings.first(Severity.UNPLAYABLE));
    }

    public String name() {
        return name;
    }

    public List<TowerDefinition> towers() {
        return towers;
    }

    public int size() {
        return towers.size();
    }

    /** The groups of 9 (the last one may be shorter). */
    public int groups() {
        return (towers.size() + OverviewModel.CELLS - 1) / OverviewModel.CELLS;
    }

    /** The tower at {@code group}/{@code index}; empty past the last one. */
    public Optional<TowerDefinition> tower(int group, int index) {
        int position = group * OverviewModel.CELLS + index;
        return index >= 0 && index < OverviewModel.CELLS && position >= 0 && position < towers.size()
                ? Optional.of(towers.get(position)) : Optional.empty();
    }

    /** The 0-based position of the tower {@code towerId}, or −1. */
    public int position(String towerId) {
        for (int i = 0; i < towers.size(); i++) {
            if (towers.get(i).id().equals(towerId)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Whether the tower at {@code position} can be started: the designer marks it playable, {@code
     * EditorFindings.State.VALID} (decision 6, amended 2026-10-17).
     */
    public boolean startable(int position) {
        return refusals.get(position).isEmpty();
    }

    /** Why the tower at {@code position} cannot be started, in the designer's own words; empty if it can. */
    public Optional<String> refusal(int position, Texts texts) {
        return refusals.get(position).map(finding -> texts.get(EditorFindings.messageKey(Optional.of(finding))));
    }

    /** The towers of this library {@code profile} marked solved (decision 5). */
    public int solvedCount(Profile profile) {
        return (int) towers.stream().filter(tower -> profile.customSolved(name, tower.id())).count();
    }

    /**
     * The "(xx%)" of {@code solved} towers of this library, rounded up as the original's ({@code S:7456-7471},
     * {@code Progression#completedPercent}) unless the quotient is 99, so 100 only once all are solved.
     */
    public int percent(int solved) {
        int scaled = solved * 100;
        int percent = scaled / towers.size();
        return percent != 99 && scaled % towers.size() != 0 ? percent + 1 : percent;
    }
}
