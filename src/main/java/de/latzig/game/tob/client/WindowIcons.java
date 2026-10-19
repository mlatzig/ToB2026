package de.latzig.game.tob.client;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Phase 7c decision 3: the window's icon, the ToB 2005 icon in six sizes ({@code src/main/resources/icons}, made by
 * the plan's {@code icon7c.py}: the original 16 and 32 px and their pixel-exact enlargements). The packages' launcher
 * icons ({@code src/package/ToB2026.ico} and {@code .png}) are the same images.
 */
public final class WindowIcons {

    private static final Logger LOG = LoggerFactory.getLogger(WindowIcons.class);

    /** The icon sizes in pixels, smallest first. */
    public static final List<Integer> SIZES = List.of(16, 32, 48, 64, 128, 256);

    private WindowIcons() {
    }

    /** The class path resource of the icon with the given size. */
    static String resource(int size) {
        return "/icons/ToB2026-" + size + ".png";
    }

    /**
     * The icons for {@code AppSettings.setIcons}, one per size in {@link #SIZES}; null (the system's default icon)
     * if one cannot be read - logged, the game starts anyway.
     */
    public static BufferedImage[] load() {
        BufferedImage[] icons = new BufferedImage[SIZES.size()];
        for (int i = 0; i < icons.length; i++) {
            String resource = resource(SIZES.get(i));
            try (InputStream in = WindowIcons.class.getResourceAsStream(resource)) {
                icons[i] = in == null ? null : ImageIO.read(in);
            } catch (IOException e) {
                icons[i] = null;
            }
            if (icons[i] == null) {
                LOG.warn("Cannot read the window icon {}; the system's default icon is used", resource);
                return null;
            }
        }
        return icons;
    }
}
