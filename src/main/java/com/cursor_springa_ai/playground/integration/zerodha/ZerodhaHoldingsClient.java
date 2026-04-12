package com.cursor_springa_ai.playground.integration.zerodha;

import com.cursor_springa_ai.playground.exception.ZerodhaClientException;
import com.cursor_springa_ai.playground.dto.zerodha.ZerodhaHoldingItem;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Holding;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Zerodha holdings client using the official KiteConnect SDK.
 * Simplified from the previous JSON-based REST implementation.
 */
@Component
public class ZerodhaHoldingsClient {

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


        return item;
    }

}

