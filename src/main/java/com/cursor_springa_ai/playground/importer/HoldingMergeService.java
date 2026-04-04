package com.cursor_springa_ai.playground.importer;

import com.cursor_springa_ai.playground.model.UserHolding;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@Service
public class HoldingMergeService {

    private static final Logger logger = Logger.getLogger(HoldingMergeService.class.getName());

    List<UserHolding> mergeDuplicateHoldings(List<PreparedHolding> preparedHoldings) {
        Map<Long, UserHolding> mergedByInstrumentId = new LinkedHashMap<>();
        for (PreparedHolding preparedHolding : preparedHoldings) {
            UserHolding holding = preparedHolding.userHolding();
            Long instrumentId = holding.getInstrument().getId();
            if (instrumentId == null) {
                throw new IllegalStateException(
                        "Instrument id missing for symbol " + preparedHolding.symbol());
            }

            UserHolding existing = mergedByInstrumentId.get(instrumentId);
            if (existing == null) {
                mergedByInstrumentId.put(instrumentId, holding);
                continue;
            }

            logger.warning("Merging duplicate Zerodha holdings for instrumentId="
                    + instrumentId + ", symbol=" + existing.getSymbol());
            mergeHolding(existing, holding);
        }
        return List.copyOf(mergedByInstrumentId.values());
    }

    private void mergeHolding(UserHolding existing, UserHolding incoming) {
        int quantity = existing.getQuantity() + incoming.getQuantity();
        BigDecimal investedValue = existing.getInvestedValue().add(incoming.getInvestedValue());
        BigDecimal currentValue = existing.getCurrentValue().add(incoming.getCurrentValue());
        BigDecimal pnl = existing.getPnl().add(incoming.getPnl());
        BigDecimal dayChange = existing.getDayChange().add(incoming.getDayChange());
        BigDecimal weightPercent = existing.getWeightPercent().add(incoming.getWeightPercent());

        BigDecimal closeValue = existing.getClosePrice().multiply(BigDecimal.valueOf(existing.getQuantity()))
                .add(incoming.getClosePrice().multiply(BigDecimal.valueOf(incoming.getQuantity())));
        BigDecimal avgPrice = divide(investedValue, BigDecimal.valueOf(quantity));
        BigDecimal lastPrice = divide(currentValue, BigDecimal.valueOf(quantity));
        BigDecimal closePrice = divide(closeValue, BigDecimal.valueOf(quantity));
        BigDecimal pnlPercent = percentage(pnl, investedValue);
        BigDecimal dayChangePct = percentage(dayChange, closeValue);

        existing.setQuantity(quantity);
        existing.setAvgPrice(avgPrice);
        existing.setLastPrice(lastPrice);
        existing.setClosePrice(closePrice);
        existing.setInvestedValue(investedValue);
        existing.setCurrentValue(currentValue);
        existing.setPnl(pnl);
        existing.setPnlPercent(pnlPercent);
        existing.setDayChange(dayChange);
        existing.setDayChangePercent(dayChangePct);
        existing.setWeightPercent(weightPercent);
    }

    private BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return numerator.divide(denominator, 6, RoundingMode.HALF_UP);
    }

    private BigDecimal percentage(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return numerator.divide(denominator, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }
}