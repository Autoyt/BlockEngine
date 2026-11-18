package dev.auto.blockengine.flare;

import java.util.UUID;

/** Offer identity is per attempt, so late success after resend or disable cannot reactivate the HUD. */
final class InspectorSession {
    private UUID offerId;
    private boolean loaded;

    UUID offer() {
        offerId = UUID.randomUUID();
        loaded = false;
        return offerId;
    }

    UUID offerId() { return offerId; }

    boolean loaded(UUID id) {
        if (!id.equals(offerId)) return false;
        loaded = true;
        return true;
    }

    boolean failed(UUID id) {
        if (!id.equals(offerId)) return false;
        loaded = false;
        return true;
    }

    boolean ready(boolean enabled, boolean transportLoaded) {
        return enabled && transportLoaded && loaded;
    }

    UUID clear() {
        UUID previous = offerId;
        offerId = null;
        loaded = false;
        return previous;
    }
}
