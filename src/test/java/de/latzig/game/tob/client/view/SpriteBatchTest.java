package de.latzig.game.tob.client.view;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.VertexBuffer.Type;

import de.latzig.game.tob.client.logic.effects.ExplosionSprites;
import de.latzig.game.tob.core.model.FxAtlas;
import de.latzig.game.tob.core.model.FxSequence;

/**
 * Pins {@link SpriteBatch#update}'s vertex output (fix round 1: the per-sprite quad corners must be written
 * straight into the reused {@code positions}/{@code uvs} buffers, no {@code float[][]} or other object allocated
 * per sprite per frame) and that repeated calls reuse the same backing arrays rather than reallocating them.
 */
class SpriteBatchTest {

    private static final FxAtlas ATLAS = new FxAtlas("fx.png", 100, 100, Map.of(), Map.of("seq",
            new FxSequence(0, 0, 10, 10, 1, 1)), "test");

    private static float[] floats(Geometry geometry, Type type) {
        float[] out = new float[geometry.getMesh().getFloatBuffer(type).limit()];
        geometry.getMesh().getFloatBuffer(type).rewind();
        geometry.getMesh().getFloatBuffer(type).get(out);
        geometry.getMesh().getFloatBuffer(type).rewind();
        return out;
    }

    private static float[] field(SpriteBatch batch, String name) throws ReflectiveOperationException {
        Field f = SpriteBatch.class.getDeclaredField(name);
        f.setAccessible(true);
        return (float[]) f.get(batch);
    }

    @Test
    void oneSpriteWritesExactCornerCoordinatesWithoutAnyTemporaryArray() {
        SpriteBatch batch = new SpriteBatch("batch", ATLAS, null, 2);
        ExplosionSprites.Sprite sprite = new ExplosionSprites.Sprite("seq", 0, 0f, 0f, 0f, 1f);
        Vector3f left = new Vector3f(1f, 0f, 0f);
        Vector3f up = new Vector3f(0f, 1f, 0f);

        int shown = batch.update(List.of(sprite), left, up);

        assertEquals(1, shown);
        assertArrayEquals(new float[] {
                1f, -1f, 0f,
                -1f, -1f, 0f,
                -1f, 1f, 0f,
                1f, -1f, 0f,
                -1f, 1f, 0f,
                1f, 1f, 0f,
                0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f},
                floats(batch.geometry(), Type.Position), 0f,
                "both triangles of the one shown sprite's quad, the second (unused) slot collapsed to a point");
        assertArrayEquals(new float[] {
                0f, 0.1f,
                0.1f, 0.1f,
                0.1f, 0f,
                0f, 0.1f,
                0.1f, 0f,
                0f, 0f,
                0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f}, floats(batch.geometry(), Type.TexCoord), 0f);
    }

    @Test
    void aSecondUpdateWithTheSameSpriteCountReusesTheSameBuffersAndOverwritesThemInPlace() throws Exception {
        SpriteBatch batch = new SpriteBatch("batch", ATLAS, null, 2);
        Vector3f left = new Vector3f(1f, 0f, 0f);
        Vector3f up = new Vector3f(0f, 1f, 0f);
        float[] positionsBefore = field(batch, "positions");
        float[] uvsBefore = field(batch, "uvs");

        batch.update(List.of(new ExplosionSprites.Sprite("seq", 0, 0f, 0f, 0f, 1f)), left, up);
        float[] afterFirst = floats(batch.geometry(), Type.Position).clone();
        batch.update(List.of(new ExplosionSprites.Sprite("seq", 0, 5f, 0f, 0f, 1f)), left, up);
        float[] afterSecond = floats(batch.geometry(), Type.Position);

        assertSame(positionsBefore, field(batch, "positions"), "the positions array is never reallocated");
        assertSame(uvsBefore, field(batch, "uvs"), "the uvs array is never reallocated");
        assertEquals(5f, afterSecond[0] - afterFirst[0], 1e-5f, "overwritten in place with the new sprite's x");
    }
}
