package com.cursor_springa_ai.playground.repository;

import com.cursor_springa_ai.playground.model.Holding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface HoldingRepository extends JpaRepository<Holding, Long> {

    Optional<Holding> findByPortfolioIdAndSymbol(String portfolioId, String symbol);

    @Modifying
    void deleteByPortfolioIdAndSymbol(String portfolioId, String symbol);
}
