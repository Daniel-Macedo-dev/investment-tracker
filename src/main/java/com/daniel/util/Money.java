package com.daniel.util;

import javafx.scene.control.TextFormatter;

import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.function.UnaryOperator;

public final class Money {
    private Money() {}

    public static String centsToText(long cents) {
        long abs = Math.abs(cents);
        long reais = abs / 100;
        long cent = abs % 100;
        String s = reais + "," + (cent < 10 ? "0" + cent : cent);
        return cents < 0 ? "-" + s : s;
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

    public static TextFormatter<String> currencyFormatter() {
        Locale ptBR = new Locale("pt", "BR");
        char decimalSep = new DecimalFormatSymbols(ptBR).getDecimalSeparator(); // ','

        UnaryOperator<TextFormatter.Change> filter = change -> {
            String newText = change.getControlNewText();

            if (newText.isBlank()) return change;

            String digits = newText.replaceAll("\\D", "");

            if (digits.length() > 16) return null;

            if (digits.isEmpty()) {
                change.setText("");
                change.setRange(0, change.getControlText().length());
                return change;
            }

            String formatted;
            if (digits.length() == 1) formatted = "0" + decimalSep + "0" + digits;
            else if (digits.length() == 2) formatted = "0" + decimalSep + digits;
            else {
                String reais = digits.substring(0, digits.length() - 2);
                String cents = digits.substring(digits.length() - 2);
                formatted = reais + decimalSep + cents;
            }

            change.setText(formatted);
            change.setRange(0, change.getControlText().length());
            return change;
        };

        return new TextFormatter<>(filter);
    }
}
