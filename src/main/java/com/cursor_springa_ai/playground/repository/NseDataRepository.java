package com.cursor_springa_ai.playground.repository;

import com.cursor_springa_ai.playground.model.NseData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NseDataRepository extends JpaRepository<NseData, String> {

    List<NseData> findBySymbolIn(List<String> symbols);
}
