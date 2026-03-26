package com.cursor_springa_ai.playground.integration.zerodha;

import com.cursor_springa_ai.playground.integration.zerodha.dto.ZerodhaOrderItem;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Zerodha orders client using the official KiteConnect SDK.
 * Fetches open/pending orders and individual order history, converting them to
 * {@link ZerodhaOrderItem} DTOs, following the same pattern as
 * {@link ZerodhaHoldingsClient}.
 */
@Component
public class ZerodhaOrdersClient {

    private final KiteConnectClient kiteConnectClient;

    public ZerodhaOrdersClient(KiteConnectClient kiteConnectClient) {
        this.kiteConnectClient = kiteConnectClient;
    }

    /**
     * Fetch all orders from Zerodha API.
     *
     * @return List of orders as DTOs
     * @throws ZerodhaClientException if authentication fails or API call fails
     */
    public List<ZerodhaOrderItem> fetchOrders() {
        if (!kiteConnectClient.hasActiveSession()) {
            throw new ZerodhaClientException(
                    "Missing Zerodha auth. Complete /api/zerodha/callback or login via /api/zerodha/login-url");
        }

        try {
            List<Order> sdkOrders = kiteConnectClient.getOrders();
            if (sdkOrders == null) {
                return Collections.emptyList();
            }

            List<ZerodhaOrderItem> items = new ArrayList<>();
            for (Order order : sdkOrders) {
                ZerodhaOrderItem item = convertToOrderItem(order);
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
                        "Access denied fetching orders (HTTP 403). The access token may have expired. " +
                        "Fix: Re-login via GET /api/zerodha/login-url",
                        ex);
            }
            throw new ZerodhaClientException("Failed to fetch orders from Zerodha: " + message, ex);
        } catch (IOException ex) {
            throw new ZerodhaClientException("Network error fetching orders: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new ZerodhaClientException("Failed to fetch orders from Zerodha: " + ex.getMessage(), ex);
        }
    }

    /**
     * Fetch the history (all status transitions) of a specific order.
     *
     * @param orderId Zerodha order ID
     * @return List of order history states as DTOs
     * @throws ZerodhaClientException if authentication fails or API call fails
     */
    public List<ZerodhaOrderItem> fetchOrderHistory(String orderId) {
        if (!kiteConnectClient.hasActiveSession()) {
            throw new ZerodhaClientException(
                    "Missing Zerodha auth. Complete /api/zerodha/callback or login via /api/zerodha/login-url");
        }
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId is required");
        }

        try {
            List<Order> sdkOrders = kiteConnectClient.getOrderHistory(orderId);
            if (sdkOrders == null) {
                return Collections.emptyList();
            }

            List<ZerodhaOrderItem> items = new ArrayList<>();
            for (Order order : sdkOrders) {
                ZerodhaOrderItem item = convertToOrderItem(order);
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
                        "Access denied fetching order history (HTTP 403). The access token may have expired. " +
                        "Fix: Re-login via GET /api/zerodha/login-url",
                        ex);
            }
            throw new ZerodhaClientException("Failed to fetch order history from Zerodha: " + message, ex);
        } catch (IOException ex) {
            throw new ZerodhaClientException("Network error fetching order history: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new ZerodhaClientException("Failed to fetch order history from Zerodha: " + ex.getMessage(), ex);
        }
    }

    /**
     * Convert SDK {@link Order} to {@link ZerodhaOrderItem} DTO.
     */
    private ZerodhaOrderItem convertToOrderItem(Order order) {
        if (order == null) {
            return null;
        }

        ZerodhaOrderItem item = new ZerodhaOrderItem();
        item.setOrderId(order.orderId);
        item.setExchangeOrderId(order.exchangeOrderId);
        item.setParentOrderId(order.parentOrderId);
        item.setTradingSymbol(order.tradingSymbol);
        item.setExchange(order.exchange);
        item.setOrderVariety(order.orderVariety);
        item.setOrderType(order.orderType);
        item.setTransactionType(order.transactionType);
        item.setProduct(order.product);
        item.setStatus(order.status);
        item.setStatusMessage(order.statusMessage);
        item.setQuantity(order.quantity);
        item.setFilledQuantity(order.filledQuantity);
        item.setPendingQuantity(order.pendingQuantity);
        item.setDisclosedQuantity(order.disclosedQuantity);
        item.setPrice(order.price);
        item.setTriggerPrice(order.triggerPrice);
        item.setAveragePrice(order.averagePrice);
        item.setValidity(order.validity);
        item.setTag(order.tag);
        item.setOrderTimestamp(order.orderTimestamp);
        item.setExchangeTimestamp(order.exchangeTimestamp);
        return item;
    }
}
