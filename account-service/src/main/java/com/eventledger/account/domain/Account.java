package com.eventledger.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @Column(name = "account_id")
    private String accountId;

    @Column(name = "balance", nullable = false)
    private BigDecimal balance;

    public Account() {
    }

    public Account(String accountId, BigDecimal balance) {
        this.accountId = accountId;
        this.balance = balance;
    }

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
}
