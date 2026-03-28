package com.cursor_springa_ai.playground.integration.zerodha;

import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Holding;
import com.zerodhatech.models.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Wrapper around the official Zerodha KiteConnect SDK.
 * Simplifies authentication and holdings retrieval.
 */
@Component
public class KiteConnectClient {

    private final String apiKey;
    private final String apiSecret;
    private KiteConnect kiteSdk;
    private String accessToken;
    private String publicToken;
    private String userId;

    public KiteConnectClient(
            @Value("${zerodha.api-key}") String apiKey,
            @Value("${zerodha.api-secret}") String apiSecret
    ) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        initializeKiteSdk();
    }

    /**
     * Initialize the KiteConnect SDK with API key.
     */
    private void initializeKiteSdk() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ZerodhaClientException("Missing zerodha.api-key in application.properties");
        }
        this.kiteSdk = new KiteConnect(apiKey);
    }

    /**
     * Get the login URL that users should open in their browser.
     * 
     * @return Login URL
     */
    public String getLoginUrl() {
        return kiteSdk.getLoginURL();
    }

    /**
     * Exchange request token for access token and session.
     * Call this after user completes login and callback returns request_token.
     * 
     * @param requestToken The request token from the login callback
     * @throws KiteException If session generation fails
     * @throws IOException If network error occurs
     */
    public void generateSession(String requestToken) throws KiteException, IOException {
        if (requestToken == null || requestToken.isBlank()) {
            throw new IllegalArgumentException("request_token is required");
        }
        if (apiSecret == null || apiSecret.isBlank()) {
            throw new ZerodhaClientException("Missing zerodha.api-secret in application.properties");
        }

        try {
            User user = kiteSdk.generateSession(requestToken, apiSecret);
            
            if (user == null || user.accessToken == null || user.accessToken.isBlank()) {
                throw new ZerodhaClientException("Failed to generate session: access token is missing");
            }

            // Store tokens for future API calls
            this.accessToken = user.accessToken;
            this.publicToken = user.publicToken;
            this.userId = user.userId;

            // Set tokens in the SDK for all future calls
            kiteSdk.setAccessToken(user.accessToken);
            kiteSdk.setPublicToken(user.publicToken);
        } catch (ZerodhaClientException ex) {
            throw ex;
        } catch (KiteException ex) {
            throw ex;
        } catch (IOException ex) {
            throw ex;
        }
    }

    /**
     * Check if an active session exists.
     * 
     * @return true if access token is set
     */
    public boolean hasActiveSession() {
        return accessToken != null && !accessToken.isBlank();
    }

    /**
     * Fetch holdings (portfolio) for the authenticated user.
     * 
     * @return List of user's holdings
     * @throws KiteException If holdings retrieval fails
     * @throws IOException If network error occurs
     */
    public List<Holding> getHoldings() throws KiteException, IOException {
        if (!hasActiveSession()) {
            throw new ZerodhaClientException(
                    "No active session. Complete login first: GET /api/zerodha/login-url → callback → generateSession()");
        }

        try {
            return kiteSdk.getHoldings();
        } catch (KiteException ex) {
            String message = ex.getMessage();
            if (message != null && message.contains("403")) {
                throw new ZerodhaClientException(
                        "Access denied fetching holdings. The access token may have expired or is invalid. " +
                        "Fix: Re-login via /api/zerodha/login-url",
                        ex);
            }
            throw ex;
        }
    }

    /**
     * Get the current access token (useful for storing/debugging).
     * 
     * @return Current access token or null if not authenticated
     */
    public String getAccessToken() {
        return accessToken;
    }

    /**
     * Get the current public token.
     * 
     * @return Current public token or null if not authenticated
     */
    public String getPublicToken() {
        return publicToken;
    }

    /**
     * Get the Zerodha client ID (userId) obtained after session generation.
     *
     * @return Zerodha client ID or null if not authenticated
     */
    public String getUserId() {
        return userId;
    }
}
