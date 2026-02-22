package com.masterai.config;

import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ModelMetadataRegistry {

    private final Map<String, Double> modelWeights = new ConcurrentHashMap<>();

    public ModelMetadataRegistry() {
        // Initialize with default weights
        // Higher weight = more trusted model
        modelWeights.put("OpenAI GPT-3.5", 0.8);
        modelWeights.put("Anthropic Claude 3", 0.9); // Higher due to reasoning capability
        modelWeights.put("Google Gemini Pro", 0.85);
        modelWeights.put("Groq Llama 3", 0.75); // Fast but maybe less accurate than Claude 3 Opus/Sonnet
    }

    public double getWeight(String modelName) {
        return modelWeights.getOrDefault(modelName, 0.5); // Default neutral weight
    }
    
    public void updateWeight(String modelName, double weight) {
        modelWeights.put(modelName, weight);
    }
}
