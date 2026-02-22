package com.masterai.service.ai;

import com.masterai.util.ResponseNormalizer;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class GroqService extends BaseAiService {

    public GroqService(@Qualifier("groqClient") ChatModel chatClient, ResponseNormalizer normalizer) {
        super(chatClient, normalizer, "Groq Llama 3");
    }

    @Override
    public boolean supportsMultimodal() {
        return false;
    }
}
