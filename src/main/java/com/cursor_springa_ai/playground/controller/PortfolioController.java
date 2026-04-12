package com.cursor_springa_ai.playground.controller;

import com.cursor_springa_ai.playground.exception.NotAuthenticatedException;
import com.cursor_springa_ai.playground.dto.ChatRequest;
import com.cursor_springa_ai.playground.dto.ChatResponse;
import com.cursor_springa_ai.playground.dto.PortfolioAnalysisResponse;
import com.cursor_springa_ai.playground.dto.UserHoldingDto;
import com.cursor_springa_ai.playground.dto.zerodha.ZerodhaImportResponse;
import com.cursor_springa_ai.playground.model.entity.User;
import com.cursor_springa_ai.playground.ai.service.PortfolioAnalysisService;
import com.cursor_springa_ai.playground.ai.service.PortfolioChatService;
import com.cursor_springa_ai.playground.service.PortfolioService;
import com.cursor_springa_ai.playground.service.ZerodhaAuthService;
import com.cursor_springa_ai.playground.importer.ZerodhaImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Portfolio Management", description = "Manage portfolios, holdings, imports, and AI analysis")
@RestController
@RequestMapping("/api/portfolios")
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final PortfolioAnalysisService portfolioAnalysisService;
    private final PortfolioChatService portfolioChatService;
    private final ZerodhaImportService zerodhaImportService;
    private final ZerodhaAuthService zerodhaAuthService;

    public PortfolioController(
            PortfolioService portfolioService,
            PortfolioAnalysisService portfolioAnalysisService,
            PortfolioChatService portfolioChatService,
            ZerodhaImportService zerodhaImportService,
            ZerodhaAuthService zerodhaAuthService
    ) {
        this.portfolioService = portfolioService;
        this.portfolioAnalysisService = portfolioAnalysisService;
        this.portfolioChatService = portfolioChatService;
        this.zerodhaImportService = zerodhaImportService;
        this.zerodhaAuthService = zerodhaAuthService;
    }

    @Operation(summary = "Get current user's holdings",
            description = "Returns the current holdings belonging to the authenticated Zerodha user.",
            responses = @ApiResponse(responseCode = "200", description = "Holdings returned"))
    @GetMapping("/me")
    public List<UserHoldingDto> getPortfolio() {
        return portfolioService.getPortfolio(requireAuthenticatedUser());
    }


    @Operation(summary = "Import holdings from Zerodha",
            description = "Fetches live holdings from the authenticated Zerodha account and saves them to the portfolio.",
            responses = @ApiResponse(responseCode = "200", description = "Holdings imported"))
    @PostMapping("/holdings/import/zerodha")
    public ZerodhaImportResponse importFromZerodha() {
        return zerodhaImportService.importHoldings(requireAuthenticatedUser());
    }

    @Operation(summary = "Run AI portfolio analysis",
            description = "Computes portfolio metrics and generates AI-powered investment advice using Ollama LLM.",
            responses = @ApiResponse(responseCode = "200", description = "Analysis result returned"))
    @GetMapping("/analysis")
    public PortfolioAnalysisResponse analyzePortfolio() {
        return portfolioAnalysisService.analyzePortfolio(requireAuthenticatedUser());
    }

    @Operation(summary = "Ask a follow-up portfolio question",
            description = "Uses the latest saved AI portfolio analysis and recent chat history to answer a follow-up question.",
            responses = @ApiResponse(responseCode = "200", description = "Chat answer returned"))
    @PostMapping("/chat")
    public ChatResponse askQuestion(@Valid @RequestBody ChatRequest request) {
        User user = requireAuthenticatedUser();
        return portfolioChatService.askQuestion(user, request.getQuestion());
    }

    private User requireAuthenticatedUser() {
        User currentUser = zerodhaAuthService.getCurrentUser();
        if (currentUser == null) {
            throw new NotAuthenticatedException("No authenticated Zerodha user found. Please complete login first.");
        }
        return currentUser;
    }
}
