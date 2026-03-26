package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.model.Transaction;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * In-memory transaction table that stores order data imported from Zerodha.
 * Keyed by the Zerodha {@code orderId} so that re-importing the same order
 * updates the existing record rather than creating a duplicate.
 */
@Service
public class TransactionStore {

    private static final Logger logger = Logger.getLogger(TransactionStore.class.getName());

    /** Map of transaction id → Transaction (acts as the in-memory transaction table). */
    private final Map<String, Transaction> store = new ConcurrentHashMap<>();

    /** Secondary index: Zerodha orderId → transaction id (for fast lookup by orderId). */
    private final Map<String, String> orderIdIndex = new ConcurrentHashMap<>();

    /**
     * Persist (insert or update) a transaction keyed by its Zerodha order ID.
     * If a transaction with the same order ID already exists it is replaced.
     *
     * @param transaction transaction to save
     * @return the saved transaction
     */
    public Transaction save(Transaction transaction) {
        if (transaction.getId() == null) {
            transaction.setId(UUID.randomUUID().toString());
        }
        store.put(transaction.getId(), transaction);
        if (transaction.getOrderId() != null) {
            orderIdIndex.put(transaction.getOrderId(), transaction.getId());
        }
        logger.info("Saved transaction | orderId: " + transaction.getOrderId()
                + ", symbol: " + transaction.getTradingSymbol()
                + ", status: " + transaction.getStatus()
                + " | Total transactions: " + store.size());
        return transaction;
    }

    /**
     * Return all stored transactions.
     *
     * @return list of all transactions
     */
    public List<Transaction> findAll() {
        return new ArrayList<>(store.values());
    }

    /**
     * Find a transaction by Zerodha order ID using the secondary index.
     *
     * @param orderId Zerodha order ID
     * @return the transaction, or {@code null} if not found
     */
    public Transaction findByOrderId(String orderId) {
        String id = orderIdIndex.get(orderId);
        return id != null ? store.get(id) : null;
    }

    /**
     * Return the number of transactions currently stored.
     *
     * @return size of the store
     */
    public int size() {
        return store.size();
    }
}
