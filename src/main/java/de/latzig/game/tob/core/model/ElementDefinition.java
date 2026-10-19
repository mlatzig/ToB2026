package de.latzig.game.tob.core.model;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Visual definition of one object type: mesh groups, animation ranges, the ToB 2009 source description and how it
 * breaks apart ({@link Explosion}, Phase 4b-1).
 */
public record ElementDefinition(ObjectType type, List<MeshGroup> groups, List<AnimationRange> animations,
        String source, Explosion explosion) {

    public ElementDefinition {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(explosion, "explosion");
        groups = List.copyOf(groups);
        animations = List.copyOf(animations);
        if (groups.isEmpty()) {
            throw new IllegalArgumentException(type.code() + ": no mesh groups");
        }
        Set<Integer> ids = new HashSet<>();
        for (MeshGroup group : groups) {
            if (!ids.add(group.id())) {
                throw new IllegalArgumentException(type.code() + ": duplicate group " + group.id());
            }
        }
        Set<String> animationNames = new HashSet<>();
        for (AnimationRange animation : animations) {
            if (!animationNames.add(animation.name())) {
                throw new IllegalArgumentException(type.code() + ": duplicate animation " + animation.name());
            }
        }
        if (explosion.meshes().size() > groups.get(0).parts().size()) {
            throw new IllegalArgumentException(type.code() + ": " + explosion.meshes().size()
                    + " explosion meshes for " + groups.get(0).parts().size() + " parts");
        }
    }

    /** An element with the default explosion ({@link Explosion#DEFAULT}). */
    public ElementDefinition(ObjectType type, List<MeshGroup> groups, List<AnimationRange> animations,
            String source) {
        this(type, groups, animations, source, Explosion.DEFAULT);
    }
}
