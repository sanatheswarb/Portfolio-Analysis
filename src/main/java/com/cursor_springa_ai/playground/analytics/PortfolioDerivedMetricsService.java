package com.cursor_springa_ai.playground.analytics;

import com.cursor_springa_ai.playground.ai.reasoning.PortfolioReasoningContext;
import com.cursor_springa_ai.playground.analytics.model.EnrichedHoldingData;
import com.cursor_springa_ai.playground.ai.dto.SectorExposureSummary;
import com.cursor_springa_ai.playground.ai.tools.HoldingClassifier;
import com.cursor_springa_ai.playground.model.PortfolioClassification;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Computes derived portfolio metrics (top holdings, market-cap exposure, sector exposure)
 * from an enriched holding dataset.  Lives in the analytics layer because these are
 * deterministic analytical computations, not AI-tool-specific logic.
 */
@Component
public class PortfolioDerivedMetricsService {

    public List<EnrichedHoldingData> topHoldings(PortfolioReasoningContext context, int limit) {
        return topHoldings(context.enrichedHoldings(), limit);
    }

    public List<EnrichedHoldingData> topHoldings(List<EnrichedHoldingData> holdings, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return holdings.stream()
                .filter(holding -> holding.allocationPercent() != null)
                .sorted(HoldingClassifier.BY_ALLOCATION_DESC)
                .limit(limit)
                .toList();
    }

    public BigDecimal smallCapExposure(PortfolioReasoningContext context) {
        return smallCapExposure(context.classification(), context.enrichedHoldings());
    }

    public BigDecimal smallCapExposure(PortfolioClassification classification, List<EnrichedHoldingData> holdings) {
        if (classification != null && classification.smallCapExposure() != null) {
            return classification.smallCapExposure();
        }
        return marketCapExposure(holdings, "smallcap");
    }

    public BigDecimal marketCapExposure(PortfolioReasoningContext context, String marketCapType) {
        return marketCapExposure(context.enrichedHoldings(), marketCapType);
    }

    public BigDecimal marketCapExposure(List<EnrichedHoldingData> holdings, String marketCapType) {
        return holdings.stream()
                .filter(holding -> holding.marketCapType() != null)
                .filter(holding -> holding.marketCapType().equalsIgnoreCase(marketCapType))
                .map(EnrichedHoldingData::allocationPercent)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<SectorExposureSummary> sectorExposure(PortfolioReasoningContext context, int limit) {
        return sectorExposure(context.enrichedHoldings(), limit);
    }

    public List<SectorExposureSummary> sectorExposure(List<EnrichedHoldingData> holdings, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        Map<String, BigDecimal> exposureBySector = new LinkedHashMap<>();
        Map<String, String> displaySectorByNormalizedKey = new LinkedHashMap<>();
        for (EnrichedHoldingData holding : holdings) {
            if (holding.sector() == null || holding.sector().isBlank() || holding.allocationPercent() == null) {
                continue;
            }
            String normalizedSectorKey = normalizeSectorKey(holding.sector());
            String displaySector = holding.sector().trim();
            displaySectorByNormalizedKey.putIfAbsent(normalizedSectorKey, displaySector);
            exposureBySector.merge(normalizedSectorKey, holding.allocationPercent(), BigDecimal::add);
        }
        return exposureBySector.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> new SectorExposureSummary(displaySectorByNormalizedKey.get(entry.getKey()), entry.getValue()))
                .toList();
    }

    private String normalizeSectorKey(String sector) {
        return sector.trim().toLowerCase(Locale.ROOT);
    }
}
