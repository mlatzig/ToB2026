package de.latzig.game.tob.core.model;

/** Behavioural family of a placed object, independent of its initial facing. */
public enum ObjectFamily {
    ZAPPER, PUSHER, GRABBER,
    ENEMY_ZAPPER, ENEMY_PUSHER, ROTATING_ZAPPER, ROTATING_PUSHER,
    WORM, WATCHER, HOPPER, PROXIMITY_MINE, TIME_BOMB, PUSH_BLOCK, REFLECTOR, PRISM,
    LIZARD, KLONDIKE, BONUS_FLAG, CONVERTER, FORCER_UP, FORCER_DOWN, FREEZER, WIPER, EXCHANGER
}
