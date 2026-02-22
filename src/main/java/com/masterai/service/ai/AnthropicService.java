package com.masterai.service.ai;

import com.masterai.util.ResponseNormalizer;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class AnthropicService extends BaseAiService {

    public AnthropicService(@Qualifier("anthropicClient") ChatModel chatClient, ResponseNormalizer normalizer) {
        super(chatClient, normalizer, "Anthropic Claude 3");
    }

    @Override
    public boolean supportsMultimodal() {
        return true;
    }
}
