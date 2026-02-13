package com.daniel.util;

import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;

import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

public final class Money {

    private static final Locale LOCALE_BR = new Locale("pt", "BR");
    private static final NumberFormat BRL = NumberFormat.getCurrencyInstance(LOCALE_BR);

    private static final char DECIMAL_SEP = new DecimalFormatSymbols(LOCALE_BR).getDecimalSeparator();
    private static final char GROUP_SEP = new DecimalFormatSymbols(LOCALE_BR).getGroupingSeparator();

    private static final Pattern ALLOWED = Pattern.compile("[0-9\\sR\\$\\.,]*");

    private Money() {}

    public static long textToCentsOrZero(String input) {
        if (input == null) return 0L;
        String s = input.trim();
        if (s.isEmpty()) return 0L;

        s = s.replace("R$", "").trim();
        s = s.replace(" ", "");

        if (s.indexOf('.') >= 0 && s.indexOf(',') >= 0) {
            s = s.replace(".", "").replace(",", ".");
        } else if (s.indexOf(',') >= 0) {
            s = s.replace(",", ".");
        }

        double v = Double.parseDouble(s);
        return Math.round(v * 100.0);
    }

    public static String centsToCurrencyText(long cents) {
        return BRL.format(cents / 100.0);
    }

    public static String centsToText(long cents) {
        return centsToCurrencyText(cents);
    }

    public static TextFormatter<String> currencyFormatterEditable() {
        UnaryOperator<TextFormatter.Change> filter = change -> {
            String n = change.getControlNewText();
            if (n == null) return change;
            if (!ALLOWED.matcher(n).matches()) return null;
            return change;
        };
        return new TextFormatter<>(filter);
    }

    public static void applyCurrencyFormatOnBlur(TextField field) {
        field.focusedProperty().addListener((obs, was, is) -> {
            if (was && !is) {
                long cents = textToCentsOrZero(field.getText());
                if (cents == 0) {
                    field.setText("");
                } else {
                    field.setText(centsToCurrencyText(cents));
                }
            }
        });
    }

    public static void applyFormatOnBlur(TextField field) {
        applyCurrencyFormatOnBlur(field);
    }
}
