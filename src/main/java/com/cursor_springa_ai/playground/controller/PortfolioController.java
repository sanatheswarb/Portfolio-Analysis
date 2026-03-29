package com.cursor_springa_ai.playground.controller;

import com.cursor_springa_ai.playground.dto.AddHoldingRequest;
import com.cursor_springa_ai.playground.dto.CreatePortfolioRequest;
import com.cursor_springa_ai.playground.dto.PortfolioAnalysisResponse;
import com.cursor_springa_ai.playground.dto.ZerodhaImportResponse;
import com.cursor_springa_ai.playground.model.Holding;
import com.cursor_springa_ai.playground.model.Portfolio;
import com.cursor_springa_ai.playground.model.User;
import com.cursor_springa_ai.playground.service.PortfolioAnalysisService;
import com.cursor_springa_ai.playground.service.PortfolioService;
import com.cursor_springa_ai.playground.service.ZerodhaAuthService;
import com.cursor_springa_ai.playground.service.ZerodhaImportService;
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Portfolio createPortfolio(@Valid @RequestBody CreatePortfolioRequest request) {
        return portfolioService.createPortfolio(request.ownerName());
    }

    @GetMapping
    public List<Portfolio> getAllPortfolios() {
        return portfolioService.getAllPortfolios();
    }

    @GetMapping("/me")
    public Portfolio getPortfolio() {
        return portfolioService.getPortfolio(requireAuthenticatedUser());
    }

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

    @DeleteMapping("/holdings/{symbol}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeHolding(
            @PathVariable String symbol
    ) {
        portfolioService.removeHolding(requireAuthenticatedUser(), symbol);
    }

    @GetMapping("/analysis")
    public PortfolioAnalysisResponse analyzePortfolio() {
        return portfolioAnalysisService.analyzeCurrentUserPortfolio();
    }

    @PostMapping("/holdings/import/zerodha")
    public ZerodhaImportResponse importFromZerodha() {
        return zerodhaImportService.importHoldings();
    }

    private User requireAuthenticatedUser() {
        User currentUser = zerodhaAuthService.getCurrentUser();
        if (currentUser == null) {
            throw new IllegalStateException("No authenticated Zerodha user found. Please complete login first.");
        }
        return currentUser;
    }
}
