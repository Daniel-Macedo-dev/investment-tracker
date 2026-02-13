package com.daniel.ui;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;

import java.util.Optional;

public final class Dialogs {
    private Dialogs() {}

    public static void info(String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Info");
        a.setHeaderText(null);
        a.setContentText(message == null ? "" : message);
        a.showAndWait();
    }

    public static boolean confirm(String title, String message) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle(title == null ? "Confirmar" : title);
        a.setHeaderText(null);
        a.setContentText(message == null ? "" : message);
        Optional<ButtonType> r = a.showAndWait();
        return r.isPresent() && r.get() == ButtonType.OK;
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

    public static void error(String message) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Erro");
        a.setHeaderText(null);
        a.setContentText(message == null ? "Ocorreu um erro." : message);
        a.showAndWait();
    }

    public static void error(Throwable ex) {
        if (ex == null) { error("Ocorreu um erro."); return; }

        String msg = ex.getMessage();
        Throwable c = ex.getCause();
        if (c != null && c.getMessage() != null && !c.getMessage().isBlank()) {
            msg = (msg == null ? "" : msg) + "\n\nCausa: " + c.getMessage();
        }
        error(msg);
    }

    private static long parseToCents(String input) {
        String s = input.replace("R$", "").trim();
        if (s.contains(",") && s.contains(".")) s = s.replace(".", "").replace(",", ".");
        else if (s.contains(",")) s = s.replace(",", ".");
        double v = Double.parseDouble(s);
        return Math.round(v * 100.0);
    }
}
