package com.daniel.ui.components;

import com.daniel.util.Money;
import javafx.application.Platform;
import javafx.scene.control.TableCell;
import javafx.scene.control.TextField;

public final class MoneyEditingCell<S> extends TableCell<S, Number> {

    private final TextField field = new TextField();
    private boolean committing;

    public MoneyEditingCell() {
        field.setTextFormatter(Money.currencyFormatterEditable());
        field.setPromptText("R$ 0,00");
        Money.applyCurrencyFormatOnBlur(field);

        field.setOnAction(e -> commitIfPossible());

        field.focusedProperty().addListener((obs, oldV, focused) -> {
            if (!focused) commitIfPossible();
        });

        field.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case ESCAPE -> cancelEdit();
            }
        });
    }

    private void commitIfPossible() {
        if (committing) return;
        committing = true;
        try {
            long cents = Money.textToCentsOrZero(field.getText());
            Platform.runLater(() -> {
                try {
                    commitEdit(cents / 100.0);
                } finally {
                    committing = false;
                }
            });
        } catch (Exception ex) {
            committing = false;
        }
    }

    @Override public void startEdit() {
        super.startEdit();
        syncFromItem();
        setGraphic(field);
        field.requestFocus();
        field.positionCaret(field.getText().length());
    }

    @Override public void cancelEdit() {
        super.cancelEdit();
        syncFromItem();
        setGraphic(field);
    }

    @Override protected void updateItem(Number item, boolean empty) {
        super.updateItem(item, empty);
        if (empty) {
            setGraphic(null);
            return;
        }
        syncFromItem();
        setGraphic(field);
    }

    private void syncFromItem() {
        Number item = getItem();
        long cents = item == null ? 0 : Math.round(item.doubleValue() * 100);
        field.setText(cents == 0 ? "" : Money.centsToCurrencyText(cents));
    }
}
