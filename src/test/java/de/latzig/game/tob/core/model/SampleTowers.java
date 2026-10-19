package de.latzig.game.tob.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Small, fully populated sample data for tests. */
public final class SampleTowers {

    private SampleTowers() {
    }

    public static Palette palette() {
        List<Rgb12> colours = new ArrayList<>();
        for (int i = 0; i < Palette.SIZE; i++) {
            colours.add(new Rgb12(i % 16, (i * 3) % 16, 15 - (i % 16)));
        }
        return new Palette(colours);
    }

    public static TowerGrid grid() {
        return TowerGrid.builder()
                .set(4, 11, 0, new Cell(BaseType.FLOOR, ObjectType.ZAP))
                .set(5, 11, 0, new Cell(BaseType.LIFT_UP_START, null))
                .set(5, 11, 1, new Cell(BaseType.LIFT_UP_STOP, null))
                .set(6, 4, 0, new Cell(BaseType.FLOOR, ObjectType.KLD))
                .set(7, 8, 1, new Cell(BaseType.LIFT_DOWN_START, ObjectType.GRB))
                .set(7, 8, 0, new Cell(BaseType.LIFT_DOWN_STOP, null))
                .set(8, 8, 0, new Cell(BaseType.BOX, null))
                .build();
    }

    public static TowerDefinition tower(String id) {
        return new TowerDefinition(id, 0, 0, "Softly Softly!", "Pete Cooke",
                new Conditions(600, false, 3, 0),
                new Behaviour(true, false, 3),
                new Effects(false, true, false, 3, 4, 1),
                new Moon(0, 0, 8, 28, 2),
                palette(),
                new Sounds(2, true, false),
                new Lock(true, "lebab"),
                grid(),
                Optional.of(new RemakeVisuals(0xFFFFFF, 3, 0x004B4B, 0x006969, 6, 0xFFFFFF, true, false, 5, 0xA500A5, 0xE10000)));
    }

    public static TowerLibrary library() {
        TowerDefinition second = new TowerDefinition("sample-001", 0, 1, "Second", "", new Conditions(0, false, 0, 0),
                new Behaviour(false, true, 0), new Effects(true, false, true, 0, 0, 2), new Moon(4, 5, 128, 56, 1),
                palette(), new Sounds(0, false, true), new Lock(false, ""), TowerGrid.empty(), Optional.empty());
        return new TowerLibrary("sample", "Sample Library", "unit test", List.of(tower("sample-000"), second));
    }
}
