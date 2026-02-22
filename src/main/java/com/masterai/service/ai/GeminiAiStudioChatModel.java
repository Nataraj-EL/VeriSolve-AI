package com.masterai.service.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.model.Media;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class GeminiAiStudioChatModel implements ChatModel {

    private static final Logger logger = LoggerFactory.getLogger(GeminiAiStudioChatModel.class);

    private final String apiKey;
    private final String modelName;
    private final RestClient restClient;

    public GeminiAiStudioChatModel(String apiKey, String modelName, RestClient.Builder restClientBuilder) {
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent";
        
        // Extract user message content
        String userContent = prompt.getInstructions().stream()
                .filter(m -> m instanceof org.springframework.ai.chat.messages.UserMessage || m instanceof org.springframework.ai.chat.messages.SystemMessage)
                .map(org.springframework.ai.chat.messages.Message::getContent)
                .collect(java.util.stream.Collectors.joining("\n"));

        // Build Native Gemini Request Parts
        List<Map<String, Object>> parts = new java.util.ArrayList<>();
        parts.add(Map.of("text", userContent));

        // Check for media
        prompt.getInstructions().stream()
            .filter(m -> m instanceof org.springframework.ai.chat.messages.UserMessage)
            .map(m -> (org.springframework.ai.chat.messages.UserMessage) m)
            .forEach(um -> {
                if (um.getMedia() != null) {
                    for (Media media : um.getMedia()) {
                        try {
                            org.springframework.core.io.Resource resource = (org.springframework.core.io.Resource) media.getData();
                            byte[] imageBytes = resource.getContentAsByteArray();
                            String base64Image = java.util.Base64.getEncoder().encodeToString(imageBytes);
                            parts.add(Map.of(
                                "inline_data", Map.of(
                                    "mime_type", media.getMimeType().toString(),
                                    "data", base64Image
                                )
                            ));
                        } catch (Exception e) {
                            logger.error("Failed to convert media to base64 for Gemini", e);
                        }
                    }
                }
            });

        Map<String, Object> requestBody = Map.of(
            "contents", List.of(
                Map.of("parts", parts)
            )
        );

        logger.info("Sending Gemini Request (Parts: {}) to: {}", parts.size(), endpoint);

        int maxRetries = 2;
        int attempt = 0;
        long backoff = 500;

        while (attempt <= maxRetries) {
            try {
                GeminiResponse response = restClient.post()
                        .uri(endpoint)
                        .header("x-goog-api-key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .body(GeminiResponse.class);

                if (response != null && response.candidates != null && !response.candidates.isEmpty()) {
                    String responseText = response.candidates.get(0).content.parts.get(0).text;
                    return new ChatResponse(List.of(new Generation(new AssistantMessage(responseText))));
                } else {
                    logger.error("Empty or invalid response from Gemini: {}", response);
                    throw new RuntimeException("Empty response from Gemini API");
                }
            } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests e) {
                logger.warn("Gemini 429 Too Many Requests on attempt {}. Retrying in {}ms...", attempt + 1, backoff);
                if (attempt == maxRetries) {
                    logger.error("Gemini Quota Exceeded after retries. Returning fallback.");
                    // Return a valid JSON that represents failure, so BaseAiService doesn't crash
                    String fallbackJson = """
                        {
                            "final_answer": "QUOTA_EXCEEDED",
                            "reasoning_steps": ["Gemini API Quota Exceeded"],
                            "confidence_score": 0.0
                        }
                        """;
                    return new ChatResponse(List.of(new Generation(new AssistantMessage(fallbackJson))));
                }
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during Gemini retry backoff", ie);
                }
                backoff *= 3; // Exponential backoff 500 -> 1500
                attempt++;
            } catch (Exception e) {
                logger.error("Failed to call Gemini API: {}", e.getMessage());
                throw new RuntimeException("Gemini API call failed", e);
            }
        }
        throw new RuntimeException("Gemini API call failed after retries");
    }



    // Stub for stream, not used in this project yet
    @Override
    public reactor.core.publisher.Flux<ChatResponse> stream(Prompt prompt) {
       throw new UnsupportedOperationException("Streaming not implemented for GeminiAiStudioChatModel");
    }

    // Inner DTOs for JSON Parsing
    record GeminiResponse(List<Candidate> candidates) {}
    record Candidate(Content content) {}
    record Content(List<Part> parts) {}
    record Part(String text) {}
}
