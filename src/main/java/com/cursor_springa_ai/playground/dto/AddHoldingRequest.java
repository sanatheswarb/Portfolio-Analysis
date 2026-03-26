package com.cursor_springa_ai.playground.dto;

import com.cursor_springa_ai.playground.model.AssetType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AddHoldingRequest(
        @NotBlank(message = "Symbol is required")
        String symbol,
        @NotBlank(message = "Exchange is required")
        String exchange,
        @NotNull(message = "Asset type is required")
        AssetType assetType,
        @NotNull(message = "Quantity is required")
        @DecimalMin(value = "0.000001", message = "Quantity must be > 0")
        BigDecimal quantity,
        @NotNull(message = "Average buy price is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "Average buy price must be > 0")
        BigDecimal averageBuyPrice
) {
}
