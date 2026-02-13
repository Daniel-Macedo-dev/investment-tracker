package com.daniel.ui.components;

import com.daniel.util.Money;
import javafx.scene.control.TableCell;
import javafx.scene.control.TextField;

public final class MoneyEditingCell<S> extends TableCell<S, Number> {
    private final TextField field = new TextField();

    public MoneyEditingCell() {
        field.setTextFormatter(Money.currencyFormatterEditable());
        field.setPromptText("R$ 0,00");
        Money.applyCurrencyFormatOnBlur(field);
        field.setOnAction(e -> commitNow());
        field.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case ESCAPE -> cancelEdit();
            }
        });
    }

    private void commitNow() {
        long cents = Money.textToCentsOrZero(field.getText());
        commitEdit(cents / 100.0);
    }

    @Override public void startEdit() {
        super.startEdit();
        if (getItem() != null) {
            long cents = Math.round(getItem().doubleValue() * 100);
            field.setText(cents == 0 ? "" : Money.centsToCurrencyText(cents));
        } else {
            field.setText("");
        }
        setGraphic(field);
        field.requestFocus();
        field.positionCaret(field.getText().length());
    }

    @Override public void cancelEdit() {
        super.cancelEdit();
        setGraphic(field);
        updateItem(getItem(), false);
    }

    @Override protected void updateItem(Number item, boolean empty) {
        super.updateItem(item, empty);
        if (empty) {
            setGraphic(null);
            return;
        }
        long cents = item == null ? 0 : Math.round(item.doubleValue() * 100);
        field.setText(cents == 0 ? "" : Money.centsToCurrencyText(cents));
        setGraphic(field);
    }
}
