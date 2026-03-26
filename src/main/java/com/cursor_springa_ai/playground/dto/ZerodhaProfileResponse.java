package com.cursor_springa_ai.playground.dto;

import java.util.List;

/**
 * Response DTO for the authenticated user's Zerodha profile.
 * Maps fields from {@link com.zerodhatech.models.Profile}.
 * FREE API – accessible with developer-console registration and OAuth session only.
 */
public record ZerodhaProfileResponse(
        String userType,
        String email,
        String userName,
        String userShortname,
        String broker,
        List<String> exchanges,
        List<String> products,
        List<String> orderTypes,
        String avatarUrl
) {}
