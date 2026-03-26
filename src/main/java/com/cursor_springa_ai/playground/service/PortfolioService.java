package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.model.Holding;
import com.cursor_springa_ai.playground.model.Portfolio;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

@Service
public class PortfolioService {

    private static final Logger logger = Logger.getLogger(PortfolioService.class.getName());
    private final Map<String, Portfolio> portfolioStore = new ConcurrentHashMap<>();

    public Portfolio createPortfolio(String ownerName) {
        String id = UUID.randomUUID().toString();
        Portfolio portfolio = new Portfolio(id, ownerName);
        portfolioStore.put(id, portfolio);
        logger.info("Created new portfolio | ID: " + id + ", Owner: " + ownerName + " | Total portfolios: " + portfolioStore.size());
        return portfolio;
    }

    public List<Portfolio> getAllPortfolios() {
        return new ArrayList<>(portfolioStore.values());
    }

    public Portfolio getPortfolio(String id) {
        Portfolio portfolio = portfolioStore.get(id);
        if (portfolio == null) {
            throw new IllegalArgumentException("Portfolio not found for id: " + id);
        }
        return portfolio;
    }

    public Holding addOrUpdateHolding(String portfolioId, Holding incomingHolding) {
        Portfolio portfolio = getPortfolio(portfolioId);
        String symbolKey = incomingHolding.getSymbol().toUpperCase(Locale.ROOT);
        incomingHolding.setSymbol(symbolKey);
        portfolio.getHoldings().put(symbolKey, incomingHolding);
        logger.info("Added/Updated holding in portfolio " + portfolioId + " | Symbol: " + symbolKey +
                ", Qty: " + incomingHolding.getQuantity() + ", AvgPrice: " + incomingHolding.getAverageBuyPrice() +
                ", Exchange: " + incomingHolding.getExchange() + " | Total holdings: " + portfolio.getHoldings().size());
        return incomingHolding;
    }

    public void removeHolding(String portfolioId, String symbol) {
        Portfolio portfolio = getPortfolio(portfolioId);
        String symbolKey = symbol.toUpperCase(Locale.ROOT);
        Holding removed = portfolio.getHoldings().remove(symbolKey);
        if (removed == null) {
            throw new IllegalArgumentException("Holding not found for symbol: " + symbolKey);
        }
    }
}
