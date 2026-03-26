package com.cursor_springa_ai.playground.controller;

import com.cursor_springa_ai.playground.dto.ZerodhaMFHoldingItem;
import com.cursor_springa_ai.playground.dto.ZerodhaMarginSegment;
import com.cursor_springa_ai.playground.dto.ZerodhaPositionItem;
import com.cursor_springa_ai.playground.dto.ZerodhaPositionsResponse;
import com.cursor_springa_ai.playground.dto.ZerodhaProfileResponse;
import com.cursor_springa_ai.playground.integration.zerodha.KiteConnectClient;
import com.cursor_springa_ai.playground.integration.zerodha.ZerodhaClientException;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.MFHolding;
import com.zerodhatech.models.Margin;
import com.zerodhatech.models.Position;
import com.zerodhatech.models.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller that exposes Zerodha KiteConnect APIs that are FREE to use
 * – they only require a developer-console app registration and a valid OAuth
 * session (API key + request-token callback).  No paid subscription is needed.
 *
 * <p>FREE APIs exposed here:
 * <ul>
 *   <li>GET /api/zerodha/data/profile        – authenticated user's profile</li>
 *   <li>GET /api/zerodha/data/margins        – equity &amp; commodity margin/funds</li>
 *   <li>GET /api/zerodha/data/positions      – intraday (day) and net positions</li>
 *   <li>GET /api/zerodha/data/mf-holdings    – mutual-fund holdings</li>
 * </ul>
 *
 * <p>PAID APIs (NOT exposed here, require a Kite Connect subscription):
 * <ul>
 *   <li>getQuote()         – live market quotes (bid/ask/depth)</li>
 *   <li>getOHLC()          – real-time OHLC candles</li>
 *   <li>getLTP()           – real-time last-traded price</li>
 *   <li>getHistoricalData()– historical candlestick data</li>
 * </ul>
 *
 * <p>Authentication flow (all free):
 * <ol>
 *   <li>GET /api/zerodha/login-url  – obtain the Kite login URL</li>
 *   <li>User logs in; Kite redirects to /api/zerodha/callback with request_token</li>
 *   <li>Access token is stored in-process; subsequent calls to this controller work</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/zerodha/data")
public class ZerodhaDataController {

    private final KiteConnectClient kiteConnectClient;

    public ZerodhaDataController(KiteConnectClient kiteConnectClient) {
        this.kiteConnectClient = kiteConnectClient;
    }

    /**
     * Fetch the authenticated user's profile.
     * FREE – no paid subscription required.
     *
     * @return Profile information (name, email, exchanges, products, order types)
     */
    @GetMapping("/profile")
    public ZerodhaProfileResponse getProfile() {
        try {
            Profile p = kiteConnectClient.getProfile();
            return new ZerodhaProfileResponse(
                    p.userType,
                    p.email,
                    p.userName,
                    p.userShortname,
                    p.broker,
                    p.exchanges != null ? Arrays.asList(p.exchanges) : List.of(),
                    p.products != null ? Arrays.asList(p.products) : List.of(),
                    p.orderTypes != null ? Arrays.asList(p.orderTypes) : List.of(),
                    p.avatarURL
            );
        } catch (ZerodhaClientException ex) {
            throw ex;
        } catch (KiteException | IOException ex) {
            throw new ZerodhaClientException("Failed to fetch profile from Zerodha: " + ex.getMessage(), ex);
        }
    }

    /**
     * Fetch equity and commodity margin / available funds.
     * FREE – no paid subscription required.
     *
     * @return Map keyed by segment ("equity" / "commodity"), each with available and utilised margins
     */
    @GetMapping("/margins")
    public Map<String, ZerodhaMarginSegment> getMargins() {
        try {
            Map<String, Margin> raw = kiteConnectClient.getMargins();
            Map<String, ZerodhaMarginSegment> result = new LinkedHashMap<>();
            if (raw != null) {
                raw.forEach((segment, margin) -> result.put(segment, toMarginSegment(margin)));
            }
            return result;
        } catch (ZerodhaClientException ex) {
            throw ex;
        } catch (KiteException | IOException ex) {
            throw new ZerodhaClientException("Failed to fetch margins from Zerodha: " + ex.getMessage(), ex);
        }
    }

