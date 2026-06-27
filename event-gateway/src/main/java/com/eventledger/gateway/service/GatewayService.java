package com.eventledger.gateway.service;

import com.eventledger.gateway.client.AccountClient;
import com.eventledger.gateway.domain.GatewayEvent;
import com.eventledger.gateway.dto.*;
import com.eventledger.gateway.repository.GatewayEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class GatewayService {

    private static final Logger log = LoggerFactory.getLogger(GatewayService.class);

    private final GatewayEventRepository repository;
    private final AccountClient accountClient;

    public GatewayService(GatewayEventRepository repository, AccountClient accountClient) {
        this.repository = repository;
        this.accountClient = accountClient;
    }

    @Transactional
    public GatewayEvent submitEvent(EventRequest request) {
        Optional<GatewayEvent> existing = repository.findById(request.eventId());
        if (existing.isPresent()) {
            log.info("Idempotency match found for eventId: {}", request.eventId());
            return existing.get();
        }

        TransactionRequest txRequest = new TransactionRequest(
                request.eventId(),
                request.type(),
                request.amount(),
                request.currency(),
                request.eventTimestamp()
        );

        // Call Account Service directly (which is proxy-protected by Circuit Breaker)
        accountClient.applyTransaction(request.accountId(), txRequest);

        GatewayEvent newEvent = new GatewayEvent(
                request.eventId(),
                request.accountId(),
                request.type(),
                request.amount(),
                request.currency(),
                request.eventTimestamp(),
                Instant.now()
        );

        return repository.save(newEvent);
    }

    public AccountBalanceResponse getBalance(String accountId) {
        return accountClient.getBalance(accountId);
    }

    public AccountDetailsResponse getAccountDetails(String accountId) {
        return accountClient.getAccountDetails(accountId);
    }

    public List<GatewayEvent> getEventsForAccount(String accountId) {
        return repository.findByAccountIdOrderByEventTimestampAsc(accountId);
    }

    public Optional<GatewayEvent> getEventById(String eventId) {
        return repository.findById(eventId);
    }
}
