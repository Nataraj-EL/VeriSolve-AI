package com.masterai.service.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Deque;
import java.util.stream.Collectors;

@Service
public class ProviderHealthManager {

    private static final Logger logger = LoggerFactory.getLogger(ProviderHealthManager.class);
    private static final double MIN_WEIGHT = 0.2;
    private static final double MAX_WEIGHT = 1.0;

    @org.springframework.beans.factory.annotation.Value("${masterai.health.auth-suppression-duration-seconds:3600}")
    private long authSuppressionSeconds;

    @org.springframework.beans.factory.annotation.Value("${masterai.health.quota-suppression-duration-seconds:600}")
    private long quotaSuppressionSeconds;

    private final Map<String, ProviderState> providerStates = new ConcurrentHashMap<>();

    public enum Status {
        ACTIVE,
        SUPPRESSED, // Quota, Billing, Auth
        DEGRADED    // High latency, 5xx errors
    }

    public enum FailureReason {
        QUOTA_EXCEEDED,
        BILLING_EXCEEDED,
        AUTH_FAILED,
        PROVIDER_ERROR, // 5xx
        TIMEOUT,
        LOGICAL_INCONSISTENCY,
        OTHER
    }

    private static class ProviderState {
        String name;
        AtomicReference<Status> status = new AtomicReference<>(Status.ACTIVE);
        long suppressedUntil = 0;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        // Rolling window for logical outcomes: true = failure, false = clean
        Deque<Boolean> recentLogicOutcomes = new ConcurrentLinkedDeque<>();
        int windowSize = 50;
        AtomicInteger totalLatency = new AtomicInteger(0);
        int requestCount = 0;

        ProviderState(String name) {
            this.name = name;
        }
    }

    public boolean isProviderAvailable(String providerName) {
        ProviderState state = providerStates.computeIfAbsent(providerName, ProviderState::new);
        
        if (state.status.get() == Status.SUPPRESSED) {
            if (System.currentTimeMillis() > state.suppressedUntil) {
                logger.info("Provider {} suppression expired. Reactivating.", providerName);
                state.status.set(Status.ACTIVE);
                state.suppressedUntil = 0;
                return true;
            }
            return false;
        }
        return true;
    }

    public void recordSuccess(String providerName, long latencyMs) {
        ProviderState state = providerStates.computeIfAbsent(providerName, ProviderState::new);
        state.successCount.incrementAndGet();
        state.totalLatency.addAndGet((int) latencyMs);
        state.requestCount++;
        
        // If it was degraded, maybe upgrade? (Simplification: just keep active)
        if (state.status.get() == Status.DEGRADED) {
            state.status.set(Status.ACTIVE);
        }
    }

    public void recordLogicallyClean(String providerName) {
        ProviderState state = providerStates.computeIfAbsent(providerName, ProviderState::new);
        state.recentLogicOutcomes.addFirst(false);
        if (state.recentLogicOutcomes.size() > state.windowSize) {
            state.recentLogicOutcomes.removeLast();
        }
    }

    public void recordFailure(String providerName, FailureReason reason) {
        ProviderState state = providerStates.computeIfAbsent(providerName, ProviderState::new);

        switch (reason) {
            case QUOTA_EXCEEDED:
            case BILLING_EXCEEDED:
                state.failureCount.incrementAndGet();
                suppress(state, quotaSuppressionSeconds * 1000, "Quota/Billing Exceeded");
                break;
            case AUTH_FAILED:
                state.failureCount.incrementAndGet();
                suppress(state, authSuppressionSeconds * 1000, "Authentication Failed");
                break;
            case PROVIDER_ERROR:
            case TIMEOUT:
                state.failureCount.incrementAndGet();
                state.status.compareAndSet(Status.ACTIVE, Status.DEGRADED);
                break;
            case LOGICAL_INCONSISTENCY:
                // Logic failures don't count as availability failures
                state.recentLogicOutcomes.addFirst(true);
                if (state.recentLogicOutcomes.size() > state.windowSize) {
                    state.recentLogicOutcomes.removeLast();
                }
                logger.warn("Recorded logical inconsistency for provider {}. Current window size: {}", 
                    state.name, state.recentLogicOutcomes.size());
                break;
            default:
                break;
        }
    }

    private void suppress(ProviderState state, long durationMs, String reason) {
        long until = System.currentTimeMillis() + durationMs;
        state.status.set(Status.SUPPRESSED);
        state.suppressedUntil = until;
        logger.warn("Suppressing provider {} for {}s. Reason: {}", state.name, durationMs / 1000, reason);
    }

    // Dynamic Weight Calculation
    // Simple heuristic: 1.0 - (failureRate * 0.5) - (latencyPenalty)
    // Bounded [0.2, 1.0]
    public double getDynamicWeight(String providerName) {
        ProviderState state = providerStates.get(providerName);
        if (state == null || state.requestCount == 0) return 1.0; // Default

        int total = state.successCount.get() + state.failureCount.get();
        if (total == 0) return 1.0;

        double failureRate = (double) state.failureCount.get() / total;
        double avgLatency = (double) state.totalLatency.get() / Math.max(1, state.successCount.get());

        // Penalty Model
        double score = 1.0;
        score -= (failureRate * 0.5); // Up to 0.5 penalty for 100% failure
        
        // Rolling Logic Penalty
        long logicFailures = state.recentLogicOutcomes.stream().filter(b -> b).count();
        int outcomesCount = state.recentLogicOutcomes.size();
        if (outcomesCount > 0) {
            double logicFailureRate = (double) logicFailures / outcomesCount;
            // logicPenalty = min(0.20, logicFailureRate * 0.25)
            double logicPenalty = Math.min(0.20, logicFailureRate * 0.25);
            score -= logicPenalty;
        }

        // Latency penalty
        if (avgLatency > 2000) {
            score -= Math.min(0.3, (avgLatency - 2000) / 5000.0);
        }

        return Math.max(MIN_WEIGHT, Math.min(MAX_WEIGHT, score));
    }
    
    public Status getStatus(String providerName) {
        ProviderState state = providerStates.get(providerName);
        return state != null ? state.status.get() : Status.ACTIVE;
    }
}
