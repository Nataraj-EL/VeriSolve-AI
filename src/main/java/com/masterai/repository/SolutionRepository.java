package com.masterai.repository;

import com.masterai.model.Solution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SolutionRepository extends JpaRepository<Solution, String> {
    Optional<Solution> findByQuestionId(String questionId);
}
