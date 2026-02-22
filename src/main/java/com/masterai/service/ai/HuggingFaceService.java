package com.masterai.service.ai;

import com.masterai.util.ResponseNormalizer;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class HuggingFaceService extends BaseAiService {

    public HuggingFaceService(@Qualifier("huggingFaceClient") ChatModel chatClient, ResponseNormalizer normalizer) {
        super(chatClient, normalizer, "HuggingFace");
    }

    @Override
    public boolean supportsMultimodal() {
        return false;
    }
}
