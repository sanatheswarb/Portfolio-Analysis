package com.cursor_springa_ai.playground.integration.market;

import com.cursor_springa_ai.playground.dto.StockMetrics;
import com.cursor_springa_ai.playground.integration.market.dto.NseQuoteResponse;
import com.cursor_springa_ai.playground.integration.zerodha.dto.ZerodhaHoldingItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@Service
public class NseApiClient {

    private static final Logger logger = Logger.getLogger(NseApiClient.class.getName());
    private static final String NSE_API_URL = "https://www.nseindia.com/api/quote-equity?symbol=";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public NseApiClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Fetch metrics for multiple holdings from NSE API.
     * NSE API requires individual calls per symbol (no batch support).
     */
    public Map<String, StockMetrics> fetchMetricsForHoldings(List<ZerodhaHoldingItem> holdings) {
        Map<String, StockMetrics> result = new HashMap<>();

        if (holdings == null || holdings.isEmpty()) {
            return result;
        }

        for (ZerodhaHoldingItem holding : holdings) {
            String symbol = holding.getTradingSymbol();
            if (symbol != null && !symbol.isBlank()) {
                try {
                    StockMetrics metrics = fetchMetricsForSymbol(symbol);
                    if (metrics != null) {
                        result.put(symbol, metrics);
                    }
                } catch (Exception e) {
                    logger.warning("Failed to fetch NSE metrics for " + symbol + ": " + e.getMessage());
                }
            }
        }

        return result;
    }

    /**
     * Fetch metrics for a single symbol from NSE API.
     * API: https://www.nseindia.com/api/quote-equity?symbol=INFY
     */
    public StockMetrics fetchMetricsForSymbol(String symbol) {
        try {
            logger.info("Fetching NSE metrics for symbol: " + symbol);

            String url = NSE_API_URL + symbol;

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            headers.set("Accept", "application/json");
            headers.set("Referer", "https://www.nseindia.com/");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                NseQuoteResponse nseResponse = objectMapper.readValue(response.getBody(), NseQuoteResponse.class);

                if (nseResponse != null) {
                    BigDecimal pe = null;
                    if (nseResponse.metadata() != null && nseResponse.metadata().pdSymbolPe() != null) {
                        pe = BigDecimal.valueOf(nseResponse.metadata().pdSymbolPe());
                    }

                    BigDecimal week52High = null;
                    BigDecimal week52Low = null;
                    if (nseResponse.priceInfo() != null && nseResponse.priceInfo().weekHighLow() != null) {
                        if (nseResponse.priceInfo().weekHighLow().max() != null) {
                            week52High = BigDecimal.valueOf(nseResponse.priceInfo().weekHighLow().max());
                        }
                        if (nseResponse.priceInfo().weekHighLow().min() != null) {
                            week52Low = BigDecimal.valueOf(nseResponse.priceInfo().weekHighLow().min());
                        }
                    }

                    String sector = "N/A";
                    if (nseResponse.industryInfo() != null && nseResponse.industryInfo().sector() != null) {
                        sector = nseResponse.industryInfo().sector();
                    }

                    BigDecimal sectorPe = null;
                    if (nseResponse.metadata() != null && nseResponse.metadata().pdSectorPe() != null) {
                        sectorPe = BigDecimal.valueOf(nseResponse.metadata().pdSectorPe());
                    }

                    Long issuedSize = null;
                    if (nseResponse.securityInfo() != null) {
                        issuedSize = nseResponse.securityInfo().issuedSize();
                    }

                    // Market cap type is not directly provided by NSE API.
                    // It can be approximated from the company's market cap (issuedSize * price),
                    // but SEBI thresholds change periodically. Left as N/A for now.
                    StockMetrics metrics = new StockMetrics(
                            symbol,
                            sector,
                            "N/A", // marketCapType: not available from NSE quote API
                            pe,
                            null, // NSE API doesn't provide beta
                            week52High,
                            week52Low,
                            sectorPe,
                            issuedSize,
                            null  // NSE API doesn't provide 200DMA directly
                    );

                    logger.info("Fetched NSE metrics for " + symbol + " | Sector: " + sector +
                            ", PE: " + pe + ", 52WeekHigh: " + week52High + ", 52WeekLow: " + week52Low +
                            ", SectorPE: " + sectorPe + ", IssuedSize: " + issuedSize);

                    return metrics;
                }
            }
        } catch (Exception e) {
            logger.warning("Error fetching NSE metrics for " + symbol + ": " + e.getMessage());
        }

        return null;
    }
}
