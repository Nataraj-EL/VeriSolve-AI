package com.masterai.service;

import com.masterai.dto.AnswerResponse;
import com.masterai.dto.QuestionRequest;
import com.masterai.dto.StructuredAIResponse;
import com.masterai.service.ai.AnthropicService;
import com.masterai.service.ai.CohereService;
import com.masterai.service.ai.GeminiService;
import com.masterai.service.ai.GroqService;
import com.masterai.service.ai.HuggingFaceService;
import com.masterai.service.ai.OpenAiService;
import com.masterai.service.consensus.ConsensusEngine;
import com.masterai.util.ArithmeticValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Subtask;

@Service
public class OrchestratorService {

    private static final Logger logger = LoggerFactory.getLogger(OrchestratorService.class);
    
    private final OpenAiService openAiService;
    private final AnthropicService anthropicService;
    private final GeminiService geminiService;
    private final GroqService groqService;
    private final CohereService cohereService;
    private final HuggingFaceService huggingFaceService;
    
    private final ConsensusEngine consensusEngine;
    private final PersistenceService persistenceService;
    private final com.masterai.service.health.ProviderHealthManager healthManager;
    private final ArithmeticValidator arithmeticValidator;

    public OrchestratorService(OpenAiService openAiService, 
                               AnthropicService anthropicService, 
                               GeminiService geminiService, 
                               GroqService groqService,
                               CohereService cohereService,
                               HuggingFaceService huggingFaceService,
                               ConsensusEngine consensusEngine,
                               PersistenceService persistenceService,
                               com.masterai.service.health.ProviderHealthManager healthManager,
                               ArithmeticValidator arithmeticValidator) {
        this.openAiService = openAiService;
        this.anthropicService = anthropicService;
        this.geminiService = geminiService;
        this.groqService = groqService;
        this.cohereService = cohereService;
        this.huggingFaceService = huggingFaceService;
        this.consensusEngine = consensusEngine;
        this.persistenceService = persistenceService;
        this.healthManager = healthManager;
        this.arithmeticValidator = arithmeticValidator;
    }

