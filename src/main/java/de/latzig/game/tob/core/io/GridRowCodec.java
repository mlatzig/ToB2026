package de.latzig.game.tob.core.io;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.latzig.game.tob.core.model.BaseType;
import de.latzig.game.tob.core.model.Cell;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.TowerGrid;

/** Encodes one grid level as 16 text rows (row 0 = north) of 16 whitespace-separated tokens. */
public final class GridRowCodec {

    private static final int TOKEN_WIDTH = 7;
    private static final Map<BaseType, String> BASE_TOKENS = new EnumMap<>(BaseType.class);
    private static final Map<String, BaseType> TOKEN_BASES = new HashMap<>();

    static {
        BASE_TOKENS.put(BaseType.EMPTY, ".");
        BASE_TOKENS.put(BaseType.FLOOR, "FLR");
        BASE_TOKENS.put(BaseType.BOX, "BOX");
        BASE_TOKENS.put(BaseType.LIFT_UP_START, "LUS");
        BASE_TOKENS.put(BaseType.LIFT_DOWN_START, "LDS");
        BASE_TOKENS.put(BaseType.LIFT_UP_STOP, "LUE");
        BASE_TOKENS.put(BaseType.LIFT_DOWN_STOP, "LDE");
        BASE_TOKENS.forEach((base, token) -> TOKEN_BASES.put(token, base));
    }

    private GridRowCodec() {
    }

    public static String encodeCell(Cell cell) {
        String base = BASE_TOKENS.get(cell.base());
        return cell.hasObject() ? base + ":" + cell.object().code() : base;
    }

    public static Cell decodeCell(String token) {
        int colon = token.indexOf(':');
        String baseToken = colon < 0 ? token : token.substring(0, colon);
        BaseType base = TOKEN_BASES.get(baseToken);
        if (base == null) {
            throw new IllegalArgumentException("unknown base token '" + baseToken + "' in '" + token + "'");
        }
        if (colon < 0) {
            return new Cell(base, null);
        }
        return new Cell(base, ObjectType.fromCode(token.substring(colon + 1)));
    }

    public static List<String> encodeLevel(TowerGrid grid, int level) {
        List<String> rows = new ArrayList<>(TowerGrid.DEPTH);
        for (int y = 0; y < TowerGrid.DEPTH; y++) {
            StringBuilder row = new StringBuilder();
            for (int x = 0; x < TowerGrid.WIDTH; x++) {
                if (x > 0) {
                    row.append(' ');
                }
                row.append(String.format("%-" + TOKEN_WIDTH + "s", encodeCell(grid.cell(x, y, level))));
            }
            rows.add(row.toString().stripTrailing());
        }
        return rows;
    }

    public static void decodeLevel(List<String> rows, int level, TowerGrid.Builder target) {
        if (rows.size() != TowerGrid.DEPTH) {
            throw new IllegalArgumentException(
                    "level " + level + ": expected " + TowerGrid.DEPTH + " rows, got " + rows.size());
        }
        for (int y = 0; y < TowerGrid.DEPTH; y++) {
            String[] tokens = rows.get(y).strip().split("\\s+");
            if (tokens.length != TowerGrid.WIDTH) {
                throw new IllegalArgumentException("level " + level + " row " + y + ": expected " + TowerGrid.WIDTH
                        + " tokens, got " + tokens.length);
            }
            for (int x = 0; x < TowerGrid.WIDTH; x++) {
                try {
                    target.set(x, y, level, decodeCell(tokens[x]));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(
                            "level " + level + " row " + y + " column " + x + ": " + e.getMessage(), e);
                }
            }
        }
    }
}
