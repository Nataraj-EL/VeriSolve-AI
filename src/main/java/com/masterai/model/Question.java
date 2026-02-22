package com.masterai.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "questions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Question {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(columnDefinition = "TEXT")
    private String text;

    private String imageUrl;

    @Enumerated(EnumType.STRING)
    private QuestionType type; // APTITUDE, CODING

    private Long processingTimeMs;

    @Column(columnDefinition = "TEXT") // Storing JSON as TEXT for simplicity without extra deps
    private String modelLatencyJson; // Map<String, Long> serialized

    @CreationTimestamp
    private LocalDateTime createdAt;
    
    public enum QuestionType {
        APTITUDE, CODING
    }
}
