package com.masterai.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record StructuredAIResponse(
    @JsonProperty("final_answer") Object finalAnswer, // Can be String or List<String>
    @JsonProperty("reasoning_steps") List<String> reasoningSteps,
    @JsonProperty("confidence_score") double confidenceScore,
    String provider,
    long latencyMs
) {
    public String getFinalAnswerAsString() {
        if (finalAnswer instanceof List) {
            return String.join("\n", (List<String>) finalAnswer);
        }
        return (String) finalAnswer;
    }
    // Constructor for Jackson (JSON parsing) where provider/latency is unknown
    @JsonCreator
    public StructuredAIResponse(
        @JsonProperty("final_answer") Object finalAnswer,
        @JsonProperty("reasoning_steps") List<String> reasoningSteps,
        @JsonProperty("confidence_score") double confidenceScore
    ) {
        this(finalAnswer, reasoningSteps, confidenceScore, "Unknown", 0);
    }
    
    // Helper to create a new instance with the provider set
    public StructuredAIResponse withProvider(String providerName) {
        return new StructuredAIResponse(finalAnswer, reasoningSteps, confidenceScore, providerName, latencyMs);
    }

    public StructuredAIResponse withLatency(long latencyMs) {
        return new StructuredAIResponse(finalAnswer, reasoningSteps, confidenceScore, provider, latencyMs);
    }
}
