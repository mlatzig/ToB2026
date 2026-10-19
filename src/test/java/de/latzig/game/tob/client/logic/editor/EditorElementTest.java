package de.latzig.game.tob.client.logic.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.client.logic.Texts;
import de.latzig.game.tob.core.io.GuiAtlasReader;
import de.latzig.game.tob.core.model.BaseType;
import de.latzig.game.tob.core.model.GuiAtlas;
import de.latzig.game.tob.core.model.ObjectType;

/** Phase 6b spec decision 2: ToB 2009's palette, packed: four base elements, then the 36 objects. */
class EditorElementTest {

    @Test
    void thePaletteHoldsTheFourBaseElementsThenEveryObjectOnce() {
        assertEquals(EditorElement.BASE_COUNT + ObjectType.values().length, EditorElement.PALETTE.size());
        assertEquals(EditorElement.FLOOR, EditorElement.PALETTE.get(0));
        assertEquals(EditorElement.LIFT_DOWN, EditorElement.PALETTE.get(3));
        assertEquals(EditorElement.of(ObjectType.ZAP), EditorElement.PALETTE.get(4), "ToB 2009: Z, G, P, K first");
        assertEquals(EditorElement.of(ObjectType.EXC), EditorElement.PALETTE.get(39));
        Set<ObjectType> objects = EnumSet.noneOf(ObjectType.class);
        EditorElement.PALETTE.stream().filter(e -> e.kind() == EditorElement.Kind.OBJECT)
                .forEach(e -> assertTrue(objects.add(e.object()), e.toString()));
        assertEquals(EnumSet.allOf(ObjectType.class), objects);
    }

    @Test
    void everyEntryAndBaseHasANameAndASpriteOfTheGuiAtlas() throws IOException {
        Texts texts = Texts.english();
        for (EditorElement element : EditorElement.PALETTE) {
            assertFalse(texts.get(element.textKey()).isBlank(), element.toString());
        }
        assertEquals("Lift (Down)", texts.get(EditorElement.LIFT_DOWN.textKey()));
        assertEquals("editor.lfd1", EditorElement.LIFT_DOWN.region(), "the palette shows ToB 2009's sprite");
        assertEquals("editor.lfd2", EditorElement.region(BaseType.LIFT_DOWN_STOP));
        Path atlas = Path.of("src/main/resources/assets/gui/gui-atlas.json");
        assumeTrue(Files.isRegularFile(atlas), "generated assets not available");
        GuiAtlas gui = new GuiAtlasReader().read(atlas);
        for (EditorElement element : EditorElement.PALETTE) {
            assertTrue(gui.regions().containsKey(element.region()), element.region());
        }
        for (BaseType base : BaseType.values()) {
            assertTrue(gui.regions().containsKey(EditorElement.region(base)), base.toString());
        }
    }
}
