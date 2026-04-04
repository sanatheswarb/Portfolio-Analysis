package com.cursor_springa_ai.playground.repository;

import com.cursor_springa_ai.playground.model.UserHolding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserHoldingRepository extends JpaRepository<UserHolding, Long> {

    Optional<UserHolding> findByUserIdAndInstrumentId(Long userId, Long instrumentId);

    Optional<UserHolding> findByUserIdAndInstrumentSymbolIgnoreCase(Long userId, String symbol);

    List<UserHolding> findByUserId(Long userId);

    @Query("SELECT uh FROM UserHolding uh "
         + "JOIN FETCH uh.user u "
         + "LEFT JOIN FETCH u.portfolioStats "
         + "JOIN FETCH uh.instrument i "
         + "LEFT JOIN FETCH i.stockFundamentals "
         + "WHERE u.id = :userId")
    List<UserHolding> findByUserIdWithStatsAndFundamentals(@Param("userId") Long userId);
}
