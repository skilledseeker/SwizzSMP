package com.swizzsmp.effects.cinematic;

import net.kyori.adventure.util.TriState;

/** Makes the player look like they are burning (no damage); put out when the show ends. */
final class VisualFire extends Layer {

    private boolean lit;

    VisualFire(Cinematic host) {
        super(host);
    }

    void set(boolean on) {
        lit = on;
        host.player.setVisualFire(on ? TriState.TRUE : TriState.NOT_SET);
    }

    @Override
    void stop() {
        if (lit) {
            set(false);
        }
    }
}
