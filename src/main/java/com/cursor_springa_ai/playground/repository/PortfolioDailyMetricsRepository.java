package com.cursor_springa_ai.playground.repository;

import com.cursor_springa_ai.playground.model.PortfolioDailyMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface PortfolioDailyMetricsRepository extends JpaRepository<PortfolioDailyMetrics, Long> {

    boolean existsByUserIdAndSnapshotDate(Long userId, LocalDate snapshotDate);
}
