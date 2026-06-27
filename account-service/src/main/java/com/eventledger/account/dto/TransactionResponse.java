package com.eventledger.account.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(
    Long id,
    String eventId,
    String type,
    BigDecimal amount,
    String currency,
    Instant timestamp
) {}
