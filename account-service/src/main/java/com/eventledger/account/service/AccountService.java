package com.eventledger.account.service;

import com.eventledger.account.domain.Account;
import com.eventledger.account.domain.Transaction;
import com.eventledger.account.dto.*;
import com.eventledger.account.repository.AccountRepository;
import com.eventledger.account.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public AccountService(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public void applyTransaction(String accountId, TransactionRequest request) {
        log.info("Checking idempotency in Account Service for eventId: {}", request.eventId());
        if (transactionRepository.existsByEventId(request.eventId())) {
            log.info("Transaction for eventId {} has already been applied. Skipping.", request.eventId());
            return;
        }

        log.info("Applying transaction type {} amount {} to account {}", request.type(), request.amount(), accountId);
        
        // Find or create account
        Account account = accountRepository.findById(accountId)
                .orElseGet(() -> {
                    log.info("Account {} does not exist. Creating default account.", accountId);
                    return accountRepository.save(new Account(accountId, BigDecimal.ZERO));
                });

        // Calculate and update balance
        BigDecimal currentBalance = account.getBalance();
        BigDecimal newBalance;
        if ("CREDIT".equalsIgnoreCase(request.type())) {
            newBalance = currentBalance.add(request.amount());
        } else if ("DEBIT".equalsIgnoreCase(request.type())) {
            newBalance = currentBalance.subtract(request.amount());
        } else {
            throw new IllegalArgumentException("Unknown transaction type: " + request.type());
        }

        account.setBalance(newBalance);
        accountRepository.save(account);

        // Record transaction
        Transaction transaction = new Transaction(
                accountId,
                request.eventId(),
                request.type(),
                request.amount(),
                request.currency(),
                request.timestamp()
        );
        transactionRepository.save(transaction);
        log.info("Transaction saved. Account {} new balance is {}", accountId, newBalance);
    }

    @Transactional(readOnly = true)
    public AccountBalanceResponse getBalance(String accountId) {
        log.info("Fetching balance for account: {}", accountId);
        
        // Verify account exists
        if (!accountRepository.existsById(accountId)) {
            log.info("Account {} not found. Returning zero balance.", accountId);
            return new AccountBalanceResponse(accountId, BigDecimal.ZERO);
        }

        // Net balance is strictly the sum of CREDITs minus the sum of DEBITs
        List<Transaction> transactions = transactionRepository.findByAccountIdOrderByTimestampAsc(accountId);
        BigDecimal calculatedBalance = calculateNetBalance(transactions);
        
        return new AccountBalanceResponse(accountId, calculatedBalance);
    }

    @Transactional(readOnly = true)
    public AccountDetailsResponse getAccountDetails(String accountId) {
        log.info("Fetching details for account: {}", accountId);

        List<Transaction> transactions = transactionRepository.findByAccountIdOrderByTimestampAsc(accountId);
        BigDecimal calculatedBalance = calculateNetBalance(transactions);

        List<TransactionResponse> recentTransactions = transactions.stream()
                .map(tx -> new TransactionResponse(
                        tx.getId(),
                        tx.getEventId(),
                        tx.getType(),
                        tx.getAmount(),
                        tx.getCurrency(),
                        tx.getTimestamp()
                ))
                .collect(Collectors.toList());

        return new AccountDetailsResponse(accountId, calculatedBalance, recentTransactions);
    }

    private BigDecimal calculateNetBalance(List<Transaction> transactions) {
        BigDecimal balance = BigDecimal.ZERO;
        for (Transaction tx : transactions) {
            if ("CREDIT".equalsIgnoreCase(tx.getType())) {
                balance = balance.add(tx.getAmount());
            } else if ("DEBIT".equalsIgnoreCase(tx.getType())) {
                balance = balance.subtract(tx.getAmount());
            }
        }
        return balance;
    }
}
