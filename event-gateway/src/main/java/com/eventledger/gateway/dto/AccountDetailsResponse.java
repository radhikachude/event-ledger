package com.eventledger.gateway.dto;

import java.math.BigDecimal;
import java.util.List;

public record AccountDetailsResponse(
    String accountId,
    BigDecimal balance,
    List<TransactionResponse> recentTransactions
) {}
