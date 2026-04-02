package com.cursor_springa_ai.playground.controller;

import com.cursor_springa_ai.playground.dto.PortfolioAnalysisResponse;
import com.cursor_springa_ai.playground.dto.UserHoldingDto;
import com.cursor_springa_ai.playground.dto.ZerodhaImportResponse;
import com.cursor_springa_ai.playground.model.User;
import com.cursor_springa_ai.playground.ai.orchestration.PortfolioAnalysisService;
import com.cursor_springa_ai.playground.service.PortfolioService;
import com.cursor_springa_ai.playground.service.ZerodhaAuthService;
import com.cursor_springa_ai.playground.service.ZerodhaImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Portfolio Management", description = "Manage portfolios, holdings, imports, and AI analysis")
@RestController
@RequestMapping("/api/portfolios")
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final PortfolioAnalysisService portfolioAnalysisService;
    private final ZerodhaImportService zerodhaImportService;
    private final ZerodhaAuthService zerodhaAuthService;

    public PortfolioController(
            PortfolioService portfolioService,
            PortfolioAnalysisService portfolioAnalysisService,
            ZerodhaImportService zerodhaImportService,
            ZerodhaAuthService zerodhaAuthService
    ) {
        this.portfolioService = portfolioService;
        this.portfolioAnalysisService = portfolioAnalysisService;
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
        return zerodhaImportService.importHoldings();
    }

    @Operation(summary = "Run AI portfolio analysis",
            description = "Computes portfolio metrics and generates AI-powered investment advice using Ollama LLM.",
            responses = @ApiResponse(responseCode = "200", description = "Analysis result returned"))
    @GetMapping("/analysis")
    public PortfolioAnalysisResponse analyzePortfolio() {
        return portfolioAnalysisService.analyzeCurrentUserPortfolio();
    }

    private User requireAuthenticatedUser() {
        User currentUser = zerodhaAuthService.getCurrentUser();
        if (currentUser == null) {
            throw new IllegalStateException("No authenticated Zerodha user found. Please complete login first.");
        }
        return currentUser;
    }
}
