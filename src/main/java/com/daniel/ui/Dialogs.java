package com.daniel.ui;

import com.daniel.util.Money;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;

import java.util.Optional;

public final class Dialogs {
    private Dialogs() {}

    public static void info(String message) {
        show(Alert.AlertType.INFORMATION, "Info", message);
    }

    public static void warn(String message) {
        show(Alert.AlertType.WARNING, "Atenção", message);
    }

    public static void error(String message) {
        show(Alert.AlertType.ERROR, "Erro", message == null ? "Ocorreu um erro." : message);
    }

    public static boolean confirm(String title, String message) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle(title == null ? "Confirmar" : title);
        a.setHeaderText(null);
        a.setContentText(message == null ? "Confirmar ação?" : message);

        Optional<ButtonType> r = a.showAndWait();
        return r.isPresent() && r.get() == ButtonType.OK;
    }

    private static void show(Alert.AlertType type, String title, String message) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(message == null ? "" : message);
        a.showAndWait();
    }

    public static String askText(String title, String label) {
        TextInputDialog d = new TextInputDialog();
        d.setTitle(title == null ? "Entrada" : title);
        d.setHeaderText(null);
        d.setContentText(label == null ? "Digite:" : label);

        return d.showAndWait()
                .map(String::trim)
                .orElse(null);
    }

    public static Long askAmountCents(String title, String header) {
        TextInputDialog d = new TextInputDialog();
        d.setTitle(title == null ? "Valor" : title);
        d.setHeaderText(header);
        d.setContentText("Valor:");

        Optional<String> opt = d.showAndWait();
        if (opt.isEmpty()) return null; // cancel

        String raw = opt.get().trim();
        if (raw.isBlank()) return 0L;

        try {
            long cents = Money.textToCentsOrZero(raw);
            if (cents < 0) {
                error("Valor inválido.");
                return null;
            }
            return cents;
        } catch (Exception e) {
            error("Valor inválido.");
            return null;
        }
    }
}
