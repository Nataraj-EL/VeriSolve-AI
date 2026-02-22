package com.masterai.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "solutions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Solution {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @OneToOne
    @JoinColumn(name = "question_id")
    private Question question;

    @Column(columnDefinition = "TEXT")
    private String verifiedAnswer;

    @Column(columnDefinition = "TEXT")
    private String explanation;
    
    private double confidenceScore;
    
    private String agreementLevel;

    @ElementCollection
    @CollectionTable(name = "solution_steps", joinColumns = @JoinColumn(name = "solution_id"))
    @Column(name = "step", length = 1000) // Increase length for detailed steps
    private List<String> steps;

    @Column(columnDefinition = "TEXT")
    private String providerScoresJson; // Map<String, ModelScoreDetail>

    @CreationTimestamp
    private LocalDateTime solvedAt;
    
    // Helper to store Map as JSON string
    public void setProviderScores(Object scores) {
        try {
            this.providerScoresJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(scores);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
