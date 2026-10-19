package de.latzig.game.tob.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

/**
 * Phase 7c decision 3: the ToB 2005 icon - the original 16 and 32 px, pixel-exact enlargements to 48 (16 x 3), 64, 128
 * and 256 px (32 x 2, 4, 8) - as the window's icons and as the packages' icons ({@code src/package}, read from the
 * project root, Maven's working directory).
 */
class WindowIconsTest {

    @Test
    void theWindowGetsTheIconInSixSizes() {
        BufferedImage[] icons = WindowIcons.load();
        assertNotNull(icons, "every icon resource is readable");
        assertEquals(WindowIcons.SIZES.size(), icons.length);
        for (int i = 0; i < icons.length; i++) {
            assertEquals(WindowIcons.SIZES.get(i), icons[i].getWidth());
            assertEquals(WindowIcons.SIZES.get(i), icons[i].getHeight());
        }
    }

    @Test
    void theEnlargementsArePixelExactCopiesOfTheOriginals() {
        BufferedImage[] icons = WindowIcons.load();
        assertEnlarged(icons[0], icons[2], 3);
        assertEnlarged(icons[1], icons[3], 2);
        assertEnlarged(icons[1], icons[4], 4);
        assertEnlarged(icons[1], icons[5], 8);
    }

    @Test
    void thePackageIconsAreTheSameImages() throws IOException {
        BufferedImage png = ImageIO.read(Path.of("src/package/ToB2026.png").toFile());
        assertArrayEquals(pixels(WindowIcons.load()[5]), pixels(png), "the Linux icon is the 256 px icon");
        ByteBuffer ico = ByteBuffer.wrap(Files.readAllBytes(Path.of("src/package/ToB2026.ico")))
                .order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0, ico.getShort(0), "ICO reserved field");
        assertEquals(1, ico.getShort(2), "ICO type: icon");
        List<Integer> sizes = new ArrayList<>();
        for (int i = 0; i < ico.getShort(4); i++) {
            int width = Byte.toUnsignedInt(ico.get(6 + 16 * i));
            sizes.add(width == 0 ? 256 : width);
        }
        assertEquals(WindowIcons.SIZES, sizes.stream().sorted().toList(), "the Windows icon has the six sizes");
    }

    private static void assertEnlarged(BufferedImage original, BufferedImage enlarged, int factor) {
        assertEquals(original.getWidth() * factor, enlarged.getWidth());
        for (int y = 0; y < enlarged.getHeight(); y++) {
            for (int x = 0; x < enlarged.getWidth(); x++) {
                assertEquals(original.getRGB(x / factor, y / factor), enlarged.getRGB(x, y),
                        enlarged.getWidth() + " px at " + x + "," + y);
            }
        }
    }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }
}
