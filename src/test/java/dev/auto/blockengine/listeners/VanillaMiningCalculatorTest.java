package dev.auto.blockengine.listeners;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VanillaMiningCalculatorTest {
    @Test
    void appliesTheConfiguredMultiplierToBothToolProfiles() {
        assertEquals(0.2f, VanillaMiningCalculator.progress(6, 2, 2, true), 0.00001f);
        assertEquals(0.06f, VanillaMiningCalculator.progress(6, 2, 2, false), 0.00001f);
        assertEquals(0.05f, VanillaMiningCalculator.progress(6, 2, 0.5f, true), 0.00001f);
    }

    @Test
    void handlesInstantAndDisabledMiningWithoutDivisionByZero() {
        assertEquals(1f, VanillaMiningCalculator.progress(1, 0, 1, false));
        assertEquals(0f, VanillaMiningCalculator.progress(1, -1, 1, true));
        assertEquals(0f, VanillaMiningCalculator.progress(1, 1, 0, true));
    }
}
