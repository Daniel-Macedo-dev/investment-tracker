package com.daniel.util;

import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.function.UnaryOperator;

public final class Money {
    private static final Locale PT_BR = new Locale("pt", "BR");
    private static final NumberFormat NF = NumberFormat.getNumberInstance(PT_BR);

    static {
        NF.setMinimumFractionDigits(2);
        NF.setMaximumFractionDigits(2);
        NF.setGroupingUsed(true);
    }

    private Money() {}

    public static String centsToText(long cents) {
        double v = cents / 100.0;
        return NF.format(v);
    }

    public static long textToCentsOrZero(String input) {
        if (input == null) return 0;
        String s = input.trim();
        if (s.isBlank()) return 0;

        s = s.replace("R$", "").trim();
        s = s.replace(" ", "");

        if (s.contains(",") && s.contains(".")) {
            s = s.replace(".", "").replace(",", ".");
        } else if (s.contains(",")) {
            s = s.replace(",", ".");
        }

        s = s.replaceAll("[^0-9.\\-]", "");
        if (s.isBlank() || s.equals("-") || s.equals(".")) return 0;

        double v = Double.parseDouble(s);
        return Math.round(v * 100.0);
    }

    public static TextFormatter<String> currencyFormatterEditable() {
        UnaryOperator<TextFormatter.Change> filter = change -> {
            String t = change.getControlNewText();
            if (t.isBlank()) return change;

            if (!t.matches("[0-9.,\\-\\sR$]*")) return null;

            String cleaned = t.replace("R$", "").trim();

            if (cleaned.indexOf('-') > 0) return null;
            if (cleaned.chars().filter(ch -> ch == '-').count() > 1) return null;

            return change;
        };

        return new TextFormatter<>(filter);
    }

    public static void applyFormatOnBlur(TextField field) {
        field.focusedProperty().addListener((obs, oldV, focused) -> {
            if (!focused) {
                long cents = textToCentsOrZero(field.getText());
                field.setText(cents == 0 ? "" : centsToText(cents));
            }
        });
    }
}
