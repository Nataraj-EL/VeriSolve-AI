package com.masterai.dto;

import java.util.List;
import java.util.Map;

public record AnswerResponse(
    Object verifiedAnswer, // Can be String or List<String>
    String explanation,
    double confidence,
    List<String> steps,
    String modelAgreement, // High, Medium, Low
    Map<String, ModelScoreDetail> providerScores, // Detailed breakdown
    String arbitrationMode, // "CONSENSUS" or "SINGLE_SENSOR"
    List<String> validationFlags, // e.g. ["REASONING_INCONSISTENT"]
    String subject // "APTITUDE" or "CODING"
) {}
