package de.latzig.game.tob.client.logic.effects;

import java.util.ArrayList;
import java.util.List;

import de.latzig.game.tob.client.logic.MeshFrame;
import de.latzig.game.tob.core.model.ElementDefinition;
import de.latzig.game.tob.core.model.Explosion;
import de.latzig.game.tob.core.model.MeshGroup;
import de.latzig.game.tob.core.model.MeshPart;

/**
 * The meshes an exploding element breaks into (Phase 4b-1 spec §2 decisions 1, 3 and 4; ToB 2005
 * {@code TWR_destroyElement}): its pre-broken debris meshes ({@link Explosion#meshes()}, one key) with the materials of
 * its first group's parts, or else the parts of the group the client showed at the key it showed (the enemy pusher
 * thus explodes its own second part, not ToB 2005's {@code FPW+1}).
 */
public final class ExplosionParts {

    /** ToB 2005's floor debris ({@code TWR_MESH_ID_FLR + 50}): 64 triangles with STATIC atlas UVs, no sprites. */
    public static final String FLOOR_DEBRIS = "meshes/1550-expl_floor.tobmesh";

    /** One exploding mesh: the material of {@code part}, the mesh {@code mesh} at animation key {@code key}. */
    public record Piece(MeshPart part, String mesh, float key) {
    }

    private ExplosionParts() {
    }

    /** The pieces of {@code element} shown at {@code frame}. */
    public static List<Piece> of(ElementDefinition element, MeshFrame frame) {
        List<Piece> pieces = new ArrayList<>();
        List<String> preBroken = element.explosion().meshes();
        if (!preBroken.isEmpty()) {
            List<MeshPart> parts = element.groups().get(0).parts();
            for (int i = 0; i < preBroken.size(); i++) {
                pieces.add(new Piece(parts.get(i), preBroken.get(i), 0f));
            }
            return pieces;
        }
        MeshGroup shown = element.groups().get(0);
        for (MeshGroup group : element.groups()) {
            if (group.id() == frame.group()) {
                shown = group;
            }
        }
        for (MeshPart part : shown.parts()) {
            pieces.add(new Piece(part, part.mesh(), frame.key()));
        }
        return pieces;
    }
}
