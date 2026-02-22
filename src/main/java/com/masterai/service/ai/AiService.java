package com.masterai.service.ai;

import com.masterai.dto.QuestionRequest;
import com.masterai.dto.StructuredAIResponse;

public interface AiService {
    StructuredAIResponse solve(QuestionRequest request);
    String getName();
    default boolean supportsMultimodal() {
        return false;
    }
}
