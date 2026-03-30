package com.cursor_springa_ai.playground.repository;

import com.cursor_springa_ai.playground.model.AiAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiAnalysisRepository extends JpaRepository<AiAnalysis, Long> {

    List<AiAnalysis> findByUserIdOrderByCreatedAtDesc(Long userId);
}
