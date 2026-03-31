package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.model.User;
import com.cursor_springa_ai.playground.model.UserHolding;
import com.cursor_springa_ai.playground.repository.UserHoldingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PortfolioService {

    private final UserHoldingRepository userHoldingRepository;

    public PortfolioService(UserHoldingRepository userHoldingRepository) {
        this.userHoldingRepository = userHoldingRepository;
    }


    @Transactional(readOnly = true)
    public List<UserHolding> getPortfolio(User user) {
        return userHoldingRepository.findByUserId(user.getId());
    }
}
