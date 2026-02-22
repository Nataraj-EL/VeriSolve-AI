package com.masterai.service.ai;

import com.masterai.util.ResponseNormalizer;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class OpenAiService extends BaseAiService {

    public OpenAiService(@Qualifier("openAiClient") ChatModel chatClient, ResponseNormalizer normalizer) {
        super(chatClient, normalizer, "OpenAI GPT-3.5");
    }

    @Override
    public boolean supportsMultimodal() {
        return false; // GPT-3.5 used in config doesn't support vision
    }
}
