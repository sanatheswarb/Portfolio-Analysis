package com.cursor_springa_ai.playground.dto;

import com.cursor_springa_ai.playground.model.Transaction;

import java.util.List;

/**
 * Response DTO returned after fetching and storing orders from Zerodha.
 *
 * @param count        number of orders imported
 * @param transactions list of stored transactions
 */
public record ZerodhaOrdersResponse(int count, List<Transaction> transactions) {
}
