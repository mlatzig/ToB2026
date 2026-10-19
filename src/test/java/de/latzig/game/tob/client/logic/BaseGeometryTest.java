package de.latzig.game.tob.client.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.client.logic.game.LiftHeights;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.RemakeVisuals;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.sim.Command;
import de.latzig.game.tob.core.sim.Lift;
import de.latzig.game.tob.core.sim.LiftState;
import de.latzig.game.tob.core.sim.RayOutcome;
import de.latzig.game.tob.core.sim.SimEvent;
import de.latzig.game.tob.core.sim.SimOptions;
import de.latzig.game.tob.core.sim.Simulation;
import de.latzig.game.tob.core.sim.SpiderId;
import de.latzig.game.tob.core.sim.TestTowers;
import de.latzig.game.tob.core.sim.WorldState;

class BaseGeometryTest {

    private static final float EPS = 1e-6f;

    private static WorldState world(TestTowers.Builder tower) {
        return Simulation.start(tower.build(), SimOptions.DEFAULT).world();
    }

    private static int quads(WorldState world, int level) {
        TowerDefinition tower = world.tower();
        return BaseGeometry.level(world, level, TowerColours.of(tower)).triangles() / 2;
    }

    @Test
    void aLoneFloorShowsItsTopItsBottomAndFourEdges() {
        WorldState world = world(TestTowers.tower().level(0, "F"));

        assertEquals(6, quads(world, 0));
        assertEquals(BaseGeometry.FLOOR_HEIGHT, BaseGeometry.level(world, 0, TowerColours.of(world.tower())).maxY(),
                EPS);
        assertEquals(0, quads(world, 1));
    }

    @Test
    void neighbouringFloorsAndBoxesHideTheFacesTheyCover() {
        assertEquals(10, quads(world(TestTowers.tower().level(0, "FF")), 0), "2 tops, 2 bottoms, 6 edges");
        assertEquals(10, quads(world(TestTowers.tower().level(0, "BB")), 0), "2 tops, 2 bottoms, 6 sides");
        assertEquals(11, quads(world(TestTowers.tower().level(0, "FB")), 0),
                "floor: top, bottom, 3 edges; box: top, bottom, 4 sides (a floor edge covers too little)");
        WorldState stacked = world(TestTowers.tower().level(0, "B").level(1, "F"));
        assertEquals(5, quads(stacked, 0), "the floor above hides the box top");
        assertEquals(5, quads(stacked, 1), "the box below hides the floor bottom");
    }

    @Test
    void aFloorEatenByTheWiperRebuildsItsLevelWithoutThatFloor() {
        // z=0: floor (0..3,0), wiper (2,0) slot 3, grabber (3,0) turned west; the wiper sweeps west 15 frames later
        Simulation sim = Simulation.start(TestTowers.tower().level(0, "FFFF").object(2, 0, 0, ObjectType.WIP)
                .object(3, 0, 0, ObjectType.GRB).build(), SimOptions.DEFAULT);
        WorldState world = sim.world();
        assertEquals(18, quads(world, 0));
        sim.step(List.of(new Command.TurnLeft(SpiderId.GRABBER)));
        List<SimEvent> events = new ArrayList<>();
        while (!events.contains(new SimEvent.RayHit(0, 2, 3, RayOutcome.GRABBED))) {
            events = sim.step(List.of(new Command.Fire(SpiderId.GRABBER)));
        }
        List<SimEvent> later = new ArrayList<>();
        for (int frame = 0; frame < 30; frame++) {
            later.addAll(sim.step(List.of()));
        }

        assertEquals(2, later.stream().filter(SimEvent.FloorEaten.class::isInstance).count());
        assertEquals(Set.of(0, 1), BaseGeometry.dirtyLevels(later));
        assertEquals(10, quads(world, 0), "two floors left");
    }

