package de.latzig.game.tob.client.view;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial.CullHint;
import com.jme3.scene.shape.Quad;

import de.latzig.game.tob.client.logic.game.PanelLayout;
import de.latzig.game.tob.client.logic.game.TextPlacement;
import de.latzig.game.tob.client.logic.ui.Drawing;
import de.latzig.game.tob.core.model.GuiAtlas;

/**
 * Draws a start screen's {@link Drawing} in the GUI node (Phase 5a spec §3): GUI atlas quads ({@link AtlasSprite}) and
 * bitmap-font texts from pools that grow as needed, in the drawing's order (z {@value #Z_BASE} + order ×
 * {@value #Z_STEP}), and the black fade overlay in front of everything. One material per tint (white, grey). The view
 * only reads.
 */
public final class CanvasView {

    public static final float Z_BASE = 20f;
    public static final float Z_STEP = 0.01f;
    public static final float Z_FADE = 90f;

    private final GuiAtlas atlas;
    private final AssetLibrary assets;
    private final BitmapFont font;
    private final Node node = new Node("start-screens");
    private final List<AtlasSprite> sprites = new ArrayList<>();
    private final List<BitmapText> texts = new ArrayList<>();
    private final Map<Integer, Material> tints = new HashMap<>();
    private final Geometry fade;
    private final Material fadeMaterial;
    private final ColorRGBA fadeColour = new ColorRGBA(0f, 0f, 0f, 0f);
    private int shownSprites;
    private int shownTexts;

    /** @throws AssetLoadException if the GUI atlas or its image is missing */
    public CanvasView(AssetLibrary assets, BitmapFont font) {
        this.assets = assets;
        this.atlas = assets.guiAtlas();
        this.font = font;
        AssetManager manager = assets.manager();
        fadeMaterial = new Material(manager, "Common/MatDefs/Misc/Unshaded.j3md");
        fadeMaterial.setColor("Color", fadeColour);
        fadeMaterial.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        fade = new Geometry("start-fade", new Quad(1f, 1f));
        fade.setMaterial(fadeMaterial);
        fade.setCullHint(CullHint.Always);
        node.attachChild(fade);
    }

    public Node node() {
        return node;
    }

    /** Shows {@code drawing} over a {@code width} × {@code height} window, black at {@code fadeAlpha} in front. */
    public void show(Drawing drawing, float fadeAlpha, int width, int height) {
        List<Drawing.Sprite> wanted = drawing.sprites();
        for (int i = 0; i < wanted.size(); i++) {
            Drawing.Sprite sprite = wanted.get(i);
            sprite(i).show(sprite.region(), sprite.rect(), z(sprite.order()), material(sprite.tint()));
        }
        for (int i = wanted.size(); i < shownSprites; i++) {
            sprites.get(i).hide();
        }
        shownSprites = wanted.size();
        List<Drawing.Label> labels = drawing.labels();
        for (int i = 0; i < labels.size(); i++) {
            place(text(i), labels.get(i));
        }
        for (int i = labels.size(); i < shownTexts; i++) {
            texts.get(i).setCullHint(CullHint.Always);
        }
        shownTexts = labels.size();
        fade.setCullHint(fadeAlpha > 0f ? CullHint.Never : CullHint.Always);
        fade.setLocalScale(width, height, 1f);
        fade.setLocalTranslation(0f, 0f, Z_FADE);
        fadeMaterial.setColor("Color", fadeColour.set(0f, 0f, 0f, Math.min(1f, fadeAlpha)));
    }

    /** Hides everything (the screens are left). */
    public void hide() {
        show(new Drawing(List.of(), List.of()), 0f, 1, 1);
    }

    private static float z(int order) {
        return Z_BASE + order * Z_STEP;
    }

    private void place(BitmapText text, Drawing.Label label) {
        PanelLayout.Rect rect = label.rect();
        text.setSize(label.size());
        text.setText(label.text());
        int rgb = label.colour();
        text.setColor(new ColorRGBA(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, 1f));
        float x = switch (label.align()) {
            case LEFT -> rect.x();
            case CENTRE -> rect.x() + (rect.width() - text.getLineWidth()) / 2f;
            case RIGHT -> rect.x() + rect.width() - text.getLineWidth();
        };
        text.setLocalTranslation(x, TextPlacement.top(rect.y() + rect.height() / 2f, text.getLineHeight()),
                z(label.order()));
        text.setCullHint(CullHint.Inherit);
    }

    private AtlasSprite sprite(int index) {
        while (sprites.size() <= index) {
            AtlasSprite sprite = new AtlasSprite("start-" + sprites.size(), atlas, material(Drawing.WHITE));
            node.attachChild(sprite.geometry());
            sprites.add(sprite);
        }
        return sprites.get(index);
    }

    private BitmapText text(int index) {
        while (texts.size() <= index) {
            BitmapText text = new BitmapText(font);
            text.setCullHint(CullHint.Always);
            node.attachChild(text);
            texts.add(text);
        }
        return texts.get(index);
    }

    private Material material(int tint) {
        return tints.computeIfAbsent(tint, rgb -> {
            Material material = assets.guiMaterial();
            if (rgb != Drawing.WHITE) {
                material.setColor("Color", new ColorRGBA(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f,
                        (rgb & 0xFF) / 255f, 1f));
            }
            return material;
        });
    }

    /** The regions of the shown sprites in drawing order (tests). */
    List<String> shownRegions() {
        List<String> regions = new ArrayList<>();
        for (int i = 0; i < shownSprites; i++) {
            regions.add(sprites.get(i).region());
        }
        return regions;
    }

    /** The shown texts (tests). */
    List<String> shownTexts() {
        List<String> shown = new ArrayList<>();
        for (int i = 0; i < shownTexts; i++) {
            shown.add(texts.get(i).getText());
        }
        return shown;
    }

    /** The fade overlay's alpha, 0 while hidden (tests). */
    float fadeAlpha() {
        return fade.getCullHint() == CullHint.Always ? 0f : fadeColour.a;
    }

    /** The z of shown text {@code index} (tests). */
    float textZ(int index) {
        return texts.get(index).getLocalTranslation().z;
    }

    /** The z of shown sprite {@code index} (tests). */
    float spriteZ(int index) {
        return sprites.get(index).geometry().getLocalTranslation().z;
    }
}
