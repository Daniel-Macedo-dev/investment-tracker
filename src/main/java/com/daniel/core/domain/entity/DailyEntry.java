package com.daniel.core.domain.entity;

import java.time.LocalDate;
import java.util.Map;

public record DailyEntry(
        LocalDate date,
        long cashCents,
        Map<InvestmentType, Long> investmentValuesCents
) {
    public Map<Object, Object> investments() {

        return Map.of();
    }
}
