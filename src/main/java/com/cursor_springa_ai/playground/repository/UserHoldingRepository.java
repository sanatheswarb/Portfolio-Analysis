package com.cursor_springa_ai.playground.repository;

import com.cursor_springa_ai.playground.model.UserHolding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserHoldingRepository extends JpaRepository<UserHolding, Long> {

    Optional<UserHolding> findByUserIdAndInstrumentInstrumentToken(Long userId, Long instrumentToken);

    List<UserHolding> findByUserId(Long userId);
}
