package com.cursor_springa_ai.playground.repository;

import com.cursor_springa_ai.playground.model.StockFundamentals;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockFundamentalsRepository extends JpaRepository<StockFundamentals, Long> {
}
