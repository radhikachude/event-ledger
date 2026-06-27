package com.eventledger.gateway.dto;

import java.math.BigDecimal;

public record AccountBalanceResponse(
    String accountId,
    BigDecimal balance
) {}
