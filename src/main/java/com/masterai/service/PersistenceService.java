package com.masterai.service;

import com.masterai.dto.AnswerResponse;
import com.masterai.dto.QuestionRequest;
import com.masterai.model.Question;
import com.masterai.model.Solution;
import com.masterai.repository.QuestionRepository;
import com.masterai.repository.SolutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

@Service
public class PersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(PersistenceService.class);
    private final QuestionRepository questionRepository;
    private final SolutionRepository solutionRepository;

    public PersistenceService(QuestionRepository questionRepository, SolutionRepository solutionRepository) {
        this.questionRepository = questionRepository;
        this.solutionRepository = solutionRepository;
    }

    @Async
    @Transactional
    public CompletableFuture<Void> saveInteraction(QuestionRequest request, AnswerResponse response, long processingTimeMs, java.util.Map<String, Long> modelLatencies) {
        try {
            String latencyJson = null;
            try {
                 latencyJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(modelLatencies);
            } catch (Exception e) {
                 logger.error("Failed to serialize latencies", e);
            }

            // 1. Save Question
            Question question = new Question();
            question.setText(request.text());
            question.setType(request.subject() != null ? Question.QuestionType.valueOf(request.subject()) : Question.QuestionType.APTITUDE);
            question.setProcessingTimeMs(processingTimeMs);
            question.setModelLatencyJson(latencyJson);
            question.setCreatedAt(LocalDateTime.now());
            
            question = questionRepository.save(question);

            // 2. Save Solution
            Solution solution = new Solution();
            solution.setQuestion(question);
            if (response.verifiedAnswer() instanceof java.util.List) {
                solution.setVerifiedAnswer(String.join("\n", (java.util.List<String>) response.verifiedAnswer()));
            } else {
                solution.setVerifiedAnswer((String) response.verifiedAnswer());
            }
            solution.setExplanation(response.explanation());
            solution.setConfidenceScore(response.confidence());
            solution.setAgreementLevel(response.modelAgreement());
            solution.setSteps(response.steps());
            solution.setSolvedAt(LocalDateTime.now());
            
            // Serialize provider scores using the helper method
            solution.setProviderScores(response.providerScores());

            solutionRepository.save(solution);
            
            logger.info("Persisted interaction for question ID: {}", question.getId());
        } catch (Exception e) {
            logger.error("Failed to persist interaction", e);
        }
        return CompletableFuture.completedFuture(null);
    }
}
