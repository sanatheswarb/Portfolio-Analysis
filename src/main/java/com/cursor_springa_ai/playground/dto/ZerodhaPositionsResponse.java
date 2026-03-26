package com.cursor_springa_ai.playground.dto;

import java.util.List;

/**
 * Response DTO for the Zerodha positions endpoint.
 * Contains two buckets returned by KiteConnect: day (intraday) and net (net positions).
 * FREE API – accessible with developer-console registration and OAuth session only.
 */
public record ZerodhaPositionsResponse(
        List<ZerodhaPositionItem> day,
        List<ZerodhaPositionItem> net
) {}
