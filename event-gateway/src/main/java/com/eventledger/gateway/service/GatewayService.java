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

        GatewayEvent newEvent = new GatewayEvent(
                request.eventId(),
                request.accountId(),
                request.type(),
                request.amount(),
                request.currency(),
                request.eventTimestamp(),
                Instant.now(),
                "PENDING"
        );

        // Persist as PENDING first so it is safely saved in our outbox
        GatewayEvent savedEvent = repository.saveAndFlush(newEvent);

        TransactionRequest txRequest = new TransactionRequest(
                request.eventId(),
                request.type(),
                request.amount(),
                request.currency(),
                request.eventTimestamp()
        );

        try {
            // Attempt to forward transaction to Account Service
            accountClient.applyTransaction(request.accountId(), txRequest);
            
            // If successful, update status to PROCESSED
            savedEvent.setStatus("PROCESSED");
            repository.save(savedEvent);
            log.info("Successfully processed event: {}", request.eventId());
        } catch (Exception e) {
            // Catch exception to prevent transaction rollback; keeps event in PENDING state
            log.warn("Account Service is unreachable. Event {} queued locally in H2 database. Reason: {}", 
                    request.eventId(), e.getMessage());
        }

        return savedEvent;
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
