package dev.auto.blockengine.flare;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InspectorSessionTest {
    @Test
    void onlyLatestOfferCanActivateAndAllThreeGatesAreRequired() {
        InspectorSession session = new InspectorSession();
        UUID first = session.offer();
        UUID second = session.offer();

        assertFalse(session.loaded(first));
        assertFalse(session.ready(true, true));
        assertTrue(session.loaded(second));
        assertFalse(session.ready(false, true));
        assertFalse(session.ready(true, false));
        assertTrue(session.ready(true, true));
    }

    @Test
    void disableInvalidatesOutstandingLoadsAndPreservesUnrelatedIds() {
        InspectorSession session = new InspectorSession();
        UUID first = session.offer();
        assertFalse(session.failed(UUID.randomUUID()));
        assertEquals(first, session.clear());
        assertFalse(session.loaded(first));
        assertFalse(session.ready(true, true));
    }

    @Test
    void failureOfCurrentOfferStopsReadiness() {
        InspectorSession session = new InspectorSession();
        UUID id = session.offer();
        assertTrue(session.loaded(id));
        assertTrue(session.failed(id));
        assertFalse(session.ready(true, true));
    }
}
