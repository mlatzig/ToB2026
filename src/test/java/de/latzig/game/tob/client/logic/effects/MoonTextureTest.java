package de.latzig.game.tob.client.logic.effects;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class MoonTextureTest {

    private static final MoonTexture.Surface GREY = new MoonTexture.Surface(MoonTexture.Kind.CRATERED, 0xFFFFFF, 70,
            11L);

    /** The albedo's red channel at the texel nearest to the unit direction (x, y, z). */
    private static int albedoAt(MoonTexture.Maps maps, double x, double y, double z) {
        double latitude = Math.asin(Math.max(-1, Math.min(1, y)));
        double longitude = Math.atan2(x, z);
        if (longitude < 0) {
            longitude += 2 * Math.PI;
        }
        int column = Math.min(maps.width() - 1, (int) (longitude / (2 * Math.PI) * maps.width()));
        int row = Math.min(maps.height() - 1, (int) ((latitude + Math.PI / 2) / Math.PI * maps.height()));
        return maps.albedo()[(row * maps.width() + column) * 4] & 0xFF;
    }

    @Test
    void theSameSurfaceGivesTheSameMapsAndTheSeedCounts() {
        MoonTexture.Maps once = MoonTexture.generate(GREY, 256, 128);

        MoonTexture.Maps again = MoonTexture.generate(GREY, 256, 128);
        assertArrayEquals(once.albedo(), again.albedo(), "deterministic, however the rows are scheduled");
        assertArrayEquals(once.normal(), again.normal());
        MoonTexture.Maps other = MoonTexture.generate(new MoonTexture.Surface(MoonTexture.Kind.CRATERED, 0xFFFFFF,
                70, 12L), 256, 128);
        assertFalse(Arrays.equals(once.albedo(), other.albedo()), "another seed, another moon");
        assertEquals(256 * 128 * 4, once.albedo().length);
        assertEquals(256 * 128 * 4, once.normal().length);
    }

    @Test
    void theDirectionOfATexelIsOnTheUnitSphereWithTheSouthPoleInRowZero() {
        double[] south = MoonTexture.direction(0, 0, 64, 32);
        double[] north = MoonTexture.direction(10, 31, 64, 32);
        double[] equator = MoonTexture.direction(16, 16, 64, 32);

        assertTrue(south[1] < -0.99, "row 0 is the south pole's row");
        assertTrue(north[1] > 0.99);
        assertEquals(1.0, Math.sqrt(equator[0] * equator[0] + equator[1] * equator[1] + equator[2] * equator[2]),
                1e-12);
        assertTrue(equator[0] > 0.99, "a quarter turn east of longitude 0 is +x");
    }

    @Test
    void theMapsHaveNoSeamAtTheDateLine() {
        MoonTexture.Maps maps = MoonTexture.generate(GREY, 512, 256);
        for (byte[] map : new byte[][] {maps.albedo(), maps.normal()}) {
            long[] columns = new long[maps.width()];
            for (int y = 0; y < maps.height(); y++) {
                for (int x = 0; x < maps.width(); x++) {
                    int next = (x + 1) % maps.width();
                    for (int c = 0; c < 3; c++) {
                        columns[x] += Math.abs((map[(y * maps.width() + next) * 4 + c] & 0xFF)
                                - (map[(y * maps.width() + x) * 4 + c] & 0xFF));
                    }
                }
            }
            double mean = Arrays.stream(columns).average().orElseThrow();
            long seam = columns[maps.width() - 1];
            assertTrue(seam < mean * 1.5, "the date line is no edge: seam " + seam + " vs mean " + mean);
        }
    }

    /**
     * Pinched poles would show as features squeezed towards the poles: then two points a fixed angle apart on the
     * sphere would differ much more near a pole than at the equator. Computed on the sphere, both regions look alike.
     */
    @Test
    void thePolesAreNotPinched() {
        MoonTexture.Maps maps = MoonTexture.generate(GREY, 1024, 512);
        Random random = new Random(5L);
        double step = 0.02;
        double polar = 0;
        double equatorial = 0;
        int samples = 4000;
        for (int i = 0; i < samples; i++) {
            for (int region = 0; region < 2; region++) {
                double latitude = region == 0 ? Math.toRadians(80 + random.nextDouble() * 9.5)
                        : Math.toRadians(-10 + random.nextDouble() * 20);
                double longitude = random.nextDouble() * 2 * Math.PI;
                double[] p = {Math.cos(latitude) * Math.sin(longitude), Math.sin(latitude),
                        Math.cos(latitude) * Math.cos(longitude)};
                double heading = random.nextDouble() * 2 * Math.PI;
                double[] east = {Math.cos(longitude), 0, -Math.sin(longitude)};
                double[] north = {-Math.sin(latitude) * Math.sin(longitude), Math.cos(latitude),
                        -Math.sin(latitude) * Math.cos(longitude)};
                double[] q = new double[3];
                for (int k = 0; k < 3; k++) {
                    q[k] = p[k] * Math.cos(step) + (Math.cos(heading) * east[k] + Math.sin(heading) * north[k])
                            * Math.sin(step);
                }
                double difference = Math.abs(albedoAt(maps, p[0], p[1], p[2]) - albedoAt(maps, q[0], q[1], q[2]));
                if (region == 0) {
                    polar += difference;
                } else {
                    equatorial += difference;
                }
            }
        }
        double ratio = polar / equatorial;
        assertTrue(ratio > 0.6 && ratio < 1.6, "the same detail per angle near the pole and at the equator: " + ratio);
    }

    @Test
    void everyKindHasItsOwnLookAndTheGasPlanetIsBandedWithoutRelief() {
        MoonTexture.Maps gas = MoonTexture.generate(new MoonTexture.Surface(MoonTexture.Kind.GAS, 0xFFFFFF, 0, 41L),
                256, 128);
        for (int i = 0; i < gas.normal().length; i += 4) {
            assertEquals(128, gas.normal()[i] & 0xFF, "flat normals: red");
            assertEquals(128, gas.normal()[i + 1] & 0xFF, "flat normals: green");
        }
        MoonTexture.Maps ice = MoonTexture.generate(new MoonTexture.Surface(MoonTexture.Kind.ICE, 0xF2FAFF, 12, 13L),
                256, 128);
        MoonTexture.Maps grey = MoonTexture.generate(GREY, 256, 128);
        assertTrue(mean(ice.albedo(), 2) > mean(grey.albedo(), 2) + 40, "the ice moon is bright");
        MoonTexture.Maps desert = MoonTexture.generate(new MoonTexture.Surface(MoonTexture.Kind.DESERT, 0xFFB380, 40,
                52L), 256, 128);
        assertTrue(mean(desert.albedo(), 0) > mean(desert.albedo(), 2) + 30, "the tinted desert moon is reddish");
    }

    /**
     * Acceptance fix: the gas planet's bands have a strong contrast from pole to pole, and its albedo varies along
     * every latitude circle (ragged band edges, streaks), so its slow turn is visible. Bands that only depend on the
     * latitude would give a spread of about 0 along a circle.
     */
    @Test
    void theGasPlanetHasStrongBandsThatVaryAroundItsCircumference() {
        MoonTexture.Maps gas = MoonTexture.generate(new MoonTexture.Surface(MoonTexture.Kind.GAS, 0xFFFFFF, 0, 41L),
                512, 256);
        int width = gas.width();
        double along = 0;
        int rows = 0;
        double[] rowMeans = new double[gas.height()];
        for (int y = 0; y < gas.height(); y++) {
            double sum = 0;
            double squares = 0;
            for (int x = 0; x < width; x++) {
                int red = gas.albedo()[(y * width + x) * 4] & 0xFF;
                sum += red;
                squares += red * red;
            }
            double mean = sum / width;
            rowMeans[y] = mean;
            double latitude = Math.asin(MoonTexture.direction(0, y, width, gas.height())[1]);
            if (Math.abs(latitude) < Math.toRadians(60)) {
                along += Math.sqrt(Math.max(0, squares / width - mean * mean));
                rows++;
            }
        }
        double alongCircle = along / rows;
        double meanOfMeans = Arrays.stream(rowMeans).average().orElseThrow();
        double acrossBands = Math.sqrt(Arrays.stream(rowMeans).map(m -> (m - meanOfMeans) * (m - meanOfMeans))
                .average().orElseThrow());
        System.out.printf("MoonTextureTest: gas albedo spread along a latitude circle %.1f, across the bands %.1f%n",
                alongCircle, acrossBands);
        assertTrue(alongCircle >= 8, "the albedo varies along a latitude circle: " + alongCircle);
        assertTrue(acrossBands >= 18, "strong band contrast from pole to pole: " + acrossBands);
    }

    /**
     * Acceptance fix: craters are not round and uniform. Walking out from a crater's centre in 36 directions, the
     * radius where its bowl ends ({@code q} = 1) differs around the crater (irregular, partly oval outline); the
     * craters differ in depth and rim, and some are eroded and some oval. Round craters would give a spread of 0.
     */
    @Test
    void theCratersAreIrregularOvalAndVaried() {
        java.util.List<MoonTexture.Crater> craters = MoonTexture.craters(GREY);
        double[] spreads = craters.stream().mapToDouble(MoonTextureTest::outlineSpread).sorted().toArray();
        double median = spreads[spreads.length / 2];
        System.out.printf("MoonTextureTest: crater outline spread (max - min) / mean: min %.2f, median %.2f%n",
                spreads[0], median);
        assertTrue(spreads[0] > 0.04, "every crater's outline is irregular: " + spreads[0]);
        assertTrue(median > 0.12, "clearly not radially symmetric: " + median);
        double[] depths = craters.stream().mapToDouble(MoonTexture.Crater::depth).sorted().toArray();
        double[] rims = craters.stream().mapToDouble(MoonTexture.Crater::rim).sorted().toArray();
        assertTrue(depths[depths.length - 1] > 1.5 * depths[0], "the depth varies");
        assertTrue(rims[rims.length - 1] > 1.5 * rims[0], "the rim height varies");
        long eroded = craters.stream().filter(c -> c.erosion() > 0.3).count();
        long oval = craters.stream().filter(c -> c.stretch() > 1.15).count();
        assertTrue(eroded >= 5 && eroded < craters.size() / 2, "some eroded craters: " + eroded);
        assertTrue(oval >= 5 && oval < craters.size() / 2, "some oval craters: " + oval);
    }

    /** (max − min) / mean of the radius where {@code crater}'s bowl ends, in 36 directions around its centre. */
    private static double outlineSpread(MoonTexture.Crater crater) {
        double[] c = {crater.x(), crater.y(), crater.z()};
        double[] up = Math.abs(c[1]) < 0.9 ? new double[] {0, 1, 0} : new double[] {1, 0, 0};
        double[] e1 = normalize(cross(up, c));
        double[] e2 = cross(c, e1);
        double min = Double.MAX_VALUE;
        double max = 0;
        double sum = 0;
        for (int k = 0; k < 36; k++) {
            double theta = 2 * Math.PI * k / 36;
            double step = crater.radius() / 500;
            double angle = step;
            while (angle < crater.limit()) {
                double[] d = new double[3];
                for (int i = 0; i < 3; i++) {
                    d[i] = c[i] * Math.cos(angle) + (Math.cos(theta) * e1[i] + Math.sin(theta) * e2[i])
                            * Math.sin(angle);
                }
                if (MoonTexture.craterQ(crater, d[0], d[1], d[2]) >= 1) {
                    break;
                }
                angle += step;
            }
            min = Math.min(min, angle);
            max = Math.max(max, angle);
            sum += angle;
        }
        return (max - min) / (sum / 36);
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double[] normalize(double[] a) {
        double length = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        return new double[] {a[0] / length, a[1] / length, a[2] / length};
    }

    @Test
    void aCancelledGenerationStops() {
        AtomicInteger asked = new AtomicInteger();
        assertThrows(CancellationException.class,
                () -> MoonTexture.generate(GREY, 512, 256, () -> asked.incrementAndGet() > 10));
        assertThrows(IllegalArgumentException.class, () -> MoonTexture.generate(GREY, 300, 100));
    }

    @Test
    void theDetailTextureTilesAndOnlyDarkensALittle() {
        byte[] detail = MoonTexture.detail(64, 3L, 0.2f);
        int min = 255;
        int max = 0;
        for (int i = 0; i < detail.length; i += 4) {
            min = Math.min(min, detail[i] & 0xFF);
            max = Math.max(max, detail[i] & 0xFF);
        }
        assertTrue(min >= 255 * 0.8 - 1 && max <= 255, min + ".." + max);
        assertTrue(max - min > 10, "not flat");
        assertArrayEquals(detail, MoonTexture.detail(64, 3L, 0.2f));
    }

    private static double mean(byte[] rgba, int channel) {
        long sum = 0;
        for (int i = channel; i < rgba.length; i += 4) {
            sum += rgba[i] & 0xFF;
        }
        return (double) sum / (rgba.length / 4);
    }
}
