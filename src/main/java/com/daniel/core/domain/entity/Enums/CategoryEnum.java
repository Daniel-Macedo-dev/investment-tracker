package com.daniel.core.domain.entity;

public enum CategoryEnum {
    RENDA_FIXA("Renda Fixa", "#3b82f6"),
    RENDA_VARIAVEL("Renda Variável", "#22c55e"),
    FUNDOS("Fundos de Investimento", "#f59e0b"),
    PREVIDENCIA("Previdência", "#8b5cf6"),
    CRIPTOMOEDAS("Criptomoedas", "#ec4899"),
    OUTROS("Outros", "#6b7280");

    private final String displayName;
    private final String color;

    CategoryEnum(String displayName, String color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String getDisplayName() { return displayName; }
    public String getColor() { return color; }
}

