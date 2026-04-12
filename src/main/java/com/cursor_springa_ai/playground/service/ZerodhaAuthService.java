package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.integration.zerodha.KiteConnectClient;
import com.cursor_springa_ai.playground.integration.zerodha.ZerodhaClientException;
import com.cursor_springa_ai.playground.model.entity.User;
import com.cursor_springa_ai.playground.repository.UserRepository;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Zerodha authentication service using the official KiteConnect SDK.
 * Simplified from the previous manual REST-based implementation.
 */
@Service
public class ZerodhaAuthService {

    private static final String BROKER_ZERODHA = "ZERODHA";

    private final KiteConnectClient kiteConnectClient;
    private final UserRepository userRepository;

    public ZerodhaAuthService(KiteConnectClient kiteConnectClient, UserRepository userRepository) {
        this.kiteConnectClient = kiteConnectClient;
        this.userRepository = userRepository;
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
     * Generate session after user completes login and upsert the user record.
     *
     * @param requestToken The request token from the login callback
     */
    public void generateSession(String requestToken) {
        try {
            kiteConnectClient.generateSession(requestToken);
            upsertUser(kiteConnectClient.getUserId());
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

    /**
     * Return the persisted User for the currently active Zerodha session, or null if not
     * authenticated.
     */
    public User getCurrentUser() {
        String zerodhaUserId = kiteConnectClient.getUserId();
        if (zerodhaUserId == null || zerodhaUserId.isBlank()) {
            return null;
        }
        return userRepository.findByBrokerAndBrokerUserId(BROKER_ZERODHA, zerodhaUserId).orElse(null);
    }

    // ------------------------------------------------------------------
    // private helpers
    // ------------------------------------------------------------------

    private void upsertUser(String zerodhaUserId) {
        if (zerodhaUserId == null || zerodhaUserId.isBlank()) {
            return;
        }
        userRepository.findByBrokerAndBrokerUserId(BROKER_ZERODHA, zerodhaUserId)
                .orElseGet(() -> userRepository.save(new User(BROKER_ZERODHA, zerodhaUserId)));
    }
}
