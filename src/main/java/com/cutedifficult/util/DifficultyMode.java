package com.cutedifficult.util;

/**
 * Two paths a player can walk through the world.
 *
 * <p><b>CRUEL</b> — the default. All mechanics active. The world hates you. The
 * foxes can guide you, but only if you prove yourself worthy.
 *
 * <p><b>PATH_OF_PEACE</b> — activated by typing the surrender phrase in chat.
 * Mechanics drop to vanilla-plus. All spiritual content (Kyuubi, Dragon
 * liberation, etc.) is permanently locked. A grey particle floats above your
 * head. The villagers do not look at you. The foxes do not come.
 */
public enum DifficultyMode {
    /** Default. Everything hurts. Everything has meaning. */
    CRUEL,

    /** Surrendered. Lifted suffering. Lost grace. */
    PATH_OF_PEACE
}
