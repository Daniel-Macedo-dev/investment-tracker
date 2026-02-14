package com.daniel.core.domain.entity;

import java.time.LocalDate;

public record Flow(
        long id,
        LocalDate date,
        FlowKind fromKind, Long fromInvestmentTypeId,
        FlowKind toKind,   Long toInvestmentTypeId,
        long amountCents,
        String note
) {}
