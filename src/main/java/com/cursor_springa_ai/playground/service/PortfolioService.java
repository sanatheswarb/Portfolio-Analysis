package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.dto.UserHoldingDto;
import com.cursor_springa_ai.playground.model.entity.User;
import com.cursor_springa_ai.playground.model.entity.UserHolding;
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
    public List<UserHoldingDto> getPortfolio(User user) {
        return userHoldingRepository.findByUserId(user.getId())
                .stream()
                .map(this::toDto)
                .toList();
    }

    private UserHoldingDto toDto(UserHolding userHolding) {
        return new UserHoldingDto(
                userHolding.getId(),
                userHolding.getSymbol(),
                userHolding.getQuantity(),
                userHolding.getAvgPrice(),
                userHolding.getClosePrice(),
                userHolding.getLastPrice(),
                userHolding.getInvestedValue(),
                userHolding.getCurrentValue(),
                userHolding.getPnl(),
                userHolding.getPnlPercent(),
                userHolding.getDayChange(),
                userHolding.getDayChangePercent(),
                userHolding.getWeightPercent(),
                userHolding.getUpdatedAt()
        );
    }
}