    @Test
    void aLiftColumnRisesWithItsPlatformAndStaysOutOfTheLevelMeshes() {
        Simulation sim = Simulation.start(TestTowers.tower().level(0, "U").level(1, "u")
                .object(0, 0, 0, ObjectType.ZAP).build(), SimOptions.DEFAULT);
        WorldState world = sim.world();
        Lift lift = world.lifts().get(0);
        assertEquals(0, quads(world, 0));
        assertEquals(0, quads(world, 1));
        assertEquals(BaseGeometry.FLOOR_HEIGHT, BaseGeometry.lift(lift, LiftHeights.ratio(lift, world, 0f), false,
                0xFFFFFF, 0xFFFFFF).maxY(), EPS);

        sim.step(List.of(new Command.Back(SpiderId.ZAPPER)));
        for (int frame = 0; frame < 31; frame++) {
            sim.step(List.of());
        }

        assertEquals(LiftState.TOP, lift.state());
        MeshArrays top = BaseGeometry.lift(lift, LiftHeights.ratio(lift, world, 0f), false, 0xFFFFFF, 0xFFFFFF);
        assertEquals(1f + BaseGeometry.FLOOR_HEIGHT, top.maxY(), EPS);
        // BasisElementLFTRenderer: top, bottom, 4 shaft sides and 4 platform-rim sides = 10 quads, 20 triangles.
        assertEquals(20, top.triangles(), "top, bottom, four shaft sides and four rim sides");
        assertEquals(18, BaseGeometry.lift(lift, 1f, true, 0xFFFFFF, 0xFFFFFF).triangles(), "no bottom on a box");
    }

    @Test
    void liftSidesDrawACellColouredShaftAndALiftColouredPlatformRim() {
        // BasisElementLFTRenderer draws two quads per compass direction, not the box-side quadrant: a shaft from the
        // cell bottom to the platform, in the cell colour, always at the source's fixed rotation 1 (not rotate1 or
        // rotate2); and a 2-unit platform rim above it, in the lift colour, geometrically and texture-wise a floor
        // edge (floorEdgeUv) at rotate1 (north/south) or rotate2 (east/west). The shaft's U runs .5..1 along the
        // wall (STATIC_POINTS[4]/[5]), its V from 0 at the platform to .5 * ratio at the cell bottom (point0 =
        // (1, .5 * ratio), point1 = (.5, .5 * ratio)) -- the same for every direction, since the fixed rotation
        // cancels the per-cell rotation level out. The lift of aLiftColumnRisesWithItsPlatformAndStaysOutOfTheLevel
        // Meshes is at grid (4,11,0), where rotate1 = 3 and rotate2 = 0; also the bottom (BasisElementLFTRenderer's
        // colorBottom) is the darkened cell colour, not the darkened lift colour.
        Simulation sim = Simulation.start(TestTowers.tower().level(0, "U").level(1, "u")
                .object(0, 0, 0, ObjectType.ZAP).build(), SimOptions.DEFAULT);
        Lift lift = sim.world().lifts().get(0);
        int liftColour = 0x00FF00;
        int cellColour = 0xFF0000;
        MeshArrays mesh = BaseGeometry.lift(lift, 0.5f, false, liftColour, cellColour);

        assertEquals(20, mesh.triangles());
        float[] uvs = mesh.uvs();
        // Builder emits top(0), bottom(1), shaft north/east/south/west(2-5), rim north/east/south/west(6-9).
        float[] shaft = {0.5f, 0.25f, 1f, 0.25f, 1f, 0f, 0.5f, 0.25f, 1f, 0f, 0.5f, 0f};
        assertArrayEquals(shaft, Arrays.copyOfRange(uvs, 24, 36), EPS, "north shaft");
        assertArrayEquals(shaft, Arrays.copyOfRange(uvs, 36, 48), EPS, "east shaft");
        assertArrayEquals(shaft, Arrays.copyOfRange(uvs, 48, 60), EPS, "south shaft");
        assertArrayEquals(shaft, Arrays.copyOfRange(uvs, 60, 72), EPS, "west shaft");
        assertArrayEquals(new float[] {0.5f, 0.59375f, 0f, 0.59375f, 0f, 0.625f, 0.5f, 0.59375f, 0f, 0.625f, 0.5f,
                0.625f}, Arrays.copyOfRange(uvs, 72, 84), EPS, "north rim");
        assertArrayEquals(new float[] {0f, 0.53125f, 0.5f, 0.53125f, 0.5f, 0.5f, 0f, 0.53125f, 0.5f, 0.5f, 0f, 0.5f},
                Arrays.copyOfRange(uvs, 84, 96), EPS, "east rim");
        assertArrayEquals(new float[] {1f, 0.59375f, 0.5f, 0.59375f, 0.5f, 0.625f, 1f, 0.59375f, 0.5f, 0.625f, 1f,
                0.625f}, Arrays.copyOfRange(uvs, 96, 108), EPS, "south rim");
        assertArrayEquals(new float[] {0.5f, 0.53125f, 1f, 0.53125f, 1f, 0.5f, 0.5f, 0.53125f, 1f, 0.5f, 0.5f, 0.5f},
                Arrays.copyOfRange(uvs, 108, 120), EPS, "west rim");

        float[] colors = mesh.colors();
        assertEquals(0f, colors[0], EPS, "the top is the lift colour (no red)");
        assertEquals(1f, colors[1], EPS, "the top is the lift colour (green)");
        assertTrue(colors[24] > 0f, "the bottom is the darkened cell colour, not the darkened lift colour");
        assertEquals(1f, colors[24 * 2], EPS, "the north shaft is the cell colour (red)");
        assertEquals(1f, colors[24 * 6 + 1], EPS, "the north rim is the lift colour (green)");
    }

