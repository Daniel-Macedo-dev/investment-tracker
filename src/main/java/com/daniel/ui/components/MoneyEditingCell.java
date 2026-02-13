package com.daniel.ui.components;

import com.daniel.util.Money;
import javafx.scene.control.TableCell;
import javafx.scene.control.TextField;

public final class MoneyEditingCell<S> extends TableCell<S, Number> {
    private final TextField field = new TextField();

    public MoneyEditingCell() {
        field.setTextFormatter(Money.currencyFormatter());
        field.setPromptText("0,00");

        field.focusedProperty().addListener((obs, oldV, focused) -> {
            if (!focused) commitEdit(Money.textToCentsOrZero(field.getText()) / 100.0);
        });

        field.setOnAction(e -> commitEdit(Money.textToCentsOrZero(field.getText()) / 100.0));
    }

    @Override
    protected void updateItem(Number item, boolean empty) {
        super.updateItem(item, empty);
        if (empty) {
            setGraphic(null);
            return;
        }
        long cents = item == null ? 0 : Math.round(item.doubleValue() * 100);
        field.setText(cents == 0 ? "" : Money.centsToText(cents));
        setGraphic(field);
    }
}
