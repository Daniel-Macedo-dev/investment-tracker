package com.daniel.domain;

import java.time.Instant;

public record Transaction(
        long id,
        Instant createdAt,
        TransactionType type,
        long accountId,
        long amountCents,
        String note
) {}
