package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.model.UserHolding;
import com.cursor_springa_ai.playground.repository.UserHoldingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserHoldingSyncService {

    private final UserHoldingRepository userHoldingRepository;

    public UserHoldingSyncService(UserHoldingRepository userHoldingRepository) {
        this.userHoldingRepository = userHoldingRepository;
    }

    @Transactional
    public void replaceHoldings(Long userId, List<UserHolding> holdings) {
        userHoldingRepository.deleteByUserId(userId);
        userHoldingRepository.saveAll(holdings);
    }
}