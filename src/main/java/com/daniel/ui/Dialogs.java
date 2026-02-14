package com.daniel.ui;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;

import java.util.Optional;

public final class Dialogs {
    private Dialogs() {}

    public static void info(String title, String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title == null ? "Info" : title);
        a.setHeaderText(null);
        a.setContentText(message == null ? "" : message);
        a.showAndWait();
    }

    public static void info(String message) {
        info("Info", message);
    }

    public static void error(String message) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Erro");
        a.setHeaderText(null);
        a.setContentText(message == null ? "Ocorreu um erro." : message);
        a.showAndWait();
    }

    public static boolean confirm(String title, String header, String message) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle(title == null ? "Confirmar" : title);
        a.setHeaderText(header);
        a.setContentText(message == null ? "" : message);
        Optional<ButtonType> r = a.showAndWait();
        return r.isPresent() && r.get() == ButtonType.OK;
    }

    public static boolean confirm(String title, String message) {
        return confirm(title, null, message);
    }

    public static Long askAmountCents(String title, String header) {
        TextInputDialog d = new TextInputDialog();
        d.setTitle(title);
        d.setHeaderText(header);
        d.setContentText("Valor (ex: 1234,56):");

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

    private static long parseToCents(String input) {
        String s = input.replace("R$", "").trim();
        if (s.contains(",") && s.contains(".")) s = s.replace(".", "").replace(",", ".");
        else if (s.contains(",")) s = s.replace(",", ".");
        double v = Double.parseDouble(s);
        return Math.round(v * 100.0);
    }
}