    /**
     * Phase 5e: in the Classic Look a floor's sides take their own colour; its bottom is the darkened top. The
     * vertex colours are the palette's sRGB colours made linear (controller gamma rule).
     */
    @Test
    void sidesTakeTheirOwnColourAndTheBottomTheDarkenedTop() {
        WorldState world = world(TestTowers.tower().level(0, "F").object(0, 0, 0, ObjectType.ZAP));
        ClassicColours colours = new ClassicColours(ClassicColoursTest.SACRIFICE, 0);

        MeshArrays mesh = BaseGeometry.level(world, 0, colours);

        float[] colors = mesh.colors();
        assertEquals(12, mesh.triangles(), "top, bottom and four edges");
        assertEquals(SrgbColours.toLinear(0xEE / 255f), colors[0], EPS,
                "the top: square set 1, lit top (entry 3)");
        assertEquals(SrgbColours.toLinear(Math.round(0xEE * 0.75f) / 255f), colors[24], EPS,
                "the bottom: the top a quarter darker");
        for (int side = 2; side < 6; side++) {
            assertEquals(SrgbColours.toLinear(0x66 / 255f), colors[24 * side], EPS,
                    "side " + side + ": square set 1, side (entry 1)");
        }
        assertEquals(colours.top(4, 11, 0), new TowerColours(0, 0, 0, 0, 0xEE0000, 0xEE0000, 0, 0).top(4, 11, 0),
                "the Remake look's top is its cell colour");
    }

    /** Phase 5e: the Classic Look's lift platform has a grey top and rim, the shaft the cell's side colour. */
    @Test
    void aClassicLiftHasItsOwnTopRimShaftAndBottomColours() {
        Simulation sim = Simulation.start(TestTowers.tower().level(0, "U").level(1, "u")
                .object(0, 0, 0, ObjectType.ZAP).build(), SimOptions.DEFAULT);
        Lift lift = sim.world().lifts().get(0);

        MeshArrays mesh = BaseGeometry.lift(lift, 0.5f, false, 0xBBBBBB, 0x777777, 0xEE0000, 0x660000);

        float[] colors = mesh.colors();
        assertEquals(0xBB / 255f, colors[0], EPS, "the top");
        assertEquals(Math.round(0xEE * 0.75f) / 255f, colors[24], EPS, "the bottom: the darkened cell top");
        assertEquals(0x66 / 255f, colors[24 * 2], EPS, "the north shaft: the cell's side colour");
        assertEquals(0x77 / 255f, colors[24 * 6], EPS, "the north rim: the lift's side colour");
    }

