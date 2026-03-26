package com.cursor_springa_ai.playground.controller;

import com.cursor_springa_ai.playground.dto.ZerodhaLoginUrlResponse;
import com.cursor_springa_ai.playground.dto.ZerodhaSessionResponseDto;
import com.cursor_springa_ai.playground.dto.ZerodhaSessionStatusResponse;
import com.cursor_springa_ai.playground.service.ZerodhaAuthService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/zerodha")
public class ZerodhaAuthController {

    private final ZerodhaAuthService zerodhaAuthService;

    public ZerodhaAuthController(ZerodhaAuthService zerodhaAuthService) {
        this.zerodhaAuthService = zerodhaAuthService;
    }

    @GetMapping("/login-url")
    public ZerodhaLoginUrlResponse getLoginUrl() {
        return new ZerodhaLoginUrlResponse(zerodhaAuthService.buildLoginUrl());
    }

    @GetMapping("/session")
    public ZerodhaSessionStatusResponse sessionStatus() {
        boolean ok = zerodhaAuthService.hasActiveSession();
        String hint = ok
                ? "Session is active in this JVM. Call import on the same host/port without restarting the app."
                : "No session. Open login-url, complete login, then ensure the browser redirects to this server callback URL.";
        return new ZerodhaSessionStatusResponse(ok, hint);
    }

    @GetMapping("/callback")
    public ZerodhaSessionResponseDto callback(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "request_token", required = false) String requestToken,
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
}
