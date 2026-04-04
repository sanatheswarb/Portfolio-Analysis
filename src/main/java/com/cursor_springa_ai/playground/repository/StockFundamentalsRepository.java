package com.cursor_springa_ai.playground.repository;

import com.cursor_springa_ai.playground.model.StockFundamentals;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockFundamentalsRepository extends JpaRepository<StockFundamentals, Long> {

    List<StockFundamentals> findAllByInstrumentIdIn(List<Long> instrumentIds);

    java.util.Optional<StockFundamentals> findByInstrumentId(Long instrumentId);
}