    /**
     * Phase 5e, controller gamma rule: jME renders vertex colours as linear, so the Classic Look's floors and lifts
     * carry the linearised palette colours; the Remake look's vertex colours (texture tints) stay as they are.
     */
    @Test
    void classicVertexColoursAreTheLinearisedPaletteColoursAndRemakeOnesAreUnchanged() {
        WorldState world = world(TestTowers.tower().level(0, "F").object(0, 0, 0, ObjectType.ZAP));
        Lift lift = world(TestTowers.tower().level(0, "U").level(1, "u").object(0, 0, 0, ObjectType.ZAP)).lifts()
                .get(0);
        ClassicColours classic = new ClassicColours(ClassicColoursTest.SACRIFICE, 0);
        TowerColours remake = new TowerColours(0, 0, 0, 0, 0xEE0000, 0xEE0000, 0xBBBBBB, 0);

        float[] classicFloor = BaseGeometry.level(world, 0, classic).colors();
        float[] remakeFloor = BaseGeometry.level(world, 0, remake).colors();
        float[] classicLift = BaseGeometry.lift(lift, 0.5f, false, classic).colors();
        float[] remakeLift = BaseGeometry.lift(lift, 0.5f, false, remake).colors();

        assertTrue(classic.linear(), "the Classic Look's colours are sRGB palette colours");
        assertFalse(remake.linear(), "the Remake look's vertex colours tint textures as before");
        assertEquals(SrgbColours.toLinear(0xEE / 255f), classicFloor[0], EPS, "a Classic floor top, linear");
        assertEquals(0xEE / 255f, remakeFloor[0], EPS, "the Remake floor top, unchanged");
        assertEquals(SrgbColours.toLinear(0xBB / 255f), classicLift[0], EPS, "a Classic lift top, linear");
        assertEquals(SrgbColours.toLinear(0x77 / 255f), classicLift[24 * 6], EPS, "a Classic lift rim, linear");
        assertEquals(SrgbColours.toLinear(0x66 / 255f), classicLift[24 * 2], EPS, "a Classic shaft, linear");
        assertEquals(0xBB / 255f, remakeLift[0], EPS, "the Remake lift top, unchanged");
        assertEquals(0xEE / 255f, remakeLift[24 * 2], EPS, "the Remake shaft: the cell colour, unchanged");
    }

    @Test
    void theTowerColoursComeFromThePaletteUnlessToB2009LookSettingsExist() {
        TowerDefinition tower = TestTowers.tower().level(0, "F").build();
        TowerColours colours = TowerColours.of(tower);
        assertEquals(0x888877, colours.skyTop(), "palette entry 8 at the zenith (Phase 4a)");
        assertEquals(0x775588, colours.skyHorizon(), "palette entry 7 at the horizon");
        assertEquals(0x1133EE, colours.ground());
        assertEquals(0x0000FF, colours.ground2(), "palette entry 16");
        assertEquals(0x662299, colours.cellColour(0, 0, 0));
        assertEquals(0x3399CC, colours.cellColour(1, 0, 0), "CROSSED alternates");
        assertEquals(TowerColours.DEFAULT_LIFT, colours.lift());

        TowerColours remake = TowerColours.of(tower.withRemake(new RemakeVisuals(
                0xFFEEDD, 3, 0x004B4B, 0x006969, 6, 0xABCDEF, false, false, TowerColours.ALTERNATED, 0xA500A5,
                0xE10000)));
        assertEquals(0x004B4B, remake.ground());
        assertEquals(0x006969, remake.ground2());
        assertEquals(0xABCDEF, remake.lift());
        assertEquals(0xFFEEDD, remake.sun());
        assertEquals(0xA500A5, remake.cellColour(3, 5, 0));
        assertEquals(0xE10000, remake.cellColour(3, 5, 1));
        assertEquals(0x888877, remake.skyTop(), "the sky always comes from the palette");
        assertEquals(0x404040, TowerColours.mix(0x000000, 0x808080, 0.5f));
        assertTrue(BaseGeometry.darker(0x808080) < 0x808080);
    }