    /**
     * Fetch intraday (day) and net positions for the current trading day.
     * FREE – no paid subscription required.
     *
     * @return Positions grouped into "day" (intraday) and "net" buckets
     */
    @GetMapping("/positions")
    public ZerodhaPositionsResponse getPositions() {
        try {
            Map<String, List<Position>> raw = kiteConnectClient.getPositions();
            List<ZerodhaPositionItem> day = toPositionItems(raw != null ? raw.get("day") : null);
            List<ZerodhaPositionItem> net = toPositionItems(raw != null ? raw.get("net") : null);
            return new ZerodhaPositionsResponse(day, net);
        } catch (ZerodhaClientException ex) {
            throw ex;
        } catch (KiteException | IOException ex) {
            throw new ZerodhaClientException("Failed to fetch positions from Zerodha: " + ex.getMessage(), ex);
        }
    }

    /**
     * Fetch mutual-fund holdings for the authenticated user.
     * FREE – no paid subscription required.
     *
     * @return List of mutual-fund holdings
     */
    @GetMapping("/mf-holdings")
    public List<ZerodhaMFHoldingItem> getMFHoldings() {
        try {
            List<MFHolding> raw = kiteConnectClient.getMFHoldings();
            if (raw == null) {
                return List.of();
            }
            return raw.stream().map(this::toMFHoldingItem).toList();
        } catch (ZerodhaClientException ex) {
            throw ex;
        } catch (KiteException | IOException ex) {
            throw new ZerodhaClientException("Failed to fetch MF holdings from Zerodha: " + ex.getMessage(), ex);
        }
    }

    // -----------------------------------------------------------------------
    // Mapping helpers
    // -----------------------------------------------------------------------

    private ZerodhaMarginSegment toMarginSegment(Margin m) {
        ZerodhaMarginSegment.AvailableMargin avail = null;
        if (m.available != null) {
            avail = new ZerodhaMarginSegment.AvailableMargin(
                    m.available.adhocMargin,
                    m.available.cash,
                    m.available.liveBalance,
                    m.available.collateral,
                    m.available.intradayPayin
            );
        }
        ZerodhaMarginSegment.UtilisedMargin used = null;
        if (m.utilised != null) {
            used = new ZerodhaMarginSegment.UtilisedMargin(
                    m.utilised.debits,
                    m.utilised.exposure,
                    m.utilised.m2mRealised,
                    m.utilised.m2mUnrealised,
                    m.utilised.optionPremium,
                    m.utilised.span,
                    m.utilised.holdingSales,
                    m.utilised.turnover
            );
        }
        return new ZerodhaMarginSegment(m.net, avail, used);
    }

    private List<ZerodhaPositionItem> toPositionItems(List<Position> positions) {
        if (positions == null) {
            return List.of();
        }
        return positions.stream().map(this::toPositionItem).toList();
    }

    private ZerodhaPositionItem toPositionItem(Position p) {
        return new ZerodhaPositionItem(
                p.tradingSymbol,
                p.exchange,
                p.product,
                p.instrumentToken,
                p.netQuantity,
                p.buyQuantity,
                p.sellQuantity,
                p.overnightQuantity,
                p.buyPrice != null ? p.buyPrice : 0.0,
                p.sellPrice != null ? p.sellPrice : 0.0,
                p.averagePrice,
                p.lastPrice != null ? p.lastPrice : 0.0,
                p.closePrice != null ? p.closePrice : 0.0,
                p.pnl != null ? p.pnl : 0.0,
                p.realised != null ? p.realised : 0.0,
                p.unrealised != null ? p.unrealised : 0.0,
                p.m2m != null ? p.m2m : 0.0,
                p.buyValue != null ? p.buyValue : 0.0,
                p.sellValue != null ? p.sellValue : 0.0,
                p.netValue != null ? p.netValue : 0.0
        );
    }

    private ZerodhaMFHoldingItem toMFHoldingItem(MFHolding h) {
        return new ZerodhaMFHoldingItem(
                h.tradingsymbol,
                h.fund,
                h.folio,
                h.quantity,
                h.averagePrice,
                h.lastPrice,
                h.pnl
        );
    }
}
