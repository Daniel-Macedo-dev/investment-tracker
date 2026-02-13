package com.daniel.ui.model;

import com.daniel.domain.InvestmentType;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;

public final class InvestmentValueRow {
    private final SimpleObjectProperty<InvestmentType> type = new SimpleObjectProperty<>();
    private final SimpleLongProperty valueCents = new SimpleLongProperty(0);

    public InvestmentValueRow(InvestmentType type, long valueCents) {
        this.type.set(type);
        this.valueCents.set(valueCents);
    }

    public InvestmentType getType() { return type.get(); }
    public SimpleObjectProperty<InvestmentType> typeProperty() { return type; }

    public long getValueCents() { return valueCents.get(); }
    public void setValueCents(long v) { valueCents.set(v); }
    public SimpleLongProperty valueCentsProperty() { return valueCents; }
}
