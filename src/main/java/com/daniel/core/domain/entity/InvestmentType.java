package com.daniel.core.domain.entity;

public record InvestmentType(long id, String name) {
    @Override
    public String toString() {
        return name();
    }
}
