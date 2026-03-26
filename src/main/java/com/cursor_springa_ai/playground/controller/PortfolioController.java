package com.cursor_springa_ai.playground.controller;

import com.cursor_springa_ai.playground.dto.AddHoldingRequest;
import com.cursor_springa_ai.playground.dto.CreatePortfolioRequest;
import com.cursor_springa_ai.playground.dto.PortfolioAnalysisResponse;
import com.cursor_springa_ai.playground.dto.ZerodhaImportRequest;
import com.cursor_springa_ai.playground.dto.ZerodhaImportResponse;
import com.cursor_springa_ai.playground.model.Holding;
import com.cursor_springa_ai.playground.model.Portfolio;
import com.cursor_springa_ai.playground.service.PortfolioAnalysisService;
import com.cursor_springa_ai.playground.service.PortfolioService;
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

    public PortfolioController(
            PortfolioService portfolioService,
            PortfolioAnalysisService portfolioAnalysisService,
            ZerodhaImportService zerodhaImportService
    ) {
        this.portfolioService = portfolioService;
        this.portfolioAnalysisService = portfolioAnalysisService;
        this.zerodhaImportService = zerodhaImportService;
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

    @GetMapping("/{portfolioId}")
    public Portfolio getPortfolio(@PathVariable String portfolioId) {
        return portfolioService.getPortfolio(portfolioId);
    }

    @PutMapping("/{portfolioId}/holdings")
    public Holding addOrUpdateHolding(
            @PathVariable String portfolioId,
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
        return portfolioService.addOrUpdateHolding(portfolioId, holding);
    }

    @DeleteMapping("/{portfolioId}/holdings/{symbol}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeHolding(
            @PathVariable String portfolioId,
            @PathVariable String symbol
    ) {
        portfolioService.removeHolding(portfolioId, symbol);
    }

    @GetMapping("/{portfolioId}/analysis")
    public PortfolioAnalysisResponse analyzePortfolio(@PathVariable String portfolioId) {
        return portfolioAnalysisService.analyzePortfolio(portfolioId);
    }

    @PostMapping("/{portfolioId}/holdings/import/zerodha")
    public ZerodhaImportResponse importFromZerodha(@PathVariable String portfolioId) {
        return zerodhaImportService.importHoldings(portfolioId);
    }

    @PostMapping("/holdings/import/zerodha")
    public ZerodhaImportResponse importFromZerodhaFirstTime(@RequestBody ZerodhaImportRequest request) {
        return zerodhaImportService.importHoldingsWithAutoCreate(
                request.portfolioId(),
                request.ownerName()
        );
    }
}
