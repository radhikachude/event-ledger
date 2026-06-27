package com.eventledger.account.repository;

import com.eventledger.account.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByAccountIdOrderByTimestampAsc(String accountId);
    boolean existsByEventId(String eventId);
}
