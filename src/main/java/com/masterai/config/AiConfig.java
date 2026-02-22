package com.masterai.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Value("${spring.ai.openai.api-key}")
    private String openAiApiKey;

    @Value("${spring.ai.anthropic.api-key}")
    private String anthropicApiKey;

    @Value("${spring.ai.groq.api-key}")
    private String groqApiKey;
    
    @Value("${spring.ai.groq.base-url}")
    private String groqBaseUrl;

    @Value("${spring.ai.gemini.api-key}")
    private String geminiApiKey;

    @Value("${spring.ai.gemini.base-url}")
    private String geminiBaseUrl;

    @Value("${spring.ai.gemini.model}")
    private String geminiModel;

    @Value("${spring.ai.groq.model}")
    private String groqModel;

    @Value("${spring.ai.cohere.api-key}")
    private String cohereApiKey;

    @Value("${spring.ai.cohere.base-url}")
    private String cohereBaseUrl;

    @Value("${spring.ai.cohere.model}")
    private String cohereModel;

    @Value("${spring.ai.huggingface.api-key}")
    private String huggingFaceApiKey;

    @Value("${spring.ai.huggingface.base-url}")
    private String huggingFaceBaseUrl;

    @Value("${spring.ai.huggingface.model}")
    private String huggingFaceModel;

    @Value("${spring.ai.huggingface.timeout}")
    private int huggingFaceTimeout;

    @org.springframework.beans.factory.annotation.Autowired
    private com.masterai.service.health.ProviderHealthManager healthManager;

    // OpenAI Client Bean
    @Bean("openAiClient")
    public ChatModel openAiClient() {
        OpenAiApi openAiApi = new OpenAiApi(openAiApiKey);
        return new OpenAiChatModel(openAiApi);
    }

    // Anthropic Client Bean
    @Bean("anthropicClient")
    public ChatModel anthropicClient() {
        AnthropicApi anthropicApi = new AnthropicApi(anthropicApiKey);
        return new AnthropicChatModel(anthropicApi);
    }

    // Groq Client Bean (Uses OpenAI Compatibility)
    @Bean("groqClient")
    public ChatModel groqClient() {
        OpenAiApi groqApi = new OpenAiApi(groqBaseUrl, groqApiKey);
        org.springframework.ai.openai.OpenAiChatOptions options = org.springframework.ai.openai.OpenAiChatOptions.builder()
                .withModel(groqModel)
                .build();
        return new OpenAiChatModel(groqApi, options);
    }
    
    // Cohere Client Bean
    @Bean("cohereClient")
    public ChatModel cohereClient() {
        return new com.masterai.service.ai.CohereChatModel(
            cohereApiKey,
            cohereModel,
            cohereBaseUrl,
            healthManager
        );
    }

    // Hugging Face Client Bean (Using OpenAI-compatible router)
    @Bean("huggingFaceClient")
    public ChatModel huggingFaceClient() {
        OpenAiApi hfApi = new OpenAiApi(huggingFaceBaseUrl, huggingFaceApiKey);
        org.springframework.ai.openai.OpenAiChatOptions options = org.springframework.ai.openai.OpenAiChatOptions.builder()
                .withModel(huggingFaceModel)
                .build();
        return new OpenAiChatModel(hfApi, options);
    }

    // Gemini Client Bean (Native Google AI Studio REST)
    @Bean("geminiClient")
    public ChatModel geminiClient() {
        org.springframework.http.client.ClientHttpRequestFactory requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        ((org.springframework.http.client.SimpleClientHttpRequestFactory) requestFactory).setConnectTimeout(5000);
        ((org.springframework.http.client.SimpleClientHttpRequestFactory) requestFactory).setReadTimeout(9000);

        return new com.masterai.service.ai.GeminiAiStudioChatModel(
            geminiApiKey,
            geminiModel,
            org.springframework.web.client.RestClient.builder().requestFactory(requestFactory)
        );
    }
}
