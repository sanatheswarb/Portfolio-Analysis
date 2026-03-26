package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.dto.ZerodhaOrdersResponse;
import com.cursor_springa_ai.playground.integration.zerodha.ZerodhaOrdersClient;
import com.cursor_springa_ai.playground.integration.zerodha.dto.ZerodhaOrderItem;
import com.cursor_springa_ai.playground.model.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ZerodhaOrdersServiceTest {

    private ZerodhaOrdersClient zerodhaOrdersClient;
    private TransactionStore transactionStore;
    private ZerodhaOrdersService zerodhaOrdersService;

    @BeforeEach
    void setUp() {
        zerodhaOrdersClient = mock(ZerodhaOrdersClient.class);
        transactionStore = new TransactionStore();
        zerodhaOrdersService = new ZerodhaOrdersService(zerodhaOrdersClient, transactionStore);
    }

    // -------------------------------------------------------------------------
    // fetchAndStoreOrders
    // -------------------------------------------------------------------------

    @Test
    void fetchAndStoreOrders_returnsEmptyResponseWhenNoOrders() {
        when(zerodhaOrdersClient.fetchOrders()).thenReturn(List.of());

        ZerodhaOrdersResponse response = zerodhaOrdersService.fetchAndStoreOrders();

        assertNotNull(response);
        assertEquals(0, response.count());
        assertTrue(response.transactions().isEmpty());
    }

    @Test
    void fetchAndStoreOrders_storesOrdersAsTransactions() {
        ZerodhaOrderItem order = buildOrderItem("ORDER001", "INFY", "COMPLETE", "BUY");
        when(zerodhaOrdersClient.fetchOrders()).thenReturn(List.of(order));

        ZerodhaOrdersResponse response = zerodhaOrdersService.fetchAndStoreOrders();

        assertEquals(1, response.count());
        assertEquals(1, response.transactions().size());

        Transaction tx = response.transactions().get(0);
        assertEquals("ORDER001", tx.getOrderId());
        assertEquals("INFY", tx.getTradingSymbol());
        assertEquals("COMPLETE", tx.getStatus());
        assertEquals("BUY", tx.getTransactionType());
        assertNotNull(tx.getImportedAt());
    }

    @Test
    void fetchAndStoreOrders_storesMultipleOrders() {
        ZerodhaOrderItem order1 = buildOrderItem("ORDER001", "INFY", "COMPLETE", "BUY");
        ZerodhaOrderItem order2 = buildOrderItem("ORDER002", "TCS", "OPEN", "SELL");
        when(zerodhaOrdersClient.fetchOrders()).thenReturn(List.of(order1, order2));

        ZerodhaOrdersResponse response = zerodhaOrdersService.fetchAndStoreOrders();

        assertEquals(2, response.count());
        assertEquals(2, response.transactions().size());
        assertEquals(2, transactionStore.size());
    }

    @Test
    void fetchAndStoreOrders_reimportingUpdatesExistingTransaction() {
        ZerodhaOrderItem order = buildOrderItem("ORDER001", "INFY", "OPEN", "BUY");
        when(zerodhaOrdersClient.fetchOrders()).thenReturn(List.of(order));
        zerodhaOrdersService.fetchAndStoreOrders();

        // Re-import with updated status
        ZerodhaOrderItem updated = buildOrderItem("ORDER001", "INFY", "COMPLETE", "BUY");
        when(zerodhaOrdersClient.fetchOrders()).thenReturn(List.of(updated));
        zerodhaOrdersService.fetchAndStoreOrders();

        // Should have only 1 entry in the store (upsert behaviour)
        assertEquals(1, transactionStore.size());
        Transaction stored = transactionStore.findByOrderId("ORDER001");
        assertNotNull(stored);
        assertEquals("COMPLETE", stored.getStatus());
    }

    // -------------------------------------------------------------------------
    // fetchAndStoreOrderHistory
    // -------------------------------------------------------------------------

    @Test
    void fetchAndStoreOrderHistory_returnsEmptyResponseWhenNoHistory() {
        when(zerodhaOrdersClient.fetchOrderHistory("ORDER001")).thenReturn(List.of());

        ZerodhaOrdersResponse response = zerodhaOrdersService.fetchAndStoreOrderHistory("ORDER001");

        assertNotNull(response);
        assertEquals(0, response.count());
        assertTrue(response.transactions().isEmpty());
    }

    @Test
    void fetchAndStoreOrderHistory_storesEachSnapshotSeparately() {
        ZerodhaOrderItem snap0 = buildOrderItem("ORDER001", "INFY", "OPEN", "BUY");
        ZerodhaOrderItem snap1 = buildOrderItem("ORDER001", "INFY", "COMPLETE", "BUY");
        when(zerodhaOrdersClient.fetchOrderHistory("ORDER001")).thenReturn(List.of(snap0, snap1));

        ZerodhaOrdersResponse response = zerodhaOrdersService.fetchAndStoreOrderHistory("ORDER001");

        assertEquals(2, response.count());
        // Both snapshots should be stored
        assertEquals(2, transactionStore.size());
    }

    @Test
    void fetchAndStoreOrderHistory_snapshotIdsAreComposite() {
        ZerodhaOrderItem snap = buildOrderItem("ORDER001", "INFY", "COMPLETE", "BUY");
        when(zerodhaOrdersClient.fetchOrderHistory("ORDER001")).thenReturn(List.of(snap));

        ZerodhaOrdersResponse response = zerodhaOrdersService.fetchAndStoreOrderHistory("ORDER001");

        Transaction tx = response.transactions().get(0);
        assertEquals("ORDER001_0", tx.getId());
    }

    // -------------------------------------------------------------------------
    // getAllTransactions
    // -------------------------------------------------------------------------

    @Test
    void getAllTransactions_returnsAllStoredTransactions() {
        ZerodhaOrderItem order1 = buildOrderItem("ORDER001", "INFY", "COMPLETE", "BUY");
        ZerodhaOrderItem order2 = buildOrderItem("ORDER002", "TCS", "OPEN", "SELL");
        when(zerodhaOrdersClient.fetchOrders()).thenReturn(List.of(order1, order2));
        zerodhaOrdersService.fetchAndStoreOrders();

        List<Transaction> all = zerodhaOrdersService.getAllTransactions();

        assertEquals(2, all.size());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ZerodhaOrderItem buildOrderItem(String orderId, String symbol, String status, String txType) {
        ZerodhaOrderItem item = new ZerodhaOrderItem();
        item.setOrderId(orderId);
        item.setTradingSymbol(symbol);
        item.setExchange("NSE");
        item.setOrderVariety("regular");
        item.setOrderType("LIMIT");
        item.setTransactionType(txType);
        item.setProduct("CNC");
        item.setStatus(status);
        item.setQuantity("10");
        item.setFilledQuantity("10");
        item.setPendingQuantity("0");
        item.setPrice("1500.00");
        item.setAveragePrice("1500.00");
        item.setValidity("DAY");
        item.setOrderTimestamp(new Date());
        return item;
    }
}
