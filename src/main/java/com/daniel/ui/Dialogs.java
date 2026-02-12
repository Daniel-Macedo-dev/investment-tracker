package com.daniel.ui;

import javafx.scene.control.Alert;
import javafx.scene.control.TextInputDialog;

public final class Dialogs {
    private Dialogs() {}

    public static Long askAmountCents(String title, String header) {
        TextInputDialog d = new TextInputDialog();
        d.setTitle(title);
        d.setHeaderText(header);
        d.setContentText("Valor:");

        return d.showAndWait()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(Dialogs::parseToCents)
                .orElse(null);
    }

    public static String askText(String title, String label) {
        TextInputDialog d = new TextInputDialog();
        d.setTitle(title);
        d.setHeaderText(null);
        d.setContentText(label);
        return d.showAndWait().map(String::trim).orElse(null);
    }

    public static void error(String message) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Erro");
        a.setHeaderText(null);
        a.setContentText(message == null ? "Ocorreu um erro." : message);
        a.showAndWait();
    }

    private static long parseToCents(String input) {
        String s = input.replace("R$", "").trim();
        if (s.contains(",") && s.contains(".")) s = s.replace(".", "").replace(",", ".");
        else if (s.contains(",")) s = s.replace(",", ".");
        double v = Double.parseDouble(s);
        return Math.round(v * 100.0);
    }
}
