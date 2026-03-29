package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.model.Holding;
import com.cursor_springa_ai.playground.model.Instrument;
import com.cursor_springa_ai.playground.model.Portfolio;
import com.cursor_springa_ai.playground.model.User;
import com.cursor_springa_ai.playground.model.UserHolding;
import com.cursor_springa_ai.playground.repository.InstrumentRepository;
import com.cursor_springa_ai.playground.repository.UserHoldingRepository;
import com.cursor_springa_ai.playground.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Logger;

@Service
public class PortfolioService {

    private static final Logger logger = Logger.getLogger(PortfolioService.class.getName());
    private static final String BROKER_LOCAL = "LOCAL_PORTFOLIO";

    private final UserRepository userRepository;
    private final UserHoldingRepository userHoldingRepository;
    private final InstrumentRepository instrumentRepository;

    public PortfolioService(UserRepository userRepository,
                            UserHoldingRepository userHoldingRepository,
                            InstrumentRepository instrumentRepository) {
        this.userRepository = userRepository;
        this.userHoldingRepository = userHoldingRepository;
        this.instrumentRepository = instrumentRepository;
    }

    @Transactional
    public Portfolio createPortfolio(String ownerName) {
        String normalizedOwner = ownerName == null ? "" : ownerName.trim();
        if (normalizedOwner.isEmpty()) {
            throw new IllegalArgumentException("ownerName must not be blank");
        }

        User user = userRepository.findByBrokerAndBrokerUserId(BROKER_LOCAL, normalizedOwner)
                .orElseGet(() -> userRepository.save(new User(BROKER_LOCAL, normalizedOwner)));

        Portfolio portfolio = toPortfolio(user, userHoldingRepository.findByUserId(user.getId()));
        logger.info("Created/Reused portfolio | ID: " + portfolio.getId() + ", Owner: " + portfolio.getOwnerName());
        return portfolio;
    }

    @Transactional(readOnly = true)
    public List<Portfolio> getAllPortfolios() {
        List<Portfolio> portfolios = new ArrayList<>();
        for (User user : userRepository.findAll()) {
            portfolios.add(toPortfolio(user, userHoldingRepository.findByUserId(user.getId())));
        }
        return portfolios;
    }

    @Transactional(readOnly = true)
    public Portfolio getPortfolio(User user) {
        return toPortfolio(user, userHoldingRepository.findByUserId(user.getId()));
    }

    @Transactional
    public Holding addOrUpdateHolding(User user, Holding incomingHolding) {
        String symbolKey = incomingHolding.getSymbol().toUpperCase(Locale.ROOT);
        String exchange = (incomingHolding.getExchange() == null || incomingHolding.getExchange().isBlank())
            ? "NSE"
            : incomingHolding.getExchange();

        Instrument instrument = instrumentRepository.findBySymbolAndExchangeIgnoreCase(
                        symbolKey,
                exchange
                )
                .orElseGet(() -> instrumentRepository.save(new Instrument(
                generateSyntheticInstrumentToken(symbolKey, exchange),
                        symbolKey,
                exchange,
                        null
                )));

        int quantityInt = incomingHolding.getQuantity().setScale(0, RoundingMode.HALF_UP).intValue();
        BigDecimal avgPrice = nvl(incomingHolding.getAverageBuyPrice());
        BigDecimal currentPrice = incomingHolding.getCurrentPrice() != null
                ? incomingHolding.getCurrentPrice()
                : avgPrice;
        BigDecimal investedValue = avgPrice.multiply(BigDecimal.valueOf(quantityInt));
        BigDecimal currentValue = currentPrice.multiply(BigDecimal.valueOf(quantityInt));
        BigDecimal pnl = incomingHolding.getProfitLoss() != null
                ? incomingHolding.getProfitLoss()
                : currentValue.subtract(investedValue);

        userHoldingRepository.findByUserIdAndInstrumentInstrumentToken(user.getId(), instrument.getInstrumentToken())
                .ifPresentOrElse(existing -> {
                    existing.setQuantity(quantityInt);
                    existing.setAvgPrice(avgPrice);
                    existing.setClosePrice(currentPrice);
                    existing.setLastPrice(currentPrice);
                    existing.setInvestedValue(investedValue);
                    existing.setCurrentValue(currentValue);
                    existing.setPnl(pnl);
                    existing.setPnlPercent(calculatePnLPercent(investedValue, pnl));
                    existing.setDayChange(BigDecimal.ZERO);
                    existing.setDayChangePercent(BigDecimal.ZERO);
                    userHoldingRepository.save(existing);
                }, () -> {
                    UserHolding created = new UserHolding(
                            user,
                            instrument,
                            quantityInt,
                            avgPrice,
                            currentPrice,
                            currentPrice,
                            investedValue,
                            currentValue,
                            pnl,
                            calculatePnLPercent(investedValue, pnl),
                            BigDecimal.ZERO,
                            BigDecimal.ZERO
                    );
                    userHoldingRepository.save(created);
                });

        recalculateWeightPercent(user.getId());

        incomingHolding.setSymbol(symbolKey);
        incomingHolding.setExchange(exchange);
        incomingHolding.setCurrentPrice(currentPrice);
        incomingHolding.setProfitLoss(pnl);

        logger.info("Added/Updated holding for user " + user.getBrokerUserId() + " | Symbol: " + symbolKey +
                ", Qty: " + incomingHolding.getQuantity() + ", AvgPrice: " + incomingHolding.getAverageBuyPrice() +
                ", Exchange: " + incomingHolding.getExchange());
        return incomingHolding;
    }

