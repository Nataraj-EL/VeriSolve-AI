package com.masterai.service.ai;

import com.masterai.service.health.ProviderHealthManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CohereChatModel implements ChatModel {

    private static final Logger logger = LoggerFactory.getLogger(CohereChatModel.class);

    private final String apiKey;
    private final String modelName;
    private final RestClient restClient;
    private final ProviderHealthManager healthManager;
    private final String PROVIDER_NAME = "Cohere";

    public CohereChatModel(String apiKey, String modelName, String baseUrl, ProviderHealthManager healthManager) {
        this.apiKey = apiKey;
        this.modelName = modelName;
        
        org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(15000); // Increased from 9s to 15s to prevent premature interrupts on slow free-tier APIs
        
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
        this.healthManager = healthManager;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        if (!healthManager.isProviderAvailable(PROVIDER_NAME)) {
            logger.warn("Cohere is currently suppressed. Skipping.");
            return createFallbackResponse("PROVIDER_SUPPRESSED");
        }

        String userContent = prompt.getInstructions().stream()
                .map(Message::getContent)
                .collect(Collectors.joining("\n"));

        Map<String, Object> requestBody = Map.of(
            "model", modelName,
            "message", userContent,
            "temperature", 0.3
        );

        long startTime = System.currentTimeMillis();
        int maxRetries = 2;
        int attempt = 0;
        long backoff = 500;

        while (attempt <= maxRetries) {
            try {
                logger.info("Sending Cohere Request...");
                CohereResponse response = restClient.post()
                        .uri("/chat")
                        .header("Authorization", "Bearer " + apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .body(CohereResponse.class);

                long latency = System.currentTimeMillis() - startTime;
                healthManager.recordSuccess(PROVIDER_NAME, latency);

                if (response != null && response.text != null) {
                    return new ChatResponse(List.of(new Generation(new AssistantMessage(response.text))));
                } else {
                    throw new RuntimeException("Empty response from Cohere");
                }
            } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests e) {
                logger.warn("Cohere 429 Too Many Requests. Attempt {}", attempt + 1);
                if (attempt == maxRetries) {
                    healthManager.recordFailure(PROVIDER_NAME, ProviderHealthManager.FailureReason.QUOTA_EXCEEDED);
                    return createFallbackResponse("QUOTA_EXCEEDED");
                }
                sleep(backoff);
                backoff *= 2;
                attempt++;
            } catch (org.springframework.web.client.HttpClientErrorException.Unauthorized e) {
                logger.error("Cohere Auth Failed");
                healthManager.recordFailure(PROVIDER_NAME, ProviderHealthManager.FailureReason.AUTH_FAILED);
                return createFallbackResponse("AUTH_FAILED");
            } catch (Exception e) {
                logger.error("Cohere API Failed: {}", e.getMessage());
                healthManager.recordFailure(PROVIDER_NAME, ProviderHealthManager.FailureReason.PROVIDER_ERROR);
                // Don't retry generic errors heavily
                if (attempt == maxRetries) return createFallbackResponse("PROVIDER_ERROR");
                sleep(backoff);
                attempt++;
            }
        }
        return createFallbackResponse("PROVIDER_ERROR");
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private ChatResponse createFallbackResponse(String reason) {
        String fallbackJson = String.format("""
            {
                "final_answer": "%s",
                "reasoning_steps": ["Cohere API Unavailable: %s"],
                "confidence_score": 0.0
            }
            """, reason, reason);
        return new ChatResponse(List.of(new Generation(new AssistantMessage(fallbackJson))));
    }



    @Override
    public reactor.core.publisher.Flux<ChatResponse> stream(Prompt prompt) {
        throw new UnsupportedOperationException("Streaming not implemented");
    }

    record CohereResponse(String text, String generation_id) {}
}
