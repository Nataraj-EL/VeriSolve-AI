package com.masterai.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterai.dto.StructuredAIResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ResponseNormalizer {

    private static final Logger logger = LoggerFactory.getLogger(ResponseNormalizer.class);
    private final ObjectMapper objectMapper;

    public ResponseNormalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public StructuredAIResponse parse(String rawOutput) {
        try {
            // First attempt: direct parsing
            return objectMapper.readValue(rawOutput, StructuredAIResponse.class);
        } catch (Exception e) {
            logger.warn("Direct parsing failed, attempting to extract JSON from markdown block. Error: {}", e.getMessage());
            return extractAndParse(rawOutput);
        }
    }

    private StructuredAIResponse extractAndParse(String rawOutput) {
        try {
            String jsonContent = rawOutput;
            
            // 1. Try to find markdown block ```json ... ```
            Pattern pattern = Pattern.compile("```json(.*?)```", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(rawOutput);
            if (matcher.find()) {
                jsonContent = matcher.group(1).trim();
            } else {
                // 2. Try to find just ``` ... ```
                pattern = Pattern.compile("```(.*?)```", Pattern.DOTALL);
                matcher = pattern.matcher(rawOutput);
                if (matcher.find()) {
                    jsonContent = matcher.group(1).trim();
                } else {
                    // 3. Fallback: match first '{' to last '}'
                    int firstBrace = rawOutput.indexOf('{');
                    int lastBrace = rawOutput.lastIndexOf('}');
                    if (firstBrace != -1 && lastBrace != -1 && firstBrace < lastBrace) {
                        jsonContent = rawOutput.substring(firstBrace, lastBrace + 1);
                    }
                }
            }
            
            // 4. Sanitize Common LLM JSON Errors
            // The lenient mapper helps, but some models (Groq/Cohere) output raw newlines inside string values:
            // "final_answer": "def foo():
            //     return 1"
            // This is invalid JSON. We must escape these newlines -> \n
            
            // Heuristic: We can't globally replace \n with \\n because that breaks the outer JSON structure (indentation).
            // We need to identify string values and escape content inside them. 
            // OR, given the strict schema, we can try to fix specific known fields if parsing fails.
            
            try {
                ObjectMapper lenientMapper = new ObjectMapper();
                lenientMapper.configure(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(), true);
                return lenientMapper.readValue(jsonContent, StructuredAIResponse.class);
            } catch (Exception e) {
                logger.warn("Lenient parsing failed. Attempting aggressive regex sanitization...", e);
                
                // Aggressive fix: Look for "key": "value" patterns where value contains newlines
                // This is hard to do perfectly with regex for nested structures, but "final_answer" is unrelated to others.
                
                // Specific fix for "final_answer": "..."
                // We capture the content of final_answer and escape it.
                // Pattern: "final_answer"\s*:\s*"(.*?)"(?=\s*,\s*"reasoning_steps")
                // Note: The value might span multiple lines (DOTALL).
                // But honestly, the simplest way is to tell the model to NOT do this in the prompt (already did).
                
                // Let's try one last trick: 
                // Convert all newlines that are NOT followed by optional whitespace and a control char (like " or }) 
                // This is too risky.
                
                // Fallback: If strict JSON fails, we manually construct the object for the specific case of code blocks.
                if (jsonContent.contains("\"final_answer\"")) {
                     // Manual extraction as last resort
                     String finalAnswer = extractField(jsonContent, "final_answer");
                     // Reasoning steps is a list, harder to extract manually without parsing.
                     // Let's try to just return what we can.
                     if (finalAnswer != null) {
                         return new StructuredAIResponse(finalAnswer, Collections.emptyList(), 0.0, "Unknown", 0);
                     }
                }
                throw e; // Re-throw if even manual extraction fails
            }
        } catch (Exception e) {
            logger.error("Failed to extract and parse JSON from output: {}", rawOutput, e);
            throw new RuntimeException("Failed to parse AI response: " + e.getMessage(), e);
        }
    }

    private String extractField(String json, String fieldName) {
        try {
            // Very basic extractor that handles newlines in value
            String marker = "\"" + fieldName + "\":";
            int start = json.indexOf(marker);
            if (start == -1) return null;
            
            int valueStart = json.indexOf("\"", start + marker.length()) + 1;
            // Find the closing quote. This is tricky if the string contains escaped quotes.
            // Assumption: The model output for code usually doesn't end with a quote unless it's the end of the JSON string.
            // A safer bet is to look for the next field key or the end of the object.
            // "reasoning_steps" usually follows.
            int nextField = json.indexOf("\"reasoning_steps\"", valueStart);
            if (nextField == -1) nextField = json.lastIndexOf("}");
            
            // Backtrack to find the comma or closing quote
            int valueEnd = json.lastIndexOf("\"", nextField);
            
            if (valueStart > 0 && valueEnd > valueStart) {
                return json.substring(valueStart, valueEnd).replace("\\n", "\n"); // Unescape if needed
            }
        } catch (Exception ex) {
            return null;
        }
        return null;
    }
}
