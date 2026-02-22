package com.masterai.service.ai;

import com.masterai.util.ResponseNormalizer;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class CohereService extends BaseAiService {

    public CohereService(@Qualifier("cohereClient") ChatModel chatClient, ResponseNormalizer normalizer) {
        super(chatClient, normalizer, "Cohere");
    }

    @Override
    public boolean supportsMultimodal() {
        return false;
    }
}
