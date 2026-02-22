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

public class HuggingFaceChatModel implements ChatModel {

    private static final Logger logger = LoggerFactory.getLogger(HuggingFaceChatModel.class);

    private final String apiKey;
    private final String modelName;
    private final RestClient restClient;
    private final ProviderHealthManager healthManager;
    private final int timeoutMs;
    private final String PROVIDER_NAME = "HuggingFace";

    public HuggingFaceChatModel(String apiKey, String modelName, String baseUrl, int timeoutMs, ProviderHealthManager healthManager) {
        this.apiKey = apiKey;
        this.modelName = modelName;
        // The base URL for inference API usually ends with the model name, but here we configured base-url separately
        // We will construct the full URL in the call
        this.restClient = RestClient.builder().baseUrl(baseUrl).build(); 
        this.timeoutMs = timeoutMs;
        this.healthManager = healthManager;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        if (!healthManager.isProviderAvailable(PROVIDER_NAME)) {
            return createFallbackResponse("PROVIDER_SUPPRESSED");
        }

        String userContent = prompt.getInstructions().stream()
                .map(Message::getContent)
                .collect(Collectors.joining("\n"));

        // Instruct format for Mistral
        String promptText = String.format("<s>[INST] %s [/INST]", userContent);

        Map<String, Object> requestBody = Map.of(
            "inputs", promptText,
            "parameters", Map.of(
                "max_new_tokens", 512,
                "return_full_text", false,
                "temperature", 0.3
            )
        );

        long startTime = System.currentTimeMillis();
        try {
            logger.info("Sending Hugging Face Request...");
            // Using a hard timeout would require setting it on the http client factory
            // For now we rely on the implementation responsive time
            
             List<HFResponse> response = restClient.post()
                    .uri("/" + modelName)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<List<HFResponse>>() {});

            long latency = System.currentTimeMillis() - startTime;
            
            if (response != null && !response.isEmpty()) {
                 healthManager.recordSuccess(PROVIDER_NAME, latency);
                 String text = response.get(0).generated_text;
                 // Basic sanitization to ensure JSON-like structure if model chatters
                 return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
            } else {
                throw new RuntimeException("Empty response from HF");
            }

        } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests e) {
             healthManager.recordFailure(PROVIDER_NAME, ProviderHealthManager.FailureReason.QUOTA_EXCEEDED);
             return createFallbackResponse("QUOTA_EXCEEDED");
        } catch (org.springframework.web.client.HttpClientErrorException.Unauthorized e) {
             healthManager.recordFailure(PROVIDER_NAME, ProviderHealthManager.FailureReason.AUTH_FAILED);
             return createFallbackResponse("AUTH_FAILED");
        } catch (Exception e) {
             logger.error("HF API Failed: {}", e.getMessage());
             healthManager.recordFailure(PROVIDER_NAME, ProviderHealthManager.FailureReason.PROVIDER_ERROR);
             return createFallbackResponse("PROVIDER_ERROR");
        }
    }

    private ChatResponse createFallbackResponse(String reason) {
        String fallbackJson = String.format("""
            {
                "final_answer": "%s",
                "reasoning_steps": ["Hugging Face API Unavailable: %s"],
                "confidence_score": 0.0
            }
            """, reason, reason);
        return new ChatResponse(List.of(new Generation(new AssistantMessage(fallbackJson))));
    }



    @Override
    public reactor.core.publisher.Flux<ChatResponse> stream(Prompt prompt) {
        throw new UnsupportedOperationException("Streaming not implemented");
    }

    record HFResponse(String generated_text) {}
}
