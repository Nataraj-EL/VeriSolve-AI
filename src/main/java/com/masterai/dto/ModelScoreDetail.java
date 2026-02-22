package com.masterai.dto;

public record ModelScoreDetail(
    double baseConfidence,
    double weight,
    double weightedScore
) {}
