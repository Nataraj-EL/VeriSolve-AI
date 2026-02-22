package com.masterai.service.consensus;

import com.masterai.config.ModelMetadataRegistry;
import com.masterai.dto.AnswerResponse;
import com.masterai.dto.ModelScoreDetail;
import com.masterai.dto.QuestionRequest;
import com.masterai.dto.StructuredAIResponse;
import com.masterai.util.ArithmeticValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ConsensusEngine {

    private static final Logger logger = LoggerFactory.getLogger(ConsensusEngine.class);
    private final ModelMetadataRegistry registry;
    private final ArithmeticValidator arithmeticValidator;

    public ConsensusEngine(ModelMetadataRegistry registry, ArithmeticValidator arithmeticValidator) {
        this.registry = registry;
        this.arithmeticValidator = arithmeticValidator;
    }

    public AnswerResponse evaluate(StructuredAIResponse... responses) {
        return evaluateWithWeights(null, new HashMap<>(), responses);
    }

    public AnswerResponse evaluateWithWeights(QuestionRequest request, Map<String, Double> dynamicWeights, StructuredAIResponse... responses) {
        // Filter valid responses
        List<StructuredAIResponse> validResponses = Arrays.stream(responses)
                .filter(r -> r != null && r.finalAnswer() != null)
                .filter(r -> r.confidenceScore() > 0.0)
                .filter(r -> !r.getFinalAnswerAsString().equalsIgnoreCase("QUOTA_EXCEEDED"))
                .filter(r -> !r.getFinalAnswerAsString().equalsIgnoreCase("AUTH_FAILED"))
                .filter(r -> !r.getFinalAnswerAsString().equalsIgnoreCase("PROVIDER_ERROR"))
                .filter(r -> !r.getFinalAnswerAsString().equalsIgnoreCase("PROVIDER_SUPPRESSED"))
                .filter(r -> !r.getFinalAnswerAsString().startsWith("Error:"))
                .toList();

        if (validResponses.isEmpty()) {
            return new AnswerResponse(
                "Error: No valid AI response generated.",
                "All models failed to return a structured answer.",
                0.0,
                new ArrayList<>(),
                "None",
                new HashMap<>(),
                "ERROR",
                new ArrayList<>(),
                request != null ? request.subject() : "APTITUDE"
            );
        }

        Map<String, ModelScoreDetail> providerScores = new HashMap<>();
        Map<String, Double> answerGroupScores = new HashMap<>();
        Map<String, Integer> answerCounts = new HashMap<>();
        Map<String, StructuredAIResponse> answerToResponseMap = new HashMap<>();
        List<String> validationFlags = new ArrayList<>();

        // 1. Determine Arbitration Mode & Global Counts
        int multimodalSuccessCount = 0;
        boolean hasImage = request != null && request.image() != null && !request.image().isEmpty();
        
        // We'll need access to which providers are multimodal. 
        // This is a bit tricky since we only have StructuredAIResponse here.
        // Let's assume for now OpenAI (if vision), Anthropic, Gemini are multimodal.
        Set<String> multimodalProviders = Set.of("Anthropic Claude 3", "Google Gemini Pro", "OpenAI GPT-4o");

        // 2. Calculate individual model scores with logic validation
        for (StructuredAIResponse response : validResponses) {
            double baseConfidence = response.confidenceScore();
            
            // MATH VALIDATION LAYER
            List<ArithmeticValidator.Inconsistency> inconsistencies = arithmeticValidator.validateSteps(response.reasoningSteps());
            if (!inconsistencies.isEmpty()) {
                baseConfidence *= 0.8; // Reduce confidence by 20%
                if (!validationFlags.contains("REASONING_INCONSISTENT")) {
                    validationFlags.add("REASONING_INCONSISTENT");
                }
            }

            // MULTIMODAL WEIGHTING
            double weight = dynamicWeights.getOrDefault(response.provider(), registry.getWeight(response.provider()));
            if (hasImage && multimodalProviders.contains(response.provider())) {
                multimodalSuccessCount++;
            }

            double finalModelScore = weight * baseConfidence;
            
            providerScores.put(response.provider(), new ModelScoreDetail(
                baseConfidence,
                weight,
                finalModelScore
            ));

            String normalizedAnswer = response.getFinalAnswerAsString().trim().toLowerCase();
            answerGroupScores.merge(normalizedAnswer, finalModelScore, Double::sum);
            answerCounts.merge(normalizedAnswer, 1, Integer::sum);
            answerToResponseMap.putIfAbsent(normalizedAnswer, response);
        }

        // Apply Multimodal Entropy Penalty (If image present but only 1 multimodal sensor responded)
        if (hasImage && multimodalSuccessCount == 1) {
            for (String provider : providerScores.keySet()) {
                if (multimodalProviders.contains(provider)) {
                    ModelScoreDetail detail = providerScores.get(provider);
                    double reducedScore = detail.weightedScore() * 0.85; // 15% reduction
                    providerScores.put(provider, new ModelScoreDetail(detail.baseConfidence(), detail.weight(), reducedScore));
                    // Update group scores accordingly (simplified: just the impact)
                    StructuredAIResponse r = validResponses.stream().filter(vr -> vr.provider().equals(provider)).findFirst().get();
                    String key = r.getFinalAnswerAsString().trim().toLowerCase();
                    answerGroupScores.put(key, answerGroupScores.get(key) - (detail.weightedScore() - reducedScore));
                }
            }
        }

        // 3. Agreement Strategy
        int totalValid = validResponses.size();
        String bestAnswerKey = "";
        double maxGroupScore = -1.0;

        for (Map.Entry<String, Double> entry : answerGroupScores.entrySet()) {
            String answerKey = entry.getKey();
            double rawSum = entry.getValue();
            int count = answerCounts.get(answerKey);
            double agreementFactor = (double) count / totalValid;
            double finalGroupScore = rawSum * agreementFactor;
            
            if (finalGroupScore > maxGroupScore) {
                maxGroupScore = finalGroupScore;
                bestAnswerKey = answerKey;
            }
        }

        // 4. Final Confidence & Mode
        StructuredAIResponse bestResponse = answerToResponseMap.get(bestAnswerKey);
        final String finalWinningAnswer = bestAnswerKey;
        double avgWinningConfidence = validResponses.stream()
            .filter(r -> r.getFinalAnswerAsString().trim().equalsIgnoreCase(finalWinningAnswer))
            .mapToDouble(r -> {
                // Re-apply validation penalty if it was found
                double c = r.confidenceScore();
                if (!arithmeticValidator.validateSteps(r.reasoningSteps()).isEmpty()) c *= 0.8;
                return c;
            })
            .average()
            .orElse(0.0);
            
        double agreementRatio = (double) answerCounts.get(bestAnswerKey) / totalValid;
        double finalConfidence = avgWinningConfidence * agreementRatio;

        String arbitrationMode = totalValid > 1 ? "CONSENSUS" : "SINGLE_SENSOR";
        if (arbitrationMode.equals("SINGLE_SENSOR")) {
            finalConfidence = Math.min(finalConfidence, 0.85);
        }

        String agreementLevel;
        if (agreementRatio == 1.0) agreementLevel = "High (Unanimous)";
        else if (agreementRatio >= 0.6) agreementLevel = "Medium (Majority)";
        else agreementLevel = "Low (Disagreement)";

        return new AnswerResponse(
            bestResponse.getFinalAnswerAsString(),
            "Consensus analysis based on weighted scoring of " + totalValid + " models.",
            finalConfidence,
            bestResponse.reasoningSteps() != null ? bestResponse.reasoningSteps() : new ArrayList<>(),
            agreementLevel,
            providerScores,
            arbitrationMode,
            validationFlags,
            request != null ? request.subject() : "APTITUDE"
        );
    }
}