    /**
     * The only UV-free slot in the first quad array for an isolated single-floor or single-box tower: {@code cell()}
     * emits top, bottom, north, east, south, west in that order, and the top is the first (12-float) UV group.
     */
    private static float[] topUv(TestTowers.Builder tower) {
        WorldState world = world(tower);
        return Arrays.copyOfRange(BaseGeometry.level(world, 0, TowerColours.of(world.tower())).uvs(), 0, 12);
    }

    @Test
    void floorTopUsesTheRotatedStaticQuadrantAtAllFourRotationLevels() {
        // AbstractBasisElementRenderer.addQuad: slot i gets position v[(i + rotation) % 4] and keeps UV t_i, so
        // source vertex k receives t[(k - rotation) mod 4]. RendererUtil.getVectorByElementPosition/ElementPosition.
        // getNeighbour (worldZ = 240 - 32y + lz, NORTH = y - 1) make the source's lz = 32 side grid north
        // (BaseGeometry's z0) and lz = 0 grid south (z0 + 1); x and height are not flipped. Under that z-flip, the
        // top quadrant STATIC_POINTS[0,1,2,3] = (0,0),(.5,0),(.5,.5),(0,.5) maps to BaseGeometry's own corner order
        // (x0,z0),(x0,z0+1),(x0+1,z0+1),(x0+1,z0) as STATIC_POINTS[1,2,3,0] = (.5,0),(.5,.5),(0,.5),(0,0), each
        // physical corner then taking the UV (rotation) positions behind it in that order (independently re-derived
        // and verified numerically for all 4 rotation levels; BasisElementFLRRenderer/BOXRenderer top calls use the
        // identical STATIC_VECTORS[8-11]/STATIC_POINTS[0-3] pairing). Grid cells (6,4,0), (7,4,0), (5,4,0), (4,4,0)
        // give rotation 0, 1, 2 and 3 (BaseGeometry.rotation, the same ROTATION_ORDER table as
        // AbstractBasisElementRenderer.getRotationLevel).
        assertArrayEquals(new float[] {0.5f, 0f, 0f, 0f, 0f, 0.5f, 0.5f, 0f, 0f, 0.5f, 0.5f, 0.5f},
                topUv(TestTowers.tower().level(0, "..F", "", "", "", "", "", "", "")), EPS, "rotation 0");
        assertArrayEquals(new float[] {0f, 0f, 0f, 0.5f, 0.5f, 0.5f, 0f, 0f, 0.5f, 0.5f, 0.5f, 0f},
                topUv(TestTowers.tower().level(0, "...F", "", "", "", "", "", "", "")), EPS, "rotation 1");
        assertArrayEquals(new float[] {0f, 0.5f, 0.5f, 0.5f, 0.5f, 0f, 0f, 0.5f, 0.5f, 0f, 0f, 0f},
                topUv(TestTowers.tower().level(0, ".F", "", "", "", "", "", "", "")), EPS, "rotation 2");
        assertArrayEquals(new float[] {0.5f, 0.5f, 0.5f, 0f, 0f, 0f, 0.5f, 0.5f, 0f, 0f, 0f, 0.5f},
                topUv(TestTowers.tower().level(0, "F", "", "", "", "", "", "", "")), EPS, "rotation 3");
    }

    @Test
    void boxTopUsesTheSameRotatedQuadrantAsAFloorTop() {
        // BasisElementBOXRenderer's top call (lines 70-78) uses the identical STATIC_VECTORS[8-11]/STATIC_POINTS[0-3]
        // pairing as BasisElementFLRRenderer's floor top, so a box top at rotation 0 (grid (6,4,0)) is pinned by the
        // same z-flipped rotation as the floor top above.
        assertArrayEquals(new float[] {0.5f, 0f, 0f, 0f, 0f, 0.5f, 0.5f, 0f, 0f, 0.5f, 0.5f, 0.5f},
                topUv(TestTowers.tower().level(0, "..B", "", "", "", "", "", "", "")), EPS);
    }

    /** The second (12-float) UV group of an isolated single-floor or single-box tower: {@code cell()} emits top,
     * bottom, north, east, south, west, and the bottom is the second group. */
    private static float[] bottomUv(TestTowers.Builder tower) {
        WorldState world = world(tower);
        return Arrays.copyOfRange(BaseGeometry.level(world, 0, TowerColours.of(world.tower())).uvs(), 12, 24);
    }

