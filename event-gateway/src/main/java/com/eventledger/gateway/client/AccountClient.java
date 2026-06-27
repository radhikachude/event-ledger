package com.eventledger.gateway.client;

import com.eventledger.gateway.dto.AccountBalanceResponse;
import com.eventledger.gateway.dto.AccountDetailsResponse;
import com.eventledger.gateway.dto.TransactionRequest;
import com.eventledger.gateway.exception.AccountServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AccountClient {

    private static final Logger log = LoggerFactory.getLogger(AccountClient.class);
    private final RestClient restClient;

    public AccountClient(RestClient.Builder restClientBuilder,
                         @Value("${account-service.url:http://localhost:8081}") String accountServiceUrl) {
        log.info("Configuring AccountClient to call Account Service at {}", accountServiceUrl);
        this.restClient = restClientBuilder.baseUrl(accountServiceUrl).build();
    }

    @CircuitBreaker(name = "accountService", fallbackMethod = "applyTransactionFallback")
    public void applyTransaction(String accountId, TransactionRequest request) {
        log.info("Sending transaction to Account Service for account {}", accountId);
        restClient.post()
                .uri("/accounts/{accountId}/transactions", accountId)
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {
                    log.error("Error from Account Service: status {}", resp.getStatusCode());
                    throw new RuntimeException("Account service error status " + resp.getStatusCode());
                })
                .toBodilessEntity();
    }

    @CircuitBreaker(name = "accountService", fallbackMethod = "getBalanceFallback")
    public AccountBalanceResponse getBalance(String accountId) {
        log.info("Fetching balance from Account Service for account {}", accountId);
        return restClient.get()
                .uri("/accounts/{accountId}/balance", accountId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {
                    log.error("Error fetching balance: status {}", resp.getStatusCode());
                    throw new RuntimeException("Account service error status " + resp.getStatusCode());
                })
                .body(AccountBalanceResponse.class);
    }

    @CircuitBreaker(name = "accountService", fallbackMethod = "getAccountDetailsFallback")
    public AccountDetailsResponse getAccountDetails(String accountId) {
        log.info("Fetching details from Account Service for account {}", accountId);
        return restClient.get()
                .uri("/accounts/{accountId}", accountId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {
                    log.error("Error fetching account details: status {}", resp.getStatusCode());
                    throw new RuntimeException("Account service error status " + resp.getStatusCode());
                })
                .body(AccountDetailsResponse.class);
    }

    // Fallbacks
    public void applyTransactionFallback(String accountId, TransactionRequest request, Throwable t) {
        log.error("Circuit Breaker Fallback triggered for applyTransaction. Error: {}", t.getMessage());
        throw new AccountServiceUnavailableException("Account service is currently unavailable. Transaction could not be processed.", t);
    }

    public AccountBalanceResponse getBalanceFallback(String accountId, Throwable t) {
        log.error("Circuit Breaker Fallback triggered for getBalance. Error: {}", t.getMessage());
        throw new AccountServiceUnavailableException("Account service is unreachable. Unable to fetch balance.", t);
    }

    public AccountDetailsResponse getAccountDetailsFallback(String accountId, Throwable t) {
        log.error("Circuit Breaker Fallback triggered for getAccountDetails. Error: {}", t.getMessage());
        throw new AccountServiceUnavailableException("Account service is unreachable. Unable to fetch account details.", t);
    }
}
