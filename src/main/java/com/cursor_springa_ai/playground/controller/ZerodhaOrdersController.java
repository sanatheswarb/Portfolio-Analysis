package com.cursor_springa_ai.playground.controller;

import com.cursor_springa_ai.playground.dto.ZerodhaOrdersResponse;
import com.cursor_springa_ai.playground.model.Transaction;
import com.cursor_springa_ai.playground.service.ZerodhaOrdersService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller that exposes Zerodha order APIs.
 *
 * <ul>
 *   <li>{@code GET /api/zerodha/orders} – fetch all orders from Zerodha and
 *       store them in the transaction table.</li>
 *   <li>{@code GET /api/zerodha/orders/{orderId}/history} – fetch the full
 *       history of a single order and store every snapshot in the transaction
 *       table.</li>
 *   <li>{@code GET /api/transactions} – return all transactions currently held
 *       in the in-memory transaction table.</li>
 * </ul>
 */
@RestController
public class ZerodhaOrdersController {

    private final ZerodhaOrdersService zerodhaOrdersService;

    public ZerodhaOrdersController(ZerodhaOrdersService zerodhaOrdersService) {
        this.zerodhaOrdersService = zerodhaOrdersService;
    }

    /**
     * Fetch all open/pending orders from Zerodha and store them in the
     * in-memory transaction table.
     *
     * @return number of orders imported and their details
     */
    @GetMapping("/api/zerodha/orders")
    public ZerodhaOrdersResponse getOrders() {
        return zerodhaOrdersService.fetchAndStoreOrders();
    }

    /**
     * Fetch the complete history of a single Zerodha order and store every
     * status snapshot in the in-memory transaction table.
     *
     * @param orderId Zerodha order ID
     * @return number of history snapshots imported and their details
     */
    @GetMapping("/api/zerodha/orders/{orderId}/history")
    public ZerodhaOrdersResponse getOrderHistory(@PathVariable String orderId) {
        return zerodhaOrdersService.fetchAndStoreOrderHistory(orderId);
    }

    /**
     * Return all transactions currently stored in the in-memory transaction
     * table.
     *
     * @return list of all transactions
     */
    @GetMapping("/api/transactions")
    public List<Transaction> getAllTransactions() {
        return zerodhaOrdersService.getAllTransactions();
    }
}
