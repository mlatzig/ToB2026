package de.latzig.game.tob.client.logic.frontend;

import java.util.ArrayList;
import java.util.List;

import de.latzig.game.tob.client.logic.Texts;
import de.latzig.game.tob.core.model.Cell;
import de.latzig.game.tob.core.model.Conditions;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.model.TowerGrid;

/**
 * The Briefing's four groups (ToB 2009 {@code BriefingWindow}, the original's "Information on Tower",
 * {@code S:3847-3860}): the tower title, the author ("Anonymous" if empty), the spiders found in the grid as
 * "Zapper, Pusher and Grabber" ("No Spiders Present!" if none) and the objective in ToB 2009's three lines, or the
 * original's "There is no task for this tower!" ({@code S:3890}) for a 0/0 tower.
 */
public record BriefingText(String title, String author, String spiders, List<String> objective) {

    public BriefingText {
        objective = List.copyOf(objective);
    }

    public static BriefingText of(TowerDefinition tower, Texts texts) {
        String author = tower.author().isBlank() ? texts.get("briefing.author.none") : tower.author();
        return new BriefingText(tower.title(), author, spiders(tower.grid(), texts),
                objective(tower.conditions(), texts));
    }

    private static String spiders(TowerGrid grid, Texts texts) {
        List<String> names = new ArrayList<>();
        for (ObjectType spider : List.of(ObjectType.ZAP, ObjectType.PSH, ObjectType.GRB)) {
            if (contains(grid, spider)) {
                names.add(texts.get(switch (spider) {
                    case ZAP -> "briefing.spider.zapper";
                    case PSH -> "briefing.spider.pusher";
                    default -> "briefing.spider.grabber";
                }));
            }
        }
        if (names.isEmpty()) {
            return texts.get("briefing.spiders.none");
        }
        if (names.size() == 1) {
            return names.get(0);
        }
        return String.join(texts.get("briefing.list.comma"), names.subList(0, names.size() - 1))
                + texts.get("briefing.list.and") + names.get(names.size() - 1);
    }

    private static boolean contains(TowerGrid grid, ObjectType type) {
        for (int level = 0; level < TowerGrid.LEVELS; level++) {
            for (int y = 0; y < TowerGrid.DEPTH; y++) {
                for (int x = 0; x < TowerGrid.WIDTH; x++) {
                    Cell cell = grid.cell(x, y, level);
                    if (cell.hasObject() && cell.object() == type) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static List<String> objective(Conditions conditions, Texts texts) {
        List<String> lines = new ArrayList<>();
        if (conditions.klondikes() > 0) {
            lines.add(texts.format(conditions.klondikes() == 1 ? "briefing.klondikes.1" : "briefing.klondikes.n",
                    conditions.klondikes()));
        }
        if (conditions.robots() > 0) {
            String key = lines.isEmpty() ? "briefing.robots." : "briefing.robots.and.";
            lines.add(texts.format(key + (conditions.robots() == 1 ? "1" : "n"), conditions.robots()));
        }
        if (lines.isEmpty()) {
            return List.of(texts.get("briefing.objective.none"));
        }
        if (conditions.timeLimitEnabled()) {
            lines.add(texts.format(conditions.timeLimitSeconds() == 1 ? "briefing.time.1" : "briefing.time.n",
                    conditions.timeLimitSeconds()));
        }
        return lines;
    }
}
