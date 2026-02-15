package com.daniel.core.domain.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InvestmentType(
        int id,
        String name,

        String category,
        String liquidity,
        LocalDate investmentDate,
        BigDecimal profitability,
        BigDecimal investedValue
) {
    public InvestmentType(int id, String name) {
        this(id, name, null, null, null, null, null);
    }

    public InvestmentType {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Nome não pode ser vazio");
        }
    }

    public boolean hasFullData() {
        return category != null && liquidity != null &&
                investmentDate != null && profitability != null &&
                investedValue != null;
    }
}
