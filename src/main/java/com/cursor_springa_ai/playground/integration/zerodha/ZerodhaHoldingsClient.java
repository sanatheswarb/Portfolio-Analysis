package com.cursor_springa_ai.playground.integration.zerodha;

import com.cursor_springa_ai.playground.integration.zerodha.dto.ZerodhaHoldingItem;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Holding;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * Zerodha holdings client using the official KiteConnect SDK.
 * Simplified from the previous JSON-based REST implementation.
 */
@Component
public class ZerodhaHoldingsClient {

    private static final Logger logger = Logger.getLogger(ZerodhaHoldingsClient.class.getName());

    private final KiteConnectClient kiteConnectClient;

    public ZerodhaHoldingsClient(KiteConnectClient kiteConnectClient) {
        this.kiteConnectClient = kiteConnectClient;
    }

    /**
     * Fetch holdings from Zerodha API.
     * 
     * @return List of holdings
     * @throws ZerodhaClientException if authentication fails or API call fails
     */
    public List<ZerodhaHoldingItem> fetchHoldings() {
        if (!kiteConnectClient.hasActiveSession()) {
            throw new ZerodhaClientException(
                    "Missing Zerodha auth. Complete /api/zerodha/callback or login via /api/zerodha/login-url");
        }

        try {
            List<Holding> sdkHoldings = kiteConnectClient.getHoldings();
            if (sdkHoldings == null) {
                return Collections.emptyList();
            }
            logger.info("Raw Zerodha sdkHoldings: " + summarizeSdkHoldings(sdkHoldings));

            // Convert SDK Holding objects to ZerodhaHoldingItem DTOs
            List<ZerodhaHoldingItem> items = new ArrayList<>();
            for (Holding holding : sdkHoldings) {
                ZerodhaHoldingItem item = convertToHoldingItem(holding);
                if (item != null) {
                    items.add(item);
                }
            }
            return items;

        } catch (ZerodhaClientException ex) {
            throw ex;
        } catch (KiteException ex) {
            String message = ex.getMessage();
            if (message != null && message.contains("403")) {
                throw new ZerodhaClientException(
                        "Access denied fetching holdings (HTTP 403). The access token may have expired. " +
                        "Fix: Re-login via GET /api/zerodha/login-url",
                        ex);
            }
            throw new ZerodhaClientException("Failed to fetch holdings from Zerodha: " + message, ex);
        } catch (IOException ex) {
            throw new ZerodhaClientException("Network error fetching holdings: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            String message = ex.getMessage();
            throw new ZerodhaClientException("Failed to fetch holdings from Zerodha: " + message, ex);
        }
    }

    /**
     * Convert SDK Holding to ZerodhaHoldingItem DTO.
     */
    private ZerodhaHoldingItem convertToHoldingItem(Holding holding) {
        if (holding == null) {
            return null;
        }

        ZerodhaHoldingItem item = new ZerodhaHoldingItem();
        if (holding.instrumentToken != null && !holding.instrumentToken.isBlank()) {
            try {
                item.setInstrumentToken(Long.parseLong(holding.instrumentToken));
            } catch (NumberFormatException ignored) {
                // non-numeric token — leave null
            }
        }
        item.setTradingSymbol(holding.tradingSymbol);

        if (holding.exchange != null) {
            item.setExchange(holding.exchange);
        }

        if (holding.isin != null) {
            item.setIsin(holding.isin);
        }

        if (holding.quantity > 0) {
            item.setQuantity(BigDecimal.valueOf(holding.quantity));
        }

        if (holding.averagePrice != null) {
            item.setAveragePrice(BigDecimal.valueOf(holding.averagePrice));
        }

        if (holding.lastPrice != null) {
            item.setLastPrice(BigDecimal.valueOf(holding.lastPrice));
        }

        if (holding.pnl != null) {
            item.setPnl(BigDecimal.valueOf(holding.pnl));
            item.setProfitLoss(holding.pnl >= 0 ? "profit" : "loss");
        }

        // If a base 'price' is available as close price, map and compute day delta
        BigDecimal closePrice = null;
        if (holding.price != null && !holding.price.isBlank()) {
            try {
                closePrice = new BigDecimal(holding.price);
                item.setClosePrice(closePrice);
            } catch (NumberFormatException ignored) {
                // ignore non-numeric price values
            }
        }

        if (item.getLastPrice() != null && closePrice != null) {
            BigDecimal dayChange = item.getLastPrice().subtract(closePrice);
            item.setDayChange(dayChange);
            if (closePrice.compareTo(BigDecimal.ZERO) != 0) {
                item.setDayChangePercentage(dayChange
                        .divide(closePrice, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)));
            }
        }

        return item;
    }

    private String summarizeSdkHoldings(List<Holding> sdkHoldings) {
        List<String> summaries = new ArrayList<>();
        for (Holding holding : sdkHoldings) {
            summaries.add("{symbol=" + holding.tradingSymbol
                    + ", token=" + holding.instrumentToken
                    + ", exchange=" + holding.exchange
                    + ", isin=" + holding.isin
                    + ", qty=" + holding.quantity
                    + ", avgPrice=" + holding.averagePrice
                    + ", lastPrice=" + holding.lastPrice
                    + ", pnl=" + holding.pnl
                    + ", closePrice=" + holding.price
                    + "}");
        }
        return summaries.toString();
    }
}

