package de.latzig.game.tob.core.sim.element;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.ObjectType;

class BehavioursTest {

    private static ElementBehaviour expected(ObjectType type) {
        return switch (type) {
            case ZAP, PSH, GRB -> SpiderBehaviour.INSTANCE;
            case FZN, FZE, FZS, FZW -> EnemyBehaviour.FIXED_ZAPPER;
            case FPN, FPE, FPS, FPW -> EnemyBehaviour.FIXED_PUSHER;
            case RZN -> EnemyBehaviour.ROTATING_ZAPPER;
            case RPN -> EnemyBehaviour.ROTATING_PUSHER;
            case KLD -> KlondikeBehaviour.INSTANCE;
            case EXC -> ExchangerBehaviour.INSTANCE;
            case WON, WOE -> WormBehaviour.INSTANCE;
            case WAT -> WatcherBehaviour.INSTANCE;
            case HOP -> HopperBehaviour.INSTANCE;
            case LZN, LZE -> LizardBehaviour.INSTANCE;
            case TMB -> TimeBombBehaviour.INSTANCE;
            case PRM -> MineBehaviour.INSTANCE;
            case FRZ -> FreezerBehaviour.INSTANCE;
            case FCU -> ForcerBehaviour.UP;
            case FCD -> ForcerBehaviour.DOWN;
            case WIP -> WiperBehaviour.INSTANCE;
            default -> StaticObstacleBehaviour.INSTANCE;
        };
    }

    @Test
    void everyTypeGetsItsClassHandler() {
        for (ObjectType type : ObjectType.values()) {
            assertSame(expected(type), Behaviours.forType(type), type.code());
        }
    }

    @Test
    void theRayHooksDefaultToNoEffect() {
        ElementBehaviour plain = StaticObstacleBehaviour.INSTANCE;

        assertFalse(plain.onZap(null, null, null));
        assertFalse(plain.onGrab(null, null, null));
        plain.onPush(null, null, null);
    }
}
