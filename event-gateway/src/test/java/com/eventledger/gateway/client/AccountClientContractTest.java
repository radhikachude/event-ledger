package com.eventledger.gateway.client;

import au.com.dius.pact.consumer.dsl.PactBuilder;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import com.eventledger.gateway.dto.TransactionRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;

@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "account-service", port = "8888")
@SpringBootTest
public class AccountClientContractTest {

    @Autowired
    private RestClient.Builder restClientBuilder;

    @Pact(consumer = "event-gateway-api", provider = "account-service")
    public V4Pact createPact(PactBuilder builder) {
        return builder
                .usingLegacyDsl()
                .given("Account Service is ready to apply transactions")
                .uponReceiving("A request to apply a CREDIT transaction of 150.00")
                    .path("/accounts/acct-123/transactions")
                    .method("POST")
                    .headers("Content-Type", "application/json")
                    .body("{\"eventId\":\"evt-001\",\"type\":\"CREDIT\",\"amount\":150.00,\"currency\":\"USD\",\"timestamp\":\"2026-05-15T14:02:11Z\"}")
                .willRespondWith()
                    .status(200)
                .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "createPact")
    public void testApplyTransactionPact() {
        // Use the autowired RestClient.Builder that inherits Spring Boot's default ISO-8601 Jackson converters
        AccountClient client = new AccountClient(restClientBuilder, "http://localhost:8888");

        TransactionRequest request = new TransactionRequest(
                "evt-001",
                "CREDIT",
                new BigDecimal("150.00"),
                "USD",
                Instant.parse("2026-05-15T14:02:11Z")
        );

        // Execute call. This will match the Pact mock server schema since dates are serialized as strings.
        client.applyTransaction("acct-123", request);
    }
}
