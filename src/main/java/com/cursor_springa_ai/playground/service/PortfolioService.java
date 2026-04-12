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

    private UserHoldingDto toDto(UserHolding h) {
        return new UserHoldingDto(
                h.getId(),
                h.getSymbol(),
                h.getQuantity(),
                h.getAvgPrice(),
                h.getClosePrice(),
                h.getLastPrice(),
                h.getInvestedValue(),
                h.getCurrentValue(),
                h.getPnl(),
                h.getPnlPercent(),
                h.getDayChange(),
                h.getDayChangePercent(),
                h.getWeightPercent(),
                h.getUpdatedAt()
        );
    }
}
