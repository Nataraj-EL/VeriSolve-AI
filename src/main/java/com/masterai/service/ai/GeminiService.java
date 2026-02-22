package com.masterai.service.ai;

import com.masterai.util.ResponseNormalizer;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class GeminiService extends BaseAiService {

    public GeminiService(@Qualifier("geminiClient") ChatModel chatClient, ResponseNormalizer normalizer) {
        super(chatClient, normalizer, "Google Gemini Pro");
    }

    @Override
    public boolean supportsMultimodal() {
        return true;
    }
}
