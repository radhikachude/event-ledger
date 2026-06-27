package com.eventledger.account.service;

import com.eventledger.account.domain.Account;
import com.eventledger.account.domain.Transaction;
import com.eventledger.account.dto.AccountBalanceResponse;
import com.eventledger.account.dto.AccountDetailsResponse;
import com.eventledger.account.dto.TransactionRequest;
import com.eventledger.account.repository.AccountRepository;
import com.eventledger.account.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class AccountServiceTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    public void setup() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    public void testApplyTransactionAndCalculatedBalance() {
        String accountId = "acct-999";

        // Apply a CREDIT transaction
        TransactionRequest tx1 = new TransactionRequest("tx-001", "CREDIT", new BigDecimal("100.00"), "USD", Instant.parse("2026-05-15T12:00:00Z"));
        accountService.applyTransaction(accountId, tx1);

        // Apply a DEBIT transaction
        TransactionRequest tx2 = new TransactionRequest("tx-002", "DEBIT", new BigDecimal("30.00"), "USD", Instant.parse("2026-05-15T13:00:00Z"));
        accountService.applyTransaction(accountId, tx2);

        // Apply another CREDIT
        TransactionRequest tx3 = new TransactionRequest("tx-003", "CREDIT", new BigDecimal("50.00"), "USD", Instant.parse("2026-05-15T14:00:00Z"));
        accountService.applyTransaction(accountId, tx3);

        // Balance should be: 100 - 30 + 50 = 120
        AccountBalanceResponse balanceResponse = accountService.getBalance(accountId);
        assertThat(balanceResponse.accountId()).isEqualTo(accountId);
        assertThat(balanceResponse.balance().doubleValue()).isEqualTo(120.00);

        // Details should contain all transactions
        AccountDetailsResponse detailsResponse = accountService.getAccountDetails(accountId);
        assertThat(detailsResponse.recentTransactions()).hasSize(3);
        assertThat(detailsResponse.balance().doubleValue()).isEqualTo(120.00);
    }

    @Test
    public void testApplyTransaction_Idempotency() {
        String accountId = "acct-idemp";
        TransactionRequest tx = new TransactionRequest("tx-dup", "CREDIT", new BigDecimal("100.00"), "USD", Instant.now());

        // Call twice
        accountService.applyTransaction(accountId, tx);
        accountService.applyTransaction(accountId, tx);

        // Verify balance and transaction count
        AccountBalanceResponse balanceResponse = accountService.getBalance(accountId);
        assertThat(balanceResponse.balance().doubleValue()).isEqualTo(100.00); // not 200.00

        List<Transaction> transactions = transactionRepository.findByAccountIdOrderByTimestampAsc(accountId);
        assertThat(transactions).hasSize(1);
    }

    @Test
    public void testBalanceCalculatedFromAllTransactions_OutofOrder() {
        String accountId = "acct-ooo";

        // Apply transactions out of chronological order
        // 1. Transaction at 15:00
        TransactionRequest tx3 = new TransactionRequest("tx-c", "DEBIT", new BigDecimal("40.00"), "USD", Instant.parse("2026-05-15T15:00:00Z"));
        accountService.applyTransaction(accountId, tx3);

        // 2. Transaction at 13:00
        TransactionRequest tx1 = new TransactionRequest("tx-a", "CREDIT", new BigDecimal("100.00"), "USD", Instant.parse("2026-05-15T13:00:00Z"));
        accountService.applyTransaction(accountId, tx1);

        // 3. Transaction at 14:00
        TransactionRequest tx2 = new TransactionRequest("tx-b", "CREDIT", new BigDecimal("50.00"), "USD", Instant.parse("2026-05-15T14:00:00Z"));
        accountService.applyTransaction(accountId, tx2);

        // Net balance: 100 + 50 - 40 = 110
        AccountBalanceResponse balanceResponse = accountService.getBalance(accountId);
        assertThat(balanceResponse.balance().doubleValue()).isEqualTo(110.00);

        // Verify that account details transaction list is ordered chronologically by event timestamp
        AccountDetailsResponse details = accountService.getAccountDetails(accountId);
        assertThat(details.recentTransactions()).hasSize(3);
        assertThat(details.recentTransactions().get(0).eventId()).isEqualTo("tx-a"); // 13:00
        assertThat(details.recentTransactions().get(1).eventId()).isEqualTo("tx-b"); // 14:00
        assertThat(details.recentTransactions().get(2).eventId()).isEqualTo("tx-c"); // 15:00
    }
}
