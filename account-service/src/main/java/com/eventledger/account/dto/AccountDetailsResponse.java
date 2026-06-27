package com.eventledger.account.dto;

import java.math.BigDecimal;
import java.util.List;

public record AccountDetailsResponse(
    String accountId,
    BigDecimal balance,
    List<TransactionResponse> recentTransactions
) {}