    @Transactional
    public void removeHolding(User user, String symbol) {
        String symbolKey = symbol.toUpperCase(Locale.ROOT);

        Optional<UserHolding> holding = userHoldingRepository.findByUserIdAndInstrumentSymbolIgnoreCase(
            user.getId(), symbolKey);

        if (holding.isEmpty()) {
            throw new IllegalArgumentException("Holding not found for symbol: " + symbolKey);
        }

        userHoldingRepository.delete(holding.get());
        recalculateWeightPercent(user.getId());
    }

    private Portfolio toPortfolio(User user, List<UserHolding> userHoldings) {
        Portfolio portfolio = new Portfolio(user.getBrokerUserId(), user.getBrokerUserId());
        LinkedHashMap<String, Holding> holdings = new LinkedHashMap<>();
        for (UserHolding userHolding : userHoldings) {
            Holding holding = new Holding(
                    userHolding.getInstrument().getSymbol(),
                    userHolding.getInstrument().getExchange(),
                    inferAssetType(userHolding.getInstrument().getSymbol()),
                    BigDecimal.valueOf(userHolding.getQuantity()),
                    nvl(userHolding.getAvgPrice()),
                    nvl(userHolding.getLastPrice()),
                    nvl(userHolding.getPnl())
            );
            holdings.put(holding.getSymbol().toUpperCase(Locale.ROOT), holding);
        }
        portfolio.setHoldings(holdings);
        return portfolio;
    }

    private BigDecimal nvl(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private BigDecimal calculatePnLPercent(BigDecimal investedValue, BigDecimal pnl) {
        if (investedValue.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return pnl.divide(investedValue, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
    }

    private void recalculateWeightPercent(Long userId) {
        List<UserHolding> holdings = userHoldingRepository.findByUserId(userId);
        BigDecimal totalCurrentValue = holdings.stream()
                .map(UserHolding::getCurrentValue)
                .map(this::nvl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        for (UserHolding holding : holdings) {
            BigDecimal weightPercent = totalCurrentValue.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : nvl(holding.getCurrentValue())
                    .divide(totalCurrentValue, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            holding.setWeightPercent(weightPercent);
        }

        userHoldingRepository.saveAll(holdings);
    }

    private Long generateSyntheticInstrumentToken(String symbol, String exchange) {
        long candidate = Math.abs((((long) symbol.hashCode()) << 32) ^ exchange.toUpperCase(Locale.ROOT).hashCode());
        if (candidate == 0L) {
            candidate = 1L;
        }
        while (instrumentRepository.existsById(candidate)) {
            candidate++;
        }
        return candidate;
    }

    private com.cursor_springa_ai.playground.model.AssetType inferAssetType(String symbol) {
        return symbol != null && symbol.toUpperCase(Locale.ROOT).endsWith("ETF")
                ? com.cursor_springa_ai.playground.model.AssetType.ETF
                : com.cursor_springa_ai.playground.model.AssetType.STOCK;
    }
}
