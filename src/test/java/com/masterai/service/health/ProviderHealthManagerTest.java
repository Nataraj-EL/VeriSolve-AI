package com.masterai.service.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProviderHealthManagerTest {

    private ProviderHealthManager healthManager;
    private static final String PROVIDER = "TestProvider";

    @BeforeEach
    void setUp() {
        healthManager = new ProviderHealthManager();
    }

    @Test
    @DisplayName("Logic penalty should increase with failures up to 20%")
    void testWeightDegradation() {
        // Initial weight
        double initialWeight = healthManager.getDynamicWeight(PROVIDER);
        assertEquals(1.0, initialWeight);

        // Record 1 logical failure
        healthManager.recordSuccess(PROVIDER, 100);
        healthManager.recordFailure(PROVIDER, ProviderHealthManager.FailureReason.LOGICAL_INCONSISTENCY);
        double weight1 = healthManager.getDynamicWeight(PROVIDER);
        
        // 1 request, 1 logic failure -> rate=1.0, penalty=0.2
        assertTrue(weight1 < initialWeight);
        assertEquals(0.8, weight1, 0.01);

        // Record 4 more failures (Total 5)
        for(int i=0; i<4; i++) {
            healthManager.recordSuccess(PROVIDER, 100);
            healthManager.recordFailure(PROVIDER, ProviderHealthManager.FailureReason.LOGICAL_INCONSISTENCY);
        }
        double weight5 = healthManager.getDynamicWeight(PROVIDER);
        // 5/5 = 100% failure rate (since window is small still)
        // Wait, deque size is 5. failures=5. rate=1.0. penalty=0.2
        assertTrue(weight5 <= 0.81); // approx 0.8
    }

    @Test
    @DisplayName("Weights should recover after clean responses")
    void testWeightRecovery() {
        // Degrade
        for(int i=0; i<10; i++) {
            healthManager.recordSuccess(PROVIDER, 100);
            healthManager.recordFailure(PROVIDER, ProviderHealthManager.FailureReason.LOGICAL_INCONSISTENCY);
        }
        double degradedWeight = healthManager.getDynamicWeight(PROVIDER);
        assertTrue(degradedWeight <= 0.8);

        // Recover with 10 clean responses
        for(int i=0; i<10; i++) {
            healthManager.recordSuccess(PROVIDER, 100);
            healthManager.recordLogicallyClean(PROVIDER);
        }
        double recoveredWeight = healthManager.getDynamicWeight(PROVIDER);
        
        // Window is now 20 (10 fail, 10 clean). failureRate = 0.5. penalty = 0.5 * 0.25 = 0.125
        // weight = 1.0 - 0.125 = 0.875
        assertTrue(recoveredWeight > degradedWeight);
        assertTrue(recoveredWeight < 1.0);
        
        // Recover fully (Need 50 clean responses to clear window)
        for(int i=0; i<40; i++) {
            healthManager.recordSuccess(PROVIDER, 100);
            healthManager.recordLogicallyClean(PROVIDER);
        }
        assertEquals(1.0, healthManager.getDynamicWeight(PROVIDER));
    }

    @Test
    @DisplayName("Logic failures should not trigger provider suppression")
    void testLogicFailureIsolation() {
        for(int i=0; i<100; i++) {
            healthManager.recordSuccess(PROVIDER, 100);
            healthManager.recordFailure(PROVIDER, ProviderHealthManager.FailureReason.LOGICAL_INCONSISTENCY);
        }
        
        assertEquals(ProviderHealthManager.Status.ACTIVE, healthManager.getStatus(PROVIDER));
        assertTrue(healthManager.isProviderAvailable(PROVIDER));
    }
}
