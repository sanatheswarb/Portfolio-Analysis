package com.cursor_springa_ai.playground.repository;

import com.cursor_springa_ai.playground.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByBrokerAndBrokerUserId(String broker, String brokerUserId);
}
