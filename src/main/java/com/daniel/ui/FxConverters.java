package com.daniel.util;

import com.daniel.domain.InvestmentType;
import javafx.util.StringConverter;

public final class FxConverters {
    private FxConverters() {}

    public static StringConverter<InvestmentType> investmentTypeConverter() {
        return new StringConverter<>() {
            @Override public String toString(InvestmentType t) {
                return t == null ? "" : t.name();
            }
            @Override public InvestmentType fromString(String s) {
                return null;
            }
        };
    }
}
