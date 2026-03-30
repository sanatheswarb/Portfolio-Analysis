package com.cursor_springa_ai.playground.controller;

import com.cursor_springa_ai.playground.dto.AddHoldingRequest;
import com.cursor_springa_ai.playground.dto.PortfolioAnalysisResponse;
import com.cursor_springa_ai.playground.dto.ZerodhaImportResponse;
import com.cursor_springa_ai.playground.model.Holding;
import com.cursor_springa_ai.playground.model.Portfolio;
import com.cursor_springa_ai.playground.model.User;
import com.cursor_springa_ai.playground.service.PortfolioAnalysisService;
import com.cursor_springa_ai.playground.service.PortfolioService;
import com.cursor_springa_ai.playground.service.ZerodhaAuthService;
import com.cursor_springa_ai.playground.service.ZerodhaImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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

    @Operation(summary = "Create portfolio",
            description = "Creates a new portfolio for the authenticated Zerodha user.",
            responses = @ApiResponse(responseCode = "201", description = "Portfolio created"))
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Portfolio createPortfolio() {
        return portfolioService.createPortfolio(requireAuthenticatedUser());
    }

    @Operation(summary = "List all portfolios",
            description = "Returns all portfolios stored in the database.",
            responses = @ApiResponse(responseCode = "200", description = "List of portfolios"))
    @GetMapping
    public List<Portfolio> getAllPortfolios() {
        return portfolioService.getAllPortfolios();
    }

    @Operation(summary = "Get current user's portfolio",
            description = "Returns the portfolio belonging to the authenticated Zerodha user.",
            responses = @ApiResponse(responseCode = "200", description = "Portfolio returned"))
    @GetMapping("/me")
    public Portfolio getPortfolio() {
        return portfolioService.getPortfolio(requireAuthenticatedUser());
    }

    @Operation(summary = "Add or update a holding",
            description = "Adds a new holding or updates an existing one in the authenticated user's portfolio.",
            responses = @ApiResponse(responseCode = "200", description = "Holding saved"))
    @PutMapping("/holdings")
    public Holding addOrUpdateHolding(
            @Valid @RequestBody AddHoldingRequest request
    ) {
        Holding holding = new Holding(
                request.symbol(),
                request.exchange(),
                request.assetType(),
                request.quantity(),
                request.averageBuyPrice(),
                null,
                null
        );
        return portfolioService.addOrUpdateHolding(requireAuthenticatedUser(), holding);
    }

    @Operation(summary = "Remove a holding",
            description = "Removes a holding by symbol from the authenticated user's portfolio.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Holding removed"),
                    @ApiResponse(responseCode = "404", description = "Holding not found")
            })
    @DeleteMapping("/holdings/{symbol}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeHolding(
            @PathVariable String symbol
    ) {
        portfolioService.removeHolding(requireAuthenticatedUser(), symbol);
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
