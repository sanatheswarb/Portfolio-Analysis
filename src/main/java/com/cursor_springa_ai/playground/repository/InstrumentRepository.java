package com.cursor_springa_ai.playground.repository;

import com.cursor_springa_ai.playground.model.Instrument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InstrumentRepository extends JpaRepository<Instrument, Long> {
}