    @Test
    void floorAndBoxBottomsUseTheRotatedStaticQuadrantAtAllFourRotationLevels() {
        // BasisElementBOXRenderer's bottom call (lines 80-88) pairs STATIC_VECTORS[3,2,1,0] with
        // STATIC_POINTS[4,5,6,7] = (.5,0),(1,0),(1,.5),(.5,.5); under the same z-flip and the same addQuad rotation
        // as the top, BaseGeometry's own corner order (x0,z0),(x0+1,z0),(x0+1,z0+1),(x0,z0+1) maps to
        // STATIC_POINTS[6,7,4,5] = (1,.5),(.5,.5),(.5,0),(1,0) (independently re-derived and verified numerically for
        // all 4 rotation levels). BasisElementFLRRenderer's bottom call (lines 91-99) is the identical pairing, so a
        // floor and a box bottom share the same formula; grid cells as in the top test give rotation 0-3.
        assertArrayEquals(new float[] {1f, 0.5f, 1f, 0f, 0.5f, 0f, 1f, 0.5f, 0.5f, 0f, 0.5f, 0.5f},
                bottomUv(TestTowers.tower().level(0, "..F", "", "", "", "", "", "", "")), EPS, "rotation 0");
        assertArrayEquals(new float[] {1f, 0f, 0.5f, 0f, 0.5f, 0.5f, 1f, 0f, 0.5f, 0.5f, 1f, 0.5f},
                bottomUv(TestTowers.tower().level(0, "...B", "", "", "", "", "", "", "")), EPS, "rotation 1");
        assertArrayEquals(new float[] {0.5f, 0f, 0.5f, 0.5f, 1f, 0.5f, 0.5f, 0f, 1f, 0.5f, 1f, 0f},
                bottomUv(TestTowers.tower().level(0, ".F", "", "", "", "", "", "", "")), EPS, "rotation 2");
        assertArrayEquals(new float[] {0.5f, 0.5f, 1f, 0.5f, 1f, 0f, 0.5f, 0.5f, 1f, 0f, 0.5f, 0f},
                bottomUv(TestTowers.tower().level(0, "B", "", "", "", "", "", "", "")), EPS, "rotation 3");
    }

    @Test
    void floorEdgeUsesTheStaticFloorStripWithARotationShiftAndSeparateNorthSouthAndEastWestVOrientations() {
        // BasisElementFLRRenderer's south/north/east/west calls never corner-rotate (rotate = 0) but add
        // vShift = rotate1 * 1/32 (south/north) or rotate2 * 1/32 (east/west), and the south/north edges have the
        // cell-bottom corners at the quadrant's own V = 0.5 (STATIC_POINTS[12]/[13] or [8]/[9]) with the floor's top
        // corners one strip deeper at V = 0.53125 (STATIC_POINTS[18]/[19] or [16]/[17]), while the east/west edges
        // have it the other way round: the bottom at V = 0.53125, the top at V = 0.5 (east: SV[7]/[6] at height 2
        // take SP[8]/[9]; SV[2]/[3] at height 0 take SP[16]/[17]). U (independently re-derived under the same z-flip
        // as the top/bottom fix) runs the opposite way along the wall from BaseGeometry's own side() convention on
        // every edge. The lone floor at grid (4,11,0) has rotate1 = 3, rotate2 = 0 (BaseGeometry.rotation), so the
        // north/south edges show the vShift and the east/west edges show the unshifted (V 0.5/0.53125) orientation.
        WorldState world = world(TestTowers.tower().level(0, "F"));
        float[] uvs = BaseGeometry.level(world, 0, TowerColours.of(world.tower())).uvs();
        // cell() emits top(0), bottom(1), north(2), east(3), south(4), west(5).
        assertArrayEquals(new float[] {0.5f, 0.59375f, 0f, 0.59375f, 0f, 0.625f, 0.5f, 0.59375f, 0f, 0.625f, 0.5f,
                0.625f}, Arrays.copyOfRange(uvs, 24, 36), EPS, "north (rotate1 = 3)");
        assertArrayEquals(new float[] {0f, 0.53125f, 0.5f, 0.53125f, 0.5f, 0.5f, 0f, 0.53125f, 0.5f, 0.5f, 0f, 0.5f},
                Arrays.copyOfRange(uvs, 36, 48), EPS, "east (rotate2 = 0)");
        assertArrayEquals(new float[] {1f, 0.59375f, 0.5f, 0.59375f, 0.5f, 0.625f, 1f, 0.59375f, 0.5f, 0.625f, 1f,
                0.625f}, Arrays.copyOfRange(uvs, 48, 60), EPS, "south (rotate1 = 3)");
        assertArrayEquals(new float[] {0.5f, 0.53125f, 1f, 0.53125f, 1f, 0.5f, 0.5f, 0.53125f, 1f, 0.5f, 0.5f, 0.5f},
                Arrays.copyOfRange(uvs, 60, 72), EPS, "west (rotate2 = 0)");
    }

