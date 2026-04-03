package com.cursor_springa_ai.playground.repository;

import com.cursor_springa_ai.playground.model.AiAnalysis;
import com.cursor_springa_ai.playground.model.AnalysisType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AiAnalysisRepository extends JpaRepository<AiAnalysis, Long> {

    List<AiAnalysis> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Returns the most recent analysis of the given type for a user.
     * Useful for fetching the latest portfolio analysis before starting a chat.
     */
    Optional<AiAnalysis> findTopByUserIdAndAnalysisTypeOrderByCreatedAtDesc(
            Long userId, AnalysisType analysisType);

    /**
     * Returns all chat/follow-up rows linked to a base analysis, ordered
     * chronologically so conversation threads can be replayed.
     */
    List<AiAnalysis> findByParentAnalysisIdOrderByCreatedAt(Long parentAnalysisId);

    /**
     * Returns the most recent row whose type is one of the supplied types.
     * Handy for finding the latest base analysis regardless of whether it was a
     * full portfolio analysis or a holding explanation.
     */
    Optional<AiAnalysis> findTopByUserIdAndAnalysisTypeInOrderByCreatedAtDesc(
            Long userId, List<AnalysisType> analysisTypes);
}
