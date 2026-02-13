package com.daniel.domain;

public record InvestmentType(long id, String name) {
    @Override
    public String toString() {
        return name();
    }
}