    /** The north side of an isolated box: {@code cell()} emits top(0), bottom(1), north(2), east(3), south(4), west
     * (5); the north side is the third (12-float) UV group. */
    private static float[] boxNorthSideUv(TestTowers.Builder tower) {
        WorldState world = world(tower);
        return Arrays.copyOfRange(BaseGeometry.level(world, 0, TowerColours.of(world.tower())).uvs(), 24, 36);
    }

    @Test
    void boxSideFacesRotateWithTheCellAndPairUWithHeightAndVWithTheHorizontalPosition() {
        // BasisElementBOXRenderer's north face (AbstractBasisElementRenderer.addQuad rotation, lines 160-205;
        // BasisElementBOXRenderer north call, "rotate1") samples STATIC_POINTS[8-11] ("Quartal 3", U 0..0.5 / V
        // 0.5..1) and rotates per cell exactly like the top/bottom faces (fix round 1), but pairs U with height and V
        // with the horizontal position along the wall, the opposite of BaseGeometry.Builder's own side() convention.
        // Derived from the source's v0..v3/STATIC_POINTS[8-11] pairing for all 4 rotation levels, accounting for the
        // z-axis flip between the source's and BaseGeometry's coordinate conventions established for the top/bottom
        // fix (round 1): grid (6,4,0)/(7,4,0)/(5,4,0)/(4,4,0) give rotation 0/1/2/3.
        assertArrayEquals(new float[] {0f, 0.5f, 0f, 1f, 0.5f, 1f, 0f, 0.5f, 0.5f, 1f, 0.5f, 0.5f},
                boxNorthSideUv(TestTowers.tower().level(0, "..B", "", "", "", "", "", "", "")), EPS, "rotation 0");
        assertArrayEquals(new float[] {0f, 1f, 0.5f, 1f, 0.5f, 0.5f, 0f, 1f, 0.5f, 0.5f, 0f, 0.5f},
                boxNorthSideUv(TestTowers.tower().level(0, "...B", "", "", "", "", "", "", "")), EPS, "rotation 1");
        assertArrayEquals(new float[] {0.5f, 1f, 0.5f, 0.5f, 0f, 0.5f, 0.5f, 1f, 0f, 0.5f, 0f, 1f},
                boxNorthSideUv(TestTowers.tower().level(0, ".B", "", "", "", "", "", "", "")), EPS, "rotation 2");
        assertArrayEquals(new float[] {0.5f, 0.5f, 0f, 0.5f, 0f, 1f, 0.5f, 0.5f, 0f, 1f, 0.5f, 1f},
                boxNorthSideUv(TestTowers.tower().level(0, "B", "", "", "", "", "", "", "")), EPS, "rotation 3");
    }