    public AnswerResponse solve(QuestionRequest request) {
        String userText = request.text();
        boolean hasImage = request.image() != null && !request.image().isEmpty();

        if ((userText == null || userText.trim().isEmpty()) && !hasImage) {
            logger.warn("Received empty request (no text, no image)");
            return new AnswerResponse(
                "I couldn't find a question to solve. Please provide text or an image.",
                "The received request was empty. Please ensure you have typed a question or uploaded an image.",
                0.0,
                java.util.List.of("Request validation failed"),
                "ERROR",
                new java.util.HashMap<>(),
                "ERROR",
                java.util.List.of("EMPTY_INPUT"),
                request.subject()
            );
        }

        logger.info("Starting orchestration for question: {}", userText);
        long startTime = System.currentTimeMillis();
        
        // Define Tiers
        // Tier 1: Primary Providers
        java.util.List<com.masterai.service.ai.AiService> tier1Providers = java.util.List.of(
            openAiService, anthropicService, geminiService, groqService, cohereService
        );
        
        // Tier 2: Fallback
        com.masterai.service.ai.AiService tier2Provider = huggingFaceService;

        // Use default StructuredTaskScope which does not shutdown on failure, allowing partial success
        try (var scope = new StructuredTaskScope<StructuredAIResponse>()) {
            
            // 1. Execute Tier 1
            java.util.List<Subtask<StructuredAIResponse>> tier1Tasks = new java.util.ArrayList<>();
            
            for (com.masterai.service.ai.AiService service : tier1Providers) {
                if (healthManager.isProviderAvailable(service.getName())) {
                    tier1Tasks.add(scope.fork(() -> service.solve(request)));
                } else {
                    logger.warn("Skipping {} (Suppressed/Unavailable)", service.getName());
                }
            }

            // Wait for Tier 1 with a "Race to 2" strategy
            long deadline = System.currentTimeMillis() + 8000; // 8s total timeout
            boolean allFinished = false;
            while (System.currentTimeMillis() < deadline) {
                try {
                    scope.joinUntil(java.time.Instant.now().plusMillis(500));
                    allFinished = true; // All tasks completed before total timeout
                    break;
                } catch (java.util.concurrent.TimeoutException e) {
                    long successCount = tier1Tasks.stream().filter(t -> t.state() == Subtask.State.SUCCESS).count();
                    if (successCount >= 2) {
                        logger.info("Tier 1 Race won: {} providers responded. Proceeding.", successCount);
                        break;
                    }
                }
            }
            
            // Cleanly shutdown and join to satisfy StructuredTaskScope lifecycle requirements avoiding IllegalStateException
            if (!allFinished) {
                scope.shutdown();
                scope.join();
            }

            // Collect Tier 1 Results Safely
            java.util.List<StructuredAIResponse> successfulResponses = new java.util.ArrayList<>();
            for (int i = 0; i < tier1Tasks.size(); i++) {
                Subtask<StructuredAIResponse> task = tier1Tasks.get(i);
                String name = tier1Providers.get(i).getName();
                
                StructuredAIResponse response = getResultOrLogFailure(task, name);
                if (response != null && isValidResponse(response)) {
                    successfulResponses.add(response);
                    healthManager.recordSuccess(response.provider(), response.latencyMs());
                    
                    if (arithmeticValidator.validateSteps(response.reasoningSteps()).isEmpty()) {
                        healthManager.recordLogicallyClean(response.provider());
                    } else {
                        healthManager.recordFailure(response.provider(), com.masterai.service.health.ProviderHealthManager.FailureReason.LOGICAL_INCONSISTENCY);
                    }
                }
            }
            
            // 2. Check Tier 2 Trigger
            // Optimization: If we have at least 2 successful Tier 1 responses, skip Tier 2 to save time.
            if (successfulResponses.size() < 2) {
                logger.info("Insufficient Tier 1 responses ({}). Triggering Tier 2 (Hugging Face).", successfulResponses.size());
                if (healthManager.isProviderAvailable(tier2Provider.getName())) {
                     try (var tier2Scope = new java.util.concurrent.StructuredTaskScope<StructuredAIResponse>()) {
                         Subtask<StructuredAIResponse> tier2Task = tier2Scope.fork(() -> tier2Provider.solve(request));
                         
                         boolean t2Finished = false;
                         try {
                             tier2Scope.joinUntil(java.time.Instant.now().plusSeconds(10));
                             t2Finished = true;
                         } catch (java.util.concurrent.TimeoutException e) {
                             logger.warn("Tier 2 orchestration reached 10s timeout.");
                         }
                         
                         if (!t2Finished) {
                             tier2Scope.shutdown();
                             tier2Scope.join();
                         }
                         
                         StructuredAIResponse response = getResultOrLogFailure(tier2Task, tier2Provider.getName());
                         if (response != null && isValidResponse(response)) {
                             successfulResponses.add(response);
                             healthManager.recordSuccess(response.provider(), response.latencyMs());
                             
                             if (arithmeticValidator.validateSteps(response.reasoningSteps()).isEmpty()) {
                                 healthManager.recordLogicallyClean(response.provider());
                             } else {
                                 healthManager.recordFailure(response.provider(), com.masterai.service.health.ProviderHealthManager.FailureReason.LOGICAL_INCONSISTENCY);
                             }
                         }
                     }
                }
            }

            // 3. Consensus
            // Convert list to array
            StructuredAIResponse[] responsesArray = successfulResponses.toArray(new StructuredAIResponse[0]);
            
            // We need to inject dynamic weights into Consensus Engine or pass them.
            // Let's modify ConsensusEngine interface to verify if it needs map.
            // Actually, ConsensusEngine usually looks up static registry.
            // We should pass a map of dynamic weights to ConsensusEngine.evaluate?
            // Refactoring ConsensusEngine.evaluate to accept weights map.
            
            java.util.Map<String, Double> dynamicWeights = new java.util.HashMap<>();
            // Only add weights for Tier 1, as per requirement "Tier 2 provider health does not affect Tier 1 dynamic weighting"
            // Actually, we just need weights for *participating* providers.
            for (StructuredAIResponse r : successfulResponses) {
                if (!r.provider().equals("HuggingFace")) { // valid Tier 1
                    dynamicWeights.put(r.provider(), healthManager.getDynamicWeight(r.provider()));
                } else {
                    dynamicWeights.put(r.provider(), 0.5); // Fixed weight for fallback? Or just default.
                }
            }

            AnswerResponse response = consensusEngine.evaluateWithWeights(request, dynamicWeights, responsesArray);
            
            // Calculate total processing time
            long totalProcessingTime = System.currentTimeMillis() - startTime;
            
            // Collect latencies
            java.util.Map<String, Long> latencies = new java.util.HashMap<>();
            for (StructuredAIResponse r : successfulResponses) {
                latencies.put(r.provider(), r.latencyMs());
            }

            // Async persistence
            persistenceService.saveInteraction(request, response, totalProcessingTime, latencies);

            return response;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Orchestration interrupted", e);
        }
    }
    
    private boolean isValidResponse(StructuredAIResponse r) {
        if (r == null || r.finalAnswer() == null) return false;
        String answer = r.getFinalAnswerAsString();
        return !answer.equals("QUOTA_EXCEEDED") &&
               !answer.equals("AUTH_FAILED") &&
               !answer.equals("PROVIDER_ERROR") &&
               r.confidenceScore() > 0.0;
    }

    private StructuredAIResponse getResultOrLogFailure(Subtask<StructuredAIResponse> task, String providerName) {
        if (task.state() == Subtask.State.SUCCESS) {
            return task.get();
        } else if (task.state() == Subtask.State.FAILED) {
            logger.error("{} failed execution: {}", providerName, task.exception().getMessage());
            return null;
        }
        return null;
    }
}
