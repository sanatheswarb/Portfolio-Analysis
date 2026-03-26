package com.cursor_springa_ai.playground.dto;

import jakarta.validation.constraints.NotBlank;

public record CreatePortfolioRequest(
        @NotBlank(message = "Owner name is required")
        String ownerName
) {
}
