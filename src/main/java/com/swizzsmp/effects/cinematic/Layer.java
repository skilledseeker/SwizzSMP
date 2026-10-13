package com.swizzsmp.effects.cinematic;

/**
 * A reusable piece of a cinematic (a sigil, a black hole, a time freeze…). Entities a layer
 * spawns through its host are removed with the host; anything else it changes in the world
 * or on players is undone in {@link #stop()}, which the host calls once when the show ends,
 * however it ends.
 */
abstract class Layer {

    protected final Cinematic host;

    Layer(Cinematic host) {
        this.host = host;
        host.register(this);
    }

    /** Undoes anything that isn't a spawned entity. Must be safe to call more than once. */
    void stop() {
    }
}
