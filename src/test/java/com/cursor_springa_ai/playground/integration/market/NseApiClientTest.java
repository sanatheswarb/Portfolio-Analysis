package com.cursor_springa_ai.playground.integration.market;

import com.cursor_springa_ai.playground.dto.StockMetrics;
import com.cursor_springa_ai.playground.integration.market.dto.NseQuoteResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NseApiClientTest {

    @Test
    void fetchMetricsForSymbol_marksEtfSectorWhenIsEtfSecIsTrue() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        NseApiClient client = new NseApiClient(restTemplate, new ObjectMapper());

        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("""
                        {
                          "info": {
                            "symbol": "NIFTYBEES",
                            "companyName": "Nippon India ETF Nifty 50 BeES",
                            "isETFSec": true
                          },
                          "priceInfo": {
                            "lastPrice": 255.26,
                            "weekHighLow": {
                              "max": 302.25,
                              "min": 231.30
                            }
                          },
                          "industryInfo": {
                            "sector": "Financial Services",
                            "industry": "Capital Markets"
                          },
                          "securityInfo": {
                            "issuedSize": 2235366385
                          }
                        }
                        """));

        StockMetrics metrics = client.fetchMetricsForSymbol("NIFTYBEES");

        assertNotNull(metrics);
        assertEquals("ETF", metrics.sector());
    }

    @Test
    void fetchMetricsForSymbol_usesSectorInsteadOfIndustryForNonEtf() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        NseApiClient client = new NseApiClient(restTemplate, new ObjectMapper());

        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("""
                        {
                          "info": {
                            "symbol": "INFY",
                            "companyName": "Infosys Limited",
                            "isETFSec": false
                          },
                          "priceInfo": {
                            "lastPrice": 1500.00,
                            "weekHighLow": {
                              "max": 1900.00,
                              "min": 1100.00
                            }
                          },
                          "industryInfo": {
                            "sector": "Information Technology",
                            "industry": "Computers - Software"
                          },
                          "securityInfo": {
                            "issuedSize": 4140000000
                          }
                        }
                        """));

        StockMetrics metrics = client.fetchMetricsForSymbol("INFY");

        assertNotNull(metrics);
        assertEquals("Information Technology", metrics.sector());
    }

    @Test
    void fetchQuote_encodesSymbolsAndReturnsParsedQuote() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        NseApiClient client = new NseApiClient(restTemplate, new ObjectMapper());

        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("""
                        {
                          "info": {
                            "symbol": "M&M",
                            "companyName": "Mahindra & Mahindra Ltd",
                            "isETFSec": false
                          },
                          "industryInfo": {
                            "sector": "Automobile and Auto Components",
                            "industry": "Passenger Cars & Utility Vehicles"
                          }
                        }
                        """));

        Optional<NseQuoteResponse> quoteOpt = client.fetchQuote("M&M");

        assertTrue(quoteOpt.isPresent());
        assertEquals("M&M", quoteOpt.get().info().symbol());
        assertEquals("Automobile and Auto Components", quoteOpt.get().industryInfo().sector());
        verify(restTemplate).exchange(
                eq("https://www.nseindia.com/api/quote-equity?symbol=M%26M"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)
        );
    }
}