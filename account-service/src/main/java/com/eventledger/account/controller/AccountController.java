package com.eventledger.account.controller;

import com.eventledger.account.dto.*;
import com.eventledger.account.service.AccountService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

@RestController
public class AccountController {

    private static final Logger log = LoggerFactory.getLogger(AccountController.class);

    private final AccountService accountService;
    private final DataSource dataSource;
    private final Counter transactionCounter;

    public AccountController(AccountService accountService, DataSource dataSource, MeterRegistry meterRegistry) {
        this.accountService = accountService;
        this.dataSource = dataSource;
        this.transactionCounter = Counter.builder("account.transactions.applied")
                .description("Total number of transactions applied in Account Service")
                .register(meterRegistry);
    }

    @PostMapping("/accounts/{accountId}/transactions")
    public ResponseEntity<Void> applyTransaction(@PathVariable String accountId, @RequestBody TransactionRequest request) {
        log.info("Received transaction application request for account: {}", accountId);
        transactionCounter.increment();
        accountService.applyTransaction(accountId, request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/accounts/{accountId}/balance")
    public ResponseEntity<AccountBalanceResponse> getBalance(@PathVariable String accountId) {
        log.info("Received balance query request for account: {}", accountId);
        AccountBalanceResponse response = accountService.getBalance(accountId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/accounts/{accountId}")
    public ResponseEntity<AccountDetailsResponse> getAccountDetails(@PathVariable String accountId) {
        log.info("Received details query request for account: {}", accountId);
        AccountDetailsResponse response = accountService.getAccountDetails(accountId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "UP");
        status.put("service", "account-service");

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
