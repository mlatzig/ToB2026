package de.latzig.game.tob.client.view;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.jme3.asset.AssetManager;
import com.jme3.asset.DesktopAssetManager;

/** The generated, svn-ignored assets (Phases 1b/1c, Task 1) and a headless jME asset manager for tests. */
public final class GeneratedAssets {

    private GeneratedAssets() {
    }

    public static Path root() {
        return Path.of(System.getProperty("tob.assets", "src/main/resources/assets"));
    }

    public static boolean available() {
        return Files.isRegularFile(root().resolve("elements.json"));
    }

    /** A desktop asset manager without any window or GPU; jME's loader warnings are silenced. */
    public static AssetManager assetManager() {
        Logger.getLogger("com.jme3").setLevel(Level.SEVERE);
        return new DesktopAssetManager(true);
    }
}
