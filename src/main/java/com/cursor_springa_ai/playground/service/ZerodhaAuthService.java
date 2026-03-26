package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.integration.zerodha.KiteConnectClient;
import com.cursor_springa_ai.playground.integration.zerodha.ZerodhaClientException;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Zerodha authentication service using the official KiteConnect SDK.
 * Simplified from the previous manual REST-based implementation.
 */
@Service
public class ZerodhaAuthService {

    private final KiteConnectClient kiteConnectClient;

    public ZerodhaAuthService(KiteConnectClient kiteConnectClient) {
        this.kiteConnectClient = kiteConnectClient;
    }

    /**
     * Get the login URL for user authentication.
     * 
     * @return Zerodha login URL
     */
    public String buildLoginUrl() {
        return kiteConnectClient.getLoginUrl();
    }

    /**
     * Generate session after user completes login.
     * 
     * @param requestToken The request token from the login callback
     */
    public void generateSession(String requestToken) {
        try {
            kiteConnectClient.generateSession(requestToken);
        } catch (ZerodhaClientException ex) {
            throw ex;
        } catch (KiteException ex) {
            throw new ZerodhaClientException("Failed to generate Zerodha session: " + ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new ZerodhaClientException("Network error during Zerodha authentication: " + ex.getMessage(), ex);
        }
    }

    /**
     * Check if an active session exists.
     * 
     * @return true if user is authenticated
     */
    public boolean hasActiveSession() {
        return kiteConnectClient.hasActiveSession();
    }
}
