package com.cursor_springa_ai.playground.controller;

import com.cursor_springa_ai.playground.dto.ZerodhaLoginUrlResponse;
import com.cursor_springa_ai.playground.dto.ZerodhaSessionResponseDto;
import com.cursor_springa_ai.playground.dto.ZerodhaSessionStatusResponse;
import com.cursor_springa_ai.playground.service.ZerodhaAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Tag(name = "Zerodha Authentication", description = "OAuth login flow for Zerodha KiteConnect")
@RestController
@RequestMapping("/api/zerodha")
public class ZerodhaAuthController {

    private final ZerodhaAuthService zerodhaAuthService;

    public ZerodhaAuthController(ZerodhaAuthService zerodhaAuthService) {
        this.zerodhaAuthService = zerodhaAuthService;
    }

    @Operation(summary = "Get Zerodha OAuth login URL",
            description = "Returns the Zerodha OAuth login URL. Open it in a browser to authenticate.",
            responses = @ApiResponse(responseCode = "200", description = "Login URL returned"))
    @GetMapping("/login-url")
    public ZerodhaLoginUrlResponse getLoginUrl() {
        return new ZerodhaLoginUrlResponse(zerodhaAuthService.buildLoginUrl());
    }

    @Operation(summary = "OAuth callback",
            description = "Exchanges the Zerodha request_token for an active session. Zerodha redirects here after login.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Session created successfully"),
                    @ApiResponse(responseCode = "400", description = "Missing or invalid request_token")
            })
    @GetMapping("/callback")
    public ZerodhaSessionResponseDto callback(
            @Parameter(description = "Login status returned by Zerodha (e.g. 'success' or 'error')")
            @RequestParam(value = "status", required = false) String status,
            @Parameter(description = "One-time request token from Zerodha after successful login")
            @RequestParam(value = "request_token", required = false) String requestToken,
            @Parameter(description = "Error message returned by Zerodha when status is 'error'")
            @RequestParam(value = "message", required = false) String kiteMessage
    ) {
        if ("error".equalsIgnoreCase(status != null ? status : "")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Kite login failed: " + (kiteMessage != null ? kiteMessage : "status=error"));
        }
        if (requestToken == null || requestToken.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Missing request_token. After login, Zerodha must redirect to this app's callback URL.");
        }
        zerodhaAuthService.generateSession(requestToken);
        return new ZerodhaSessionResponseDto(
                "Zerodha session created. You can now import holdings.",
                zerodhaAuthService.hasActiveSession()
        );
    }

    @Operation(summary = "Check session status",
            description = "Returns whether a Zerodha session is currently active in this JVM instance.",
            responses = @ApiResponse(responseCode = "200", description = "Session status returned"))
    @GetMapping("/session")
    public ZerodhaSessionStatusResponse sessionStatus() {
        boolean ok = zerodhaAuthService.hasActiveSession();
        String hint = ok
                ? "Session is active in this JVM. Call import on the same host/port without restarting the app."
                : "No session. Open login-url, complete login, then ensure the browser redirects to this server callback URL.";
        return new ZerodhaSessionStatusResponse(ok, hint);
    }
}
