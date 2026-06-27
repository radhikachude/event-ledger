package com.eventledger.gateway.scheduler;

import com.eventledger.gateway.client.AccountClient;
import com.eventledger.gateway.domain.GatewayEvent;
import com.eventledger.gateway.dto.TransactionRequest;
import com.eventledger.gateway.repository.GatewayEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GatewayOutboxScheduler {

    private static final Logger log = LoggerFactory.getLogger(GatewayOutboxScheduler.class);

    private final GatewayEventRepository repository;
    private final AccountClient accountClient;

    public GatewayOutboxScheduler(GatewayEventRepository repository, AccountClient accountClient) {
        this.repository = repository;
        this.accountClient = accountClient;
    }

    @Scheduled(fixedDelayString = "${gateway.outbox.poll-interval-ms:5000}")
    public void processPendingEvents() {
        List<GatewayEvent> pendingEvents = repository.findByStatusOrderByEventTimestampAsc("PENDING");
        
        if (pendingEvents.isEmpty()) {
            return;
        }

        log.info("Found {} pending events in local outbox queue. Retrying transmission...", pendingEvents.size());

        for (GatewayEvent event : pendingEvents) {
            log.info("Processing pending event: {}", event.getEventId());
            
            TransactionRequest txRequest = new TransactionRequest(
                    event.getEventId(),
                    event.getType(),
                    event.getAmount(),
                    event.getCurrency(),
                    event.getEventTimestamp()
            );

            try {
                accountClient.applyTransaction(event.getAccountId(), txRequest);
                
                event.setStatus("PROCESSED");
                repository.save(event);
                log.info("Successfully processed outbox event: {}", event.getEventId());
            } catch (Exception e) {
                log.warn("Failed to process outbox event {}. Aborting batch to maintain chronological ordering. Reason: {}", 
                        event.getEventId(), e.getMessage());
                // Break out of loop to preserve chronological delivery order
                break;
            }
        }
    }
}
