package com.masterai.service.consensus;

import com.masterai.config.ModelMetadataRegistry;
import com.masterai.dto.AnswerResponse;
import com.masterai.dto.ModelScoreDetail;
import com.masterai.dto.StructuredAIResponse;
import com.masterai.util.ArithmeticValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsensusEngineTest {

    @Mock
    private ModelMetadataRegistry registry;

    @Mock
    private ArithmeticValidator arithmeticValidator;

    private ConsensusEngine consensusEngine;

    @BeforeEach
    void setUp() {
        consensusEngine = new ConsensusEngine(registry, arithmeticValidator);
    }

    @Test
    @DisplayName("Should reach unanimous agreement when all models agree")
    void testUnanimousAgreement() {
        // Given
        setupWeights();
        StructuredAIResponse r1 = createResponse("OpenAI", "42", 0.9);
        StructuredAIResponse r2 = createResponse("Claude", "42", 0.95);
        StructuredAIResponse r3 = createResponse("Gemini", "42", 0.9);

        // When
        AnswerResponse response = consensusEngine.evaluate(r1, r2, r3);

        // Then
        assertEquals("42", response.verifiedAnswer().toString());
        assertEquals("High (Unanimous)", response.modelAgreement());
        assertTrue(response.confidence() > 0.9);
        assertEquals(3, response.providerScores().size());
    }

    @Test
    @DisplayName("Should select majority answer in 2 vs 1 split")
    void testMajorityVote() {
        // Given
        setupWeights();
        StructuredAIResponse r1 = createResponse("OpenAI", "42", 0.9);
        StructuredAIResponse r2 = createResponse("Claude", "42", 0.95); // Majority
        StructuredAIResponse r3 = createResponse("Gemini", "7", 0.8);  // Dissenter

        // When
        AnswerResponse response = consensusEngine.evaluate(r1, r2, r3);

        // Then
        assertEquals("42", response.verifiedAnswer().toString());
        assertEquals("Medium (Majority)", response.modelAgreement());
        
        Map<String, ModelScoreDetail> scores = response.providerScores();
        assertNotNull(scores.get("OpenAI"));
        assertNotNull(scores.get("Gemini"));
    }

    @Test
    @DisplayName("Should handle partial failures (null responses)")
    void testPartialFailure() {
        // Given
        setupWeights();
        StructuredAIResponse r1 = createResponse("OpenAI", "Java", 0.9);
        StructuredAIResponse r2 = null; // Failed model
        StructuredAIResponse r3 = createResponse("Gemini", "Java", 0.85);

        // When
        AnswerResponse response = consensusEngine.evaluate(r1, r2, r3);

        // Then
        assertEquals("Java", response.verifiedAnswer().toString());
        assertEquals("High (Unanimous)", response.modelAgreement()); // Unanimous among *valid* responses
        assertEquals(2, response.providerScores().size());
    }

    @Test
    @DisplayName("High confidence should outweigh lower confidence even with same weights")
    void testConfidenceImpact() {
        // Given
        when(registry.getWeight("ModelA")).thenReturn(1.0);
        when(registry.getWeight("ModelB")).thenReturn(1.0);
        
        // Both single votes, but A has higher confidence
        StructuredAIResponse r1 = createResponse("ModelA", "Answer A", 0.99);
        StructuredAIResponse r2 = createResponse("ModelB", "Answer B", 0.10);

        // When
        AnswerResponse response = consensusEngine.evaluate(r1, r2);

        // Then
        // Group A score: 1.0 * 0.99 * (1/2 agreement) = 0.495
        // Group B score: 1.0 * 0.10 * (1/2 agreement) = 0.05
        assertEquals("Answer A", response.verifiedAnswer().toString());
    }

    @Test
    @DisplayName("Should fallback gracefully when no valid responses")
    void testNoValidResponses() {
        // When
        AnswerResponse response = consensusEngine.evaluate(null, null);

        // Then
        assertEquals("None", response.modelAgreement());
        assertTrue(response.verifiedAnswer().toString().startsWith("Error"));
    }
    
    @Test
    @DisplayName("Should normalize answers (case insensitive trim)")
    void testNormalization() {
        // Given
        setupWeights();
        StructuredAIResponse r1 = createResponse("OpenAI", "  hello ", 0.9);
        StructuredAIResponse r2 = createResponse("Claude", "HELLO", 0.9);
        
        // When
        AnswerResponse response = consensusEngine.evaluate(r1, r2);
        
        // Then
        // Should be treated as same answer
        assertTrue(response.modelAgreement().contains("Unanimous"));
        // Should preserve casing of the "best" response (first one usually)
        assertEquals("hello", response.verifiedAnswer().toString().trim().toLowerCase());
    }

    // Helpers
    private void setupWeights() {
        org.mockito.Mockito.lenient().when(registry.getWeight("OpenAI")).thenReturn(0.8);
        org.mockito.Mockito.lenient().when(registry.getWeight("Claude")).thenReturn(0.9);
        org.mockito.Mockito.lenient().when(registry.getWeight("Gemini")).thenReturn(0.85);
    }

    private StructuredAIResponse createResponse(String provider, String answer, double confidence) {
        return new StructuredAIResponse(answer, List.of("step1"), confidence, provider, 100L);
    }
}
