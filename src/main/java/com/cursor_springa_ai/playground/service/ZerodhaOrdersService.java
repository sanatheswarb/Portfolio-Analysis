package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.dto.ZerodhaOrdersResponse;
import com.cursor_springa_ai.playground.integration.zerodha.ZerodhaOrdersClient;
import com.cursor_springa_ai.playground.integration.zerodha.dto.ZerodhaOrderItem;
import com.cursor_springa_ai.playground.model.Transaction;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Service that fetches orders (and order history) from the Zerodha KiteConnect
 * API and stores them in the in-memory {@link TransactionStore}, following the
 * same pattern as {@link ZerodhaImportService} for holdings.
 */
@Service
public class ZerodhaOrdersService {

    private final ZerodhaOrdersClient zerodhaOrdersClient;
    private final TransactionStore transactionStore;

    public ZerodhaOrdersService(ZerodhaOrdersClient zerodhaOrdersClient,
                                TransactionStore transactionStore) {
        this.zerodhaOrdersClient = zerodhaOrdersClient;
        this.transactionStore = transactionStore;
    }

    /**
     * Fetch all current orders from Zerodha and persist each one as a
     * {@link Transaction} in the transaction store.
     *
     * @return summary with count and list of stored transactions
     */
    public ZerodhaOrdersResponse fetchAndStoreOrders() {
        List<ZerodhaOrderItem> orders = zerodhaOrdersClient.fetchOrders();
        List<Transaction> saved = new ArrayList<>();

        for (ZerodhaOrderItem order : orders) {
            Transaction tx = toTransaction(order);
            // Use orderId as the id so re-importing the same order is an upsert
            if (tx.getOrderId() != null) {
                tx.setId(tx.getOrderId());
            }
            saved.add(transactionStore.save(tx));
        }

        return new ZerodhaOrdersResponse(saved.size(), saved);
    }

    /**
     * Fetch the full history of a single order from Zerodha and persist each
     * status snapshot as a {@link Transaction} in the transaction store.
     *
     * <p>Each history snapshot is stored with a composite key
     * {@code orderId + "_" + index} so that individual snapshots do not
     * overwrite each other in the store.
     *
     * @param orderId Zerodha order ID
     * @return summary with count and list of stored transaction snapshots
     */
    public ZerodhaOrdersResponse fetchAndStoreOrderHistory(String orderId) {
        List<ZerodhaOrderItem> history = zerodhaOrdersClient.fetchOrderHistory(orderId);
        List<Transaction> saved = new ArrayList<>();

        for (int i = 0; i < history.size(); i++) {
            ZerodhaOrderItem snapshot = history.get(i);
            Transaction tx = toTransaction(snapshot);
            // Use a composite key so each history snapshot is stored separately
            tx.setId(orderId + "_" + i);
            saved.add(transactionStore.save(tx));
        }

        return new ZerodhaOrdersResponse(saved.size(), saved);
    }

    /**
     * Return all transactions currently held in the transaction store.
     *
     * @return list of all stored transactions
     */
    public List<Transaction> getAllTransactions() {
        return transactionStore.findAll();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Transaction toTransaction(ZerodhaOrderItem order) {
        Transaction tx = new Transaction();
        // id is intentionally left null here; callers set it before persisting
        // (orderId for normal orders, composite orderId_index for history snapshots)
        tx.setOrderId(order.getOrderId());
        tx.setExchangeOrderId(order.getExchangeOrderId());
        tx.setParentOrderId(order.getParentOrderId());
        tx.setTradingSymbol(order.getTradingSymbol());
        tx.setExchange(order.getExchange());
        tx.setOrderVariety(order.getOrderVariety());
        tx.setOrderType(order.getOrderType());
        tx.setTransactionType(order.getTransactionType());
        tx.setProduct(order.getProduct());
        tx.setStatus(order.getStatus());
        tx.setStatusMessage(order.getStatusMessage());
        tx.setQuantity(order.getQuantity());
        tx.setFilledQuantity(order.getFilledQuantity());
        tx.setPendingQuantity(order.getPendingQuantity());
        tx.setDisclosedQuantity(order.getDisclosedQuantity());
        tx.setPrice(order.getPrice());
        tx.setTriggerPrice(order.getTriggerPrice());
        tx.setAveragePrice(order.getAveragePrice());
        tx.setValidity(order.getValidity());
        tx.setTag(order.getTag());
        tx.setOrderTimestamp(order.getOrderTimestamp());
        tx.setExchangeTimestamp(order.getExchangeTimestamp());
        tx.setImportedAt(Instant.now());
        return tx;
    }
}
