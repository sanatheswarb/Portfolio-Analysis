package com.cursor_springa_ai.playground.repository;

import com.cursor_springa_ai.playground.model.HoldingSnapshot;
import com.cursor_springa_ai.playground.model.HoldingSnapshotId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface HoldingSnapshotRepository extends JpaRepository<HoldingSnapshot, HoldingSnapshotId> {

    boolean existsByPkUserIdAndPkInstrumentTokenAndPkSnapshotDate(
            Long userId, Long instrumentToken, LocalDate snapshotDate);

    List<HoldingSnapshot> findByPkUserIdAndPkSnapshotDate(Long userId, LocalDate snapshotDate);
}
