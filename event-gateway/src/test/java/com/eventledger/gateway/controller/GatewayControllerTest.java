package com.eventledger.gateway.controller;

import com.eventledger.gateway.client.AccountClient;
import com.eventledger.gateway.domain.GatewayEvent;
import com.eventledger.gateway.dto.AccountBalanceResponse;
import com.eventledger.gateway.dto.AccountDetailsResponse;
import com.eventledger.gateway.repository.GatewayEventRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class GatewayControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private GatewayEventRepository eventRepository;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @SpyBean
    private AccountClient accountClient;

    @BeforeEach
    public void setup() {
        eventRepository.deleteAll();
        Mockito.reset(accountClient);
        // Reset the circuit breaker state to prevent test pollution
        if (circuitBreakerRegistry.circuitBreaker("accountService") != null) {
            circuitBreakerRegistry.circuitBreaker("accountService").reset();
        }
    }

    private String getBaseUrl() {
        return "http://localhost:" + port;
    }

    @Test
    public void testSubmitEvent_Success() {
        Mockito.doNothing().when(accountClient).applyTransaction(any(), any());

        String eventId = "evt-001";
        String accountId = "acct-123";
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", eventId);
        payload.put("accountId", accountId);
        payload.put("type", "CREDIT");
        payload.put("amount", 150.00);
        payload.put("currency", "USD");
        payload.put("eventTimestamp", "2026-05-15T14:02:11Z");

        ResponseEntity<GatewayEvent> response = restTemplate.postForEntity(
                getBaseUrl() + "/events",
                payload,
                GatewayEvent.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getEventId()).isEqualTo(eventId);
        assertThat(response.getBody().getStatus()).isEqualTo("PROCESSED");

        Mockito.verify(accountClient, Mockito.times(1)).applyTransaction(eq(accountId), any());
    }

    @Test
    public void testSubmitEvent_ValidationFailure() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", ""); // blank
        payload.put("accountId", "acct-123");
        payload.put("type", "INVALID"); // invalid type
        payload.put("amount", -50.00); // negative amount
        payload.put("currency", "USD");
        payload.put("eventTimestamp", "2026-05-15T14:02:11Z");

        ResponseEntity<String> response = restTemplate.postForEntity(
                getBaseUrl() + "/events",
                payload,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    public void testSubmitEvent_Idempotency() {
        Mockito.doNothing().when(accountClient).applyTransaction(any(), any());

        String eventId = "evt-002";
        String accountId = "acct-123";

        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", eventId);
        payload.put("accountId", accountId);
        payload.put("type", "CREDIT");
        payload.put("amount", 100.00);
        payload.put("currency", "USD");
        payload.put("eventTimestamp", "2026-05-15T14:02:11Z");

        // Submit first time
        ResponseEntity<GatewayEvent> response1 = restTemplate.postForEntity(
                getBaseUrl() + "/events",
                payload,
                GatewayEvent.class
        );
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Submit second time (duplicate)
        ResponseEntity<GatewayEvent> response2 = restTemplate.postForEntity(
                getBaseUrl() + "/events",
                payload,
                GatewayEvent.class
        );

        // Should return 200 OK for duplicate, and the saved event
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response2.getBody()).isNotNull();
        assertThat(response2.getBody().getEventId()).isEqualTo(eventId);

        // Gateway should only call Account Service once
        Mockito.verify(accountClient, Mockito.times(1)).applyTransaction(eq(accountId), any());
    }

    @Test
    public void testGetEvents_ChronologicalSorting() {
        String accountId = "acct-sorting";
        
        GatewayEvent eventLater = new GatewayEvent("evt-later", accountId, "CREDIT", BigDecimal.TEN, "USD", Instant.parse("2026-05-15T15:00:00Z"), Instant.now(), "PROCESSED");
        GatewayEvent eventEarlier = new GatewayEvent("evt-earlier", accountId, "DEBIT", BigDecimal.ONE, "USD", Instant.parse("2026-05-15T14:00:00Z"), Instant.now(), "PROCESSED");
        
        // Save out of chronological order (later first)
        eventRepository.save(eventLater);
        eventRepository.save(eventEarlier);

        ResponseEntity<GatewayEvent[]> response = restTemplate.getForEntity(
                getBaseUrl() + "/events?account=" + accountId,
                GatewayEvent[].class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        GatewayEvent[] events = response.getBody();
        assertThat(events).isNotNull();
        assertThat(events).hasSize(2);
        // The first event in list must be the earlier event timestamp-wise
        assertThat(events[0].getEventId()).isEqualTo("evt-earlier");
        assertThat(events[1].getEventId()).isEqualTo("evt-later");
    }

    @Test
    public void testTracePropagation_HeaderFlow() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Trace-Id", "test-trace-id-12345");
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(null, headers);

        // Query events (reads from local DB, triggers trace filter)
        ResponseEntity<GatewayEvent[]> response = restTemplate.exchange(
                getBaseUrl() + "/events?account=acct-nonexistent",
                HttpMethod.GET,
                entity,
                GatewayEvent[].class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Trace ID should propagate back in the response headers
        List<String> traceHeaders = response.getHeaders().get("X-Trace-Id");
        assertThat(traceHeaders).isNotNull();
        assertThat(traceHeaders.get(0)).isEqualTo("test-trace-id-12345");
    }

    @Test
    public void testResiliency_AccountServiceDown_QueuesLocally() {
        // Setup Account Client mock to throw an exception
        Mockito.doThrow(new RuntimeException("Account Service is unreachable"))
                .when(accountClient).applyTransaction(any(), any());

        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", "evt-fail");
        payload.put("accountId", "acct-123");
        payload.put("type", "CREDIT");
        payload.put("amount", 200.00);
        payload.put("currency", "USD");
        payload.put("eventTimestamp", "2026-05-15T14:02:11Z");

        ResponseEntity<GatewayEvent> response = restTemplate.postForEntity(
                getBaseUrl() + "/events",
                payload,
                GatewayEvent.class
        );

        // Assert 202 Accepted is returned, and status is PENDING (queued locally)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo("PENDING");
    }
}