    @Test
    void boxEastSouthAndWestSidesShareTheQuadrantOfNorthAndSouthRespectively() {
        // BasisElementBOXRenderer's east call reuses the same STATIC_POINTS[8-11] quadrant as north, but rotated by
        // "rotate2" (AbstractBasisElementRenderer.getRotationLevel(position, 2)), not "rotate1"; south and west reuse
        // STATIC_POINTS[12-15] ("Quartal 4", U 0.5..1 / V 0.5..1), south by "rotate1" (like north), west by
        // "rotate2" (like east). At grid (6,4,0), rotate1 = 0 but rotate2 = 2 (BaseGeometry.rotation, same
        // ROTATION_ORDER table with offset 0 or 2), so north/south are the rotation-0 quadrant and east/west are the
        // rotation-2 quadrant; this confirms the full compass-to-quadrant choice, not just north, and that north/south
        // and east/west are rotated independently.
        WorldState world = world(TestTowers.tower().level(0, "..B", "", "", "", "", "", "", ""));
        float[] uvs = BaseGeometry.level(world, 0, TowerColours.of(world.tower())).uvs();
        // cell() emits top(0), bottom(1), north(2), east(3), south(4), west(5)
        assertArrayEquals(new float[] {0.5f, 1f, 0.5f, 0.5f, 0f, 0.5f, 0.5f, 1f, 0f, 0.5f, 0f, 1f},
                Arrays.copyOfRange(uvs, 36, 48), EPS, "east");
        assertArrayEquals(new float[] {0.5f, 0.5f, 0.5f, 1f, 1f, 1f, 0.5f, 0.5f, 1f, 1f, 1f, 0.5f},
                Arrays.copyOfRange(uvs, 48, 60), EPS, "south");
        assertArrayEquals(new float[] {1f, 1f, 1f, 0.5f, 0.5f, 0.5f, 1f, 1f, 0.5f, 0.5f, 0.5f, 1f},
                Arrays.copyOfRange(uvs, 60, 72), EPS, "west");
    }

    @Test
    void cellColourPinsTheRemainingPatternFormulasAsImplementedToday() {
        // TowerColours.cellColour's LINE_BY_LINE, CROSSED_IN_HALF, ONE_IN_FOUR and the three CROSS_FADED_BY_* branches
        // (TowerColours.java:47-55) have no test yet (review "Important"); these pin today's formulas exactly as
        // written. If the author later decides to follow AbstractBasisElementRenderer.getColor's original pattern
        // table more closely, these assertions will be adapted together with that decision.
        int p1 = 0x111111;
        int p2 = 0x222222;

        TowerColours lineByLine = new TowerColours(0, 0, 0, TowerColours.LINE_BY_LINE, p1, p2, 0, 0);
        assertEquals(p2, lineByLine.cellColour(0, 0, 0));
        assertEquals(p1, lineByLine.cellColour(0, 1, 0));

        TowerColours crossedInHalf = new TowerColours(0, 0, 0, TowerColours.CROSSED_IN_HALF, p1, p2, 0, 0);
        assertEquals(p1, crossedInHalf.cellColour(0, 0, 0));
        assertEquals(p2, crossedInHalf.cellColour(8, 0, 0));

        TowerColours oneInFour = new TowerColours(0, 0, 0, TowerColours.ONE_IN_FOUR, p1, p2, 0, 0);
        assertEquals(p2, oneInFour.cellColour(0, 0, 0));
        assertEquals(p1, oneInFour.cellColour(1, 0, 0));

        TowerColours fadedByLine = new TowerColours(0, 0, 0, TowerColours.CROSS_FADED_BY_LINE, p1, p2, 0, 0);
        assertEquals(p2, fadedByLine.cellColour(0, 0, 0));
        assertEquals(p1, fadedByLine.cellColour(0, 15, 0));

        TowerColours fadedByLevel = new TowerColours(0, 0, 0, TowerColours.CROSS_FADED_BY_LEVEL, p1, p2, 0, 0);
        assertEquals(p1, fadedByLevel.cellColour(0, 0, 0));
        assertEquals(p2, fadedByLevel.cellColour(0, 0, 5));

        TowerColours fadedByRadius = new TowerColours(0, 0, 0, TowerColours.CROSS_FADED_BY_RADIUS, p1, p2, 0, 0);
        assertEquals(p2, fadedByRadius.cellColour(8, 8, 0));
        assertEquals(p1, fadedByRadius.cellColour(8, 8, 1));
        assertEquals(p1, fadedByRadius.cellColour(0, 8, 0));
    }
}
