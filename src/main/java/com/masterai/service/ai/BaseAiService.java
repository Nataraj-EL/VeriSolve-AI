package com.masterai.service.ai;

import com.masterai.dto.QuestionRequest;
import com.masterai.dto.StructuredAIResponse;
import com.masterai.util.ResponseNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.model.Media;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeTypeUtils;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseAiService implements AiService {
    
    protected static final Logger logger = LoggerFactory.getLogger(BaseAiService.class);
    protected final ChatModel chatClient;
    protected final ResponseNormalizer normalizer;
    protected final String modelName;

    public BaseAiService(ChatModel chatClient, ResponseNormalizer normalizer, String modelName) {
        this.chatClient = chatClient;
        this.normalizer = normalizer;
        this.modelName = modelName;
    }

    @Override
    public StructuredAIResponse solve(QuestionRequest request) {
        boolean isCoding = "CODING".equalsIgnoreCase(request.subject());
        
        String systemPrompt = """
            You are an expert AI. Solve step-by-step and return ONLY strictly valid JSON.
            Do not include any conversational text.
            """;
            
        if (isCoding) {
            systemPrompt += """
                
                STRICT DSA MODE: You are a Competitive Programming Engine. 
                For every problem, you MUST perform a structured analysis before writing code.
                
                Your 'reasoning_steps' list MUST strictly follow this format:
                1. "Classification: [Problem Type]"
                2. "Metadata: Input/Output/Constraints"
                3. "Strategy: [Algorithm Choice]"
                4. "Complexity: Time/Space"
                5. "Edge Cases"
                6. "Step-by-step logic..."
                
                Your 'final_answer' MUST be a LIST OF STRINGS containing the code.
                Example: ["def solve(nums):", "    return sum(nums)"]
                """;
        } else {
            systemPrompt += """
                
                STRICT APTITUDE MODE: You are a Logical Reasoning Engine.
                Analyze the question methodically. DO NOT default to common answers like "12" unless mathematically certain.
                
                Your 'reasoning_steps' list MUST strictly follow:
                1. "Classification: [Category]"
                2. "Core Concept: [Subject]"
                3. "Strategy: [Method]"
                4. "Edge Cases/Context"
                5. "Step-by-step logic..."
                
                Your 'final_answer' MUST be a SINGLE STRING containing only the final value/choice.
                Example: "42" or "Option B"
                """;
        }
            
        systemPrompt += """
            
            Return ONLY strictly valid JSON.
            {
              "final_answer": %s,
              "reasoning_steps": ["Step 1", "Step 2", ...],
              "confidence_score": 0.95
            }
            """.formatted(isCoding ? "[\"line 1\", \"line 2\"]" : "\"concise answer\"");
            
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));

        String userText = request.text();
        if (request.image() != null && !request.image().isEmpty()) {
            if (this.supportsMultimodal()) {
                try {
                    Resource imageResource = request.image().getResource();
                    Media media = new Media(MimeTypeUtils.IMAGE_JPEG, imageResource);
                    messages.add(new UserMessage(userText != null ? userText : "Analyze the attached image and solve the problem.", List.of(media)));
                } catch (Exception e) {
                    logger.error("Failed to process image for model {}: {}", modelName, e.getMessage());
                    messages.add(new UserMessage(userText != null ? userText : "Analyze the problem and solve."));
                }
            } else {
                // Degradation logic
                String degradationText = userText != null ? userText : "";
                degradationText += "\n\n[NOTE: An image was provided but this sensor is text-only. Perform inference based on the text context.]";
                messages.add(new UserMessage(degradationText));
            }
        } else {
            messages.add(new UserMessage(userText != null ? userText : "Please provide a problem to solve."));
        }

        Prompt prompt = new Prompt(messages);
        long startTime = System.currentTimeMillis();
        
        try {
            logger.info("{} solving question (Multimodal: {})...", modelName, supportsMultimodal());
            String rawResponse = chatClient.call(prompt).getResult().getOutput().getContent();
            logger.info("{} Raw Response: {}", modelName, rawResponse);
            long latency = System.currentTimeMillis() - startTime;
            return normalizer.parse(rawResponse).withProvider(modelName).withLatency(latency);
        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : "";
            boolean isPermanentFailure = errorMsg.contains("401") || errorMsg.contains("403") || 
                                       errorMsg.contains("404") || errorMsg.contains("429") ||
                                       errorMsg.contains("invalid x-api-key");
            
            if (isPermanentFailure) {
                logger.warn("{} encountered non-transient error. Skipping retry. Error: {}", modelName, errorMsg);
                throw e; 
            }

            logger.warn("{} failed to parse or execute. Retrying once... Error: {}", modelName, errorMsg);
            try {
                String retryInstruction = "\n\nSYSTEM ERROR: Your previous response was not valid JSON. Return ONLY valid JSON this time.";
                messages.add(new UserMessage(retryInstruction));
                String rawResponse = chatClient.call(new Prompt(messages)).getResult().getOutput().getContent();
                long latency = System.currentTimeMillis() - startTime;
                return normalizer.parse(rawResponse).withProvider(modelName).withLatency(latency);
            } catch (Exception retryEx) {
                logger.error("{} failed on retry.", modelName, retryEx);
                throw new RuntimeException("AI Model " + modelName + " failed to return valid JSON", retryEx);
            }
        }
    }

    @Override
    public String getName() {
        return modelName;
    }
}
