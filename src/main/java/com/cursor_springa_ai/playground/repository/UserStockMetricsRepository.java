package com.cursor_springa_ai.playground.repository;

import com.cursor_springa_ai.playground.model.entity.UserStockMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserStockMetricsRepository extends JpaRepository<UserStockMetrics, Long> {

        Optional<UserStockMetrics> findByUserIdAndInstrumentId(
            Long userId, Long instrumentId);
}
