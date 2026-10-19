package de.latzig.game.tob.core.sim.element;

import de.latzig.game.tob.core.model.ObjectType;

/** Behaviour per element type (the class dispatch {@code \00B048}, {@code S:9273-9299}). */
public final class Behaviours {

    private Behaviours() {
    }

    public static ElementBehaviour forType(ObjectType type) {
        return switch (type.family()) {
            case ZAPPER, PUSHER, GRABBER -> SpiderBehaviour.INSTANCE;
            case ENEMY_ZAPPER -> EnemyBehaviour.FIXED_ZAPPER;
            case ENEMY_PUSHER -> EnemyBehaviour.FIXED_PUSHER;
            case ROTATING_ZAPPER -> EnemyBehaviour.ROTATING_ZAPPER;
            case ROTATING_PUSHER -> EnemyBehaviour.ROTATING_PUSHER;
            case KLONDIKE -> KlondikeBehaviour.INSTANCE;
            case EXCHANGER -> ExchangerBehaviour.INSTANCE;
            case WORM -> WormBehaviour.INSTANCE;
            case WATCHER -> WatcherBehaviour.INSTANCE;
            case HOPPER -> HopperBehaviour.INSTANCE;
            case LIZARD -> LizardBehaviour.INSTANCE;
            case TIME_BOMB -> TimeBombBehaviour.INSTANCE;
            case PROXIMITY_MINE -> MineBehaviour.INSTANCE;
            case FREEZER -> FreezerBehaviour.INSTANCE;
            case FORCER_UP -> ForcerBehaviour.UP;
            case FORCER_DOWN -> ForcerBehaviour.DOWN;
            case WIPER -> WiperBehaviour.INSTANCE;
            default -> StaticObstacleBehaviour.INSTANCE;
        };
    }
}
