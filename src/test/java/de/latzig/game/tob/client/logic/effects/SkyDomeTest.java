package de.latzig.game.tob.client.logic.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.client.logic.MeshArrays;
import de.latzig.game.tob.client.logic.TowerColours;
import de.latzig.game.tob.core.sim.TestTowers;

class SkyDomeTest {

    @Test
    void theDomeFadesFromTheHorizonColourToTheSkyTopAroundTheCamera() {
        TowerColours colours = TowerColours.of(TestTowers.tower().level(0, "F").build());

        MeshArrays dome = SkyDome.arrays(colours, 100f);

        assertEquals(SkyDome.SEGMENTS * (SkyDome.RINGS + 1) * 2, dome.triangles());
        assertEquals(100f, dome.maxY(), 1e-3f);
        float[] p = dome.positions();
        float[] c = dome.colors();
        float[] uv = dome.uvs();
        for (int corner = 0; corner < p.length / 3; corner++) {
            float x = p[corner * 3];
            float y = p[corner * 3 + 1];
            float z = p[corner * 3 + 2];
            assertEquals(100f, (float) Math.sqrt(x * x + y * y + z * z), 1e-3f);
            int rgb = Math.round(c[corner * 4] * 255) << 16 | Math.round(c[corner * 4 + 1] * 255) << 8
                    | Math.round(c[corner * 4 + 2] * 255);
            if (y > 99.99f) {
                assertEquals(colours.skyTop(), rgb);
                assertEquals(0.5f, uv[corner * 2], 1e-3f, "the zenith sees the centre of the sky texture");
                assertEquals(0.5f, uv[corner * 2 + 1], 1e-3f);
            } else if (y < 100f * Math.sin(Math.toRadians(20))) {
                assertEquals(colours.skyHorizon(), rgb, "below about 23 degrees, the skirt included");
            }
        }
    }

    /** Moon haze (Phase 6b-2): {@link SkyDome#look} gives what the dome shows in a direction. */
    @Test
    void lookGivesEachDomeCornersTextureSpotAndColour() {
        TowerColours colours = new TowerColours(0x0088CC, 0x102030, 0x005555, 0, 0, 0, 0, 0);

        MeshArrays dome = SkyDome.arrays(colours, 100f);

        float[] p = dome.positions();
        for (int corner = 0; corner < p.length / 3; corner++) {
            float[] look = SkyDome.look(colours, p[corner * 3] / 100f, p[corner * 3 + 1] / 100f,
                    p[corner * 3 + 2] / 100f);
            assertEquals(dome.uvs()[corner * 2], look[0], 1e-5f);
            assertEquals(dome.uvs()[corner * 2 + 1], look[1], 1e-5f);
            for (int c = 0; c < 3; c++) {
                assertEquals(dome.colors()[corner * 4 + c], look[2 + c], 1e-5f);
            }
        }
    }

    @Test
    void theColourMixFollowsTheToB2005SkyPlane() {
        assertEquals(0f, SkyDome.horizonMix(Math.PI / 2), 1e-6f, "sky colour 1 at the zenith");
        assertEquals(1f, SkyDome.horizonMix(0), 1e-6f, "sky colour 2 at the horizon");
        assertEquals(1f, SkyDome.horizonMix(-0.3), 1e-6f, "and below it");
        assertEquals(1f, SkyDome.horizonMix(Math.toRadians(23)), 1e-6f);
        float at30 = SkyDome.horizonMix(Math.toRadians(30));
        float at60 = SkyDome.horizonMix(Math.toRadians(60));
        assertTrue(at30 > at60 && at60 > 0f && at30 < 1f, at30 + " " + at60);
        assertEquals(6472.0, SkyDome.planeDistance(0), 1.0, "the 2005 plane meets the horizon about 6472 units out");
    }
}
