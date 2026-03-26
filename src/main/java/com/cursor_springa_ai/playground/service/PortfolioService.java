package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.model.Holding;
import com.cursor_springa_ai.playground.model.Portfolio;
import com.cursor_springa_ai.playground.repository.HoldingRepository;
import com.cursor_springa_ai.playground.repository.PortfolioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Logger;

@Service
@Transactional
public class PortfolioService {

    private static final Logger logger = Logger.getLogger(PortfolioService.class.getName());

    private final PortfolioRepository portfolioRepository;
    private final HoldingRepository holdingRepository;

    public PortfolioService(PortfolioRepository portfolioRepository, HoldingRepository holdingRepository) {
        this.portfolioRepository = portfolioRepository;
        this.holdingRepository = holdingRepository;
    }

    public Portfolio createPortfolio(String ownerName) {
        String id = UUID.randomUUID().toString();
        Portfolio portfolio = new Portfolio(id, ownerName);
        Portfolio saved = portfolioRepository.save(portfolio);
        logger.info("Created new portfolio | ID: " + id + ", Owner: " + ownerName);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Portfolio> getAllPortfolios() {
        return portfolioRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Portfolio getPortfolio(String id) {
        return portfolioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Portfolio not found for id: " + id));
    }

    public Holding addOrUpdateHolding(String portfolioId, Holding incomingHolding) {
        Portfolio portfolio = getPortfolio(portfolioId);
        String symbolKey = incomingHolding.getSymbol().toUpperCase(Locale.ROOT);

        Holding holdingToSave = holdingRepository
                .findByPortfolioIdAndSymbol(portfolioId, symbolKey)
                .orElseGet(Holding::new);

        holdingToSave.setPortfolio(portfolio);
        holdingToSave.setSymbol(symbolKey);
        holdingToSave.setExchange(incomingHolding.getExchange());
        holdingToSave.setAssetType(incomingHolding.getAssetType());
        holdingToSave.setQuantity(incomingHolding.getQuantity());
        holdingToSave.setAverageBuyPrice(incomingHolding.getAverageBuyPrice());
        holdingToSave.setCurrentPrice(incomingHolding.getCurrentPrice());
        holdingToSave.setProfitLoss(incomingHolding.getProfitLoss());

        Holding saved = holdingRepository.save(holdingToSave);
        logger.info("Added/Updated holding in portfolio " + portfolioId + " | Symbol: " + symbolKey +
                ", Qty: " + saved.getQuantity() + ", AvgPrice: " + saved.getAverageBuyPrice() +
                ", Exchange: " + saved.getExchange());
        return saved;
    }

    public void removeHolding(String portfolioId, String symbol) {
        String symbolKey = symbol.toUpperCase(Locale.ROOT);
        if (holdingRepository.findByPortfolioIdAndSymbol(portfolioId, symbolKey).isEmpty()) {
            throw new IllegalArgumentException("Holding not found for symbol: " + symbolKey);
        }
        holdingRepository.deleteByPortfolioIdAndSymbol(portfolioId, symbolKey);
    }
}
