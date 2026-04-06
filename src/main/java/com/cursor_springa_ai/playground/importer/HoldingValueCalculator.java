package com.cursor_springa_ai.playground.importer;

import com.cursor_springa_ai.playground.integration.zerodha.dto.ZerodhaHoldingItem;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * Pure financial calculator for a single Zerodha holding item.
 * Contains no I/O or persistence logic — safe to instantiate inline.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Compute the total current portfolio value from a list of raw items.</li>
 *   <li>Derive all per-holding financial values (invested value, PnL, day change, weight).</li>
 * </ul>
 */
@Service
public class HoldingValueCalculator {

    /**
     * Sums {@code quantity × lastPrice} for all items with positive quantities.
     */
    public BigDecimal computeTotalCurrentValue(List<ZerodhaHoldingItem> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (ZerodhaHoldingItem item : items) {
            if (item.getQuantity() != null && item.getLastPrice() != null
                    && item.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
                total = total.add(item.getQuantity().multiply(item.getLastPrice()));
            }
        }
        return total;
    }

    /**
     * Derives all financial values for a single holding item.
     *
     * @param item               raw holding item from the Zerodha API
     * @param totalCurrentValue  pre-computed total portfolio value, used for weight calculation
     * @param nsePreviousClose   NSE previous close (preferred over Kite close price when available)
     * @return a {@link HoldingComputedValues} record with all derived fields
     */
    public HoldingComputedValues computeValues(ZerodhaHoldingItem item,
                                               BigDecimal totalCurrentValue,
                                               BigDecimal nsePreviousClose) {
        BigDecimal qty = item.getQuantity();
        // Indian equity markets do not support fractional shares; round to the nearest
        // whole number defensively before storing as Integer.
        int qtyInt = qty.setScale(0, RoundingMode.HALF_UP).intValue();
        BigDecimal avgPrice = Objects.requireNonNullElse(item.getAveragePrice(), BigDecimal.ZERO);
        BigDecimal lastPrice = Objects.requireNonNullElse(item.getLastPrice(), BigDecimal.ZERO);
        BigDecimal closePrice = resolveClosePrice(item.getClosePrice(), nsePreviousClose);
        String symbol = TradingSymbolNormalizer.normalize(item.getTradingSymbol());

        BigDecimal investedValue = qty.multiply(avgPrice);
        BigDecimal currentValue = qty.multiply(lastPrice);
        BigDecimal pnl = Objects.requireNonNullElse(item.getPnl(), BigDecimal.ZERO);
        BigDecimal pnlPercent = investedValue.compareTo(BigDecimal.ZERO) != 0
                ? pnl.divide(investedValue, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;
        BigDecimal dayChange = calculateDayChange(lastPrice, closePrice, item.getDayChange());
        BigDecimal dayChangePct = calculateDayChangePct(dayChange, closePrice, item.getDayChangePercentage());
        BigDecimal weightPercent = totalCurrentValue.compareTo(BigDecimal.ZERO) != 0
                ? currentValue.divide(totalCurrentValue, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        return new HoldingComputedValues(
                symbol, qtyInt, avgPrice, lastPrice, closePrice,
                investedValue, currentValue, pnl, pnlPercent,
                dayChange, dayChangePct, weightPercent
        );
    }

    // ------------------------------------------------------------------
    // private helpers
    // ------------------------------------------------------------------

    /**
     * Prefer the NSE previous close over the Kite close price when available and positive.
     */
    private BigDecimal resolveClosePrice(BigDecimal kiteClosePrice, BigDecimal nsePreviousClose) {
        if (nsePreviousClose != null && nsePreviousClose.compareTo(BigDecimal.ZERO) > 0) {
            return nsePreviousClose;
        }
        return Objects.requireNonNullElse(kiteClosePrice, BigDecimal.ZERO);
    }

    private BigDecimal calculateDayChange(BigDecimal lastPrice, BigDecimal closePrice,
                                          BigDecimal fallbackDayChange) {
        if (closePrice != null && closePrice.compareTo(BigDecimal.ZERO) > 0) {
            return Objects.requireNonNullElse(lastPrice, BigDecimal.ZERO).subtract(closePrice);
        }
        return Objects.requireNonNullElse(fallbackDayChange, BigDecimal.ZERO);
    }

    private BigDecimal calculateDayChangePct(BigDecimal dayChange, BigDecimal closePrice,
                                             BigDecimal fallbackPct) {
        if (closePrice != null && closePrice.compareTo(BigDecimal.ZERO) > 0) {
            return dayChange.divide(closePrice, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }
        return Objects.requireNonNullElse(fallbackPct, BigDecimal.ZERO);
    }

}
