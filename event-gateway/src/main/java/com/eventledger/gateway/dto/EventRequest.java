package com.eventledger.gateway.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record EventRequest(
    @NotBlank(message = "eventId is required")
    String eventId,

    @NotBlank(message = "accountId is required")
    String accountId,

    @NotBlank(message = "type is required")
    @Pattern(regexp = "^(CREDIT|DEBIT)$", message = "type must be CREDIT or DEBIT")
    String type,

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.00", inclusive = false, message = "amount must be greater than 0")
    BigDecimal amount,

    @NotBlank(message = "currency is required")
    String currency,

    @NotNull(message = "eventTimestamp is required")
    Instant eventTimestamp,

    Map<String, Object> metadata
) {}
