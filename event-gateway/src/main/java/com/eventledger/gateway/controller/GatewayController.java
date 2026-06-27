package com.eventledger.gateway.controller;

import com.eventledger.gateway.domain.GatewayEvent;
import com.eventledger.gateway.dto.AccountBalanceResponse;
import com.eventledger.gateway.dto.AccountDetailsResponse;
import com.eventledger.gateway.dto.EventRequest;
import com.eventledger.gateway.service.GatewayService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class GatewayController {

    private static final Logger log = LoggerFactory.getLogger(GatewayController.class);

    private final GatewayService gatewayService;
    private final DataSource dataSource;
    private final Counter requestCounter;

    public GatewayController(GatewayService gatewayService, DataSource dataSource, MeterRegistry meterRegistry) {
        this.gatewayService = gatewayService;
        this.dataSource = dataSource;
        this.requestCounter = Counter.builder("gateway.events.received")
                .description("Total number of events received by gateway")
                .register(meterRegistry);
    }

    @PostMapping("/events")
    public ResponseEntity<GatewayEvent> submitEvent(@Valid @RequestBody EventRequest request) {
        log.info("Received event submission: {}", request.eventId());
        requestCounter.increment();
        
        // Check if event already exists before submitting
        boolean existsBefore = gatewayService.getEventById(request.eventId()).isPresent();
        GatewayEvent result = gatewayService.submitEvent(request);
        
        if (existsBefore) {
            log.info("Returning 200 OK for duplicate event: {}", request.eventId());
            return ResponseEntity.ok(result);
        } else {
            log.info("Returning 201 Created for new event: {}", request.eventId());
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        }
    }

    @GetMapping("/events/{id}")
    public ResponseEntity<GatewayEvent> getEventById(@PathVariable String id) {
        log.info("Retrieving event by ID: {}", id);
        return gatewayService.getEventById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/events")
    public ResponseEntity<List<GatewayEvent>> getEvents(@RequestParam("account") String accountId) {
        log.info("Listing events for account: {}", accountId);
        List<GatewayEvent> events = gatewayService.getEventsForAccount(accountId);
        return ResponseEntity.ok(events);
    }

    @GetMapping("/accounts/{accountId}/balance")
    public ResponseEntity<AccountBalanceResponse> getAccountBalance(@PathVariable String accountId) {
        log.info("Routing balance query for account: {}", accountId);
        AccountBalanceResponse balance = gatewayService.getBalance(accountId);
        return ResponseEntity.ok(balance);
    }

    @GetMapping("/accounts/{accountId}")
    public ResponseEntity<AccountDetailsResponse> getAccountDetails(@PathVariable String accountId) {
        log.info("Routing account details query for account: {}", accountId);
        AccountDetailsResponse details = gatewayService.getAccountDetails(accountId);
        return ResponseEntity.ok(details);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "UP");
        status.put("service", "event-gateway-api");

        try (Connection conn = dataSource.getConnection()) {
            status.put("database", "UP");
            status.put("databaseDetails", conn.getMetaData().getDatabaseProductName() + " " + conn.getMetaData().getDatabaseProductVersion());
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            status.put("status", "DOWN");
            status.put("database", "DOWN");
            status.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(status);
        }
    }
}
